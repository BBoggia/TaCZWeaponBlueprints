package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResearchTreeBranchLayoutComposerTest {
    @Test
    void composesOneFramedBranchWithoutChangingItsLocalShape() {
        ResearchTreeGroupSkeleton skeleton = pistolSkeleton();
        ResearchTreeLayoutPolicy policy = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;

        ResearchTreeLayout layout = ResearchTreeBranchLayoutComposer.compose(
                skeleton, skeleton.width() + 80, policy);

        int contentWidth = skeleton.width() + 80;
        assertEquals(
                contentWidth + 2 * policy.groupPadding() + 2 * policy.canvasPadding(),
                layout.width());
        assertEquals(
                skeleton.height() + policy.groupHeaderHeight()
                        + 2 * policy.groupPadding() + 2 * policy.canvasPadding()
                        + 2 * policy.portalClearance(),
                layout.height());
        assertEquals(skeleton.tierCount(), layout.tierCount());
        assertEquals(1, layout.groupRegions().size());
        ResearchTreeLayout.GroupRegion region = layout.groupRegions().get(0);
        assertEquals(skeleton.groupId(), region.groupId());
        assertEquals(policy.canvasPadding(), region.x());
        assertEquals(policy.canvasPadding(), region.y());

        int expectedOffsetX = policy.canvasPadding() + policy.groupPadding() + 40;
        int expectedOffsetY = policy.canvasPadding()
                + policy.groupHeaderHeight() + policy.groupPadding()
                + policy.portalClearance();
        for (int index = 0; index < skeleton.nodes().size(); index++) {
            ResearchTreeGroupSkeleton.PositionedNode source = skeleton.nodes().get(index);
            ResearchTreeLayout.PositionedNode composed = layout.nodes().get(index);
            assertEquals(index, composed.nodeOrdinal());
            assertEquals(source.nodeId(), composed.blueprintId());
            assertEquals(source.component(), composed.component());
            assertEquals(source.tier(), composed.tier());
            assertEquals(source.orderInTier(), composed.orderInTier());
            assertEquals(source.x() + expectedOffsetX, composed.x());
            assertEquals(source.y() + expectedOffsetY, composed.y());
        }
        assertTrue(layout.nodes().stream().allMatch(node ->
                node.y() >= region.y() + policy.groupHeaderHeight()));
        assertTrue(layout.hiddenAnchors().isEmpty());
        assertTrue(layout.categoryLanes().isEmpty());
    }

    @Test
    void zeroPaddingPolicyStillReservesSafePortalBanks() {
        ResearchTreeGroupSkeleton skeleton = pistolSkeleton();
        ResearchTreeLayoutPolicy compact = new ResearchTreeLayoutPolicy(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                ResearchTreeLayout.NODE_WIDTH, 0, 0);

        ResearchTreeLayout layout = ResearchTreeBranchLayoutComposer.compose(
                skeleton, 0, compact);
        int clearance = ResearchTreeLayout.PORTAL_SIZE
                + ResearchTreeLayout.PORTAL_NODE_GAP;

        assertTrue(layout.nodes().stream().allMatch(node -> node.y() >= clearance));
        assertTrue(layout.nodes().stream().allMatch(node ->
                node.y() + ResearchTreeLayout.NODE_HEIGHT + clearance <= layout.height()));
    }

    @Test
    void portalPaddingChangesReservedBranchGeometry() {
        ResearchTreeGroupSkeleton skeleton = pistolSkeleton();
        ResearchTreeLayoutPolicy base = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;
        ResearchTreeLayoutPolicy expanded = new ResearchTreeLayoutPolicy(
                base.canvasPadding(), base.nodeGap(), base.tierGap(), base.componentGap(),
                base.intraGroupGap(), base.interGroupGap(), base.groupPadding(),
                base.groupHeaderHeight(), 40, base.maxRankBlockWidth(),
                base.orderingSweeps(), base.compactionSweeps());

        ResearchTreeLayout normal = ResearchTreeBranchLayoutComposer.compose(
                skeleton, 0, base);
        ResearchTreeLayout padded = ResearchTreeBranchLayoutComposer.compose(
                skeleton, 0, expanded);

        assertEquals(72, padded.height() - normal.height());
        assertEquals(36, padded.nodes().get(0).y() - normal.nodes().get(0).y());
    }

    @Test
    void minimumContentWidthNeverShrinksTheSharedSkeleton() {
        ResearchTreeGroupSkeleton skeleton = pistolSkeleton();

        ResearchTreeLayout layout = ResearchTreeBranchLayoutComposer.compose(
                skeleton, 0, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);

        int offsetX = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW.canvasPadding()
                + ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW.groupPadding();
        for (int index = 0; index < skeleton.nodes().size(); index++) {
            assertEquals(
                    skeleton.nodes().get(index).x() + offsetX,
                    layout.nodes().get(index).x());
        }
    }

    @Test
    void rejectsNullInvalidAndUnboundedCompositionInputs() {
        ResearchTreeGroupSkeleton skeleton = pistolSkeleton();
        ResearchTreeLayoutPolicy policy = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;

        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeBranchLayoutComposer.compose(null, 0, policy));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeBranchLayoutComposer.compose(skeleton, 0, null));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeBranchLayoutComposer.compose(skeleton, -1, policy));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeBranchLayoutComposer.compose(
                        skeleton, ResearchTreeLayout.MAX_DIMENSION, policy));
    }

    private static ResearchTreeGroupSkeleton pistolSkeleton() {
        return ResearchTreeGroupSkeletonBuilder.build(
                        ResearchTreeRedesignFixture.defaultPistolProgression(),
                        ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW)
                .group(ResearchTreeRedesignFixture.PISTOL_GROUP_ID)
                .orElseThrow();
    }
}
