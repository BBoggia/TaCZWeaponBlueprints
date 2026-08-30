package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.List;

/** Adds one branch frame, header clearance, and portal width around a local skeleton. */
public final class ResearchTreeBranchLayoutComposer {
    private ResearchTreeBranchLayoutComposer() {
    }

    public static ResearchTreeLayout compose(
            ResearchTreeGroupSkeleton skeleton,
            int minimumContentWidth,
            ResearchTreeLayoutPolicy policy) {
        if (skeleton == null || policy == null) {
            throw new IllegalArgumentException("Research Tree branch composition cannot be null");
        }
        if (minimumContentWidth < 0
                || minimumContentWidth > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException("invalid Research Tree branch portal width");
        }

        int contentWidth = Math.max(skeleton.width(), minimumContentWidth);
        int portalClearance = policy.portalClearance();
        int regionWidth = Math.addExact(
                contentWidth,
                Math.multiplyExact(2, policy.groupPadding()));
        int regionHeight = Math.addExact(
                policy.groupHeaderHeight(),
                Math.addExact(
                        Math.addExact(
                                skeleton.height(),
                                Math.multiplyExact(2, policy.groupPadding())),
                        Math.multiplyExact(2, portalClearance)));
        int canvasWidth = Math.addExact(
                regionWidth,
                Math.multiplyExact(2, policy.canvasPadding()));
        int canvasHeight = Math.addExact(
                regionHeight,
                Math.multiplyExact(2, policy.canvasPadding()));
        ensureDimension(canvasWidth);
        ensureDimension(canvasHeight);

        int originX = Math.addExact(
                Math.addExact(policy.canvasPadding(), policy.groupPadding()),
                (contentWidth - skeleton.width()) / 2);
        int originY = Math.addExact(
                Math.addExact(
                        policy.canvasPadding() + policy.groupHeaderHeight(),
                        policy.groupPadding()),
                portalClearance);
        List<ResearchTreeLayout.PositionedNode> positioned = new ArrayList<>(
                skeleton.nodes().size());
        for (int localOrdinal = 0; localOrdinal < skeleton.nodes().size(); localOrdinal++) {
            ResearchTreeGroupSkeleton.PositionedNode node = skeleton.nodes().get(localOrdinal);
            positioned.add(new ResearchTreeLayout.PositionedNode(
                    localOrdinal,
                    node.nodeId(),
                    node.component(),
                    node.tier(),
                    node.orderInTier(),
                    Math.addExact(originX, node.x()),
                    Math.addExact(originY, node.y())));
        }
        ResearchTreeLayout.GroupRegion region = new ResearchTreeLayout.GroupRegion(
                skeleton.groupId(),
                policy.canvasPadding(),
                policy.canvasPadding(),
                regionWidth,
                regionHeight);
        List<ResearchTreeLayout.EdgeRouteHint> routeHints = skeleton.edgeRouteHints().stream()
                .map(hint -> new ResearchTreeLayout.EdgeRouteHint(
                        hint.prerequisiteId(),
                        hint.dependentId(),
                        hint.waypoints().stream()
                                .map(waypoint -> new ResearchTreeLayout.RouteWaypoint(
                                        waypoint.rank(),
                                        Math.addExact(originX, waypoint.x()),
                                        Math.addExact(originY, waypoint.y())))
                                .toList()))
                .toList();
        return new ResearchTreeLayout(
                canvasWidth,
                canvasHeight,
                skeleton.tierCount(),
                positioned,
                List.of(),
                List.of(),
                List.of(region),
                routeHints);
    }

    private static void ensureDimension(int value) {
        if (value <= 0 || value > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "composed Research Tree branch exceeds its dimension limit");
        }
    }
}
