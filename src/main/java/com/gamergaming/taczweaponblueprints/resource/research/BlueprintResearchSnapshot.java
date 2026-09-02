package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;

import net.minecraft.resources.ResourceLocation;

public final class BlueprintResearchSnapshot {
    public static final int MAX_DEFINITIONS_PER_TYPE = 4096;
    public static final int MAX_PREREQUISITE_DEPTH = 64;
    public static final int MAX_TOTAL_TAG_VALUES = 65_536;
    public static final int MAX_TOTAL_RULE_TARGET_TERMS = 65_536;
    public static final int MAX_EXPANDED_TAG_BINDINGS = 262_144;
    public static final int MAX_TOTAL_INGREDIENT_TERMS = 65_536;
    public static final int MAX_TOTAL_PREREQUISITES = 65_536;
    public static final int MAX_TOTAL_GROUP_MEMBERS = 65_536;
    public static final int MAX_TOTAL_TECH_TREE_ENTRIES = 65_536;
    public static final int MAX_TOTAL_TECH_TREE_TARGET_TERMS = 65_536;
    public static final int MAX_EXPANDED_TECH_TREE_TAG_BINDINGS = 262_144;
    public static final BlueprintResearchSnapshot EMPTY = new BlueprintResearchSnapshot(
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
            Map.of(), Map.of(), Map.of());

    private final Map<ResourceLocation, BlueprintLootTag> tags;
    private final Map<ResourceLocation, BlueprintResearchProfile> profiles;
    private final Map<ResourceLocation, BlueprintResearchRule> rules;
    private final Map<ResourceLocation, ResearchTreeGroupDefinition> groups;
    private final Map<ResourceLocation, ResearchTechTreeDefinition> techTrees;
    private final Map<ResourceLocation, ResearchTechTreeEntryBundle> techTreeEntryBundles;
    private final Map<ResourceLocation, ResearchAutomaticPlacementProfile> automaticPlacementProfiles;
    private final Map<ResourceLocation, ResearchAutomaticPlacementProfile> automaticPlacementProfilesByTree;
    private final Map<ResourceLocation, List<RuleBinding>> rulesByProfile;
    private final Map<ResourceLocation, List<GroupBinding>> groupsByProfile;
    private final Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> placementsByProfile;
    private final Map<ResourceLocation, Map<ResourceLocation, ProgressionCoordinate>>
            techTreeProgressionByProfile;
    private final Map<ResourceLocation, TechTreeIndex> techTreeIndexes;

    private BlueprintResearchSnapshot(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups,
            Map<ResourceLocation, ResearchTechTreeDefinition> techTrees,
            Map<ResourceLocation, ResearchTechTreeEntryBundle> techTreeEntryBundles,
            Map<ResourceLocation, ResearchAutomaticPlacementProfile> automaticPlacementProfiles,
            Map<ResourceLocation, ResearchAutomaticPlacementProfile> automaticPlacementProfilesByTree,
            Map<ResourceLocation, List<RuleBinding>> rulesByProfile,
            Map<ResourceLocation, List<GroupBinding>> groupsByProfile,
            Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> placementsByProfile,
            Map<ResourceLocation, Map<ResourceLocation, ProgressionCoordinate>>
                    techTreeProgressionByProfile,
            Map<ResourceLocation, TechTreeIndex> techTreeIndexes) {
        this.tags = immutableMap(tags);
        this.profiles = immutableMap(profiles);
        this.rules = immutableMap(rules);
        this.groups = immutableMap(groups);
        this.techTrees = immutableMap(techTrees);
        this.techTreeEntryBundles = immutableMap(techTreeEntryBundles);
        this.automaticPlacementProfiles = immutableMap(automaticPlacementProfiles);
        this.automaticPlacementProfilesByTree = immutableMap(automaticPlacementProfilesByTree);
        this.rulesByProfile = immutableRuleMap(rulesByProfile);
        this.groupsByProfile = immutableGroupMap(groupsByProfile);
        this.placementsByProfile = immutablePlacementMap(placementsByProfile);
        this.techTreeProgressionByProfile = immutableProgressionMap(techTreeProgressionByProfile);
        this.techTreeIndexes = immutableTechTreeIndexMap(techTreeIndexes);
    }

    public static BlueprintResearchSnapshot create(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules) {
        return create(tags, profiles, rules, Map.of());
    }

    public static BlueprintResearchSnapshot create(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups) {
        return create(tags, profiles, rules, groups, Map.of(), Map.of());
    }

    public static BlueprintResearchSnapshot create(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups,
            Map<ResourceLocation, ResearchTechTreeDefinition> techTrees,
            Map<ResourceLocation, ResearchTechTreeEntryBundle> techTreeEntryBundles) {
        return create(tags, profiles, rules, groups, techTrees, techTreeEntryBundles, Map.of());
    }

    public static BlueprintResearchSnapshot create(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups,
            Map<ResourceLocation, ResearchTechTreeDefinition> techTrees,
            Map<ResourceLocation, ResearchTechTreeEntryBundle> techTreeEntryBundles,
            Map<ResourceLocation, ResearchAutomaticPlacementProfile> automaticPlacementProfiles) {
        Map<ResourceLocation, BlueprintLootTag> sortedTags = sortedCopy(tags);
        Map<ResourceLocation, BlueprintResearchProfile> sortedProfiles = sortedCopy(profiles);
        Map<ResourceLocation, BlueprintResearchRule> sortedRules = sortedCopy(rules);
        Map<ResourceLocation, ResearchTreeGroupDefinition> sortedGroups = sortedCopy(groups);
        Map<ResourceLocation, ResearchTechTreeDefinition> sortedTechTrees = sortedCopy(techTrees);
        Map<ResourceLocation, ResearchTechTreeEntryBundle> sortedTechTreeEntryBundles =
                sortedCopy(techTreeEntryBundles);
        Map<ResourceLocation, ResearchAutomaticPlacementProfile> sortedAutomaticPlacementProfiles =
                sortedCopy(automaticPlacementProfiles);
        validateDefinitionCounts(
                sortedTags,
                sortedProfiles,
                sortedRules,
                sortedGroups,
                sortedTechTrees,
                sortedTechTreeEntryBundles,
                sortedAutomaticPlacementProfiles);
        validateDefinitionIds(
                sortedTags,
                sortedProfiles,
                sortedRules,
                sortedGroups,
                sortedTechTrees,
                sortedTechTreeEntryBundles,
                sortedAutomaticPlacementProfiles);
        validateDefinitions(
                sortedTags,
                sortedProfiles,
                sortedRules,
                sortedGroups,
                sortedTechTrees,
                sortedTechTreeEntryBundles,
                sortedAutomaticPlacementProfiles);
        sortedProfiles.values().forEach(profile -> {
            profile.researchCost().validateForSnapshot();
            profile.reverseEngineering().validateForSnapshot();
            BlueprintResearchPolicyDefinition.fromProfile(profile);
        });
        validateProfileTechTrees(sortedProfiles, sortedTechTrees);
        Map<ResourceLocation, ResearchAutomaticPlacementProfile> automaticProfilesByTree =
                compileAutomaticPlacementProfiles(sortedAutomaticPlacementProfiles, sortedTechTrees);

        Map<ResourceLocation, List<RuleBinding>> byProfile = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BlueprintResearchRule> entry : sortedRules.entrySet()) {
            ResourceLocation ruleId = entry.getKey();
            BlueprintResearchRule rule = entry.getValue();
            rule.validateForSnapshot();
            BlueprintResearchProfile profile = sortedProfiles.get(rule.profile());
            if (profile == null) {
                throw new IllegalArgumentException(
                        "research rule " + ruleId + " references missing profile " + rule.profile());
            }
            for (ResourceLocation tagId : rule.target().tags()) {
                if (!sortedTags.containsKey(tagId)) {
                    throw new IllegalArgumentException(
                            "research rule " + ruleId + " references missing blueprint tag " + tagId);
                }
            }

            // Validate every rule overlay even when it is currently shadowed by a
            // higher-priority definition. A future datapack replacement must not
            // expose an invalid economy that was silently accepted earlier.
            BlueprintResearchPolicyDefinition.fromProfile(profile)
                    .apply(ruleId, rule, BlueprintResearchTarget.MatchSpecificity.EXACT);
            byProfile.computeIfAbsent(rule.profile(), ignored -> new ArrayList<>())
                    .add(new RuleBinding(ruleId, rule));
        }
        byProfile.values().forEach(bindings ->
                bindings.sort(Comparator.comparing(binding -> binding.ruleId().toString())));
        validateAggregateLimits(
                sortedTags,
                sortedProfiles,
                sortedRules,
                sortedGroups,
                sortedTechTreeEntryBundles);
        Map<ResourceLocation, TechTreeIndex> techTreeIndexes = compileTechTreeEntries(
                sortedTags,
                sortedTechTrees,
                sortedTechTreeEntryBundles);
        validatePrerequisites(sortedProfiles, byProfile);
        GroupIndex groupIndex = compileGroups(sortedProfiles, sortedGroups, byProfile);
        Map<ResourceLocation, Map<ResourceLocation, ProgressionCoordinate>> progressionByProfile =
                compileTechTreeProgression(
                        sortedProfiles,
                        byProfile,
                        techTreeIndexes);
        return new BlueprintResearchSnapshot(
                sortedTags,
                sortedProfiles,
                sortedRules,
                sortedGroups,
                sortedTechTrees,
                sortedTechTreeEntryBundles,
                sortedAutomaticPlacementProfiles,
                automaticProfilesByTree,
                byProfile,
                groupIndex.groupsByProfile(),
                groupIndex.placementsByProfile(),
                progressionByProfile,
                techTreeIndexes);
    }

    public Map<ResourceLocation, BlueprintLootTag> tags() {
        return tags;
    }

    public Map<ResourceLocation, BlueprintResearchProfile> profiles() {
        return profiles;
    }

    public Map<ResourceLocation, BlueprintResearchRule> rules() {
        return rules;
    }

    public Map<ResourceLocation, ResearchTreeGroupDefinition> groups() {
        return groups;
    }

    public Map<ResourceLocation, ResearchTechTreeDefinition> techTrees() {
        return techTrees;
    }

    public Map<ResourceLocation, ResearchTechTreeEntryBundle> techTreeEntryBundles() {
        return techTreeEntryBundles;
    }

    public Map<ResourceLocation, ResearchAutomaticPlacementProfile> automaticPlacementProfiles() {
        return automaticPlacementProfiles;
    }

    public java.util.Optional<ResearchAutomaticPlacementProfile> automaticPlacementProfileForTree(
            ResourceLocation treeId) {
        return treeId == null
                ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(automaticPlacementProfilesByTree.get(treeId));
    }

    public boolean usesAutomaticWeaponPlacement(ResourceLocation profileId) {
        BlueprintResearchProfile profile = profileId == null ? null : profiles.get(profileId);
        return profile != null
                && profile.techTree()
                        .map(techTrees::get)
                        .filter(ResearchTechTreeDefinition::usesAutomaticWeaponPlacement)
                        .isPresent();
    }

    public Map<ResourceLocation, List<RuleBinding>> rulesByProfile() {
        return rulesByProfile;
    }

    public List<RuleBinding> rulesForProfile(ResourceLocation profileId) {
        return rulesByProfile.getOrDefault(profileId, List.of());
    }

    public List<GroupBinding> groupsForProfile(ResourceLocation profileId) {
        return groupsByProfile.getOrDefault(profileId, List.of());
    }

    public List<TechTreeEntryBinding> techTreeEntriesFor(ResourceLocation treeId) {
        TechTreeIndex index = techTreeIndexes.get(treeId);
        return index == null ? List.of() : index.entries();
    }

    /** Compiled rank authority for exact prerequisite-graph members in one profile. */
    public Map<ResourceLocation, ProgressionCoordinate> techTreeProgressionForProfile(
            ResourceLocation profileId) {
        return profileId == null
                ? Map.of()
                : techTreeProgressionByProfile.getOrDefault(profileId, Map.of());
    }

    public java.util.Optional<ProgressionCoordinate> techTreeProgressionFor(
            ResourceLocation profileId,
            ResourceLocation blueprintId) {
        if (profileId == null || blueprintId == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(
                techTreeProgressionForProfile(profileId).get(blueprintId));
    }

    List<TechTreeEntryBinding> exactTechTreeEntriesFor(
            ResourceLocation treeId,
            ResourceLocation blueprintId) {
        TechTreeIndex index = techTreeIndexes.get(treeId);
        return index == null ? List.of() : index.exact().getOrDefault(blueprintId, List.of());
    }

    List<TechTreeEntryBinding> tagTechTreeEntriesFor(
            ResourceLocation treeId,
            ResourceLocation blueprintId) {
        TechTreeIndex index = techTreeIndexes.get(treeId);
        return index == null ? List.of() : index.tags().getOrDefault(blueprintId, List.of());
    }

    List<TechTreeEntryBinding> selectorTechTreeEntriesFor(ResourceLocation treeId) {
        TechTreeIndex index = techTreeIndexes.get(treeId);
        return index == null ? List.of() : index.selectors();
    }

    java.util.Optional<TechTreeEntryBinding> staticTechTreeEntryFor(
            ResourceLocation treeId,
            ResourceLocation blueprintId) {
        TechTreeIndex index = techTreeIndexes.get(treeId);
        return selectStaticTechTreeEntry(blueprintId, index);
    }

    public java.util.Optional<ResearchTreeGroupPlacement> placementFor(
            ResourceLocation profileId,
            ResourceLocation blueprintId) {
        if (profileId == null || blueprintId == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(
                placementsByProfile.getOrDefault(profileId, Map.of()).get(blueprintId));
    }

    private static void validateDefinitionCounts(
            Map<?, ?> tags,
            Map<?, ?> profiles,
            Map<?, ?> rules,
            Map<?, ?> groups,
            Map<?, ?> techTrees,
            Map<?, ?> techTreeEntryBundles,
            Map<?, ?> automaticPlacementProfiles) {
        if (tags.size() > MAX_DEFINITIONS_PER_TYPE
                || profiles.size() > MAX_DEFINITIONS_PER_TYPE
                || rules.size() > MAX_DEFINITIONS_PER_TYPE
                || groups.size() > MAX_DEFINITIONS_PER_TYPE
                || techTrees.size() > MAX_DEFINITIONS_PER_TYPE
                || techTreeEntryBundles.size() > MAX_DEFINITIONS_PER_TYPE
                || automaticPlacementProfiles.size() > MAX_DEFINITIONS_PER_TYPE) {
            throw new IllegalArgumentException(
                    "research data cannot contain more than " + MAX_DEFINITIONS_PER_TYPE
                            + " definitions of one type");
        }
    }

    private static void validateAggregateLimits(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups,
            Map<ResourceLocation, ResearchTechTreeEntryBundle> techTreeEntryBundles) {
        long tagValues = tags.values().stream().mapToLong(tag -> tag.values().size()).sum();
        if (tagValues > MAX_TOTAL_TAG_VALUES) {
            throw new IllegalArgumentException(
                    "research data cannot contain more than " + MAX_TOTAL_TAG_VALUES + " total blueprint-tag values");
        }

        long targetTerms = 0L;
        long expandedTagBindings = 0L;
        long prerequisiteIds = 0L;
        long ingredientTerms = profiles.values().stream()
                .map(BlueprintResearchProfile::researchCost)
                .mapToLong(BlueprintResearchSnapshot::ingredientTermCount)
                .sum();
        ingredientTerms += profiles.values().stream()
                .map(BlueprintResearchProfile::reverseEngineering)
                .map(BlueprintReverseEngineeringPolicy::cost)
                .mapToLong(BlueprintResearchSnapshot::ingredientTermCount)
                .sum();
        for (BlueprintResearchRule rule : rules.values()) {
            targetTerms += rule.target().blueprints().size() + rule.target().tags().size();
            targetTerms += rule.target().selector().map(selector -> selector.termCount()).orElse(0);
            prerequisiteIds += rule.prerequisiteRequirements()
                    .map(values -> Math.multiplyExact(
                            (long) rule.target().blueprints().size(),
                            values.alternativeCount()))
                    .orElse(0L);
            ingredientTerms += rule.researchCost()
                    .map(BlueprintResearchSnapshot::ingredientTermCount)
                    .orElse(0L);
            ingredientTerms += rule.reverseEngineering()
                    .flatMap(BlueprintReverseEngineeringOverride::cost)
                    .map(BlueprintResearchSnapshot::ingredientTermCount)
                    .orElse(0L);
            for (ResourceLocation tagId : rule.target().tags()) {
                BlueprintLootTag tag = tags.get(tagId);
                if (tag != null) {
                    expandedTagBindings += tag.values().size();
                }
            }
        }
        if (targetTerms > MAX_TOTAL_RULE_TARGET_TERMS) {
            throw new IllegalArgumentException(
                    "research rules cannot contain more than " + MAX_TOTAL_RULE_TARGET_TERMS + " total target terms");
        }
        if (expandedTagBindings > MAX_EXPANDED_TAG_BINDINGS) {
            throw new IllegalArgumentException(
                    "research rules cannot expand to more than " + MAX_EXPANDED_TAG_BINDINGS + " tag bindings");
        }
        if (prerequisiteIds > MAX_TOTAL_PREREQUISITES) {
            throw new IllegalArgumentException(
                    "research rules cannot contain more than " + MAX_TOTAL_PREREQUISITES
                            + " expanded prerequisite bindings");
        }
        if (ingredientTerms > MAX_TOTAL_INGREDIENT_TERMS) {
            throw new IllegalArgumentException(
                    "research data cannot contain more than " + MAX_TOTAL_INGREDIENT_TERMS
                            + " total ingredient terms");
        }
        long groupMembers = groups.values().stream()
                .mapToLong(ResearchTreeGroupDefinition::memberCount)
                .sum();
        if (groupMembers > MAX_TOTAL_GROUP_MEMBERS) {
            throw new IllegalArgumentException(
                    "research-tree groups cannot contain more than " + MAX_TOTAL_GROUP_MEMBERS
                            + " total members");
        }
        long techTreeEntries = techTreeEntryBundles.values().stream()
                .mapToLong(bundle -> bundle.entries().size())
                .sum();
        if (techTreeEntries > MAX_TOTAL_TECH_TREE_ENTRIES) {
            throw new IllegalArgumentException(
                    "Research Tech Tree data cannot contain more than "
                            + MAX_TOTAL_TECH_TREE_ENTRIES + " total entries");
        }
        long techTreeTargetTerms = techTreeEntryBundles.values().stream()
                .mapToLong(ResearchTechTreeEntryBundle::targetTermCount)
                .sum();
        if (techTreeTargetTerms > MAX_TOTAL_TECH_TREE_TARGET_TERMS) {
            throw new IllegalArgumentException(
                    "Research Tech Tree data cannot contain more than "
                            + MAX_TOTAL_TECH_TREE_TARGET_TERMS + " total target terms");
        }
        long expandedTechTreeTagBindings = 0L;
        for (ResearchTechTreeEntryBundle bundle : techTreeEntryBundles.values()) {
            for (ResearchTechTreeEntryBundle.Entry entry : bundle.entries()) {
                for (ResourceLocation tagId : entry.target().tags()) {
                    BlueprintLootTag tag = tags.get(tagId);
                    if (tag != null) {
                        expandedTechTreeTagBindings += tag.values().size();
                    }
                }
            }
        }
        if (expandedTechTreeTagBindings > MAX_EXPANDED_TECH_TREE_TAG_BINDINGS) {
            throw new IllegalArgumentException(
                    "Research Tech Tree data cannot expand to more than "
                            + MAX_EXPANDED_TECH_TREE_TAG_BINDINGS + " tag bindings");
        }
    }

    private static long ingredientTermCount(BlueprintResearchCost cost) {
        return cost.ingredients().stream()
                .mapToLong(ingredient -> ingredient.items().isEmpty() ? 1L : ingredient.items().size())
                .sum();
    }

    private static void validateDefinitions(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups,
            Map<ResourceLocation, ResearchTechTreeDefinition> techTrees,
            Map<ResourceLocation, ResearchTechTreeEntryBundle> techTreeEntryBundles,
            Map<ResourceLocation, ResearchAutomaticPlacementProfile> automaticPlacementProfiles) {
        for (Map.Entry<ResourceLocation, BlueprintLootTag> entry : tags.entrySet()) {
            BlueprintLootTag tag = entry.getValue();
            if (tag == null
                    || tag.format() != BlueprintLootTag.CURRENT_FORMAT
                    || tag.values().isEmpty()
                    || tag.values().size() > BlueprintLootTag.MAX_VALUES
                    || tag.values().stream().anyMatch(value ->
                            value == null
                                    || value.toString().length()
                                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)) {
                throw new IllegalArgumentException("invalid programmatic blueprint tag " + entry.getKey());
            }
        }
        if (profiles.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("research profile definitions cannot be null");
        }
        profiles.values().forEach(BlueprintResearchProfile::validateForSnapshot);
        if (rules.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("research rule definitions cannot be null");
        }
        if (groups.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("research-tree group definitions cannot be null");
        }
        if (techTrees.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Research Tech Tree definitions cannot be null");
        }
        if (techTreeEntryBundles.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Research Tech Tree entry bundles cannot be null");
        }
        if (automaticPlacementProfiles.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("automatic-placement profiles cannot be null");
        }
        techTrees.values().forEach(ResearchTechTreeDefinition::validateForSnapshot);
        techTreeEntryBundles.values().forEach(ResearchTechTreeEntryBundle::validateForSnapshot);
        automaticPlacementProfiles.values().forEach(ResearchAutomaticPlacementProfile::validateForSnapshot);
    }

    private static void validateProfileTechTrees(
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, ResearchTechTreeDefinition> techTrees) {
        for (Map.Entry<ResourceLocation, BlueprintResearchProfile> entry : profiles.entrySet()) {
            java.util.Optional<ResourceLocation> selectedTree = entry.getValue().techTree();
            if (selectedTree.isPresent() && !techTrees.containsKey(selectedTree.orElseThrow())) {
                throw new IllegalArgumentException(
                        "research profile " + entry.getKey() + " references missing Research Tech Tree "
                                + selectedTree.orElseThrow());
            }
        }
    }

    private static Map<ResourceLocation, ResearchAutomaticPlacementProfile> compileAutomaticPlacementProfiles(
            Map<ResourceLocation, ResearchAutomaticPlacementProfile> profiles,
            Map<ResourceLocation, ResearchTechTreeDefinition> techTrees) {
        Map<ResourceLocation, ResearchAutomaticPlacementProfile> byTree = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> owners = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ResearchAutomaticPlacementProfile> entry : profiles.entrySet()) {
            ResourceLocation profileId = entry.getKey();
            ResearchAutomaticPlacementProfile profile = entry.getValue();
            ResearchTechTreeDefinition tree = techTrees.get(profile.tree());
            if (tree == null) {
                throw new IllegalArgumentException(
                        "automatic-placement profile " + profileId
                                + " references missing Research Tech Tree " + profile.tree());
            }
            if (!tree.usesAutomaticWeaponPlacement()) {
                throw new IllegalArgumentException(
                        "automatic-placement profile " + profileId
                                + " references authored-only Research Tech Tree " + profile.tree());
            }
            if (tree.domain(com.gamergaming.taczweaponblueprints.research.tree
                    .ResearchTechTreeContract.Domain.WEAPONS).isEmpty()) {
                throw new IllegalArgumentException(
                        "automatic Research Tech Tree " + profile.tree()
                                + " must define a Weapons domain");
            }
            if (!profile.mode().assignsPlacement()
                    || !profile.reviewHandling().assignsPlacement()) {
                throw new IllegalArgumentException(
                        "automatic-placement profile " + profileId
                                + " must place normal and review-bearing weapons");
            }
            ResourceLocation previous = owners.putIfAbsent(profile.tree(), profileId);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree " + profile.tree()
                                + " has multiple automatic-placement profiles " + previous
                                + " and " + profileId);
            }
            byTree.put(profile.tree(), profile);
        }
        techTrees.forEach((treeId, tree) -> {
            if (tree.usesAutomaticWeaponPlacement() && !byTree.containsKey(treeId)) {
                throw new IllegalArgumentException(
                        "automatic Research Tech Tree " + treeId
                                + " requires exactly one automatic-placement profile");
            }
        });
        return Collections.unmodifiableMap(byTree);
    }

    private static Map<ResourceLocation, TechTreeIndex> compileTechTreeEntries(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, ResearchTechTreeDefinition> techTrees,
            Map<ResourceLocation, ResearchTechTreeEntryBundle> bundles) {
        Map<ResourceLocation, List<TechTreeEntryBinding>> byTree = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ResearchTechTreeEntryBundle> bundleEntry : bundles.entrySet()) {
            ResourceLocation bundleId = bundleEntry.getKey();
            ResearchTechTreeEntryBundle bundle = bundleEntry.getValue();
            bundle.validateForSnapshot();
            ResearchTechTreeDefinition tree = techTrees.get(bundle.tree());
            if (tree == null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree entry bundle " + bundleId + " references missing tree "
                                + bundle.tree());
            }
            for (int entryIndex = 0; entryIndex < bundle.entries().size(); entryIndex++) {
                int stableEntryIndex = entryIndex;
                ResearchTechTreeEntryBundle.Entry placement = bundle.entries().get(stableEntryIndex);
                for (ResourceLocation tagId : placement.target().tags()) {
                    if (!tags.containsKey(tagId)) {
                        throw new IllegalArgumentException(
                                "Research Tech Tree entry " + bundleId + "#" + stableEntryIndex
                                        + " references missing blueprint tag " + tagId);
                    }
                }
                ResearchTechTreeDefinition.DomainDefinition laneDomain = tree.domainForLane(placement.lane())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Research Tech Tree entry " + bundleId + "#" + stableEntryIndex
                                        + " references missing lane " + placement.lane()
                                        + " in tree " + bundle.tree()));
                if (laneDomain.domain() != placement.domain()) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree entry " + bundleId + "#" + stableEntryIndex
                                    + " places lane " + placement.lane() + " in domain "
                                    + placement.domain() + " but the tree defines it in "
                                    + laneDomain.domain());
                }
                if (!tree.containsTier(placement.tier())) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree entry " + bundleId + "#" + stableEntryIndex
                                    + " references missing tier " + placement.tier());
                }
                byTree.computeIfAbsent(bundle.tree(), ignored -> new ArrayList<>())
                        .add(new TechTreeEntryBinding(bundleId, stableEntryIndex, bundle, placement));
            }
        }
        byTree.values().forEach(bindings -> bindings.sort(Comparator
                .comparing((TechTreeEntryBinding value) -> value.bundleId().toString())
                .thenComparingInt(TechTreeEntryBinding::entryIndex)));
        Map<ResourceLocation, TechTreeIndex> indexes = new LinkedHashMap<>();
        byTree.forEach((treeId, bindings) -> indexes.put(treeId, buildTechTreeIndex(bindings, tags)));
        return indexes;
    }

    private static TechTreeIndex buildTechTreeIndex(
            List<TechTreeEntryBinding> entries,
            Map<ResourceLocation, BlueprintLootTag> tags) {
        Map<ResourceLocation, LinkedHashSet<TechTreeEntryBinding>> exact = new LinkedHashMap<>();
        Map<ResourceLocation, LinkedHashSet<TechTreeEntryBinding>> expandedTags = new LinkedHashMap<>();
        List<TechTreeEntryBinding> selectors = new ArrayList<>();
        for (TechTreeEntryBinding binding : entries) {
            BlueprintResearchTarget target = binding.entry().target();
            target.blueprints().forEach(blueprintId ->
                    exact.computeIfAbsent(blueprintId, ignored -> new LinkedHashSet<>()).add(binding));
            for (ResourceLocation tagId : target.tags()) {
                BlueprintLootTag tag = tags.get(tagId);
                if (tag != null) {
                    tag.values().forEach(blueprintId ->
                            expandedTags.computeIfAbsent(blueprintId, ignored -> new LinkedHashSet<>())
                                    .add(binding));
                }
            }
            if (target.selector().isPresent()) {
                selectors.add(binding);
            }
        }
        Comparator<TechTreeEntryBinding> order = techTreeBindingOrder();
        selectors.sort(order);
        return new TechTreeIndex(
                List.copyOf(entries),
                immutableBindingSetMap(exact, order),
                immutableBindingSetMap(expandedTags, order),
                List.copyOf(selectors));
    }

    private static Map<ResourceLocation, List<TechTreeEntryBinding>> immutableBindingSetMap(
            Map<ResourceLocation, LinkedHashSet<TechTreeEntryBinding>> values,
            Comparator<TechTreeEntryBinding> order) {
        Map<ResourceLocation, List<TechTreeEntryBinding>> immutable = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    List<TechTreeEntryBinding> sorted = new ArrayList<>(entry.getValue());
                    sorted.sort(order);
                    immutable.put(entry.getKey(), List.copyOf(sorted));
                });
        return Collections.unmodifiableMap(immutable);
    }

    private static Comparator<TechTreeEntryBinding> techTreeBindingOrder() {
        return Comparator
                .comparingInt((TechTreeEntryBinding value) -> value.bundle().priority()).reversed()
                .thenComparing(value -> value.bundleId().toString())
                .thenComparingInt(TechTreeEntryBinding::entryIndex);
    }

    private static Map<ResourceLocation, Map<ResourceLocation, ProgressionCoordinate>>
            compileTechTreeProgression(
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, List<RuleBinding>> rulesByProfile,
            Map<ResourceLocation, TechTreeIndex> techTreeIndexes) {
        Map<ResourceLocation, Map<ResourceLocation, ProgressionCoordinate>> compiled =
                new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BlueprintResearchProfile> profileEntry : profiles.entrySet()) {
            java.util.Optional<ResourceLocation> treeId = profileEntry.getValue().techTree();
            if (treeId.isEmpty()) {
                continue;
            }
            Map<ResourceLocation, List<ResourceLocation>> graph = prerequisiteGraphForProfile(
                    profileEntry.getKey(),
                    rulesByProfile);
            Map<ResourceLocation, TechTreeEntryBinding> placements = new LinkedHashMap<>();
            TechTreeIndex techTreeIndex = techTreeIndexes.get(treeId.orElseThrow());
            for (ResourceLocation blueprintId : allGraphIds(graph)) {
                selectStaticTechTreeEntry(blueprintId, techTreeIndex)
                        .ifPresent(binding -> placements.put(blueprintId, binding));
            }
            compiled.put(
                    profileEntry.getKey(),
                    ResearchTechTreeProgressionResolver.resolve(
                            profileEntry.getKey(), graph, placements));
        }
        return Collections.unmodifiableMap(compiled);
    }

    private static Set<ResourceLocation> allGraphIds(
            Map<ResourceLocation, List<ResourceLocation>> graph) {
        Set<ResourceLocation> ids = new LinkedHashSet<>(graph.keySet());
        graph.values().forEach(ids::addAll);
        return ids;
    }

    private static java.util.Optional<TechTreeEntryBinding> selectStaticTechTreeEntry(
            ResourceLocation blueprintId,
            TechTreeIndex index) {
        if (index == null) {
            return java.util.Optional.empty();
        }
        List<TechTreeEntryBinding> exact = index.exact().getOrDefault(blueprintId, List.of());
        List<TechTreeEntryBinding> tags = index.tags().getOrDefault(blueprintId, List.of());
        return ResearchTechTreePlacementResolver.selectStaticBinding(exact, tags);
    }

    @SafeVarargs
    private static void validateDefinitionIds(Map<ResourceLocation, ?>... definitionMaps) {
        for (Map<ResourceLocation, ?> definitions : definitionMaps) {
            if (definitions.keySet().stream().anyMatch(id ->
                    id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)) {
                throw new IllegalArgumentException("research data contains an invalid or oversized definition ID");
            }
        }
    }

    private static void validatePrerequisites(
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, List<RuleBinding>> byProfile) {
        for (ResourceLocation profileId : profiles.keySet()) {
            Map<ResourceLocation, List<ResourceLocation>> graph = prerequisiteGraphForProfile(
                    profileId,
                    byProfile);

            Set<ResourceLocation> complete = new LinkedHashSet<>();
            for (ResourceLocation blueprintId : graph.keySet()) {
                visitPrerequisite(blueprintId, graph, complete, new LinkedHashSet<>());
            }
        }
    }

    private static Map<ResourceLocation, List<ResourceLocation>> prerequisiteGraphForProfile(
            ResourceLocation profileId,
            Map<ResourceLocation, List<RuleBinding>> byProfile) {
        Map<ResourceLocation, List<RuleBinding>> exactRules = new LinkedHashMap<>();
        for (RuleBinding binding : byProfile.getOrDefault(profileId, List.of())) {
            for (ResourceLocation targetId : binding.rule().target().blueprints()) {
                exactRules.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(binding);
            }
        }

        Map<ResourceLocation, ResearchRequirements> requirements = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<RuleBinding>> entry : exactRules.entrySet()) {
            RuleBinding selected = selectExact(entry.getValue());
            ResearchRequirements selectedRequirements = selected.rule()
                    .prerequisiteRequirements().orElse(ResearchRequirements.EMPTY);
            try {
                selectedRequirements.validateFor(entry.getKey());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        exception.getMessage() + " in rule " + selected.ruleId(),
                        exception);
            }
            requirements.put(entry.getKey(), selectedRequirements);
        }
        ResearchRequirements.validateConservativeGraph(
                requirements, MAX_PREREQUISITE_DEPTH);
        Map<ResourceLocation, List<ResourceLocation>> graph = new LinkedHashMap<>();
        requirements.forEach((dependent, value) ->
                graph.put(dependent, value.conservativeAlternatives()));
        return graph;
    }

    private static GroupIndex compileGroups(
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups,
            Map<ResourceLocation, List<RuleBinding>> rulesByProfile) {
        Map<ResourceLocation, List<GroupBinding>> byProfile = new LinkedHashMap<>();
        Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> placements =
                new LinkedHashMap<>();
        Map<ResourceLocation, Map<ResourceLocation, ResourceLocation>> owners = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, ResearchTreeGroupDefinition> entry : groups.entrySet()) {
            ResourceLocation groupId = entry.getKey();
            ResearchTreeGroupDefinition definition = entry.getValue();
            definition.validateForSnapshot();
            if (!profiles.containsKey(definition.profile())) {
                throw new IllegalArgumentException(
                        "research-tree group " + groupId + " references missing profile "
                                + definition.profile());
            }
            byProfile.computeIfAbsent(definition.profile(), ignored -> new ArrayList<>())
                    .add(new GroupBinding(groupId, definition));
            Map<ResourceLocation, ResearchTreeGroupPlacement> profilePlacements =
                    placements.computeIfAbsent(definition.profile(), ignored -> new LinkedHashMap<>());
            Map<ResourceLocation, ResourceLocation> profileOwners =
                    owners.computeIfAbsent(definition.profile(), ignored -> new LinkedHashMap<>());
            for (int rank = 0; rank < definition.ranks().size(); rank++) {
                List<ResourceLocation> members = definition.ranks().get(rank);
                for (int order = 0; order < members.size(); order++) {
                    ResourceLocation member = members.get(order);
                    ResourceLocation previous = profileOwners.putIfAbsent(member, groupId);
                    if (previous != null) {
                        throw new IllegalArgumentException(
                                "blueprint " + member + " belongs to multiple research-tree groups "
                                        + previous + " and " + groupId + " for profile "
                                        + definition.profile());
                    }
                    profilePlacements.put(
                            member,
                            new ResearchTreeGroupPlacement(groupId, rank, order));
                }
            }
        }

        byProfile.values().forEach(bindings -> bindings.sort(Comparator
                .comparingInt((GroupBinding binding) -> binding.definition().order())
                .thenComparing(binding -> binding.groupId().toString())));

        for (Map.Entry<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> profileEntry
                : placements.entrySet()) {
            ResourceLocation profileId = profileEntry.getKey();
            Map<ResourceLocation, ResearchTreeGroupPlacement> profilePlacements = profileEntry.getValue();
            Map<ResourceLocation, List<ResourceLocation>> graph = prerequisiteGraphForProfile(
                    profileId,
                    rulesByProfile);
            for (Map.Entry<ResourceLocation, ResearchTreeGroupPlacement> placementEntry
                    : profilePlacements.entrySet()) {
                ResourceLocation dependentId = placementEntry.getKey();
                ResearchTreeGroupPlacement dependent = placementEntry.getValue();
                for (ResourceLocation prerequisiteId : graph.getOrDefault(dependentId, List.of())) {
                    ResearchTreeGroupPlacement prerequisite = profilePlacements.get(prerequisiteId);
                    if (prerequisite != null && prerequisite.rank() >= dependent.rank()) {
                        throw new IllegalArgumentException(
                                "research-tree group rank for " + dependentId + " must be above prerequisite "
                                        + prerequisiteId + " in profile " + profileId);
                    }
                }
            }
        }
        return new GroupIndex(byProfile, placements);
    }

    private static RuleBinding selectExact(List<RuleBinding> bindings) {
        return bindings.stream()
                .sorted(Comparator
                        .comparingInt((RuleBinding value) -> value.rule().priority()).reversed()
                        .thenComparing(value -> value.ruleId().toString()))
                .findFirst()
                .orElseThrow();
    }

    private static void visitPrerequisite(
            ResourceLocation blueprintId,
            Map<ResourceLocation, List<ResourceLocation>> graph,
            Set<ResourceLocation> complete,
            LinkedHashSet<ResourceLocation> visiting) {
        if (complete.contains(blueprintId) || !graph.containsKey(blueprintId)) {
            return;
        }
        if (!visiting.add(blueprintId)) {
            throw new IllegalArgumentException("research prerequisite cycle: "
                    + String.join(" -> ", visiting.stream().map(ResourceLocation::toString).toList())
                    + " -> " + blueprintId);
        }
        if (visiting.size() > MAX_PREREQUISITE_DEPTH) {
            throw new IllegalArgumentException(
                    "research prerequisite graph exceeds depth " + MAX_PREREQUISITE_DEPTH
                            + " at " + blueprintId);
        }
        try {
            for (ResourceLocation prerequisite : graph.getOrDefault(blueprintId, List.of())) {
                visitPrerequisite(prerequisite, graph, complete, visiting);
            }
            complete.add(blueprintId);
        } finally {
            visiting.remove(blueprintId);
        }
    }

    private static <T> Map<ResourceLocation, T> sortedCopy(Map<ResourceLocation, T> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        if (values.keySet().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("research definition IDs cannot be null");
        }
        List<Map.Entry<ResourceLocation, T>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        Map<ResourceLocation, T> sorted = new LinkedHashMap<>();
        entries.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    private static <T> Map<ResourceLocation, T> immutableMap(Map<ResourceLocation, T> values) {
        return values == null || values.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<ResourceLocation, List<RuleBinding>> immutableRuleMap(
            Map<ResourceLocation, List<RuleBinding>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, List<RuleBinding>> immutable = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> immutable.put(entry.getKey(), List.copyOf(entry.getValue())));
        return Collections.unmodifiableMap(immutable);
    }

    private static Map<ResourceLocation, List<GroupBinding>> immutableGroupMap(
            Map<ResourceLocation, List<GroupBinding>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, List<GroupBinding>> immutable = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> immutable.put(entry.getKey(), List.copyOf(entry.getValue())));
        return Collections.unmodifiableMap(immutable);
    }

    private static Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> immutablePlacementMap(
            Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> immutable =
                new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> immutable.put(entry.getKey(), immutableMap(entry.getValue())));
        return Collections.unmodifiableMap(immutable);
    }

    private static Map<ResourceLocation, Map<ResourceLocation, ProgressionCoordinate>>
            immutableProgressionMap(
                    Map<ResourceLocation, Map<ResourceLocation, ProgressionCoordinate>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, Map<ResourceLocation, ProgressionCoordinate>> immutable =
                new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> immutable.put(entry.getKey(), immutableMap(entry.getValue())));
        return Collections.unmodifiableMap(immutable);
    }

    private static Map<ResourceLocation, TechTreeIndex> immutableTechTreeIndexMap(
            Map<ResourceLocation, TechTreeIndex> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, TechTreeIndex> immutable = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> immutable.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(immutable);
    }

    public record RuleBinding(ResourceLocation ruleId, BlueprintResearchRule rule) {
        public RuleBinding {
            if (ruleId == null || rule == null) {
                throw new IllegalArgumentException("rule binding values cannot be null");
            }
        }
    }

    public record GroupBinding(ResourceLocation groupId, ResearchTreeGroupDefinition definition) {
        public GroupBinding {
            if (groupId == null || definition == null) {
                throw new IllegalArgumentException("research-tree group binding values cannot be null");
            }
        }
    }

    public record TechTreeEntryBinding(
            ResourceLocation bundleId,
            int entryIndex,
            ResearchTechTreeEntryBundle bundle,
            ResearchTechTreeEntryBundle.Entry entry) {
        public TechTreeEntryBinding {
            if (bundleId == null || entryIndex < 0 || bundle == null || entry == null) {
                throw new IllegalArgumentException("Research Tech Tree entry binding values are invalid");
            }
            if (entryIndex >= bundle.entries().size() || bundle.entries().get(entryIndex) != entry) {
                throw new IllegalArgumentException("Research Tech Tree entry binding index is inconsistent");
            }
        }
    }

    private record TechTreeIndex(
            List<TechTreeEntryBinding> entries,
            Map<ResourceLocation, List<TechTreeEntryBinding>> exact,
            Map<ResourceLocation, List<TechTreeEntryBinding>> tags,
            List<TechTreeEntryBinding> selectors) {
        private TechTreeIndex {
            entries = List.copyOf(entries);
            exact = Collections.unmodifiableMap(new LinkedHashMap<>(exact));
            tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
            selectors = List.copyOf(selectors);
        }
    }

    private record GroupIndex(
            Map<ResourceLocation, List<GroupBinding>> groupsByProfile,
            Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> placementsByProfile) {
    }
}
