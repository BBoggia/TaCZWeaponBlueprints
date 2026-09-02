package com.gamergaming.taczweaponblueprints.research.tree;

import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupDefinition;

/**
 * Immutable, bounded presentation policy for a layered Research Tree layout.
 * Progression topology never belongs in this client-side policy.
 */
public record ResearchTreeLayoutPolicy(
        int canvasPadding,
        int nodeGap,
        int tierGap,
        int componentGap,
        int intraGroupGap,
        int interGroupGap,
        int groupPadding,
        int groupHeaderHeight,
        int portalPadding,
        int maxRankBlockWidth,
        int orderingSweeps,
        int compactionSweeps) {
    public static final int MAX_SPACING = 220;
    public static final int MAX_SWEEPS = 32;

    /** Preserves the Phase 0 spacing values; composers add their required envelopes. */
    public static final ResearchTreeLayoutPolicy UNIFIED_OVERVIEW =
            new ResearchTreeLayoutPolicy(
                    20,
                    24,
                    44,
                    24,
                    24,
                    48,
                    12,
                    18,
                    4,
                    960,
                    6,
                    6);

    public ResearchTreeLayoutPolicy {
        requireSpacing(canvasPadding, "canvas padding");
        requireSpacing(nodeGap, "node gap");
        requireSpacing(tierGap, "tier gap");
        requireSpacing(componentGap, "component gap");
        requireSpacing(intraGroupGap, "intra-group gap");
        requireSpacing(interGroupGap, "inter-group gap");
        requireSpacing(groupPadding, "group padding");
        requireSpacing(groupHeaderHeight, "group header height");
        requireSpacing(portalPadding, "portal padding");
        if (maxRankBlockWidth < ResearchTreeLayout.NODE_WIDTH
                || maxRankBlockWidth > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "research layout maximum rank-block width is invalid");
        }
        if (interGroupGap < intraGroupGap) {
            throw new IllegalArgumentException(
                    "research layout inter-group gap cannot be smaller than its intra-group gap");
        }
        if (orderingSweeps < 0 || orderingSweeps > MAX_SWEEPS
                || compactionSweeps < 0 || compactionSweeps > MAX_SWEEPS) {
            throw new IllegalArgumentException("research layout sweep count is invalid");
        }
        ensureWorstCaseRankFits(canvasPadding, nodeGap, ResearchTreeLayout.NODE_WIDTH);
        ensureWorstCaseTierStackFits();
    }

    /** Clearance from a boundary node to the outside edge of its portal bank. */
    public int portalClearance() {
        return Math.addExact(
                ResearchTreeLayout.PORTAL_SIZE + ResearchTreeLayout.PORTAL_NODE_GAP,
                portalPadding);
    }

    private static void requireSpacing(int value, String field) {
        if (value < 0 || value > MAX_SPACING) {
            throw new IllegalArgumentException("research layout " + field + " is invalid");
        }
    }

    private static void ensureWorstCaseRankFits(int padding, int gap, int nodeSize) {
        long extent = Math.addExact(
                Math.multiplyExact(2L, padding),
                Math.addExact(
                        Math.multiplyExact((long) ResearchTreeGraph.MAX_NODES, nodeSize),
                        Math.multiplyExact((long) ResearchTreeGraph.MAX_NODES - 1L, gap)));
        if (extent > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "research layout policy can exceed the logical-canvas boundary");
        }
    }

    private void ensureWorstCaseTierStackFits() {
        long extent = Math.addExact(
                Math.addExact(
                        Math.multiplyExact(2L, canvasPadding),
                        Math.multiplyExact(2L, portalClearance())),
                Math.addExact(
                        Math.multiplyExact(
                                (long) ResearchTreeGroupDefinition.MAX_RANKS,
                                ResearchTreeLayout.NODE_HEIGHT),
                        Math.multiplyExact(
                                (long) ResearchTreeGroupDefinition.MAX_RANKS - 1L,
                                tierGap)));
        if (extent > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "research layout policy can exceed the logical-canvas boundary");
        }
    }
}
