package com.gamergaming.taczweaponblueprints.capabilities;

/**
 * Shared safety boundaries for persisted and synchronized player progression.
 * Gameplay configuration may impose lower limits, but never higher ones.
 */
public final class PlayerProgressionLimits {
    public static final int DATA_VERSION = 3;
    public static final int MAX_IDS_PER_COLLECTION = 4096;
    public static final int MAX_RESOURCE_ID_LENGTH = 256;
    public static final int MAX_RESEARCH_POINTS = 1_000_000_000;
    public static final int MAX_RESEARCH_POINT_AWARD_CLAIMS = 4096;
    public static final int MAX_RESEARCH_POINT_AWARD_RATE_STATES = 512;
    public static final int MAX_RESEARCH_POINT_AWARD_WINDOW_ENTRIES = 4096;
    public static final int MAX_RESEARCH_POINT_AWARD_DEFINITIONS = 4096;
    public static final int MAX_RESEARCH_POINT_AWARD_MILESTONE_DEFINITIONS = 512;
    public static final int MAX_RESEARCH_POINT_AWARD_GROUPS_PER_EVENT = 64;
    public static final int MAX_RESEARCH_POINT_AWARD_PROFILES = 64;
    public static final int MAX_RESEARCH_POINT_AWARD_SELECTOR_TERMS = 256;
    public static final int MAX_RESEARCH_POINT_AWARD_JSON_CHARACTERS = 2_000_000;
    public static final int MAX_RESEARCH_POINT_AWARD_ABSOLUTE_PRIORITY = 1_000_000;
    /** Keeps one bench bulk-redemption packet bounded even with very large stacks. */
    public static final int MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION = 64;
    public static final int MAX_RESEARCH_POINT_FEEDBACK_NAMES = 8;
    public static final int MAX_RESEARCH_POINT_HELP_ENTRIES = 64;
    public static final int MAX_RESEARCH_POINT_INTEGRATION_SOURCES = 256;
    public static final int MAX_RECENT_UNLOCK_BATCHES = 32;
    public static final int MAX_RECENT_UNLOCK_MEMBER_IDS = 256;
    public static final int MAX_RECENT_UNLOCK_MEMBERS_PER_BATCH = 64;

    private PlayerProgressionLimits() {
    }
}
