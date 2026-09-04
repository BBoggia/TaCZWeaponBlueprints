package com.gamergaming.taczweaponblueprints.resource.research;

/** Basis-point boundaries for tie-aware automatic three-tier assignment. */
public record AutomaticWorkbenchTierPercentiles(
        int tierOneUpperBasisPoints,
        int tierTwoUpperBasisPoints) {
    public static final int BASIS_POINTS = 10_000;
    public static final AutomaticWorkbenchTierPercentiles DEFAULT =
            new AutomaticWorkbenchTierPercentiles(3_500, 7_500);

    public AutomaticWorkbenchTierPercentiles {
        if (tierOneUpperBasisPoints < 1
                || tierOneUpperBasisPoints >= tierTwoUpperBasisPoints
                || tierTwoUpperBasisPoints >= BASIS_POINTS) {
            throw new IllegalArgumentException(
                    "automatic Research Bench tier percentiles must be ordered inside 0-10000");
        }
    }
}
