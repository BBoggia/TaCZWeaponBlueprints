package com.gamergaming.taczweaponblueprints.progression;

/** Coarse server policy for physical blueprint copies a player already knows. */
public enum DuplicateBlueprintPolicy {
    /** Duplicates remain ordinary tradeable items and cannot be recycled. */
    KEEP,
    /** Duplicates remain ordinary items until manually recycled by a player. */
    MANUAL_RECYCLING
}
