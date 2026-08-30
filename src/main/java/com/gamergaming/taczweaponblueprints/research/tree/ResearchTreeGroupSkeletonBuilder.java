package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/** Builds stable group-local shapes without creating reindexed research publications. */
public final class ResearchTreeGroupSkeletonBuilder {
    private ResearchTreeGroupSkeletonBuilder() {
    }

    public static ResearchTreeGroupSkeletonCatalog build(
            ResearchTreePublication publication,
            ResearchTreeLayoutPolicy policy) {
        if (publication == null || policy == null) {
            throw new IllegalArgumentException("Research Tree skeleton inputs cannot be null");
        }
        publication = publication.legacyView();
        if (publication.graph().nodes().isEmpty()) {
            return ResearchTreeGroupSkeletonCatalog.EMPTY;
        }

        Map<ResourceLocation, List<ResearchTreeGraph.Edge>> internalEdgesByGroup =
                new LinkedHashMap<>();
        publication.presentation().groups().forEach(group ->
                internalEdgesByGroup.put(group.id(), new ArrayList<>()));
        List<ResearchTreeGroupSkeletonCatalog.CrossGroupEdge> crossGroupEdges =
                new ArrayList<>();
        for (ResearchTreeGraph.Edge edge : publication.graph().edges()) {
            ResearchTreePresentation.Membership prerequisiteMembership =
                    publication.presentation()
                    .membership(edge.prerequisiteId())
                    .orElseThrow();
            ResearchTreePresentation.Membership dependentMembership =
                    publication.presentation()
                    .membership(edge.dependentId())
                    .orElseThrow();
            if (prerequisiteMembership.rank() >= dependentMembership.rank()) {
                throw new IllegalArgumentException(
                        "Research Tree skeleton edge does not advance to a higher rank");
            }
            ResourceLocation prerequisiteGroupId = prerequisiteMembership.groupId();
            ResourceLocation dependentGroupId = dependentMembership.groupId();
            if (prerequisiteGroupId.equals(dependentGroupId)) {
                internalEdgesByGroup.get(prerequisiteGroupId).add(edge);
            } else {
                crossGroupEdges.add(new ResearchTreeGroupSkeletonCatalog.CrossGroupEdge(
                        edge.prerequisiteId(),
                        edge.dependentId(),
                        prerequisiteGroupId,
                        dependentGroupId));
            }
        }

        List<ResearchTreeGroupSkeleton> skeletons = new ArrayList<>(
                publication.presentation().groups().size());
        for (ResearchTreePresentation.Group group : publication.presentation().groups()) {
            skeletons.add(buildGroup(
                    publication,
                    group,
                    internalEdgesByGroup.get(group.id()),
                    policy));
        }
        return new ResearchTreeGroupSkeletonCatalog(skeletons, crossGroupEdges);
    }

    private static ResearchTreeGroupSkeleton buildGroup(
            ResearchTreePublication publication,
            ResearchTreePresentation.Group group,
            List<ResearchTreeGraph.Edge> internalEdges,
            ResearchTreeLayoutPolicy policy) {
        Set<ResourceLocation> memberIds = new LinkedHashSet<>();
        Map<ResourceLocation, ResearchTreePresentation.Member> membersById =
                new LinkedHashMap<>();
        for (ResearchTreePresentation.Member member : group.members()) {
            if (publication.graph().node(member.nodeId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "Research Tree skeleton group references an unknown source node");
            }
            memberIds.add(member.nodeId());
            membersById.put(member.nodeId(), member);
        }

        if (internalEdges.stream().anyMatch(edge ->
                !memberIds.contains(edge.prerequisiteId())
                        || !memberIds.contains(edge.dependentId()))) {
            throw new IllegalArgumentException(
                    "Research Tree skeleton received an edge outside its source group");
        }
        boolean authoredBlock = group.kind() == ResearchTreePresentation.Kind.AUTHORED;
        List<ResearchTreeLayoutInput.Node> inputNodes = new ArrayList<>(group.members().size());
        for (int localOrdinal = 0; localOrdinal < group.members().size(); localOrdinal++) {
            ResearchTreePresentation.Member member = group.members().get(localOrdinal);
            inputNodes.add(new ResearchTreeLayoutInput.Node(
                    localOrdinal,
                    member.nodeId(),
                    member.rank(),
                    0,
                    member.orderInRank(),
                    authoredBlock ? 0 : localOrdinal));
        }
        List<ResearchTreeLayoutInput.Edge> inputEdges = internalEdges.stream()
                .map(edge -> new ResearchTreeLayoutInput.Edge(
                        edge.prerequisiteId(), edge.dependentId()))
                .toList();
        ResearchTreeLayout rawLayout = ResearchTreeLayeredLayoutEngine.layoutInput(
                new ResearchTreeLayoutInput(inputNodes, inputEdges), policy);

        int minimumNodeX = rawLayout.nodes().stream()
                .mapToInt(ResearchTreeLayout.PositionedNode::x)
                .min()
                .orElseThrow();
        int minimumRouteX = rawLayout.edgeRouteHints().stream()
                .flatMap(hint -> hint.waypoints().stream())
                .mapToInt(waypoint -> waypoint.x() - ResearchTreeLayout.NODE_WIDTH / 2)
                .min()
                .orElse(minimumNodeX);
        int minimumX = Math.min(minimumNodeX, minimumRouteX);
        int minimumNodeY = rawLayout.nodes().stream()
                .mapToInt(ResearchTreeLayout.PositionedNode::y)
                .min()
                .orElseThrow();
        int minimumRouteY = rawLayout.edgeRouteHints().stream()
                .flatMap(hint -> hint.waypoints().stream())
                .mapToInt(waypoint -> waypoint.y() - ResearchTreeLayout.NODE_HEIGHT / 2)
                .min()
                .orElse(minimumNodeY);
        int minimumY = Math.min(minimumNodeY, minimumRouteY);
        int nodeWidth = rawLayout.nodes().stream()
                .mapToInt(node -> node.x() - minimumX + ResearchTreeLayout.NODE_WIDTH)
                .max()
                .orElseThrow();
        int routeWidth = rawLayout.edgeRouteHints().stream()
                .flatMap(hint -> hint.waypoints().stream())
                .mapToInt(waypoint -> waypoint.x() - minimumX + ResearchTreeLayout.NODE_WIDTH / 2)
                .max()
                .orElse(nodeWidth);
        int width = Math.max(nodeWidth, routeWidth);
        int nodeHeight = rawLayout.nodes().stream()
                .mapToInt(node -> node.y() - minimumY + ResearchTreeLayout.NODE_HEIGHT)
                .max()
                .orElseThrow();
        int routeHeight = rawLayout.edgeRouteHints().stream()
                .flatMap(hint -> hint.waypoints().stream())
                .mapToInt(waypoint -> waypoint.y() - minimumY + ResearchTreeLayout.NODE_HEIGHT / 2)
                .max()
                .orElse(nodeHeight);
        int height = Math.max(nodeHeight, routeHeight);

        List<ResearchTreeGroupSkeleton.PositionedNode> positioned = new ArrayList<>(
                rawLayout.nodes().size());
        for (ResearchTreeLayout.PositionedNode local : rawLayout.nodes()) {
            ResearchTreeGraph.Node source = publication.graph()
                    .node(local.blueprintId())
                    .orElseThrow();
            ResearchTreePresentation.Member member = membersById.get(local.blueprintId());
            positioned.add(new ResearchTreeGroupSkeleton.PositionedNode(
                    source.sourceOrdinal(),
                    source.blueprintId(),
                    member.rank(),
                    local.component(),
                    local.tier(),
                    local.orderInTier(),
                    local.x() - minimumX,
                    local.y() - minimumY));
        }
        List<ResearchTreeLayout.EdgeRouteHint> routeHints = rawLayout.edgeRouteHints().stream()
                .map(hint -> new ResearchTreeLayout.EdgeRouteHint(
                        hint.prerequisiteId(),
                        hint.dependentId(),
                        hint.waypoints().stream()
                                .map(waypoint -> new ResearchTreeLayout.RouteWaypoint(
                                        waypoint.rank(),
                                        waypoint.x() - minimumX,
                                        waypoint.y() - minimumY))
                                .toList()))
                .toList();
        return new ResearchTreeGroupSkeleton(
                group.id(),
                group.order(),
                width,
                height,
                rawLayout.tierCount(),
                positioned,
                internalEdges,
                routeHints);
    }
}
