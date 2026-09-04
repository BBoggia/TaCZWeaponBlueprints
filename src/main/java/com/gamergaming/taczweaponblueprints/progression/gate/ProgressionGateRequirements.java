package com.gamergaming.taczweaponblueprints.progression.gate;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;

/** Canonical AND-of-OR Progression Gate policy for one blueprint. */
public record ProgressionGateRequirements(List<ProgressionGateGroup> allOf) {
    public static final int MAX_GROUPS = 32;
    public static final int MAX_TOTAL_CONDITIONS = 64;
    public static final ProgressionGateRequirements EMPTY = new ProgressionGateRequirements(
            List.of());

    public ProgressionGateRequirements {
        if (allOf == null
                || allOf.size() > MAX_GROUPS
                || allOf.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Progression Gate groups are invalid");
        }
        int conditionCount = allOf.stream().mapToInt(group -> group.anyOf().size()).sum();
        if (conditionCount > MAX_TOTAL_CONDITIONS) {
            throw new IllegalArgumentException("Progression Gate policy has too many conditions");
        }
        Set<String> identities = new LinkedHashSet<>();
        if (allOf.stream().anyMatch(group -> !identities.add(group.canonicalKey()))) {
            throw new IllegalArgumentException("Progression Gate policy contains a duplicate group");
        }
        allOf = allOf.stream()
                .sorted(Comparator.comparing(ProgressionGateGroup::canonicalKey))
                .toList();
    }

    public Evaluation evaluate(
            ResearchInteractionMode mode,
            ProgressionGateEvidence evidence) {
        if (mode == null || evidence == null) {
            throw new IllegalArgumentException("Progression Gate evaluation inputs cannot be null");
        }
        return new Evaluation(
                mode,
                allOf.stream().map(group -> group.evaluate(mode, evidence)).toList());
    }

    public boolean satisfiedBy(
            ResearchInteractionMode mode,
            ProgressionGateEvidence evidence) {
        return evaluate(mode, evidence).satisfied();
    }

    public int conditionCount() {
        return allOf.stream().mapToInt(group -> group.anyOf().size()).sum();
    }

    public record Evaluation(
            ResearchInteractionMode mode,
        List<ProgressionGateGroup.Evaluation> groups) {
        public Evaluation {
            if (mode == null
                    || groups == null
                    || groups.size() > MAX_GROUPS
                    || groups.stream().anyMatch(java.util.Objects::isNull)
                    || groups.stream().mapToInt(group ->
                            group.applicableAlternatives().size()).sum()
                            > MAX_TOTAL_CONDITIONS) {
                throw new IllegalArgumentException("invalid Progression Gate evaluation");
            }
            groups = List.copyOf(groups);
        }

        public boolean satisfied() {
            return groups.stream().allMatch(ProgressionGateGroup.Evaluation::satisfied);
        }

        public List<ProgressionGateGroup.Evaluation> unmetGroups() {
            return groups.stream()
                    .filter(ProgressionGateGroup.Evaluation::applies)
                    .filter(group -> !group.satisfied())
                    .toList();
        }
    }
}
