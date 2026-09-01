package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;

class ResearchTechTreeLayoutPolicyTest {
    @Test
    void defaultPolicyAllowsOptInTwentyEightNodeRowsAndUsesPortalClearanceOnDemand() {
        ResearchTechTreeLayoutPolicy policy = ResearchTechTreeLayoutPolicy.DEFAULT;

        assertEquals(18, policy.portalClearance());
        assertEquals(20, policy.canvasPadding());
        assertEquals(24, policy.nodeGap());
        assertEquals(20, policy.sameTierStepGap());
        assertEquals(1320, policy.maxRankBlockWidth());
        assertEquals(28, policy.maximumNodesPerRow());
        assertEquals(policy, ResearchTechTreeLayoutPolicy.fromShared(
                com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy
                        .UNIFIED_OVERVIEW));
    }

    @Test
    void responsiveCapacityPreservesConfiguredWidthAtSupportedWidths() {
        ResearchTechTreeLayoutPolicy policy = ResearchTechTreeLayoutPolicy.DEFAULT;

        assertEquals(8, policy.effectiveNodesPerRow(8, 294));
        assertEquals(9, policy.effectiveNodesPerRow(9, 294));
        assertEquals(10, policy.effectiveNodesPerRow(10, 294));
        assertEquals(12, policy.effectiveNodesPerRow(12, 360));
        assertEquals(16, policy.effectiveNodesPerRow(16, 480));
        assertEquals(20, policy.effectiveNodesPerRow(20, 600));
        assertEquals(28, policy.effectiveNodesPerRow(28, 840));
        assertEquals(20, policy.effectiveNodesPerRow(20, 360),
                "portrait-width canvases should keep the normal landscape row intact");
        assertTrue(policy.effectiveNodesPerRow(20, 120) < 8);
        assertThrows(IllegalArgumentException.class, () ->
                policy.effectiveNodesPerRow(10, 0));
    }

    @Test
    void multipleAnyOfJunctionsReceiveBoundedNonOverlappingClearance() {
        assertEquals(0, ResearchTechTreeLayoutPolicy.requirementJunctionClearance(0));
        assertEquals(0, ResearchTechTreeLayoutPolicy.requirementJunctionClearance(1));
        assertEquals(10, ResearchTechTreeLayoutPolicy.requirementJunctionClearance(2));
        assertEquals(70, ResearchTechTreeLayoutPolicy.requirementJunctionClearance(8));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeLayoutPolicy.requirementJunctionClearance(65));
    }

    @Test
    void rejectsNegativeOversizedAndWorstCaseOverflowingPolicies() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchTechTreeLayoutPolicy(
                -1, 0, 0, 0, 0, 24, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTechTreeLayoutPolicy(
                0, 221, 0, 0, 0, 24, 0, 0));
        assertDoesNotThrow(() -> new ResearchTechTreeLayoutPolicy(
                0, 220, 0, 0, 0, 24, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTechTreeLayoutPolicy(
                220, 220, 220, 220, 220,
                ResearchTreeLayout.MAX_DIMENSION, 32, 32));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTechTreeLayoutPolicy(
                0, 0, 180, 0, 30, 24, 0, 0),
                "per-row portal envelopes must participate in worst-case height bounds");
        assertThrows(IllegalArgumentException.class, () -> new ResearchTechTreeLayoutPolicy(
                0, 0, 0, 0, 0, 24, 33, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTechTreeLayoutPolicy(
                0, 0, 0, 0, 0, ResearchTreeLayout.NODE_WIDTH - 1, 0, 0));
    }

    @Test
    void sharedMaximumWidthIsSafelyClampedForTechTreePortalPadding() {
        ResearchTreeLayoutPolicy base = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;
        ResearchTreeLayoutPolicy maximum = new ResearchTreeLayoutPolicy(
                base.canvasPadding(),
                base.nodeGap(),
                base.tierGap(),
                base.componentGap(),
                base.intraGroupGap(),
                base.interGroupGap(),
                base.groupPadding(),
                base.groupHeaderHeight(),
                base.portalPadding(),
                ResearchTreeLayout.MAX_DIMENSION,
                base.orderingSweeps(),
                base.compactionSweeps());

        ResearchTechTreeLayoutPolicy adapted = assertDoesNotThrow(
                () -> ResearchTechTreeLayoutPolicy.fromShared(maximum));

        assertEquals(
                ResearchTechTreeLayoutPolicy.rankBlockWidth(
                        ResearchTechTreeLayoutPolicy.MAXIMUM_NODES_PER_ROW,
                        base.nodeGap()),
                adapted.maxRankBlockWidth());
    }
}
