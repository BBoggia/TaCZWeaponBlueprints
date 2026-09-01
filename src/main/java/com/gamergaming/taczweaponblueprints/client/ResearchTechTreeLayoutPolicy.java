package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;
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
    /** Minimum center-to-center separation for sibling any-of junction diamonds. */
    public static final int REQUIREMENT_JUNCTION_SPACING = 10;
    /**
     * One-third scale keeps a full landscape rank usable in portrait windows;
     * the canvas' 16-pixel minimum hit target preserves interaction accuracy.
     */
    public static final double RESPONSIVE_TARGET_SCALE = 1.0D / 3.0D;
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
        int desiredSameTierStepGap = Math.min(
                policy.tierGap(),
                Math.max(0, policy.nodeGap() - 4));
        int sameTierStepGap = safeSameTierStepGap(
                policy.canvasPadding(),
                desiredSameTierStepGap,
                policy.tierGap());
        int portalPadding = Math.min(
                policy.portalPadding(),
                safePortalPadding(
                        policy.canvasPadding(),
                        sameTierStepGap,
                        policy.tierGap()));
        int maximumUsableRankWidth = Math.min(
                maximumUsableRankWidth(policy.canvasPadding()),
                rankBlockWidth(MAXIMUM_NODES_PER_ROW, policy.nodeGap()));
        return new ResearchTechTreeLayoutPolicy(
                policy.canvasPadding(),
                policy.nodeGap(),
                sameTierStepGap,
                policy.tierGap(),
                portalPadding,
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
     * viewport cannot show the row near one-third scale. The result can never
     * widen a tree beyond its authored maximum.
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

    /** Additional row clearance beyond the first drawable any-of junction. */
    static int requirementJunctionClearance(int drawableGroupCount) {
        if (drawableGroupCount < 0 || drawableGroupCount > ResearchRequirements.MAX_GROUPS) {
            throw new IllegalArgumentException(
                    "Research Tech Tree drawable requirement-group count is invalid");
        }
        return Math.multiplyExact(
                Math.max(0, drawableGroupCount - 1),
                REQUIREMENT_JUNCTION_SPACING);
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
        long extent = worstCaseHeight(
                canvasPadding, sameTierStepGap, tierGap, portalPadding);
        if (extent > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout policy can exceed the canvas height");
        }
    }

    private static int safeSameTierStepGap(
            int canvasPadding,
            int desiredSameTierStepGap,
            int tierGap) {
        long rowTransitions = (long) ResearchTreeGraph.MAX_NODES - 1L;
        long bandTransitions = Math.min(
                rowTransitions,
                (long) ResearchTechTreeDefinition.MAX_PRESENTATION_BANDS - 1L);
        long baseAtZeroStepGap = worstCaseHeight(canvasPadding, 0, tierGap, 0);
        long available = Math.max(
                0L,
                ResearchTreeLayout.MAX_DIMENSION - baseAtZeroStepGap);
        long maximum = available / Math.max(1L, rowTransitions - bandTransitions);
        return Math.min(desiredSameTierStepGap, Math.toIntExact(maximum));
    }

    private static int safePortalPadding(
            int canvasPadding,
            int sameTierStepGap,
            int tierGap) {
        long baseAtZeroPadding = worstCaseHeight(
                canvasPadding, sameTierStepGap, tierGap, 0);
        long available = Math.max(
                0L,
                ResearchTreeLayout.MAX_DIMENSION - baseAtZeroPadding);
        long paddingEnvelopePerPixel = 2L * ResearchTreeGraph.MAX_NODES;
        return Math.toIntExact(Math.min(
                MAX_SPACING,
                available / paddingEnvelopePerPixel));
    }

    private static long worstCaseHeight(
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
        long requirementJunctionEnvelope = Math.multiplyExact(
                (long) ResearchTreeGraph.MAX_EDGES,
                REQUIREMENT_JUNCTION_SPACING);
        return Math.addExact(
                2L * canvasPadding,
                Math.addExact(
                        nodeRows,
                        Math.addExact(
                                Math.addExact(rowGaps, bandGapPremium),
                                Math.addExact(
                                        portalEnvelope,
                                        requirementJunctionEnvelope))));
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
