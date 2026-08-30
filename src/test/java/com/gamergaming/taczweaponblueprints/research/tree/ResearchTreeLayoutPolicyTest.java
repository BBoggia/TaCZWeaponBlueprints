package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResearchTreeLayoutPolicyTest {
    @Test
    void unifiedPolicyPreservesThePhaseZeroGeometryContract() {
        ResearchTreeLayoutPolicy policy = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;

        assertEquals(20, policy.canvasPadding());
        assertEquals(24, policy.nodeGap());
        assertEquals(44, policy.tierGap());
        assertEquals(24, policy.componentGap());
        assertEquals(24, policy.intraGroupGap());
        assertEquals(48, policy.interGroupGap());
        assertEquals(12, policy.groupPadding());
        assertEquals(18, policy.groupHeaderHeight());
        assertEquals(4, policy.portalPadding());
        assertEquals(18, policy.portalClearance());
        assertEquals(960, policy.maxRankBlockWidth());
        assertEquals(6, policy.orderingSweeps());
        assertEquals(6, policy.compactionSweeps());

        assertEquals(policy.canvasPadding(), ResearchTreeUnifiedLayoutEngine.PADDING);
        assertEquals(policy.nodeGap(), ResearchTreeUnifiedLayoutEngine.NODE_GAP);
        assertEquals(policy.tierGap(), ResearchTreeUnifiedLayoutEngine.TIER_GAP);
        assertEquals(policy.componentGap(), ResearchTreeUnifiedLayoutEngine.COMPONENT_GAP);
    }

    @Test
    void boundedValuesAcceptCompactAndMaximumSafePolicies() {
        ResearchTreeLayoutPolicy compact = policy(0, 0, 0, 0, 0, 0, 0, 0);
        ResearchTreeLayoutPolicy maximum = policy(
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SWEEPS,
                ResearchTreeLayoutPolicy.MAX_SWEEPS);

        assertEquals(0, compact.nodeGap());
        assertEquals(ResearchTreeLayout.PORTAL_SIZE + ResearchTreeLayout.PORTAL_NODE_GAP,
                compact.portalClearance());
        assertEquals(ResearchTreeLayoutPolicy.MAX_SPACING, maximum.nodeGap());
        assertEquals(ResearchTreeLayoutPolicy.MAX_SWEEPS, maximum.orderingSweeps());
    }

    @Test
    void invalidSpacingOrderingAndWorkBoundsFailAtConfigurationTime() {
        assertThrows(IllegalArgumentException.class,
                () -> policy(-1, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> policy(0, ResearchTreeLayoutPolicy.MAX_SPACING + 1,
                        0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> policy(0, 0, 0, 0, 20, 19, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> policy(0, 0, 0, 0, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> policy(0, 0, 0, 0, 0, 0,
                        ResearchTreeLayoutPolicy.MAX_SWEEPS + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeLayoutPolicy(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                ResearchTreeLayout.NODE_WIDTH - 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeLayoutPolicy(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                ResearchTreeLayout.MAX_DIMENSION + 1, 0, 0));
    }

    @Test
    void strategyFactoryRejectsMissingPolicy() {
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeLayoutStrategy.layered(null));
    }

    private static ResearchTreeLayoutPolicy policy(
            int padding,
            int nodeGap,
            int tierGap,
            int componentGap,
            int intraGroupGap,
            int interGroupGap,
            int orderingSweeps,
            int compactionSweeps) {
        return new ResearchTreeLayoutPolicy(
                padding,
                nodeGap,
                tierGap,
                componentGap,
                intraGroupGap,
                interGroupGap,
                0,
                0,
                0,
                4_096,
                orderingSweeps,
                compactionSweeps);
    }
}
