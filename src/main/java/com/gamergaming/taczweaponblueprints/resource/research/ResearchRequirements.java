package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.resources.ResourceLocation;

/**
 * Canonical research requirements: AND across {@link #allOf()} groups and OR
 * within each group. Legacy flat prerequisites adapt to singleton groups and
 * therefore retain their mandatory-AND truth table.
 */
public record ResearchRequirements(List<ResearchPrerequisiteGroup> allOf) {
    public static final int MAX_GROUPS = 64;
    public static final int MAX_TOTAL_ALTERNATIVES = 64;
    public static final ResearchRequirements EMPTY = new ResearchRequirements(List.of());

    public static final Codec<ResearchRequirements> CODEC =
            ResearchPrerequisiteGroup.CODEC.listOf().flatXmap(
                    ResearchRequirements::validateCodec,
                    ResearchRequirements::validateCodec);

    public ResearchRequirements {
        if (allOf == null) {
            throw new IllegalArgumentException(
                    "research prerequisite groups cannot be null");
        }
        if (allOf.size() > MAX_GROUPS) {
            throw new IllegalArgumentException(
                    "research requirements cannot contain more than "
                            + MAX_GROUPS + " groups");
        }
        if (allOf.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "research requirements contain a null group");
        }
        if (allOf.stream().mapToInt(group -> group.anyOf().size()).sum()
                > MAX_TOTAL_ALTERNATIVES) {
            throw new IllegalArgumentException(
                    "research requirements cannot contain more than "
                            + MAX_TOTAL_ALTERNATIVES + " total alternatives");
        }
        Set<ResearchPrerequisiteGroup> unique = new LinkedHashSet<>(allOf);
        if (unique.size() != allOf.size()) {
            throw new IllegalArgumentException(
                    "research requirements contain a duplicate group");
        }
        allOf = allOf.stream()
                .sorted(Comparator.comparing(ResearchPrerequisiteGroup::canonicalKey))
                .toList();
    }

    /** Converts the current flat mandatory list into ANDed singleton groups. */
    public static ResearchRequirements fromLegacy(
            List<ResourceLocation> prerequisites) {
        if (prerequisites == null) {
            throw new IllegalArgumentException(
                    "legacy research prerequisites cannot be null");
        }
        if (prerequisites.isEmpty()) {
            return EMPTY;
        }
        return new ResearchRequirements(prerequisites.stream()
                .map(ResearchPrerequisiteGroup::singleton)
                .toList());
    }

    public boolean satisfiedBy(Predicate<ResourceLocation> satisfied) {
        if (satisfied == null) {
            throw new IllegalArgumentException(
                    "research prerequisite satisfaction predicate cannot be null");
        }
        return allOf.stream().allMatch(group -> group.satisfiedBy(satisfied));
    }

    public int alternativeCount() {
        return allOf.stream().mapToInt(group -> group.anyOf().size()).sum();
    }

    /** Stable union of every possible requirement edge for cycles and layout. */
    public List<ResourceLocation> conservativeAlternatives() {
        return allOf.stream()
                .flatMap(group -> group.anyOf().stream())
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    /** Returns the old flat view only when every canonical group is singleton. */
    public Optional<List<ResourceLocation>> legacySingletons() {
        if (allOf.stream().anyMatch(group -> group.anyOf().size() != 1)) {
            return Optional.empty();
        }
        return Optional.of(allOf.stream().map(group -> group.anyOf().get(0)).toList());
    }

    public void validateFor(ResourceLocation dependentId) {
        allOf.forEach(group -> group.validateFor(dependentId));
    }

    /**
     * Validates cycles and depth over the union of every possible group edge.
     * This deliberately rejects a cycle even if another alternative could avoid
     * it, keeping future grouped authoring conservative and deterministic.
     */
    public static void validateConservativeGraph(
            Map<ResourceLocation, ResearchRequirements> requirementsByDependent,
            int maximumDepth) {
        if (requirementsByDependent == null || maximumDepth < 1) {
            throw new IllegalArgumentException(
                    "research requirement graph inputs are invalid");
        }
        if (requirementsByDependent.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "research requirement graph contains null state");
        }
        Map<ResourceLocation, ResearchRequirements> stable = new LinkedHashMap<>();
        requirementsByDependent.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    entry.getValue().validateFor(entry.getKey());
                    stable.put(entry.getKey(), entry.getValue());
                });
        Set<ResourceLocation> complete = new LinkedHashSet<>();
        for (ResourceLocation dependent : stable.keySet()) {
            visit(dependent, stable, maximumDepth, complete, new LinkedHashSet<>());
        }
    }

    private static void visit(
            ResourceLocation dependent,
            Map<ResourceLocation, ResearchRequirements> graph,
            int maximumDepth,
            Set<ResourceLocation> complete,
            LinkedHashSet<ResourceLocation> visiting) {
        if (complete.contains(dependent) || !graph.containsKey(dependent)) {
            return;
        }
        if (!visiting.add(dependent)) {
            List<ResourceLocation> cycle = new ArrayList<>(visiting);
            cycle.add(dependent);
            throw new IllegalArgumentException("research prerequisite cycle: "
                    + String.join(" -> ", cycle.stream()
                            .map(ResourceLocation::toString).toList()));
        }
        if (visiting.size() > maximumDepth) {
            throw new IllegalArgumentException(
                    "research prerequisite graph exceeds depth " + maximumDepth
                            + " at " + dependent);
        }
        try {
            for (ResourceLocation prerequisite : graph.get(dependent)
                    .conservativeAlternatives()) {
                visit(prerequisite, graph, maximumDepth, complete, visiting);
            }
            complete.add(dependent);
        } finally {
            visiting.remove(dependent);
        }
    }

    private static DataResult<ResearchRequirements> validateCodec(
            List<ResearchPrerequisiteGroup> groups) {
        try {
            return DataResult.success(new ResearchRequirements(groups));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<List<ResearchPrerequisiteGroup>> validateCodec(
            ResearchRequirements requirements) {
        try {
            return DataResult.success(new ResearchRequirements(
                    requirements.allOf()).allOf());
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
