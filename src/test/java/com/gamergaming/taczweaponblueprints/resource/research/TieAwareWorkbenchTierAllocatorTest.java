package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

class TieAwareWorkbenchTierAllocatorTest {
    @Test
    void equalScoresStayTogetherAcrossPercentileBoundaries() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("test:a", 10);
        scores.put("test:b", 10);
        scores.put("test:c", 20);
        scores.put("test:d", 30);
        scores.put("test:e", 40);
        scores.put("test:f", 50);
        scores.put("test:g", 90);
        scores.put("test:h", 90);

        var assignment = TieAwareWorkbenchTierAllocator.allocate(
                scores, AutomaticWorkbenchTierPercentiles.DEFAULT);

        assertEquals(ResearchWorkbenchTier.TIER_1, assignment.get("test:a").tier());
        assertEquals(assignment.get("test:a"), assignment.get("test:b"));
        assertEquals(ResearchWorkbenchTier.TIER_2, assignment.get("test:d").tier());
        assertEquals(ResearchWorkbenchTier.TIER_3, assignment.get("test:g").tier());
        assertEquals(assignment.get("test:g"), assignment.get("test:h"));
    }

    @Test
    void allEqualCatalogUsesOneMiddleTierRatherThanSplittingArbitrarily() {
        var assignment = TieAwareWorkbenchTierAllocator.allocate(
                Map.of("test:c", 50, "test:a", 50, "test:b", 50),
                AutomaticWorkbenchTierPercentiles.DEFAULT);

        assertEquals(1, assignment.values().stream().distinct().count());
        assertEquals(ResearchWorkbenchTier.TIER_2, assignment.get("test:a").tier());
        assertEquals(3, assignment.get("test:a").tieCount());
    }

    @Test
    void evenSingleEntryInputsAreValidated() {
        assertThrows(IllegalArgumentException.class, () ->
                TieAwareWorkbenchTierAllocator.allocate(
                        Map.of("test:bad", 101), AutomaticWorkbenchTierPercentiles.DEFAULT));
    }
}
