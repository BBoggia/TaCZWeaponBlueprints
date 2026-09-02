package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeGroupedLayoutEngineTest {
    @Test
    void atlasOrdersGroupsHorizontallyAndRanksBottomToTop() {
        ResearchTreePublication publication = publication();

        ResearchTreeLayout layout = ResearchTreeGroupedLayoutEngine.allWeapons(publication);

        assertEquals(layout, ResearchTreeGroupedLayoutEngine.allWeapons(publication));
        assertEquals(List.of(id("test:first"), id("test:second")), layout.groupRegions().stream()
                .map(ResearchTreeLayout.GroupRegion::groupId)
                .toList());
        assertTrue(layout.groupRegions().get(0).right() < layout.groupRegions().get(1).x());
        assertTrue(layout.position(id("test:b")).orElseThrow().y()
                < layout.position(id("test:a")).orElseThrow().y());
        assertTrue(layout.position(id("test:d")).orElseThrow().y()
                < layout.position(id("test:c")).orElseThrow().y());
        assertInside(layout, id("test:a"), id("test:first"));
        assertInside(layout, id("test:b"), id("test:first"));
        assertInside(layout, id("test:c"), id("test:second"));
        assertInside(layout, id("test:d"), id("test:second"));
    }

    @Test
    void branchUsesOnlyItsRegionAndDisconnectedSiblingsFormAGrid() {
        java.util.ArrayList<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        java.util.ArrayList<ResearchTreePresentation.Member> members = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < 25; ordinal++) {
            nodes.add(node(ordinal, "test:node_" + ordinal, 0));
            members.add(new ResearchTreePresentation.Member(
                    id("test:node_" + ordinal), 0, ordinal));
        }
        ResearchTreeGraph graph = new ResearchTreeGraph(nodes, List.of());
        ResearchTreePresentation.Group group = new ResearchTreePresentation.Group(
                id("test:grid"), "Grid", Optional.empty(), Optional.empty(), 0,
                ResearchTreePresentation.Kind.AUTHORED, members);

        ResearchTreeLayout layout = ResearchTreeGroupedLayoutEngine.branch(graph, group);

        assertEquals(1, layout.groupRegions().size());
        assertTrue(layout.nodes().stream().map(ResearchTreeLayout.PositionedNode::x).distinct().count() > 1);
        assertTrue(layout.nodes().stream().map(ResearchTreeLayout.PositionedNode::y).distinct().count() > 1);
    }

    @Test
    void maximumDisconnectedBranchRemainsBoundedAndUsesBothAxes() {
        java.util.ArrayList<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        java.util.ArrayList<ResearchTreePresentation.Member> members = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            String value = "test:maximum_" + ordinal;
            nodes.add(node(ordinal, value, 0));
            members.add(new ResearchTreePresentation.Member(id(value), 0, ordinal));
        }
        ResearchTreeGraph graph = new ResearchTreeGraph(nodes, List.of());
        ResearchTreePresentation.Group group = new ResearchTreePresentation.Group(
                id("test:maximum"), "Maximum", Optional.empty(), Optional.empty(), 0,
                ResearchTreePresentation.Kind.AUTHORED, members);

        ResearchTreeLayout layout = assertTimeout(
                Duration.ofSeconds(5),
                () -> ResearchTreeGroupedLayoutEngine.branch(graph, group));

        assertEquals(ResearchTreeGraph.MAX_NODES, layout.nodes().size());
        assertTrue(layout.width() < ResearchTreeLayout.MAX_DIMENSION);
        assertTrue(layout.height() < ResearchTreeLayout.MAX_DIMENSION);
        assertTrue(layout.nodes().stream().map(ResearchTreeLayout.PositionedNode::x).distinct().count() > 1);
        assertTrue(layout.nodes().stream().map(ResearchTreeLayout.PositionedNode::y).distinct().count() > 1);
    }

    @Test
    void maximumAtlasWithOneGroupPerNodeRemainsDeterministicAndBounded() {
        java.util.ArrayList<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        java.util.ArrayList<ResearchTreePresentation.Group> groups = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            String value = "test:atlas_" + ordinal;
            ResourceLocation nodeId = id(value);
            nodes.add(node(ordinal, value, 0));
            groups.add(new ResearchTreePresentation.Group(
                    id("test:atlas_group_" + ordinal),
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

        ResearchTreeLayout[] layouts = assertTimeout(Duration.ofSeconds(8), () -> new ResearchTreeLayout[] {
                ResearchTreeGroupedLayoutEngine.allWeapons(publication),
                ResearchTreeGroupedLayoutEngine.allWeapons(publication)
        });

        assertEquals(layouts[0], layouts[1]);
        assertEquals(ResearchTreeGraph.MAX_NODES, layouts[0].nodes().size());
        assertEquals(ResearchTreePresentation.MAX_GROUPS, layouts[0].groupRegions().size());
        assertTrue(layouts[0].width() < ResearchTreeLayout.MAX_DIMENSION);
        assertTrue(layouts[0].height() < ResearchTreeLayout.MAX_DIMENSION);
        assertTrue(layouts[0].groupRegions().get(0).right()
                < layouts[0].groupRegions().get(1).x());
        assertTrue(layouts[0].groupRegions().get(ResearchTreePresentation.MAX_GROUPS - 2).right()
                < layouts[0].groupRegions().get(ResearchTreePresentation.MAX_GROUPS - 1).x());
    }

    private static ResearchTreePublication publication() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0),
                        node(1, "test:b", 1),
                        node(2, "test:c", 0),
                        node(3, "test:d", 1)),
                List.of(
                        new ResearchTreeGraph.Edge(id("test:a"), id("test:b")),
                        new ResearchTreeGraph.Edge(id("test:c"), id("test:d"))));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                group("test:first", "First", "test:a", "test:b", 0),
                group("test:second", "Second", "test:c", "test:d", 1)));
        return new ResearchTreePublication(graph, presentation);
    }

    private static ResearchTreePresentation.Group group(
            String groupId,
            String title,
            String root,
            String unlock,
            int order) {
        return new ResearchTreePresentation.Group(
                id(groupId), title, Optional.empty(), Optional.of(id(root)), order,
                ResearchTreePresentation.Kind.AUTHORED,
                List.of(
                        new ResearchTreePresentation.Member(id(root), 0, 0),
                        new ResearchTreePresentation.Member(id(unlock), 1, 0)));
    }

    private static void assertInside(
            ResearchTreeLayout layout,
            ResourceLocation nodeId,
            ResourceLocation groupId) {
        ResearchTreeLayout.PositionedNode node = layout.position(nodeId).orElseThrow();
        ResearchTreeLayout.GroupRegion region = layout.groupRegions().stream()
                .filter(candidate -> candidate.groupId().equals(groupId))
                .findFirst()
                .orElseThrow();
        assertTrue(node.x() >= region.x());
        assertTrue(node.x() + ResearchTreeLayout.NODE_WIDTH <= region.right());
        assertTrue(node.y() >= region.y());
        assertTrue(node.y() + ResearchTreeLayout.NODE_HEIGHT <= region.bottom());
    }

    private static ResearchTreeGraph.Node node(int ordinal, String value, int prerequisites) {
        ResourceLocation blueprintId = id(value);
        return new ResearchTreeGraph.Node(
                ordinal,
                blueprintId,
                "name." + blueprintId.getPath(),
                "rifle",
                id("test:slot/" + ordinal),
                JournalVisibility.FULL,
                false,
                false,
                prerequisites == 0,
                8,
                0,
                prerequisites,
                0,
                prerequisites == 0
                        ? ResearchTreeGraph.Availability.AVAILABLE
                        : ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
