package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;

/** Immutable, bounded single-canvas geometry policy for Tech Tree domains. */
public record ResearchTechTreeLayoutPolicy(
        int canvasPadding,
        int nodeGap,
        int sameTierStepGap,
        int tierGap,
        int portalPadding,
        int maxRankBlockWidth,
        int orderingSweeps,
        int compactionSweeps) {
    public static final int MAX_SPACING = 220;
    public static final int MAX_SWEEPS = 32;
    /**
     * A half-scale node remains backed by the canvas' 16-pixel minimum hit
     * target while keeping the normal compact canvas above overview zoom.
     */
    public static final double RESPONSIVE_TARGET_SCALE = 0.5D;
    public static final int MAXIMUM_NODES_PER_ROW = 28;
    public static final ResearchTechTreeLayoutPolicy DEFAULT =
            new ResearchTechTreeLayoutPolicy(
                    20,
                    24,
                    20,
                    44,
                    4,
                    rankBlockWidth(MAXIMUM_NODES_PER_ROW, 24),
                    6,
                    6);

    /** Uses the same immutable client spacing snapshot as the legacy canvases. */
    public static ResearchTechTreeLayoutPolicy fromShared(
            com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("shared research layout policy cannot be null");
        }
        int maximumUsableRankWidth = Math.min(
                maximumUsableRankWidth(policy.canvasPadding()),
                rankBlockWidth(MAXIMUM_NODES_PER_ROW, policy.nodeGap()));
        return new ResearchTechTreeLayoutPolicy(
                policy.canvasPadding(),
                policy.nodeGap(),
                Math.min(policy.tierGap(), 20),
                policy.tierGap(),
                policy.portalPadding(),
                maximumUsableRankWidth,
                policy.orderingSweeps(),
                policy.compactionSweeps());
    }

    public ResearchTechTreeLayoutPolicy {
        requireSpacing(canvasPadding, "canvas padding");
        requireSpacing(nodeGap, "node gap");
        requireSpacing(sameTierStepGap, "same-tier step gap");
        requireSpacing(tierGap, "tier gap");
        requireSpacing(portalPadding, "portal padding");
        if (maxRankBlockWidth < ResearchTreeLayout.NODE_WIDTH
                || maxRankBlockWidth > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "Research Tech Tree maximum rank-block width is invalid");
        }
        if (orderingSweeps < 0 || orderingSweeps > MAX_SWEEPS
                || compactionSweeps < 0 || compactionSweeps > MAX_SWEEPS) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout sweep count is invalid");
        }
        ensureWorstCaseWidthFits(canvasPadding, maxRankBlockWidth);
        ensureWorstCaseHeightFits(
                canvasPadding, sameTierStepGap, tierGap, portalPadding);
    }

    /** Space reserved only above or below a row that owns a boundary portal. */
    public int portalClearance() {
        return Math.addExact(
                ResearchTreeLayout.PORTAL_SIZE + ResearchTreeLayout.PORTAL_NODE_GAP,
                portalPadding);
    }

    /** Maximum count placed in one presentation-only wrap row. */
    public int maximumNodesPerRow() {
        return Math.min(MAXIMUM_NODES_PER_ROW, Math.max(1, Math.floorDiv(
                Math.addExact(maxRankBlockWidth, nodeGap),
                Math.addExact(ResearchTreeLayout.NODE_WIDTH, nodeGap))));
    }

    /**
     * Applies the tree-owned 8-28 cap and only wraps more aggressively when a
     * viewport cannot show the row near half scale. The result can never widen
     * a tree beyond its authored maximum.
     */
    public int effectiveNodesPerRow(int treeMaximum, int viewportWidth) {
        if (treeMaximum < 1 || treeMaximum > MAXIMUM_NODES_PER_ROW
                || viewportWidth < 1) {
            throw new IllegalArgumentException(
                    "Research Tech Tree responsive row inputs are invalid");
        }
        int configured = Math.min(treeMaximum, maximumNodesPerRow());
        long readableCanvasWidth = (long) Math.floor(
                viewportWidth / RESPONSIVE_TARGET_SCALE);
        long usableWidth = Math.max(
                ResearchTreeLayout.NODE_WIDTH,
                readableCanvasWidth - 2L * canvasPadding);
        int responsive = Math.max(1, Math.toIntExact(Math.min(
                MAXIMUM_NODES_PER_ROW,
                (usableWidth + nodeGap)
                        / (ResearchTreeLayout.NODE_WIDTH + (long) nodeGap))));
        return Math.min(configured, responsive);
    }

    private static void ensureWorstCaseWidthFits(
            int canvasPadding,
            int maxRankBlockWidth) {
        int horizontalPadding = canvasPadding;
        long extent = Math.addExact(
                2L * horizontalPadding,
                maxRankBlockWidth);
        if (extent > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout policy can exceed the canvas width");
        }
    }

    private static int maximumUsableRankWidth(int canvasPadding) {
        return Math.toIntExact(ResearchTreeLayout.MAX_DIMENSION
                - 2L * canvasPadding);
    }

    static int rankBlockWidth(int nodes, int nodeGap) {
        if (nodes < 1 || nodes > MAXIMUM_NODES_PER_ROW
                || nodeGap < 0 || nodeGap > MAX_SPACING) {
            throw new IllegalArgumentException(
                    "Research Tech Tree rank-block inputs are invalid");
        }
        return Math.addExact(
                Math.multiplyExact(nodes, ResearchTreeLayout.NODE_WIDTH),
                Math.multiplyExact(nodes - 1, nodeGap));
    }

    private static void ensureWorstCaseHeightFits(
            int canvasPadding,
            int sameTierStepGap,
            int tierGap,
            int portalPadding) {
        long nodeRows = Math.multiplyExact(
                (long) ResearchTreeGraph.MAX_NODES,
                ResearchTreeLayout.NODE_HEIGHT);
        long rowGaps = Math.multiplyExact(
                (long) ResearchTreeGraph.MAX_NODES - 1L,
                sameTierStepGap);
        long bandGapPremium = Math.multiplyExact(
                Math.min(
                        (long) ResearchTreeGraph.MAX_NODES - 1L,
                        (long) ResearchTechTreeDefinition.MAX_PRESENTATION_BANDS - 1L),
                Math.max(0, tierGap - sameTierStepGap));
        // Every public node can legitimately own both an incoming requirement
        // portal and an outgoing unlock portal. Bound the actual per-row model,
        // not the older fixed envelope that existed once per legacy tier.
        long portalEnvelope = Math.multiplyExact(
                2L * ResearchTreeGraph.MAX_NODES,
                portalClearance(portalPadding));
        long extent = Math.addExact(
                2L * canvasPadding,
                Math.addExact(
                        nodeRows,
                        Math.addExact(
                                Math.addExact(rowGaps, bandGapPremium),
                                portalEnvelope)));
        if (extent > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout policy can exceed the canvas height");
        }
    }

    private static int portalClearance(int portalPadding) {
        return Math.addExact(
                ResearchTreeLayout.PORTAL_SIZE + ResearchTreeLayout.PORTAL_NODE_GAP,
                portalPadding);
    }

    private static void requireSpacing(int value, String field) {
        if (value < 0 || value > MAX_SPACING) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout " + field + " is invalid");
        }
    }
}
