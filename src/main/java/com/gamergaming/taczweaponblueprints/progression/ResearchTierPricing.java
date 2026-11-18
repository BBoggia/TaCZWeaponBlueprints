package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;

/**
 * Scales the currency part of a research price. {@code allPercent} applies to every blueprint;
 * the tier percentages then apply to blueprints that need a Tier 2 or Tier 3 Research Bench.
 * Values are percentages of the datapack price: 100 leaves it unchanged, 200 doubles it.
 * Material ingredients are never scaled.
 */
public record ResearchTierPricing(int allPercent, int tierTwoPercent, int tierThreePercent) {
    public static final int MIN_ALL_PERCENT = 25;
    public static final int MIN_PERCENT = 50;
    public static final int MAX_PERCENT = 1000;
    public static final int UNCHANGED = 100;
    public static final ResearchTierPricing NONE = new ResearchTierPricing(UNCHANGED, UNCHANGED, UNCHANGED);
    /** Defaults for a new server: deeper research costs more, so the best weapons take longer to reach. */
    public static final int DEFAULT_TIER_TWO_PERCENT = 200;
    public static final int DEFAULT_TIER_THREE_PERCENT = 300;

    /** Tier prices only, with every blueprint otherwise at the datapack price. */
    public ResearchTierPricing(int tierTwoPercent, int tierThreePercent) {
        this(UNCHANGED, tierTwoPercent, tierThreePercent);
    }

    public ResearchTierPricing {
        if (allPercent < MIN_ALL_PERCENT || allPercent > MAX_PERCENT
                || tierTwoPercent < MIN_PERCENT || tierTwoPercent > MAX_PERCENT
                || tierThreePercent < MIN_PERCENT || tierThreePercent > MAX_PERCENT) {
            throw new IllegalArgumentException("research price percentage is outside the supported range");
        }
    }

    /** True when nothing changes any price. */
    public boolean unchanged() {
        return allPercent == UNCHANGED && tiersUnchanged();
    }

    /** True when no tier changes a price, so callers can skip looking tiers up. */
    public boolean tiersUnchanged() {
        return tierTwoPercent == UNCHANGED && tierThreePercent == UNCHANGED;
    }

    public int percentFor(int benchTier) {
        return switch (benchTier) {
            case 2 -> tierTwoPercent;
            case 3 -> tierThreePercent;
            default -> UNCHANGED;
        };
    }

    /** Rounds up so a scaled price never drops to free, and stays within the currency limit. */
    public BlueprintResearchCost apply(BlueprintResearchCost cost, int benchTier) {
        if (cost == null) {
            throw new IllegalArgumentException("research cost cannot be null");
        }
        int tierPercent = percentFor(benchTier);
        if (allPercent == UNCHANGED && tierPercent == UNCHANGED || cost.points() == 0) {
            return cost;
        }
        long scaled = ((long) cost.points() * allPercent * tierPercent + 9_999L) / 10_000L;
        return new BlueprintResearchCost(
                (int) Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, scaled), cost.ingredients());
    }
}
