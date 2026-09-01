package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable authority evidence used while proving a whole-path research route.
 * Effective requirements alone cannot identify a generated root because a
 * missing or filtered automatic publication may also produce an empty policy.
 */
public final class ResearchPathAuthority {
    private static final ResearchPathAuthority AUTHORED = new ResearchPathAuthority(
            Mode.AUTHORED, Set.of(), Map.of());

    private final Mode mode;
    private final Set<ResourceLocation> managedBlueprints;
    private final Map<ResourceLocation, NodeExpectation> expectations;

    private ResearchPathAuthority(
            Mode mode,
            Set<ResourceLocation> managedBlueprints,
            Map<ResourceLocation, NodeExpectation> expectations) {
        if (mode == null || managedBlueprints == null || expectations == null) {
            throw new IllegalArgumentException("research path authority is invalid");
        }
        this.mode = mode;
        this.managedBlueprints = immutableIds(managedBlueprints);
        Map<ResourceLocation, NodeExpectation> stable = new LinkedHashMap<>();
        expectations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (!validId(entry.getKey()) || entry.getValue() == null
                            || !this.managedBlueprints.contains(entry.getKey())) {
                        throw new IllegalArgumentException(
                                "research path authority expectation is invalid");
                    }
                    stable.put(entry.getKey(), entry.getValue());
                });
        this.expectations = Collections.unmodifiableMap(stable);
        if ((mode == Mode.AUTHORED
                        && (!this.managedBlueprints.isEmpty() || !this.expectations.isEmpty()))
                || (mode == Mode.AUTOMATIC_UNAVAILABLE && !this.expectations.isEmpty())) {
            throw new IllegalArgumentException("research path authority state is inconsistent");
        }
    }

    public static ResearchPathAuthority authored() {
        return AUTHORED;
    }

    public static ResearchPathAuthority automaticUnavailable(
            Set<ResourceLocation> managedBlueprints) {
        return new ResearchPathAuthority(
                Mode.AUTOMATIC_UNAVAILABLE, managedBlueprints, Map.of());
    }

    public static ResearchPathAuthority automaticReady(
            Set<ResourceLocation> managedBlueprints,
            Map<ResourceLocation, NodeExpectation> expectations) {
        return new ResearchPathAuthority(
                Mode.AUTOMATIC_READY, managedBlueprints, expectations);
    }

    public Mode mode() {
        return mode;
    }

    public Set<ResourceLocation> managedBlueprints() {
        return managedBlueprints;
    }

    public Optional<RootProvenance> rootProvenance(ResourceLocation blueprintId) {
        NodeExpectation expectation = expectations.get(blueprintId);
        return expectation == null ? Optional.empty() : expectation.rootProvenance();
    }

    /** Returns the failure that must stop traversal, or empty when authorized. */
    public Optional<BlueprintResearchService.Status> validate(
            BlueprintResearchPolicy policy) {
        if (policy == null) {
            return Optional.of(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
        ResourceLocation blueprintId = policy.blueprintId();
        if (mode == Mode.AUTHORED || !managedBlueprints.contains(blueprintId)) {
            return Optional.empty();
        }
        if (mode != Mode.AUTOMATIC_READY) {
            return Optional.of(BlueprintResearchService.Status.TECH_TREE_UNAVAILABLE);
        }
        NodeExpectation expectation = expectations.get(blueprintId);
        if (expectation == null) {
            return Optional.of(BlueprintResearchService.Status.TECH_TREE_UNAVAILABLE);
        }
        List<Set<ResourceLocation>> actual = policy.requirements().allOf().stream()
                .map(group -> immutableIds(new LinkedHashSet<>(group.anyOf())))
                .toList();
        if (expectation.rootProvenance().isPresent()) {
            return actual.isEmpty()
                    ? Optional.empty()
                    : Optional.of(BlueprintResearchService.Status.UNSATISFIABLE);
        }
        List<Set<ResourceLocation>> expected = expectation.activeRequirementGroups();
        if (!groupsMatch(expected, actual)) {
            return Optional.of(BlueprintResearchService.Status.UNSATISFIABLE);
        }
        return Optional.empty();
    }

    private static boolean groupsMatch(
            List<Set<ResourceLocation>> expected,
            List<Set<ResourceLocation>> actual) {
        if (expected.size() != actual.size()
                || actual.stream().anyMatch(Set::isEmpty)) {
            return false;
        }
        int[] actualByExpected = new int[expected.size()];
        java.util.Arrays.fill(actualByExpected, -1);
        for (int actualIndex = 0; actualIndex < actual.size(); actualIndex++) {
            if (!matchActualGroup(
                    actualIndex,
                    expected,
                    actual,
                    actualByExpected,
                    new boolean[expected.size()])) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchActualGroup(
            int actualIndex,
            List<Set<ResourceLocation>> expected,
            List<Set<ResourceLocation>> actual,
            int[] actualByExpected,
            boolean[] visitedExpected) {
        Set<ResourceLocation> actualGroup = actual.get(actualIndex);
        for (int expectedIndex = 0; expectedIndex < expected.size(); expectedIndex++) {
            if (visitedExpected[expectedIndex]
                    || !expected.get(expectedIndex).containsAll(actualGroup)) {
                continue;
            }
            visitedExpected[expectedIndex] = true;
            if (actualByExpected[expectedIndex] < 0
                    || matchActualGroup(
                            actualByExpected[expectedIndex],
                            expected,
                            actual,
                            actualByExpected,
                            visitedExpected)) {
                actualByExpected[expectedIndex] = actualIndex;
                return true;
            }
        }
        return false;
    }

    public enum Mode {
        AUTHORED,
        AUTOMATIC_READY,
        AUTOMATIC_UNAVAILABLE
    }

    public enum RootProvenance {
        AUTHORED_ROOT,
        GENERATED_FOUNDATION,
        GENERATED_INDEPENDENT_ROOT,
        PROGRESSION_EXEMPT_BOUNDARY
    }

    public record NodeExpectation(
            List<Set<ResourceLocation>> activeRequirementGroups,
            Optional<RootProvenance> rootProvenance) {
        public NodeExpectation {
            rootProvenance = rootProvenance == null
                    ? Optional.empty()
                    : rootProvenance;
            List<Set<ResourceLocation>> stable = new ArrayList<>();
            if (activeRequirementGroups != null) {
                activeRequirementGroups.forEach(group -> {
                    Set<ResourceLocation> copy = immutableIds(group);
                    if (copy.isEmpty()) {
                        throw new IllegalArgumentException(
                                "research path authority group cannot be empty");
                    }
                    stable.add(copy);
                });
            }
            activeRequirementGroups = List.copyOf(stable);
            if (rootProvenance.isPresent() == !activeRequirementGroups.isEmpty()) {
                throw new IllegalArgumentException(
                        "research path authority expectation is inconsistent");
            }
        }

        public static NodeExpectation requirements(
                List<? extends Set<ResourceLocation>> groups) {
            return new NodeExpectation(
                    groups == null ? List.of() : new ArrayList<>(groups),
                    Optional.empty());
        }

        public static NodeExpectation root(RootProvenance provenance) {
            if (provenance == null) {
                throw new IllegalArgumentException("research root provenance is invalid");
            }
            return new NodeExpectation(List.of(), Optional.of(provenance));
        }
    }

    private static Set<ResourceLocation> immutableIds(Set<ResourceLocation> source) {
        if (source == null) {
            throw new IllegalArgumentException("research path authority IDs are invalid");
        }
        LinkedHashSet<ResourceLocation> stable = new LinkedHashSet<>();
        source.stream()
                .sorted(Comparator.comparing(id -> id == null ? "" : id.toString()))
                .forEach(id -> {
                    if (!validId(id)) {
                        throw new IllegalArgumentException(
                                "research path authority contains an invalid ID");
                    }
                    stable.add(id);
                });
        return Collections.unmodifiableSet(stable);
    }

    private static boolean validId(ResourceLocation id) {
        return id != null
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }
}
