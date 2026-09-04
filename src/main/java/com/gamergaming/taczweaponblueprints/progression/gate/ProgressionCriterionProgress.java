package com.gamergaming.taczweaponblueprints.progression.gate;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;

import net.minecraft.resources.ResourceLocation;

/** Bounded durable progress for one custom event criterion. */
public record ProgressionCriterionProgress(ResourceLocation criterionId, int value) {
    public static final int MAX_VALUE = PlayerProgressionLimits.MAX_RESEARCH_POINTS;

    public ProgressionCriterionProgress {
        criterionId = ProgressionIds.require(criterionId, "criterion ID");
        if (value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException("criterion progress is out of bounds");
        }
    }

    public static ProgressionCriterionProgress of(String criterionId, int value) {
        return new ProgressionCriterionProgress(
                ProgressionIds.parse(criterionId, "criterion ID"),
                value);
    }

    public boolean satisfies(int requiredValue) {
        if (requiredValue < 1 || requiredValue > MAX_VALUE) {
            throw new IllegalArgumentException("required criterion progress is out of bounds");
        }
        return value >= requiredValue;
    }

    /** Increments without allowing hostile values to wrap into negative progress. */
    public ProgressionCriterionProgress increment(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("criterion increment cannot be negative");
        }
        int next = (int) Math.min((long) MAX_VALUE, (long) value + (long) amount);
        return next == value ? this : new ProgressionCriterionProgress(criterionId, next);
    }
}
