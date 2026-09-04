package com.gamergaming.taczweaponblueprints.progression.gate;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;

/** One canonical any-of group; every applicable group is required by its parent policy. */
public record ProgressionGateGroup(List<ProgressionGateCondition> anyOf) {
    public static final int MAX_ALTERNATIVES = 16;

    public ProgressionGateGroup {
        if (anyOf == null || anyOf.isEmpty()) {
            throw new IllegalArgumentException("Progression Gate group cannot be empty");
        }
        if (anyOf.size() > MAX_ALTERNATIVES
                || anyOf.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Progression Gate alternatives are invalid");
        }
        Set<String> identities = new LinkedHashSet<>();
        if (anyOf.stream().anyMatch(condition -> !identities.add(condition.canonicalKey()))) {
            throw new IllegalArgumentException("Progression Gate group contains a duplicate alternative");
        }
        anyOf = anyOf.stream()
                .sorted(Comparator.comparing(ProgressionGateCondition::canonicalKey))
                .toList();
    }

    public boolean appliesTo(ResearchInteractionMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("research interaction mode cannot be null");
        }
        return anyOf.stream().anyMatch(condition -> condition.appliesTo(mode));
    }

    public Evaluation evaluate(
            ResearchInteractionMode mode,
            ProgressionGateEvidence evidence) {
        if (mode == null || evidence == null) {
            throw new IllegalArgumentException("Progression Gate evaluation inputs cannot be null");
        }
        List<ProgressionGateCondition> applicable = anyOf.stream()
                .filter(condition -> condition.appliesTo(mode))
                .toList();
        List<ProgressionGateCondition> satisfied = applicable.stream()
                .filter(condition -> condition.satisfiedBy(mode, evidence))
                .toList();
        return new Evaluation(applicable, satisfied);
    }

    String canonicalKey() {
        return anyOf.stream()
                .map(ProgressionGateCondition::canonicalKey)
                .collect(java.util.stream.Collectors.joining("\u0001"));
    }

    public record Evaluation(
            List<ProgressionGateCondition> applicableAlternatives,
            List<ProgressionGateCondition> satisfiedAlternatives) {
        public Evaluation {
            if (applicableAlternatives == null
                    || satisfiedAlternatives == null
                    || applicableAlternatives.size() > MAX_ALTERNATIVES
                    || satisfiedAlternatives.size() > MAX_ALTERNATIVES
                    || applicableAlternatives.stream().anyMatch(java.util.Objects::isNull)
                    || satisfiedAlternatives.stream().anyMatch(java.util.Objects::isNull)
                    || new LinkedHashSet<>(applicableAlternatives).size()
                            != applicableAlternatives.size()
                    || new LinkedHashSet<>(satisfiedAlternatives).size()
                            != satisfiedAlternatives.size()) {
                throw new IllegalArgumentException("invalid Progression Gate group evaluation");
            }
            applicableAlternatives = List.copyOf(applicableAlternatives);
            satisfiedAlternatives = List.copyOf(satisfiedAlternatives);
            if (!applicableAlternatives.containsAll(satisfiedAlternatives)) {
                throw new IllegalArgumentException("invalid Progression Gate group evaluation");
            }
        }

        /** A group with no conditions for the current action imposes no requirement. */
        public boolean satisfied() {
            return applicableAlternatives.isEmpty() || !satisfiedAlternatives.isEmpty();
        }

        public boolean applies() {
            return !applicableAlternatives.isEmpty();
        }
    }
}
