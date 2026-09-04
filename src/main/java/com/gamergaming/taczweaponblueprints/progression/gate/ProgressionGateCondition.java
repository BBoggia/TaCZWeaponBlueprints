package com.gamergaming.taczweaponblueprints.progression.gate;

import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.resources.ResourceLocation;

/** One typed, scoped alternative inside a Progression Gate group. */
public sealed interface ProgressionGateCondition permits
        ProgressionGateCondition.Criterion,
        ProgressionGateCondition.Advancement,
        ProgressionGateCondition.WorkbenchTier {
    ProgressionGateScope scope();

    String messageKey();

    Disclosure disclosure();

    Type type();

    String canonicalKey();

    boolean requirementSatisfiedBy(ProgressionGateEvidence evidence);

    default boolean appliesTo(ResearchInteractionMode mode) {
        return scope().appliesTo(mode);
    }

    default boolean satisfiedBy(
            ResearchInteractionMode mode,
            ProgressionGateEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Progression Gate evidence cannot be null");
        }
        return !appliesTo(mode) || requirementSatisfiedBy(evidence);
    }

    record Criterion(
            ResourceLocation criterionId,
            int requiredValue,
            ProgressionGateScope scope,
            String messageKey,
            Disclosure disclosure) implements ProgressionGateCondition {
        public Criterion {
            criterionId = ProgressionIds.require(criterionId, "criterion ID");
            if (requiredValue < 1 || requiredValue > ProgressionCriterionProgress.MAX_VALUE) {
                throw new IllegalArgumentException("criterion requirement is out of bounds");
            }
            scope = requireScope(scope);
            messageKey = requireMessageKey(messageKey);
            disclosure = requireDisclosure(disclosure);
        }

        public static Criterion of(
                String criterionId,
                int requiredValue,
                ProgressionGateScope scope,
                String messageKey,
                Disclosure disclosure) {
            return new Criterion(
                    ProgressionIds.parse(criterionId, "criterion ID"),
                    requiredValue,
                    scope,
                    messageKey,
                    disclosure);
        }

        @Override
        public Type type() {
            return Type.CRITERION;
        }

        @Override
        public String canonicalKey() {
            return "criterion\u0000" + scope + "\u0000" + criterionId + "\u0000" + requiredValue;
        }

        @Override
        public boolean requirementSatisfiedBy(ProgressionGateEvidence evidence) {
            requireEvidence(evidence);
            return evidence.criterionValue(criterionId) >= requiredValue;
        }
    }

    record Advancement(
            ResourceLocation advancementId,
            ProgressionGateScope scope,
            String messageKey,
            Disclosure disclosure) implements ProgressionGateCondition {
        public Advancement {
            advancementId = ProgressionIds.require(advancementId, "advancement ID");
            scope = requireScope(scope);
            messageKey = requireMessageKey(messageKey);
            disclosure = requireDisclosure(disclosure);
        }

        public static Advancement of(
                String advancementId,
                ProgressionGateScope scope,
                String messageKey,
                Disclosure disclosure) {
            return new Advancement(
                    ProgressionIds.parse(advancementId, "advancement ID"),
                    scope,
                    messageKey,
                    disclosure);
        }

        @Override
        public Type type() {
            return Type.ADVANCEMENT;
        }

        @Override
        public String canonicalKey() {
            return "advancement\u0000" + scope + "\u0000" + advancementId;
        }

        @Override
        public boolean requirementSatisfiedBy(ProgressionGateEvidence evidence) {
            requireEvidence(evidence);
            return evidence.hasCompletedAdvancement(advancementId);
        }
    }

    record WorkbenchTier(
            ResearchWorkbenchTier requiredTier,
            ProgressionGateScope scope,
            String messageKey,
            Disclosure disclosure) implements ProgressionGateCondition {
        public WorkbenchTier {
            if (requiredTier == null) {
                throw new IllegalArgumentException("required workbench tier cannot be null");
            }
            scope = requireScope(scope);
            messageKey = requireMessageKey(messageKey);
            disclosure = requireDisclosure(disclosure);
        }

        @Override
        public Type type() {
            return Type.WORKBENCH_TIER;
        }

        @Override
        public String canonicalKey() {
            return "workbench_tier\u0000" + scope + "\u0000" + requiredTier.level();
        }

        @Override
        public boolean requirementSatisfiedBy(ProgressionGateEvidence evidence) {
            requireEvidence(evidence);
            return evidence.workbenchContext()
                    .map(context -> context.tier().satisfies(requiredTier))
                    .orElse(false);
        }

        @Override
        public boolean satisfiedBy(
                ResearchInteractionMode mode,
                ProgressionGateEvidence evidence) {
            if (evidence == null) {
                throw new IllegalArgumentException("Progression Gate evidence cannot be null");
            }
            return !appliesTo(mode) || evidence.workbenchContext()
                    .filter(context -> context.interactionMode() == mode)
                    .filter(context -> context.tier().satisfies(requiredTier))
                    .isPresent();
        }
    }

    private static ProgressionGateScope requireScope(ProgressionGateScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("Progression Gate scope cannot be null");
        }
        return scope;
    }

    private static String requireMessageKey(String messageKey) {
        return ProgressionIds.messageKey(messageKey, "Progression Gate message key");
    }

    private static Disclosure requireDisclosure(Disclosure disclosure) {
        if (disclosure == null) {
            throw new IllegalArgumentException("Progression Gate disclosure cannot be null");
        }
        return disclosure;
    }

    private static void requireEvidence(ProgressionGateEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Progression Gate evidence cannot be null");
        }
    }

    public enum Type {
        CRITERION,
        ADVANCEMENT,
        WORKBENCH_TIER
    }

    public enum Disclosure {
        PUBLIC,
        HIDDEN
    }
}
