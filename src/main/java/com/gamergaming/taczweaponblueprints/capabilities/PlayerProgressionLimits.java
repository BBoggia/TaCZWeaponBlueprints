package com.gamergaming.taczweaponblueprints.capabilities;

/**
 * Shared safety boundaries for persisted and synchronized player progression.
 * Gameplay configuration may impose lower limits, but never higher ones.
 */
public final class PlayerProgressionLimits {
    public static final int DATA_VERSION = 1;
    public static final int MAX_IDS_PER_COLLECTION = 4096;
    public static final int MAX_RESOURCE_ID_LENGTH = 256;
    public static final int MAX_RESEARCH_POINTS = 1_000_000_000;

    private PlayerProgressionLimits() {
    }
}
