package com.gamergaming.taczweaponblueprints.villager.trade;

/** Deliberately below the catalog and native merchant transport budgets. */
public final class TradeLimits {
    private TradeLimits() {}
    public static final int DEFINITIONS = 256, SLOTS_PER_PROFESSION = 128, OWNED_OFFERS = 256;
    public static final int CHOICES = 64, CANDIDATE_IDS = 4096, FALLBACK_DEPTH = 8;
    public static final int JSON_CHARACTERS = 65_536, TOTAL_JSON_CHARACTERS = 4 * 1024 * 1024;
    public static final int JSON_DEPTH = 16, ID_LENGTH = com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH, USES = 1024, XP = 1000;
}
