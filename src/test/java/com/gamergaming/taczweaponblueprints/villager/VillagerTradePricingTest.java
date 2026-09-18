package com.gamergaming.taczweaponblueprints.villager;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillagerTradePricingTest {
    @Test void exactFundsOverlapRetainsTheFullSecondaryCharge() {
        var price = VillagerTradePricing.normalize(24, 4, true, 64, 7500);
        assertEquals(new VillagerTradePricing.Counts(28, 0, 22), price);
    }
    @Test void distinctMaterialRemainsASeparateUndiscountedInput() {
        assertEquals(new VillagerTradePricing.Counts(24, 4, 18), VillagerTradePricing.normalize(24, 4, false, 64, 7500));
    }
    @Test void overlappingCostCannotBeTruncatedToFitOneStack() {
        assertThrows(IllegalArgumentException.class, () -> VillagerTradePricing.normalize(64, 12, true, 64, 7500));
    }
    @Test void changingDemandNeverReusesAnOldCorrection() {
        assertEquals(18, VillagerTradePricing.cost(24, 20, .05F, -30, 18, 64));
        assertEquals(18, VillagerTradePricing.cost(24, 0, .05F, -30, 18, 64));
        assertEquals(24, VillagerTradePricing.cost(24, 0, .05F, 0, 18, 64));
    }
    @Test void extremeDemandAndReputationRemainBounded() {
        assertEquals(64, VillagerTradePricing.cost(24, Integer.MAX_VALUE, .05F, 0, 18, 64));
        assertEquals(18, VillagerTradePricing.cost(24, Integer.MIN_VALUE, .05F, Integer.MIN_VALUE, 18, 64));
        assertEquals(64, VillagerTradePricing.cost(24, 0, 0, Integer.MAX_VALUE, 18, 64));
    }
    @Test void secondaryProtectionHoldsAcrossRepresentablePrices() {
        for (int primary = 1; primary <= 64; primary++) for (int secondary = 0; secondary <= 64-primary; secondary++) {
            var price = VillagerTradePricing.normalize(primary, secondary, true, 64, 7500);
            assertEquals((primary * 3 + 3) / 4 + secondary, price.minimumPrimary());
            assertEquals(primary + secondary, price.primary());
            assertEquals(price.minimumPrimary(), VillagerTradePricing.cost(price.primary(), 0, 0, -10000, price.minimumPrimary(), 64));
        }
    }
    @Test void malformedTermsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> VillagerTradePricing.normalize(0, 4, true, 64, 7500));
        assertThrows(IllegalArgumentException.class, () -> VillagerTradePricing.normalize(24, -4, true, 64, 7500));
        assertThrows(IllegalArgumentException.class, () -> VillagerTradePricing.normalize(24, 4, true, 64, 10001));
        assertThrows(IllegalArgumentException.class, () -> VillagerTradePricing.cost(24, 0, Float.NaN, 0, 18, 64));
        assertThrows(IllegalArgumentException.class, () -> VillagerTradePricing.cost(24, 0, -1, 0, 18, 64));
    }
}
