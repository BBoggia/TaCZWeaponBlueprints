package com.gamergaming.taczweaponblueprints.resource.research;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalBuilder;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionAccess;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBuilder;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteOverlay;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.PublicationRevision;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;

public final class BlueprintResearchDataManager extends SimplePreparableReloadListener<BlueprintResearchSnapshot> {
    public static final BlueprintResearchDataManager INSTANCE = new BlueprintResearchDataManager();
    public static final ResourceLocation DEFAULT_PROFILE = BlueprintConfig.DEFAULT_RESEARCH_PROFILE;

    static final String TAG_DIRECTORY = "taczweaponblueprints/blueprint_tags";
    static final String PROFILE_DIRECTORY = "taczweaponblueprints/research_profiles";
    static final String RULE_DIRECTORY = "taczweaponblueprints/research_rules";
    static final String GROUP_DIRECTORY = "taczweaponblueprints/research_tree_groups";
    static final String TECH_TREE_DIRECTORY = "taczweaponblueprints/research_tech_trees";
    static final String TECH_TREE_ENTRY_DIRECTORY = "taczweaponblueprints/research_tech_tree_entries";
    static final String AUTOMATIC_PLACEMENT_PROFILE_DIRECTORY =
            "taczweaponblueprints/research_automatic_placement_profiles";
    static final int MAX_DEFINITION_JSON_CHARACTERS = 2_000_000;

    private volatile Publication publication = new Publication(BlueprintResearchSnapshot.EMPTY, 0L);

    private BlueprintResearchDataManager() {
    }

    @Override
    protected BlueprintResearchSnapshot prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, BlueprintLootTag> tags = loadDefinitions(
                resourceManager,
                TAG_DIRECTORY,
                BlueprintLootTag.CODEC,
                "blueprint tag");
        Map<ResourceLocation, BlueprintResearchProfile> profiles = loadDefinitions(
                resourceManager,
                PROFILE_DIRECTORY,
                BlueprintResearchProfile.CODEC,
                "research profile");
        Map<ResourceLocation, BlueprintResearchRule> rules = loadDefinitions(
                resourceManager,
                RULE_DIRECTORY,
                BlueprintResearchRule.CODEC,
                "research rule");
        Map<ResourceLocation, ResearchTreeGroupDefinition> groups = loadDefinitions(
                resourceManager,
                GROUP_DIRECTORY,
                ResearchTreeGroupDefinition.CODEC,
                "research-tree group");
        Map<ResourceLocation, ResearchTechTreeDefinition> techTrees = loadDefinitions(
                resourceManager,
                TECH_TREE_DIRECTORY,
                ResearchTechTreeDefinition.CODEC,
                "Research Tech Tree");
        Map<ResourceLocation, ResearchTechTreeEntryBundle> techTreeEntryBundles = loadDefinitions(
                resourceManager,
                TECH_TREE_ENTRY_DIRECTORY,
                ResearchTechTreeEntryBundle.CODEC,
                "Research Tech Tree entry bundle");
        Map<ResourceLocation, ResearchAutomaticPlacementProfile> automaticPlacementProfiles = loadDefinitions(
                resourceManager,
                AUTOMATIC_PLACEMENT_PROFILE_DIRECTORY,
                ResearchAutomaticPlacementProfile.CODEC,
                "Research Tech Tree automatic-placement profile");

        try {
            BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                    tags,
                    profiles,
                    rules,
                    groups,
                    techTrees,
                    techTreeEntryBundles,
                    automaticPlacementProfiles);
            BlueprintResearchIngredientValidator.validateExactItems(
                    snapshot,
                    id -> ForgeRegistries.ITEMS.containsKey(id)
                            && ForgeRegistries.ITEMS.getValue(id) != Items.AIR);
            return snapshot;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid blueprint research data: " + exception.getMessage(), exception);
        }
    }

    @Override
    protected void apply(BlueprintResearchSnapshot prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        BlueprintDataManager.CatalogPublication catalog =
                BlueprintDataManager.SERVER.catalogPublication();
        ResearchTechTreeCatalogValidator.validate(prepared, catalog.blueprints());
        Publication previous = publication;
        publication = new Publication(
                prepared, PublicationRevision.next(previous.revision()));
        AutomaticWeaponPlacementCandidateManager.INSTANCE.invalidateForRevisions(
                BlueprintDataManager.SERVER.catalogRevision(), publication.revision());
        BlueprintResearchPolicyResolver.clearCache();
        BlueprintResearchDiagnostics.Summary summary = BlueprintResearchDiagnostics.summarize(prepared);
        TaCZWeaponBlueprints.LOGGER.info(
                "Applied blueprint research snapshot revision {}: {} tags, {} profiles, {} rules, {} groups, "
                        + "{} Research Tech Trees, {} Tech Tree entry bundles, and {} automatic-placement profiles "
                        + "({} exact, {} tag, and {} selector targets; {} authored group members)",
                publication.revision(),
                summary.tagCount(),
                summary.profileCount(),
                summary.ruleCount(),
                summary.groupCount(),
                prepared.techTrees().size(),
                prepared.techTreeEntryBundles().size(),
                prepared.automaticPlacementProfiles().size(),
                summary.exactTargetCount(),
                summary.tagTargetCount(),
                summary.selectorTargetCount(),
                summary.groupMemberCount());
        ResourceLocation activeProfile = progressionConfig().activeProfileId();
        if (!prepared.profiles().containsKey(activeProfile)) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Blueprint research snapshot does not define the configured active profile {}; "
                            + "default research policy is disabled",
                    activeProfile);
        }
        Set<ResourceLocation> unresolvedIngredientTags = BlueprintResearchIngredientValidator.unresolvedTags(
                prepared,
                id -> !ForgeRegistries.ITEMS.tags()
                        .getTag(TagKey.create(Registries.ITEM, id))
                        .isEmpty());
        if (!unresolvedIngredientTags.isEmpty()) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Blueprint research data references {} unresolved item tags; affected costs remain unavailable: {}",
                    unresolvedIngredientTags.size(),
                    unresolvedIngredientTags.stream().sorted().limit(12).toList());
        }
    }

    /** Logs graph metrics only after the live TaCZ catalog has been initialized. */
    public void logActiveProfileAudit() {
        ResourceLocation activeProfile = progressionConfig().activeProfileId();
        Map<ResourceLocation, com.gamergaming.taczweaponblueprints.item.BlueprintData> activeCatalog =
                BlueprintDataManager.SERVER.getBlueprintDataMap();
        var access = ModConfigs.BLUEPRINT.accessSnapshot();
        Set<ResourceLocation> exemptBlueprints = BlueprintProgressionAccess.exemptBlueprintIds(
                access, activeCatalog);
        TreeSet<ResourceLocation> missingStartingBlueprints = new TreeSet<>();
        missingStartingBlueprints.addAll(access.startingBlueprints());
        missingStartingBlueprints.removeAll(activeCatalog.keySet());
        TreeSet<ResourceLocation> missingExactExemptions = new TreeSet<>();
        missingExactExemptions.addAll(access.progressionExemptBlueprints());
        missingExactExemptions.removeAll(activeCatalog.keySet());
        TreeSet<String> unmatchedItemTypes = new TreeSet<>(access.progressionExemptItemTypes());
        activeCatalog.values().stream()
                .map(com.gamergaming.taczweaponblueprints.item.BlueprintData::getItemType)
                .filter(java.util.Objects::nonNull)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .forEach(unmatchedItemTypes::remove);
        TaCZWeaponBlueprints.LOGGER.info(
                "Blueprint access policy: {} progression-exempt catalog entries and {} configured starting grants",
                exemptBlueprints.size(),
                access.startingBlueprints().size());
        if (!missingStartingBlueprints.isEmpty()
                || !missingExactExemptions.isEmpty()
                || !unmatchedItemTypes.isEmpty()) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Blueprint access policy has {} unavailable starting IDs {}, {} unavailable exact exemptions {}, "
                            + "and {} unmatched item types {}",
                    missingStartingBlueprints.size(),
                    missingStartingBlueprints.stream().limit(12).toList(),
                    missingExactExemptions.size(),
                    missingExactExemptions.stream().limit(12).toList(),
                    unmatchedItemTypes.size(),
                    unmatchedItemTypes.stream().limit(12).toList());
        }
        List<BlueprintResearchPolicyResolver.EntryPointResolution> entryPoints =
                BlueprintResearchPolicyResolver.entryPointResolutions(
                        snapshot(),
                        BlueprintDataManager.SERVER.getBlueprintDataMap(),
                        activeProfile,
                        ModConfigs.BLUEPRINT::isItemBlacklisted,
                        BlueprintProgressionAccess::isProgressionExempt);
        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                snapshot(),
                BlueprintDataManager.SERVER.getBlueprintDataMap(),
                activeProfile);
        BlueprintResearchDiagnostics.GroupAudit groupAudit = BlueprintResearchDiagnostics.auditGroups(
                snapshot(),
                BlueprintDataManager.SERVER.getBlueprintDataMap(),
                activeProfile);
        BlueprintResearchDiagnostics.ReverseEngineeringAudit reverseAudit =
                BlueprintResearchDiagnostics.auditReverseEngineering(
                        snapshot(),
                        BlueprintDataManager.SERVER.getBlueprintDataMap(),
                        activeProfile,
                        ModConfigs.BLUEPRINT::isItemBlacklisted,
                        BlueprintProgressionAccess::isProgressionExempt);
        if (audit.catalogSize() > 0) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Research graph audit for {}: {}/{} catalog entries assigned, {} tree-visible, {} roots, "
                            + "{} leaves, {} components, and {} independent entries",
                    activeProfile,
                    audit.assignedBlueprintCount(),
                    audit.catalogSize(),
                    audit.treeVisibleBlueprintCount(),
                    audit.rootCount(),
                    audit.leafCount(),
                    audit.componentCount(),
                    audit.independentBlueprintIds().size());
        }
        if (audit.hasStructuralProblems()) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Research graph audit found structural problems: empty tree={}, {} missing prerequisites {}, "
                            + "{} visible targets with hidden prerequisite paths {}, and {} competing definitions {}",
                    audit.emptyTree(),
                    audit.missingPrerequisiteIds().size(),
                    audit.missingPrerequisiteIds().stream().limit(12).toList(),
                    audit.hiddenPrerequisiteTargetIds().size(),
                    audit.hiddenPrerequisiteTargetIds().stream().limit(12).toList(),
                    audit.competitions().size(),
                    audit.competitions().stream().limit(12).toList());
        }
        if (!audit.unselectableResearchTargetIds().isEmpty()) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Research graph contains {} tree nodes that are enabled without discovery but cannot be selected "
                            + "at their effective visibility: {}",
                    audit.unselectableResearchTargetIds().size(),
                    audit.unselectableResearchTargetIds().stream().limit(12).toList());
        }
        for (BlueprintResearchPolicyResolver.EntryPointResolution entryPoint : entryPoints) {
            if (entryPoint.usesFallback()) {
                TaCZWeaponBlueprints.LOGGER.warn(
                        "Preferred research entry point {} is unavailable; using fallback {} for profile {}",
                        entryPoint.preferred().orElseThrow(),
                        entryPoint.resolved().orElseThrow(),
                        activeProfile);
            } else if (entryPoint.unavailable()) {
                TaCZWeaponBlueprints.LOGGER.error(
                        "No configured research entry point candidate beginning with {} is present for profile {}",
                        entryPoint.preferred().orElseThrow(),
                        activeProfile);
            }
        }
        if (groupAudit.catalogSize() > 0) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Research presentation audit for {}: {} authored groups, {}/{} live entries grouped, "
                            + "{} fallback entries, and {} absent authored members",
                    activeProfile,
                    groupAudit.authoredGroupCount(),
                    groupAudit.groupedCatalogCount(),
                    groupAudit.catalogSize(),
                    groupAudit.fallbackBlueprintIds().size(),
                    groupAudit.missingMemberIds().size());
        }
        if (groupAudit.hasProblems()) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Research presentation audit for {} references {} absent catalog members: {}",
                    activeProfile,
                    groupAudit.missingMemberIds().size(),
                    groupAudit.missingMemberIds().stream().limit(12).toList());
        }
        if (reverseAudit.catalogSize() > 0) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Reverse-engineering audit for {}: {}/{} canonical catalog entries are eligible",
                    activeProfile,
                    reverseAudit.eligibleBlueprintIds().size(),
                    reverseAudit.catalogSize());
        }
        if (reverseAudit.hasProblems()) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Reverse-engineering audit found {} unmatched rules {}, {} eligible entries without canonical "
                            + "recipes {}, {} blocked targets {}, {} progression-exempt targets {}, and {} "
                            + "explicit expert economy loops {}",
                    reverseAudit.unmatchedRuleIds().size(),
                    reverseAudit.unmatchedRuleIds().stream().limit(12).toList(),
                    reverseAudit.unavailableRecipeIds().size(),
                    reverseAudit.unavailableRecipeIds().stream().limit(12).toList(),
                    reverseAudit.blockedTargetIds().size(),
                    reverseAudit.blockedTargetIds().stream().limit(12).toList(),
                    reverseAudit.exemptTargetIds().size(),
                    reverseAudit.exemptTargetIds().stream().limit(12).toList(),
                    reverseAudit.expertEconomyLoopIds().size(),
                    reverseAudit.expertEconomyLoopIds().stream().limit(12).toList());
        }
    }

    public BlueprintResearchSnapshot snapshot() {
        return publication.snapshot();
    }

    public long revision() {
        return publication.revision();
    }

    public Publication publication() {
        return publication;
    }

    public BlueprintResearchPolicy policyFor(ResourceLocation blueprintId, IPlayerRecipeData playerData) {
        BlueprintProgressionConfigSnapshot config = progressionConfig();
        return resolvePolicy(config, config.activeProfileId(), blueprintId, playerData);
    }

    public BlueprintResearchPolicy policyFor(
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData) {
        BlueprintProgressionConfigSnapshot config = progressionConfig();
        return resolvePolicy(config, profileId, blueprintId, playerData);
    }

    public BlueprintProgressionConfigSnapshot progressionConfig() {
        return ModConfigs.BLUEPRINT.progressionSnapshot();
    }

    /** Builds the current disclosure-safe tree view for one player's progression. */
    public ResearchTreeGraph treeFor(IPlayerRecipeData playerData) {
        return treePublicationFor(playerData).graph();
    }

    /** Builds the graph and disclosure-safe presentation metadata as one value. */
    public ResearchTreePublication treePublicationFor(IPlayerRecipeData playerData) {
        BlueprintProgressionConfigSnapshot config = progressionConfig();
        ResolutionContext context = resolutionContext(
                config.activeProfileId(), config);
        return buildTree(context, playerData);
    }

    /** Builds Journal and tree from one catalog/research/automatic publication context. */
    public PlayerResearchPublication playerPublicationFor(IPlayerRecipeData playerData) {
        BlueprintProgressionConfigSnapshot config = progressionConfig();
        ResolutionContext context = resolutionContext(
                config.activeProfileId(), config);
        BlueprintJournalSnapshot journal = BlueprintJournalBuilder.build(
                context.catalog().blueprints(),
                context.research().snapshot(),
                context.config(),
                playerData,
                ModConfigs.BLUEPRINT::isItemBlacklisted,
                id -> BlueprintProgressionAccess.isProgressionExempt(
                        ModConfigs.BLUEPRINT.accessSnapshot(),
                        id,
                        context.catalog().blueprints().get(id)),
                context.automatic().prerequisitePlan().orElse(null));
        return new PlayerResearchPublication(
                journal,
                buildTree(context, playerData),
                context.catalog().revision(),
                context.research().revision());
    }

    private ResearchTreePublication buildTree(
            ResolutionContext context,
            IPlayerRecipeData playerData) {
        return ResearchTreeBuilder.buildPublication(
                context.catalog().blueprints(),
                context.research().snapshot(),
                context.config(),
                playerData,
                ModConfigs.BLUEPRINT::isItemBlacklisted,
                id -> BlueprintProgressionAccess.isProgressionExempt(
                        ModConfigs.BLUEPRINT.accessSnapshot(),
                        id,
                        context.catalog().blueprints().get(id)),
                context.automatic().candidates().orElse(null),
                context.automatic().prerequisitePlan().orElse(null));
    }

    private BlueprintResearchPolicy resolvePolicy(
            BlueprintProgressionConfigSnapshot config,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData) {
        ResolutionContext context = resolutionContext(profileId, config);
        BlueprintResearchPolicy datapackPolicy = BlueprintResearchPolicyResolver.resolve(
                context.research().snapshot(),
                context.catalog().blueprints(),
                profileId,
                blueprintId,
                playerData,
                ModConfigs.BLUEPRINT::isItemBlacklisted,
                id -> BlueprintProgressionAccess.isProgressionExempt(
                        ModConfigs.BLUEPRINT.accessSnapshot(),
                        id,
                        context.catalog().blueprints().get(id)));
        return AutomaticWeaponPrerequisiteOverlay.apply(
                config.apply(datapackPolicy),
                context.automatic().prerequisitePlan().orElse(null),
                playerData,
                ModConfigs.BLUEPRINT::isItemBlacklisted,
                config.maximumUndiscoveredVisibility().allowsServerSelection(),
                context.catalog().blueprints()::containsKey,
                id -> BlueprintProgressionAccess.isProgressionExempt(
                        ModConfigs.BLUEPRINT.accessSnapshot(),
                        id,
                        context.catalog().blueprints().get(id)));
    }

    private ResolutionContext resolutionContext(
            ResourceLocation profileId,
            BlueprintProgressionConfigSnapshot config) {
        Publication research = publication;
        BlueprintDataManager.CatalogPublication catalog =
                BlueprintDataManager.SERVER.catalogPublication();
        AutomaticWeaponPlacementCandidateManager.Context automatic = Optional
                .ofNullable(research.snapshot().profiles().get(profileId))
                .flatMap(BlueprintResearchProfile::techTree)
                .map(treeId -> AutomaticWeaponPlacementCandidateManager.INSTANCE.contextFor(
                        profileId,
                        treeId,
                        catalog.revision(),
                        research.revision()))
                .orElseGet(() -> new AutomaticWeaponPlacementCandidateManager.Context(
                        Optional.empty(), Optional.empty()));
        return new ResolutionContext(research, catalog, config, automatic);
    }

    public record PlayerResearchPublication(
            BlueprintJournalSnapshot journal,
            ResearchTreePublication tree,
            long catalogRevision,
            long researchRevision) {
        public PlayerResearchPublication {
            if (journal == null || tree == null
                    || catalogRevision < 0L || researchRevision < 0L) {
                throw new IllegalArgumentException(
                        "player research publication is invalid");
            }
        }
    }

    private record ResolutionContext(
            Publication research,
            BlueprintDataManager.CatalogPublication catalog,
            BlueprintProgressionConfigSnapshot config,
            AutomaticWeaponPlacementCandidateManager.Context automatic) {
        private ResolutionContext {
            if (research == null || catalog == null || config == null
                    || automatic == null) {
                throw new IllegalArgumentException(
                        "research resolution context is invalid");
            }
        }
    }

    static ResourceLocation definitionId(ResourceLocation resourceId, String directory) {
        String prefix = directory + "/";
        String path = resourceId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            throw new IllegalArgumentException("Resource is outside " + directory + ": " + resourceId);
        }
        String definitionPath = path.substring(prefix.length(), path.length() - ".json".length());
        ResourceLocation definitionId = ResourceLocation.tryBuild(resourceId.getNamespace(), definitionPath);
        if (definitionId == null
                || definitionId.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid or oversized definition ID derived from " + resourceId);
        }
        return definitionId;
    }

    private static <T> Map<ResourceLocation, T> loadDefinitions(
            ResourceManager resourceManager,
            String directory,
            Codec<T> codec,
            String typeName) {
        Map<ResourceLocation, T> definitions = new LinkedHashMap<>();
        resourceManager.listResources(directory, id -> id.getPath().endsWith(".json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ResourceLocation resourceId = entry.getKey();
                    ResourceLocation definitionId = definitionId(resourceId, directory);
                    T definition = readDefinition(resourceId, entry.getValue(), codec, typeName);
                    if (definitions.put(definitionId, definition) != null) {
                        throw new IllegalStateException("Duplicate blueprint " + typeName + " ID " + definitionId);
                    }
                    if (definitions.size() > BlueprintResearchSnapshot.MAX_DEFINITIONS_PER_TYPE) {
                        throw new IllegalStateException(
                                "Too many blueprint " + typeName + " definitions; maximum is "
                                        + BlueprintResearchSnapshot.MAX_DEFINITIONS_PER_TYPE);
                    }
                });
        return definitions;
    }

    private static <T> T readDefinition(
            ResourceLocation resourceId,
            Resource resource,
            Codec<T> codec,
            String typeName) {
        try (Reader reader = resource.openAsReader()) {
            JsonElement json = parseBoundedJson(reader);
            DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
            return result.result().orElseThrow(() -> new IllegalStateException(
                    "Invalid blueprint " + typeName + " " + resourceId + " from pack "
                            + resource.sourcePackId() + ": "
                            + result.error().map(DataResult.PartialResult::message).orElse("unknown codec error")));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException(
                    "Failed to read blueprint " + typeName + " " + resourceId
                            + " from pack " + resource.sourcePackId(),
                    exception);
        }
    }

    static JsonElement parseBoundedJson(Reader reader) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("blueprint definition reader cannot be null");
        }
        StringBuilder json = new StringBuilder(8_192);
        char[] buffer = new char[8_192];
        while (true) {
            int remaining = MAX_DEFINITION_JSON_CHARACTERS - json.length();
            int read = reader.read(buffer, 0, Math.min(buffer.length, remaining + 1));
            if (read < 0) {
                return JsonParser.parseString(json.toString());
            }
            if (read > remaining) {
                throw new IllegalStateException(
                        "Blueprint definition exceeds the "
                                + MAX_DEFINITION_JSON_CHARACTERS
                                + " character limit");
            }
            json.append(buffer, 0, read);
        }
    }

    public record Publication(BlueprintResearchSnapshot snapshot, long revision) {
        public Publication {
            if (snapshot == null) {
                throw new IllegalArgumentException("snapshot cannot be null");
            }
            if (revision < 0L) {
                throw new IllegalArgumentException("revision cannot be negative");
            }
        }
    }
}
