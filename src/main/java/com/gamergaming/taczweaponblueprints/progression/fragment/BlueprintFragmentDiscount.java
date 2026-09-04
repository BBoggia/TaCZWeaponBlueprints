package com.gamergaming.taczweaponblueprints.progression.fragment;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

/** Overflow-safe fixed or percentage discount for one integer research cost. */
public record BlueprintFragmentDiscount(Mode mode, int value) {
    public static final int BASIS_POINTS = 10_000;
    public static final BlueprintFragmentDiscount NONE = new BlueprintFragmentDiscount(
            Mode.NONE,
            0);

    public BlueprintFragmentDiscount {
        if (mode == null) {
            throw new IllegalArgumentException("fragment discount mode cannot be null");
        }
        boolean valid = switch (mode) {
            case NONE -> value == 0;
            case FIXED -> value > 0
                    && value <= PlayerProgressionLimits.MAX_RESEARCH_POINTS;
            case PERCENTAGE -> value > 0 && value <= BASIS_POINTS;
        };
        if (!valid) {
            throw new IllegalArgumentException("invalid fragment discount value");
        }
    }

    public static BlueprintFragmentDiscount fixed(int units) {
        return new BlueprintFragmentDiscount(Mode.FIXED, units);
    }

    /** Creates a percentage discount in basis points: 2,500 is 25%. */
    public static BlueprintFragmentDiscount percentage(int basisPoints) {
        return new BlueprintFragmentDiscount(Mode.PERCENTAGE, basisPoints);
    }

    /** Percentage discounts round the removed amount down, preserving at least the remainder. */
    public int discountFor(int originalCost) {
        validateCost(originalCost);
        return switch (mode) {
            case NONE -> 0;
            case FIXED -> Math.min(originalCost, value);
            case PERCENTAGE -> Math.toIntExact(
                    (long) originalCost * (long) value / BASIS_POINTS);
        };
    }

    public int applyTo(int originalCost) {
        return originalCost - discountFor(originalCost);
    }

    private static void validateCost(int cost) {
        if (cost < 0 || cost > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("research cost is outside progression bounds");
        }
    }

    public enum Mode {
        NONE,
        FIXED,
        PERCENTAGE
    }
}
