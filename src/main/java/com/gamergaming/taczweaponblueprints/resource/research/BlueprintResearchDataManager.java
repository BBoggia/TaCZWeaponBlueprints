package com.gamergaming.taczweaponblueprints.resource.research;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
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
    public static final ResourceLocation DEFAULT_PROFILE = TaCZWeaponBlueprints.loc("duplicate_recovery");

    static final String TAG_DIRECTORY = "taczweaponblueprints/blueprint_tags";
    static final String PROFILE_DIRECTORY = "taczweaponblueprints/research_profiles";
    static final String RULE_DIRECTORY = "taczweaponblueprints/research_rules";

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

        try {
            BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(tags, profiles, rules);
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
        Publication previous = publication;
        publication = new Publication(prepared, previous.revision() + 1L);
        BlueprintResearchPolicyResolver.clearCache();
        BlueprintResearchDiagnostics.Summary summary = BlueprintResearchDiagnostics.summarize(prepared);
        TaCZWeaponBlueprints.LOGGER.info(
                "Applied blueprint research snapshot revision {}: {} tags, {} profiles, and {} rules "
                        + "({} exact, {} tag, and {} selector targets)",
                publication.revision(),
                summary.tagCount(),
                summary.profileCount(),
                summary.ruleCount(),
                summary.exactTargetCount(),
                summary.tagTargetCount(),
                summary.selectorTargetCount());
        if (!prepared.profiles().containsKey(DEFAULT_PROFILE)) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Blueprint research snapshot does not define the default profile {}; research policy is disabled",
                    DEFAULT_PROFILE);
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
        return policyFor(DEFAULT_PROFILE, blueprintId, playerData);
    }

    public BlueprintResearchPolicy policyFor(
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData) {
        return BlueprintResearchPolicyResolver.resolve(
                snapshot(),
                BlueprintDataManager.SERVER.getBlueprintDataMap(),
                profileId,
                blueprintId,
                playerData,
                ModConfigs.BLUEPRINT::isItemBlacklisted);
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
        try (BufferedReader reader = resource.openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
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
