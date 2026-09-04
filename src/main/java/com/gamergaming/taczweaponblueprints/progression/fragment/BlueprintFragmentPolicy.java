package com.gamergaming.taczweaponblueprints.progression.fragment;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

/** Immutable bounds and completion behavior for one blueprint's fragments. */
public record BlueprintFragmentPolicy(
        CompletionMode completionMode,
        int threshold,
        int retainedProgressCap,
        BlueprintFragmentDiscount researchDiscount,
        int learnedTargetResearchPoints) {
    public static final int MAX_THRESHOLD = 1_000_000;
    public static final int MAX_ARCHIVED_FRAGMENTS = 1_000_000_000;
    public static final BlueprintFragmentPolicy DISABLED = new BlueprintFragmentPolicy(
            CompletionMode.DISABLED,
            0,
            0,
            BlueprintFragmentDiscount.NONE,
            0);

    public BlueprintFragmentPolicy {
        if (completionMode == null || researchDiscount == null) {
            throw new IllegalArgumentException("fragment policy fields cannot be null");
        }
        if (learnedTargetResearchPoints < 0
                || learnedTargetResearchPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("learned-target fragment value is out of bounds");
        }
        if (completionMode == CompletionMode.DISABLED) {
            if (threshold != 0
                    || retainedProgressCap != 0
                    || !BlueprintFragmentDiscount.NONE.equals(researchDiscount)
                    || learnedTargetResearchPoints != 0) {
                throw new IllegalArgumentException("disabled fragment policy contains active values");
            }
        } else {
            if (threshold < 1
                    || threshold > MAX_THRESHOLD
                    || retainedProgressCap < threshold
                    || retainedProgressCap > MAX_ARCHIVED_FRAGMENTS) {
                throw new IllegalArgumentException("fragment threshold or retention cap is invalid");
            }
            if (completionMode == CompletionMode.TARGETED_RESEARCH_BOOST
                    && researchDiscount.mode() == BlueprintFragmentDiscount.Mode.NONE) {
                throw new IllegalArgumentException("research-boost fragments require a discount");
            }
            if (completionMode == CompletionMode.RECONSTRUCT_BLUEPRINT
                    && researchDiscount.mode() != BlueprintFragmentDiscount.Mode.NONE) {
                throw new IllegalArgumentException(
                        "reconstruction fragments cannot also discount research");
            }
        }
    }

    public boolean enabled() {
        return completionMode != CompletionMode.DISABLED;
    }

    public BlueprintFragmentProgress progress(int archived) {
        if (!enabled()) {
            throw new IllegalStateException("fragment progress is disabled");
        }
        return new BlueprintFragmentProgress(archived, threshold);
    }

    public int researchDiscountFor(int originalCost, int archived) {
        if (completionMode != CompletionMode.TARGETED_RESEARCH_BOOST
                || !progress(archived).hasCompleteSet()) {
            return 0;
        }
        return researchDiscount.discountFor(originalCost);
    }

    /** Plans an archive without mutating inventory or player state. */
    public ArchiveResult archive(int current, int offered) {
        if (!enabled()) {
            throw new IllegalStateException("fragment archiving is disabled");
        }
        if (current < 0 || current > MAX_ARCHIVED_FRAGMENTS || offered < 0) {
            throw new IllegalArgumentException("fragment archive inputs are invalid");
        }
        int capacity = Math.max(0, retainedProgressCap - Math.min(current, retainedProgressCap));
        int accepted = Math.min(offered, capacity);
        return new ArchiveResult(current, offered, accepted, offered - accepted, current + accepted);
    }

    public enum CompletionMode {
        DISABLED,
        TARGETED_RESEARCH_BOOST,
        RECONSTRUCT_BLUEPRINT
    }

    public record ArchiveResult(
            int previous,
            int offered,
            int accepted,
            int rejected,
            int resulting) {
        public ArchiveResult {
            if (previous < 0
                    || previous > MAX_ARCHIVED_FRAGMENTS
                    || offered < 0
                    || accepted < 0
                    || rejected < 0
                    || resulting < previous
                    || resulting > MAX_ARCHIVED_FRAGMENTS
                    || accepted + rejected != offered
                    || resulting - previous != accepted) {
                throw new IllegalArgumentException("invalid fragment archive result");
            }
        }
    }
}
