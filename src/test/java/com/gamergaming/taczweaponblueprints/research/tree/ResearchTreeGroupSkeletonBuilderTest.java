package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeGroupSkeletonBuilderTest {
    @Test
    void packagedPistolBecomesOneTightReusableTree() {
        ResearchTreePublication publication =
                ResearchTreeRedesignFixture.defaultPistolProgression();

        ResearchTreeGroupSkeletonCatalog catalog = ResearchTreeGroupSkeletonBuilder.build(
                publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);
        ResearchTreeGroupSkeleton pistols = catalog.group(
                ResearchTreeRedesignFixture.PISTOL_GROUP_ID).orElseThrow();

        assertEquals(catalog, ResearchTreeGroupSkeletonBuilder.build(
                publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));
        assertEquals(1, catalog.groups().size());
        assertTrue(catalog.crossGroupEdges().isEmpty());
        assertEquals(14, pistols.nodes().size());
        assertEquals(13, pistols.internalEdges().size());
        assertEquals(1L, pistols.nodes().stream()
                .map(ResearchTreeGroupSkeleton.PositionedNode::component)
                .distinct()
                .count());
        assertEquals(1L, pistols.nodes().stream()
                .filter(node -> node.authoredRank() == 2)
                .map(ResearchTreeGroupSkeleton.PositionedNode::y)
                .distinct()
                .count());
        assertEquals(0, pistols.nodes().stream()
                .mapToInt(ResearchTreeGroupSkeleton.PositionedNode::x).min().orElseThrow());
        assertEquals(0, pistols.nodes().stream()
                .mapToInt(ResearchTreeGroupSkeleton.PositionedNode::y).min().orElseThrow());
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), pistols.localLayout());
        ResearchTreeLayout.EdgeRouteHint rhinoRoute = pistols.edgeRouteHints().stream()
                .filter(hint -> hint.prerequisiteId().equals(
                        new ResourceLocation("tacz", "rhino357")))
                .findFirst()
                .orElseThrow();
        assertEquals(new ResourceLocation("tacz", "taurus500"), rhinoRoute.dependentId());
        assertEquals(1, rhinoRoute.waypoints().size());
        ResearchTreeLayout.RouteWaypoint waypoint = rhinoRoute.waypoints().get(0);
        assertEquals(3, waypoint.rank());
        List<ResearchTreeGroupSkeleton.PositionedNode> occupiedRank = pistols.nodes().stream()
                .filter(node -> node.authoredRank() == waypoint.rank())
                .toList();
        assertTrue(occupiedRank.stream().noneMatch(node -> waypoint.x() > node.x()
                && waypoint.x() < node.x() + ResearchTreeLayout.NODE_WIDTH));
        int minimumX = occupiedRank.stream()
                .mapToInt(ResearchTreeGroupSkeleton.PositionedNode::x)
                .min()
                .orElseThrow();
        int maximumRight = occupiedRank.stream()
                .mapToInt(node -> node.x() + ResearchTreeLayout.NODE_WIDTH)
                .max()
                .orElseThrow();
        assertTrue(waypoint.x() <= minimumX || waypoint.x() >= maximumRight,
                "the detached Rhino branch corridor must stay outside the main rank span");
    }

    @Test
    void catalogPartitionsEveryNodeAndRecordsOnlyTruthfulBoundaryEdges() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.connectedProgression();

        ResearchTreeGroupSkeletonCatalog catalog = ResearchTreeGroupSkeletonBuilder.build(
                publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);

        Set<ResourceLocation> skeletonNodeIds = catalog.groups().stream()
                .flatMap(group -> group.nodes().stream())
                .map(ResearchTreeGroupSkeleton.PositionedNode::nodeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertEquals(publication.graph().nodes().stream()
                        .map(ResearchTreeGraph.Node::blueprintId)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                skeletonNodeIds);
        assertEquals(3, catalog.groups().size());
        assertEquals(3, catalog.crossGroupEdges().size());
        assertEquals(Set.of(
                        edge("root", "right"),
                        edge("left_leaf", "merge"),
                        edge("right_leaf", "merge")),
                catalog.crossGroupEdges().stream()
                        .map(ResearchTreeGroupSkeletonCatalog.CrossGroupEdge::edge)
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(catalog.crossGroupEdges().stream().allMatch(edge ->
                publication.graph().edges().contains(edge.edge())));
        assertEquals(
                catalog.crossGroupEdges().stream()
                        .filter(edge -> edge.prerequisiteGroupId().equals(id("starter"))
                                || edge.dependentGroupId().equals(id("starter")))
                        .toList(),
                catalog.incidentEdges(id("starter")));
        assertTrue(catalog.crossGroupEdges().stream()
                .allMatch(edge -> catalog.containsCrossGroupEdge(edge.edge())));
        assertFalse(catalog.containsCrossGroupEdge(edge("root", "merge")));
        assertTrue(catalog.incidentEdges(id("missing_group")).isEmpty());
        assertTrue(catalog.incidentEdges(null).isEmpty());
        assertFalse(catalog.containsCrossGroupEdge(null));
    }

    @Test
    void opposingGroupDirectionsRemainNodeLevelFactsRatherThanAMacroCycle() {
        ResearchTreeGroupSkeletonCatalog catalog = ResearchTreeGroupSkeletonBuilder.build(
                ResearchTreeRedesignFixture.alternatingGroupDependencies(),
                ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);

        assertEquals(2, catalog.groups().size());
        assertEquals(2, catalog.crossGroupEdges().size());
        assertEquals(Set.of("group_a->group_b", "group_b->group_a"),
                catalog.crossGroupEdges().stream()
                        .map(edge -> edge.prerequisiteGroupId().getPath()
                                + "->" + edge.dependentGroupId().getPath())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void redactedGroupKeepsSourceOpaqueIdsWithoutReindexingThem() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.denseGeneratedCatalog();

        ResearchTreeGroupSkeletonCatalog catalog = assertTimeout(
                Duration.ofSeconds(3),
                () -> ResearchTreeGroupSkeletonBuilder.build(
                        publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));
        ResearchTreeGroupSkeleton undisclosed = catalog.group(
                ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID).orElseThrow();

        assertEquals(ResearchTreeRedesignFixture.GENERATED_NODES, undisclosed.nodes().size());
        assertTrue(undisclosed.nodes().stream().allMatch(node ->
                node.nodeId().getPath().startsWith("undisclosed/")));
        assertTrue(undisclosed.nodes().stream().allMatch(node ->
                node.sourceOrdinal() >= ResearchTreeRedesignFixture.CURATED_DEFAULT_NODES));
        assertEquals(undisclosed.nodes().stream()
                        .map(ResearchTreeGroupSkeleton.PositionedNode::nodeId)
                        .toList(),
                undisclosed.localLayout().nodes().stream()
                        .map(ResearchTreeLayout.PositionedNode::blueprintId)
                        .toList());
        assertFalse(undisclosed.nodes().get(0).nodeId().equals(
                ResearchTreeGraph.redactedNodeId(0)),
                "a local skeleton must not rewrite an opaque source ID to its local ordinal");
    }

    @Test
    void emptyPublicationProducesAnEmptyCatalog() {
        assertEquals(ResearchTreeGroupSkeletonCatalog.EMPTY,
                ResearchTreeGroupSkeletonBuilder.build(
                        ResearchTreePublication.EMPTY,
                        ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));
    }

    @Test
    void maximumPublishedGroupCountRemainsDeterministicAndBounded() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(ResearchTreeGraph.MAX_NODES);
        List<ResearchTreePresentation.Group> groups = new ArrayList<>(
                ResearchTreePresentation.MAX_GROUPS);
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            ResourceLocation nodeId = new ResourceLocation("phase_two", "maximum/" + ordinal);
            nodes.add(new ResearchTreeGraph.Node(
                    ordinal,
                    nodeId,
                    "fixture.phase_two.maximum",
                    "rifle",
                    new ResourceLocation("minecraft", "paper"),
                    JournalVisibility.FULL,
                    false,
                    false,
                    true,
                    1,
                    0,
                    0,
                    0,
                    ResearchTreeGraph.Availability.AVAILABLE));
            groups.add(new ResearchTreePresentation.Group(
                    new ResourceLocation("phase_two", "maximum_group/" + ordinal),
                    "Group " + ordinal,
                    Optional.empty(),
                    Optional.of(nodeId),
                    ordinal,
                    ResearchTreePresentation.Kind.AUTHORED,
                    List.of(new ResearchTreePresentation.Member(nodeId, 0, 0))));
        }
        ResearchTreePublication publication = new ResearchTreePublication(
                new ResearchTreeGraph(nodes, List.of()),
                new ResearchTreePresentation(groups));

        ResearchTreeGroupSkeletonCatalog catalog = assertTimeout(
                Duration.ofSeconds(8),
                () -> ResearchTreeGroupSkeletonBuilder.build(
                        publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));

        assertEquals(ResearchTreePresentation.MAX_GROUPS, catalog.groups().size());
        assertEquals(ResearchTreeGraph.MAX_NODES, catalog.groups().stream()
                .mapToInt(group -> group.nodes().size()).sum());
        assertTrue(catalog.crossGroupEdges().isEmpty());
        assertEquals(24, catalog.groups().get(ResearchTreePresentation.MAX_GROUPS - 1).width());
    }

    private static ResearchTreeGraph.Edge edge(String prerequisite, String dependent) {
        return new ResearchTreeGraph.Edge(id(prerequisite), id(dependent));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("phase_zero", path);
    }
}
