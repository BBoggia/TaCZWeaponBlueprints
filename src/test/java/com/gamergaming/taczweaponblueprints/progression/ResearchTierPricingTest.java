package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;

class ResearchTierPricingTest {
    private static final BlueprintResearchCost EIGHT = new BlueprintResearchCost(8, List.of());

    @Test
    void defaultsLeaveEveryPriceExactlyAsTheDatapackWroteIt() {
        assertTrue(ResearchTierPricing.NONE.unchanged());
        for (int tier = 0; tier <= 3; tier++) {
            assertSame(EIGHT, ResearchTierPricing.NONE.apply(EIGHT, tier));
        }
    }

    @Test
    void laterTiersPayTheirPercentageAndTierOneNeverChanges() {
        ResearchTierPricing pricing = new ResearchTierPricing(200, 400);
        assertEquals(8, pricing.apply(EIGHT, 1).points());
        assertEquals(16, pricing.apply(EIGHT, 2).points());
        assertEquals(32, pricing.apply(EIGHT, 3).points());
        assertEquals(8, pricing.apply(EIGHT, 0).points(), "a blueprint with no known tier pays the datapack price");
    }

    @Test
    void scalingRoundsUpKeepsFreeResearchFreeAndRespectsTheCurrencyLimit() {
        ResearchTierPricing discount = new ResearchTierPricing(50, 150);
        assertEquals(2, discount.apply(new BlueprintResearchCost(3, List.of()), 2).points());
        assertEquals(5, discount.apply(new BlueprintResearchCost(3, List.of()), 3).points());
        assertEquals(0, discount.apply(new BlueprintResearchCost(0, List.of()), 3).points());
        assertEquals(PlayerProgressionLimits.MAX_RESEARCH_POINTS, new ResearchTierPricing(100, 1000)
                .apply(new BlueprintResearchCost(PlayerProgressionLimits.MAX_RESEARCH_POINTS, List.of()), 3).points());
    }

    @Test
    void percentagesOutsideTheSupportedRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchTierPricing(49, 100));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTierPricing(100, 1001));
    }

    @Test
    void theOverallPriceAppliesToEveryTierAndMultipliesWithTierPrices() {
        ResearchTierPricing pricing = new ResearchTierPricing(150, 200, 300);
        BlueprintResearchCost eight = new BlueprintResearchCost(8, java.util.List.of());
        assertEquals(12, pricing.apply(eight, 1).points(), "Tier 1 pays the overall price");
        assertEquals(24, pricing.apply(eight, 2).points());
        assertEquals(36, pricing.apply(eight, 3).points());
        assertEquals(12, pricing.apply(eight, 0).points(), "a blueprint with no tier still pays the overall price");
        assertEquals(3, new ResearchTierPricing(75, 100, 100).apply(new BlueprintResearchCost(4, java.util.List.of()), 1)
                .points(), "a discount rounds up");
        assertEquals(1, new ResearchTierPricing(25, 100, 100).apply(new BlueprintResearchCost(1, java.util.List.of()), 1)
                .points(), "and never reaches free");
        assertThrows(IllegalArgumentException.class, () -> new ResearchTierPricing(24, 100, 100));
        assertEquals(new ResearchTierPricing(100, 200, 300), new ResearchTierPricing(200, 300),
                "tier-only pricing leaves the overall price alone");
    }
}
