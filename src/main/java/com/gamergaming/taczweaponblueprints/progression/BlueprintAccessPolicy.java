package com.gamergaming.taczweaponblueprints.progression;

/**
 * Pure, server-oriented policy boundary for blueprint knowledge access.
 *
 * <p>Phase 1 exposes only the learning decision. Later phases may add crafting
 * and reverse-engineering decisions without duplicating the shared policy
 * vocabulary. This class deliberately performs no capability, inventory,
 * network, configuration, or award mutation.</p>
 */
public final class BlueprintAccessPolicy {
    private BlueprintAccessPolicy() {
    }

    /**
     * Evaluates one already-resolved learning request in deterministic policy
     * precedence order.
     */
    public static LearningDecision evaluateLearning(LearningFacts facts) {
        if (facts == null) {
            throw new IllegalArgumentException("learning facts cannot be null");
        }

        if (!facts.contentAvailable()) {
            return denied(LearningStatus.CONTENT_UNAVAILABLE, facts.origin());
        }
        if (!facts.playerDataAvailable()) {
            return denied(LearningStatus.PLAYER_DATA_UNAVAILABLE, facts.origin());
        }

        // Migration is a narrow preservation lane for previously valid
        // knowledge. It cannot invent missing content or overflow capability
        // collections, but current gameplay switches must not erase it.
        if (facts.origin() == BlueprintUnlockOrigin.MIGRATION) {
            if (facts.alreadyLearned()) {
                return denied(LearningStatus.ALREADY_LEARNED, facts.origin());
            }
            if (!facts.progressionCapacityAvailable()) {
                return denied(
                        LearningStatus.PROGRESSION_CAPACITY_EXHAUSTED,
                        facts.origin());
            }
            return allowed(facts, !facts.prerequisitesSatisfied());
        }

        if (!facts.blueprintsEnabled()) {
            return denied(LearningStatus.BLUEPRINTS_DISABLED, facts.origin());
        }
        if (facts.blocked()) {
            return denied(LearningStatus.BLOCKED, facts.origin());
        }
        if (facts.progressionExempt()) {
            return denied(LearningStatus.PROGRESSION_EXEMPT, facts.origin());
        }
        if (facts.alreadyLearned()) {
            return denied(LearningStatus.ALREADY_LEARNED, facts.origin());
        }

        if (facts.origin() == BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT
                && !facts.physicalBlueprintMode().learningPermitted()) {
            return denied(
                    LearningStatus.PHYSICAL_BLUEPRINT_LEARNING_DISABLED,
                    facts.origin());
        }

        boolean prerequisitesRequired = switch (facts.origin()) {
            case TREE_RESEARCH -> true;
            case PHYSICAL_BLUEPRINT ->
                    facts.physicalBlueprintMode().prerequisitesRequired();
            case STARTING_GRANT, ADMINISTRATOR, MIGRATION -> false;
        };
        if (prerequisitesRequired && !facts.prerequisitesSatisfied()) {
            return denied(
                    LearningStatus.PREREQUISITES_UNSATISFIED,
                    facts.origin());
        }
        if (!facts.progressionCapacityAvailable()) {
            return denied(
                    LearningStatus.PROGRESSION_CAPACITY_EXHAUSTED,
                    facts.origin());
        }

        return allowed(
                facts,
                !prerequisitesRequired && !facts.prerequisitesSatisfied());
    }

    private static LearningDecision allowed(
            LearningFacts facts,
            boolean prerequisitesBypassed) {
        return new LearningDecision(
                LearningStatus.ALLOWED,
                facts.origin(),
                prerequisitesBypassed,
                facts.origin().liveAwardsEligible());
    }

    private static LearningDecision denied(
            LearningStatus status,
            BlueprintUnlockOrigin origin) {
        return new LearningDecision(status, origin, false, false);
    }

    /**
     * Complete resolved facts needed by the pure learning decision. Callers
     * remain responsible for resolving canonical content and trusted origin.
     */
    public record LearningFacts(
            BlueprintUnlockOrigin origin,
            PhysicalBlueprintLearningMode physicalBlueprintMode,
            boolean contentAvailable,
            boolean playerDataAvailable,
            boolean blueprintsEnabled,
            boolean blocked,
            boolean progressionExempt,
            boolean alreadyLearned,
            boolean progressionCapacityAvailable,
            boolean prerequisitesSatisfied) {
        public LearningFacts {
            if (origin == null || physicalBlueprintMode == null) {
                throw new IllegalArgumentException(
                        "learning facts contain null required state");
            }
        }
    }

    public enum LearningStatus {
        ALLOWED,
        CONTENT_UNAVAILABLE,
        PLAYER_DATA_UNAVAILABLE,
        BLUEPRINTS_DISABLED,
        BLOCKED,
        PROGRESSION_EXEMPT,
        ALREADY_LEARNED,
        PHYSICAL_BLUEPRINT_LEARNING_DISABLED,
        PREREQUISITES_UNSATISFIED,
        PROGRESSION_CAPACITY_EXHAUSTED
    }

    /** Typed result for later services and UI feedback mapping. */
    public record LearningDecision(
            LearningStatus status,
            BlueprintUnlockOrigin origin,
            boolean prerequisitesBypassed,
            boolean liveAwardsEligible) {
        public LearningDecision {
            if (status == null || origin == null) {
                throw new IllegalArgumentException(
                        "learning decision contains null required state");
            }
            if (status != LearningStatus.ALLOWED
                    && (prerequisitesBypassed || liveAwardsEligible)) {
                throw new IllegalArgumentException(
                        "denied learning cannot bypass prerequisites or award");
            }
            if (liveAwardsEligible && !origin.liveAwardsEligible()) {
                throw new IllegalArgumentException(
                        "learning origin is not eligible for live awards");
            }
            if (status == LearningStatus.ALLOWED
                    && liveAwardsEligible != origin.liveAwardsEligible()) {
                throw new IllegalArgumentException(
                        "allowed learning must preserve origin award eligibility");
            }
        }

        public boolean allowed() {
            return status == LearningStatus.ALLOWED;
        }

        /**
         * Whether no new durable learning is necessary because access already
         * exists either through knowledge or an explicit progression exemption.
         */
        public boolean recipeAlreadyAccessible() {
            return status == LearningStatus.ALREADY_LEARNED
                    || status == LearningStatus.PROGRESSION_EXEMPT;
        }
    }
}
