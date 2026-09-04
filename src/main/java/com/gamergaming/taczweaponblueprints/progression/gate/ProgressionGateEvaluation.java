package com.gamergaming.taczweaponblueprints.progression.gate;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.resources.ResourceLocation;

/**
 * Disclosure-safe result of evaluating one blueprint's Progression Gates.
 * Hidden conditions retain only their pack-authored message key and broad type.
 */
public record ProgressionGateEvaluation(
        ResourceLocation subjectId,
        ResearchInteractionMode interactionMode,
        Status status,
        List<UnmetGroup> unmetGroups) {
    public ProgressionGateEvaluation {
        subjectId = ProgressionIds.require(subjectId, "Progression Gate subject ID");
        if (interactionMode == null || status == null || unmetGroups == null
                || unmetGroups.size() > ProgressionGateRequirements.MAX_GROUPS
                || unmetGroups.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Progression Gate evaluation is invalid");
        }
        unmetGroups = List.copyOf(unmetGroups);
        if (status != Status.EVALUATED && !unmetGroups.isEmpty()) {
            throw new IllegalArgumentException(
                    "an unavailable Progression Gate evaluation cannot contain policy details");
        }
        int previousOrdinal = -1;
        for (UnmetGroup group : unmetGroups) {
            if (group.groupOrdinal() <= previousOrdinal) {
                throw new IllegalArgumentException(
                        "Progression Gate group ordinals must be strictly increasing");
            }
            previousOrdinal = group.groupOrdinal();
        }
    }

    public static ProgressionGateEvaluation unavailable(
            ResourceLocation subjectId,
            ResearchInteractionMode interactionMode,
            Status status) {
        if (status == null || status == Status.EVALUATED) {
            throw new IllegalArgumentException("unavailable gate status is invalid");
        }
        return new ProgressionGateEvaluation(subjectId, interactionMode, status, List.of());
    }

    public boolean satisfied() {
        return status == Status.EVALUATED && unmetGroups.isEmpty();
    }

    public boolean blocked() {
        return !satisfied();
    }

    public Optional<UnmetGroup> primaryUnmetGroup() {
        return unmetGroups.isEmpty() ? Optional.empty() : Optional.of(unmetGroups.get(0));
    }

    public enum Status {
        EVALUATED,
        INVALID_PLAYER,
        WRONG_THREAD,
        PLAYER_DATA_UNAVAILABLE,
        ADVANCEMENT_STATE_UNAVAILABLE,
        POLICY_UNAVAILABLE
    }

    /** One unsatisfied AND group and its safe OR alternatives. */
    public record UnmetGroup(int groupOrdinal, List<RequirementHint> alternatives) {
        public UnmetGroup {
            if (groupOrdinal < 0
                    || groupOrdinal >= ProgressionGateRequirements.MAX_GROUPS
                    || alternatives == null
                    || alternatives.isEmpty()
                    || alternatives.size() > ProgressionGateGroup.MAX_ALTERNATIVES
                    || alternatives.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("unmet Progression Gate group is invalid");
            }
            alternatives = List.copyOf(alternatives);
        }
    }

    /**
     * One failed alternative. Requirement identity and progress are present only
     * for conditions explicitly declared public by the pack author.
     */
    public record RequirementHint(
            ProgressionGateCondition.Type type,
            String messageKey,
            ProgressionGateCondition.Disclosure disclosure,
            Optional<ResourceLocation> publicRequirementId,
            Optional<Integer> currentValue,
            Optional<Integer> requiredValue,
            Optional<ResearchWorkbenchTier> currentTier,
            Optional<ResearchWorkbenchTier> requiredTier) {
        public RequirementHint {
            if (type == null || disclosure == null) {
                throw new IllegalArgumentException("Progression Gate requirement hint is invalid");
            }
            messageKey = ProgressionIds.messageKey(
                    messageKey, "Progression Gate message key");
            publicRequirementId = optional(publicRequirementId);
            currentValue = optional(currentValue);
            requiredValue = optional(requiredValue);
            currentTier = optional(currentTier);
            requiredTier = optional(requiredTier);
            publicRequirementId.ifPresent(id -> ProgressionIds.require(
                    id, "public Progression Gate requirement ID"));
            if (disclosure == ProgressionGateCondition.Disclosure.HIDDEN
                    && (publicRequirementId.isPresent()
                            || currentValue.isPresent()
                            || requiredValue.isPresent()
                            || currentTier.isPresent()
                            || requiredTier.isPresent())) {
                throw new IllegalArgumentException(
                        "hidden Progression Gate hints cannot expose requirement details");
            }
            validateShape(
                    type,
                    disclosure,
                    publicRequirementId,
                    currentValue,
                    requiredValue,
                    currentTier,
                    requiredTier);
        }

        private static void validateShape(
                ProgressionGateCondition.Type type,
                ProgressionGateCondition.Disclosure disclosure,
                Optional<ResourceLocation> publicRequirementId,
                Optional<Integer> currentValue,
                Optional<Integer> requiredValue,
                Optional<ResearchWorkbenchTier> currentTier,
                Optional<ResearchWorkbenchTier> requiredTier) {
            if (disclosure == ProgressionGateCondition.Disclosure.HIDDEN) {
                return;
            }
            switch (type) {
                case CRITERION -> {
                    if (publicRequirementId.isEmpty()
                            || currentValue.isEmpty()
                            || requiredValue.isEmpty()
                            || currentValue.orElseThrow() < 0
                            || currentValue.orElseThrow()
                                    > ProgressionCriterionProgress.MAX_VALUE
                            || requiredValue.orElseThrow() < 1
                            || requiredValue.orElseThrow()
                                    > ProgressionCriterionProgress.MAX_VALUE
                            || currentValue.orElseThrow()
                                    >= requiredValue.orElseThrow()
                            || currentTier.isPresent()
                            || requiredTier.isPresent()) {
                        throw new IllegalArgumentException(
                                "public criterion hint has inconsistent details");
                    }
                }
                case ADVANCEMENT -> {
                    if (publicRequirementId.isEmpty()
                            || currentValue.isPresent()
                            || requiredValue.isPresent()
                            || currentTier.isPresent()
                            || requiredTier.isPresent()) {
                        throw new IllegalArgumentException(
                                "public advancement hint has inconsistent details");
                    }
                }
                case WORKBENCH_TIER -> {
                    if (publicRequirementId.isPresent()
                            || currentValue.isPresent()
                            || requiredValue.isPresent()
                            || requiredTier.isEmpty()) {
                        throw new IllegalArgumentException(
                                "public workbench hint has inconsistent details");
                    }
                    if (currentTier.filter(tier -> tier.satisfies(
                            requiredTier.orElseThrow())).isPresent()) {
                        throw new IllegalArgumentException(
                                "satisfied workbench tier cannot be an unmet hint");
                    }
                }
            }
        }

        private static <T> Optional<T> optional(Optional<T> value) {
            return value == null ? Optional.empty() : value;
        }
    }
}
