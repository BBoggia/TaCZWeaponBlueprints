package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

class TierByDepthAllocatorTest {
    @Test
    void depthSetsTheTierAndAStrongScoreRaisesIt() {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        // Ten weapons over five ranks. The deepest one is weak and the shallowest one is strong.
        int[] rank = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4};
        int[] score = {95, 10, 20, 30, 40, 50, 60, 70, 80, 5};
        for (int index = 0; index < rank.length; index++) {
            ranks.put("test:w" + index, rank[index]);
            scores.put("test:w" + index, score[index]);
        }
        var tiers = TieAwareWorkbenchTierAllocator.allocateByDepth(
                ranks, scores, AutomaticWorkbenchTierPercentiles.DEFAULT);
        var byScore = TieAwareWorkbenchTierAllocator.allocate(scores, AutomaticWorkbenchTierPercentiles.DEFAULT);

        assertEquals(ResearchWorkbenchTier.TIER_3, tiers.get("test:w0").tier(), "strong but shallow is lifted");
        assertEquals(ResearchWorkbenchTier.TIER_1, tiers.get("test:w1").tier(), "weak and shallow stays early");
        assertEquals(ResearchWorkbenchTier.TIER_3, tiers.get("test:w9").tier(), "weak but deep stays late");
        assertEquals(95, tiers.get("test:w0").score(), "each weapon keeps its own score for diagnostics");
        int[] depthOnly = {1, 1, 1, 1, 2, 2, 2, 2, 3, 3};
        for (int index = 0; index < rank.length; index++) {
            String id = "test:w" + index;
            assertEquals(Math.max(depthOnly[index], byScore.get(id).tier().level()), tiers.get(id).tier().level(),
                    id + " takes the higher of its depth tier and its score tier");
        }
    }

    @Test
    void ranksAndScoresMustDescribeTheSameWeapons() {
        assertThrows(IllegalArgumentException.class, () -> TieAwareWorkbenchTierAllocator.allocateByDepth(
                Map.of("test:a", 0), Map.of("test:b", 10), AutomaticWorkbenchTierPercentiles.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> TieAwareWorkbenchTierAllocator.allocateByDepth(
                Map.of("test:a", -1), Map.of("test:a", 10), AutomaticWorkbenchTierPercentiles.DEFAULT));
    }
}
