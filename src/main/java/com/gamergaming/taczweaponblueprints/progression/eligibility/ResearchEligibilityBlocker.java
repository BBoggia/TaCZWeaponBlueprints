package com.gamergaming.taczweaponblueprints.progression.eligibility;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.resources.ResourceLocation;

/** One typed reason that a research-route or crafting action cannot proceed. */
public sealed interface ResearchEligibilityBlocker permits
        ResearchEligibilityBlocker.Policy,
        ResearchEligibilityBlocker.Path,
        ResearchEligibilityBlocker.WorkbenchTier,
        ResearchEligibilityBlocker.Gate,
        ResearchEligibilityBlocker.ResearchPoints,
        ResearchEligibilityBlocker.Materials,
        ResearchEligibilityBlocker.Capacity {
    ResourceLocation subjectId();

    Kind kind();

    String stableKey();

    record Policy(ResourceLocation subjectId, PolicyReason reason)
            implements ResearchEligibilityBlocker {
        public Policy {
            subjectId = requireSubject(subjectId);
            if (reason == null) {
                throw new IllegalArgumentException("policy blocker reason cannot be null");
            }
        }

        @Override
        public Kind kind() {
            return Kind.POLICY;
        }

        @Override
        public String stableKey() {
            return reason.name();
        }
    }

    record Path(
            ResourceLocation subjectId,
            int missingGroups,
            int undisclosedGroups) implements ResearchEligibilityBlocker {
        public Path {
            subjectId = requireSubject(subjectId);
            if (missingGroups < 1
                    || missingGroups > MAX_PATH_GROUPS
                    || undisclosedGroups < 0
                    || undisclosedGroups > missingGroups) {
                throw new IllegalArgumentException("path blocker counts are invalid");
            }
        }

        @Override
        public Kind kind() {
            return Kind.PATH;
        }

        @Override
        public String stableKey() {
            return sortableInt(missingGroups) + "\u0000" + sortableInt(undisclosedGroups);
        }
    }

    record WorkbenchTier(
            ResourceLocation subjectId,
            ResearchInteractionMode interactionMode,
            Optional<ResearchWorkbenchTier> currentTier,
            ResearchWorkbenchTier requiredTier) implements ResearchEligibilityBlocker {
        public WorkbenchTier {
            subjectId = requireSubject(subjectId);
            if (interactionMode == null || currentTier == null || requiredTier == null) {
                throw new IllegalArgumentException("workbench blocker fields cannot be null");
            }
            if (currentTier.filter(tier -> tier.satisfies(requiredTier)).isPresent()) {
                throw new IllegalArgumentException("satisfied workbench tier cannot be a blocker");
            }
        }

        public static WorkbenchTier missing(
                ResourceLocation subjectId,
                ResearchInteractionMode interactionMode,
                ResearchWorkbenchTier requiredTier) {
            return new WorkbenchTier(
                    subjectId,
                    interactionMode,
                    Optional.empty(),
                    requiredTier);
        }

        @Override
        public Kind kind() {
            return Kind.WORKBENCH_TIER;
        }

        @Override
        public String stableKey() {
            return interactionMode + "\u0000"
                    + currentTier.map(tier -> sortableInt(tier.level()))
                            .orElseGet(() -> sortableInt(0))
                    + "\u0000" + sortableInt(requiredTier.level());
        }
    }

    record Gate(
            ResourceLocation subjectId,
            ResearchInteractionMode interactionMode,
            int groupOrdinal,
            ProgressionGateCondition condition) implements ResearchEligibilityBlocker {
        public Gate {
            subjectId = requireSubject(subjectId);
            if (interactionMode == null
                    || groupOrdinal < 0
                    || groupOrdinal >= MAX_GATE_GROUPS
                    || condition == null
                    || !condition.appliesTo(interactionMode)) {
                throw new IllegalArgumentException("Progression Gate blocker is invalid");
            }
        }

        @Override
        public Kind kind() {
            return Kind.PROGRESSION_GATE;
        }

        @Override
        public String stableKey() {
            return interactionMode + "\u0000" + sortableInt(groupOrdinal)
                    + "\u0000" + condition.canonicalKey();
        }
    }

    record ResearchPoints(
            ResourceLocation subjectId,
            int available,
            int required) implements ResearchEligibilityBlocker {
        public ResearchPoints {
            subjectId = requireSubject(subjectId);
            if (available < 0
                    || required < 1
                    || available > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || required > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || available >= required) {
                throw new IllegalArgumentException("Research Point blocker is invalid");
            }
        }

        @Override
        public Kind kind() {
            return Kind.RESEARCH_POINTS;
        }

        @Override
        public String stableKey() {
            return sortableInt(required) + "\u0000" + sortableInt(available);
        }
    }

    record Materials(
            ResourceLocation subjectId,
            int missingTypes,
            int missingUnits) implements ResearchEligibilityBlocker {
        public Materials {
            subjectId = requireSubject(subjectId);
            if (missingTypes < 1
                    || missingTypes > MAX_MATERIAL_TYPES
                    || missingUnits < missingTypes
                    || missingUnits > MAX_MATERIAL_UNITS) {
                throw new IllegalArgumentException("material blocker counts are invalid");
            }
        }

        @Override
        public Kind kind() {
            return Kind.MATERIALS;
        }

        @Override
        public String stableKey() {
            return sortableInt(missingTypes) + "\u0000" + sortableInt(missingUnits);
        }
    }

    record Capacity(ResourceLocation subjectId, CapacityReason reason)
            implements ResearchEligibilityBlocker {
        public Capacity {
            subjectId = requireSubject(subjectId);
            if (reason == null) {
                throw new IllegalArgumentException("capacity blocker reason cannot be null");
            }
        }

        @Override
        public Kind kind() {
            return Kind.CAPACITY;
        }

        @Override
        public String stableKey() {
            return reason.name();
        }
    }

    int MAX_PATH_GROUPS = 4_096;
    int MAX_GATE_GROUPS = 32;
    int MAX_MATERIAL_TYPES = PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
    int MAX_MATERIAL_UNITS = PlayerProgressionLimits.MAX_RESEARCH_POINTS;

    private static ResourceLocation requireSubject(ResourceLocation subjectId) {
        return ProgressionIds.require(subjectId, "eligibility blocker subject ID");
    }

    private static String sortableInt(int value) {
        String raw = Integer.toString(value);
        return "0".repeat(10 - raw.length()) + raw;
    }

    enum Kind {
        POLICY(0),
        PATH(10),
        WORKBENCH_TIER(20),
        PROGRESSION_GATE(30),
        RESEARCH_POINTS(40),
        MATERIALS(50),
        CAPACITY(60);

        private final int priority;

        Kind(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }
    }

    enum PolicyReason {
        ACTION_DISABLED,
        NOT_INCLUDED,
        DISCOVERY_REQUIRED,
        NO_VALID_ROUTE,
        STALE_POLICY
    }

    enum CapacityReason {
        PROGRESSION_COLLECTION_FULL,
        OUTPUT_DELIVERY_BLOCKED
    }
}
