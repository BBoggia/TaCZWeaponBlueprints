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
    public static final BlueprintResearchSnapshot EMPTY = new BlueprintResearchSnapshot(
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    private final Map<ResourceLocation, BlueprintLootTag> tags;
    private final Map<ResourceLocation, BlueprintResearchProfile> profiles;
    private final Map<ResourceLocation, BlueprintResearchRule> rules;
    private final Map<ResourceLocation, ResearchTreeGroupDefinition> groups;
    private final Map<ResourceLocation, List<RuleBinding>> rulesByProfile;
    private final Map<ResourceLocation, List<GroupBinding>> groupsByProfile;
    private final Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> placementsByProfile;

    private BlueprintResearchSnapshot(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups,
            Map<ResourceLocation, List<RuleBinding>> rulesByProfile,
            Map<ResourceLocation, List<GroupBinding>> groupsByProfile,
            Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> placementsByProfile) {
        this.tags = immutableMap(tags);
        this.profiles = immutableMap(profiles);
        this.rules = immutableMap(rules);
        this.groups = immutableMap(groups);
        this.rulesByProfile = immutableRuleMap(rulesByProfile);
        this.groupsByProfile = immutableGroupMap(groupsByProfile);
        this.placementsByProfile = immutablePlacementMap(placementsByProfile);
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
        Map<ResourceLocation, BlueprintLootTag> sortedTags = sortedCopy(tags);
        Map<ResourceLocation, BlueprintResearchProfile> sortedProfiles = sortedCopy(profiles);
        Map<ResourceLocation, BlueprintResearchRule> sortedRules = sortedCopy(rules);
        Map<ResourceLocation, ResearchTreeGroupDefinition> sortedGroups = sortedCopy(groups);
        validateDefinitionCounts(sortedTags, sortedProfiles, sortedRules, sortedGroups);
        validateDefinitionIds(sortedTags, sortedProfiles, sortedRules, sortedGroups);
        validateDefinitions(sortedTags, sortedProfiles, sortedRules, sortedGroups);
        sortedProfiles.values().forEach(profile -> {
            profile.researchCost().validateForSnapshot();
            BlueprintResearchPolicyDefinition.fromProfile(profile);
        });

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
        validateAggregateLimits(sortedTags, sortedProfiles, sortedRules, sortedGroups);
        validatePrerequisites(sortedProfiles, byProfile);
        GroupIndex groupIndex = compileGroups(sortedProfiles, sortedGroups, byProfile);
        return new BlueprintResearchSnapshot(
                sortedTags,
                sortedProfiles,
                sortedRules,
                sortedGroups,
                byProfile,
                groupIndex.groupsByProfile(),
                groupIndex.placementsByProfile());
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

    public Map<ResourceLocation, List<RuleBinding>> rulesByProfile() {
        return rulesByProfile;
    }

    public List<RuleBinding> rulesForProfile(ResourceLocation profileId) {
        return rulesByProfile.getOrDefault(profileId, List.of());
    }

    public List<GroupBinding> groupsForProfile(ResourceLocation profileId) {
        return groupsByProfile.getOrDefault(profileId, List.of());
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
            Map<?, ?> groups) {
        if (tags.size() > MAX_DEFINITIONS_PER_TYPE
                || profiles.size() > MAX_DEFINITIONS_PER_TYPE
                || rules.size() > MAX_DEFINITIONS_PER_TYPE
                || groups.size() > MAX_DEFINITIONS_PER_TYPE) {
            throw new IllegalArgumentException(
                    "research data cannot contain more than " + MAX_DEFINITIONS_PER_TYPE
                            + " definitions of one type");
        }
    }

    private static void validateAggregateLimits(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups) {
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
        for (BlueprintResearchRule rule : rules.values()) {
            targetTerms += rule.target().blueprints().size() + rule.target().tags().size();
            targetTerms += rule.target().selector().map(selector -> selector.termCount()).orElse(0);
            prerequisiteIds += rule.prerequisites().map(List::size).orElse(0);
            ingredientTerms += rule.researchCost()
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
                            + " total prerequisites");
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
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups) {
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
        if (rules.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("research rule definitions cannot be null");
        }
        if (groups.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("research-tree group definitions cannot be null");
        }
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

        Map<ResourceLocation, List<ResourceLocation>> graph = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<RuleBinding>> entry : exactRules.entrySet()) {
            RuleBinding selected = selectExact(entry.getValue());
            List<ResourceLocation> prerequisites = selected.rule().prerequisites().orElse(List.of());
            if (prerequisites.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "research prerequisite self-reference for " + entry.getKey()
                                + " in rule " + selected.ruleId());
            }
            graph.put(entry.getKey(), prerequisites);
        }
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

    private record GroupIndex(
            Map<ResourceLocation, List<GroupBinding>> groupsByProfile,
            Map<ResourceLocation, Map<ResourceLocation, ResearchTreeGroupPlacement>> placementsByProfile) {
    }
}
