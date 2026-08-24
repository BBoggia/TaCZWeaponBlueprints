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

public record BlueprintResearchSnapshot(
        Map<ResourceLocation, BlueprintLootTag> tags,
        Map<ResourceLocation, BlueprintResearchProfile> profiles,
        Map<ResourceLocation, BlueprintResearchRule> rules,
        Map<ResourceLocation, List<RuleBinding>> rulesByProfile) {
    public static final int MAX_DEFINITIONS_PER_TYPE = 4096;
    public static final int MAX_PREREQUISITE_DEPTH = 64;
    public static final BlueprintResearchSnapshot EMPTY = new BlueprintResearchSnapshot(
            Map.of(), Map.of(), Map.of(), Map.of());

    public BlueprintResearchSnapshot {
        tags = immutableMap(tags);
        profiles = immutableMap(profiles);
        rules = immutableMap(rules);
        rulesByProfile = immutableRuleMap(rulesByProfile);
    }

    public static BlueprintResearchSnapshot create(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules) {
        Map<ResourceLocation, BlueprintLootTag> sortedTags = sortedCopy(tags);
        Map<ResourceLocation, BlueprintResearchProfile> sortedProfiles = sortedCopy(profiles);
        Map<ResourceLocation, BlueprintResearchRule> sortedRules = sortedCopy(rules);
        validateDefinitionCounts(sortedTags, sortedProfiles, sortedRules);
        validateDefinitionIds(sortedTags, sortedProfiles, sortedRules);
        validateDefinitions(sortedTags, sortedProfiles, sortedRules);
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
        validatePrerequisites(sortedProfiles, byProfile);
        return new BlueprintResearchSnapshot(sortedTags, sortedProfiles, sortedRules, byProfile);
    }

    public List<RuleBinding> rulesForProfile(ResourceLocation profileId) {
        return rulesByProfile.getOrDefault(profileId, List.of());
    }

    private static void validateDefinitionCounts(Map<?, ?> tags, Map<?, ?> profiles, Map<?, ?> rules) {
        if (tags.size() > MAX_DEFINITIONS_PER_TYPE
                || profiles.size() > MAX_DEFINITIONS_PER_TYPE
                || rules.size() > MAX_DEFINITIONS_PER_TYPE) {
            throw new IllegalArgumentException(
                    "research data cannot contain more than " + MAX_DEFINITIONS_PER_TYPE
                            + " definitions of one type");
        }
    }

    private static void validateDefinitions(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            Map<ResourceLocation, BlueprintResearchRule> rules) {
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
            Map<ResourceLocation, List<ResourceLocation>> graph = new LinkedHashMap<>();
            Map<ResourceLocation, List<RuleBinding>> exactRules = new LinkedHashMap<>();
            for (RuleBinding binding : byProfile.getOrDefault(profileId, List.of())) {
                for (ResourceLocation targetId : binding.rule().target().blueprints()) {
                    exactRules.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(binding);
                }
            }

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

            Set<ResourceLocation> complete = new LinkedHashSet<>();
            for (ResourceLocation blueprintId : graph.keySet()) {
                visitPrerequisite(blueprintId, graph, complete, new LinkedHashSet<>());
            }
        }
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

    public record RuleBinding(ResourceLocation ruleId, BlueprintResearchRule rule) {
        public RuleBinding {
            if (ruleId == null || rule == null) {
                throw new IllegalArgumentException("rule binding values cannot be null");
            }
        }
    }
}
