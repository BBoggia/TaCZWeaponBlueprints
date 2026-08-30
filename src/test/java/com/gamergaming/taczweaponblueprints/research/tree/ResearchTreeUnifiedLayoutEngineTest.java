package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchTreeOverviewBuilder;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeUnifiedLayoutEngineTest {
    @Test
    void connectedProgressionBecomesOneDeterministicBottomToTopTree() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.connectedProgression();

        ResearchTreeLayout layout = ResearchTreeUnifiedLayoutEngine.layout(publication);

        assertEquals(layout, ResearchTreeUnifiedLayoutEngine.layout(publication));
        assertTrue(layout.categoryLanes().isEmpty());
        assertTrue(layout.groupRegions().isEmpty());
        assertEquals(1L, layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::component)
                .distinct()
                .count());
        for (ResearchTreeGraph.Edge edge : publication.graph().edges()) {
            assertTrue(
                    layout.position(edge.prerequisiteId()).orElseThrow().y()
                            > layout.position(edge.dependentId()).orElseThrow().y(),
                    () -> "prerequisite must appear below dependent: " + edge);
        }

        double rootCenter = layout.position(id("root")).orElseThrow().centerX();
        double childCenter = (layout.position(id("left")).orElseThrow().centerX()
                + layout.position(id("right")).orElseThrow().centerX()) / 2.0D;
        assertTrue(Math.abs(rootCenter - childCenter) <= 1.0D);
    }

    @Test
    void crossingReductionReordersSiblingsAroundTheirRealPrerequisites() {
        ResearchTreePublication publication = crossingPublication();

        ResearchTreeLayout layout = ResearchTreeUnifiedLayoutEngine.layout(publication);

        int lowerA = layout.position(id("a")).orElseThrow().x();
        int lowerB = layout.position(id("b")).orElseThrow().x();
        int upperC = layout.position(id("c")).orElseThrow().x();
        int upperD = layout.position(id("d")).orElseThrow().x();
        assertTrue(lowerA < lowerB);
        assertTrue(upperD < upperC);
    }

    @Test
    void curatedLargeFixtureCompactsToOneReadableRankWithoutCategoryColumns() {
        ResearchTreeOverviewBuilder.Result overview = ResearchTreeOverviewBuilder.build(
                ResearchTreeRedesignFixture.denseGeneratedCatalog());

        ResearchTreeLayout layout = org.junit.jupiter.api.Assertions.assertTimeout(
                Duration.ofSeconds(3),
                () -> ResearchTreeUnifiedLayoutEngine.layout(overview.publication()));

        assertEquals(ResearchTreeRedesignFixture.CURATED_DEFAULT_NODES, layout.nodes().size());
        assertTrue(layout.width() < 1_000);
        assertTrue(layout.height() < 1_000);
        assertTrue(layout.categoryLanes().isEmpty());
        assertTrue(layout.groupRegions().isEmpty());
        assertEquals(overview.publication().presentation().groups().size(),
                layout.nodes().stream()
                        .map(ResearchTreeLayout.PositionedNode::component)
                        .distinct()
                .count());
    }

    @Test
    void disconnectedNodesInOneAuthoredGroupRemainOneTwoRankIsland() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>();
        List<ResearchTreePresentation.Member> members = new ArrayList<>();
        int components = 8;
        for (int component = 0; component < components; component++) {
            nodes.add(node(nodes.size(), "root_" + component, 0));
            nodes.add(node(nodes.size(), "child_" + component, 1));
            edges.add(new ResearchTreeGraph.Edge(
                    id("root_" + component), id("child_" + component)));
            members.add(new ResearchTreePresentation.Member(
                    id("root_" + component), 0, component));
        }
        for (int component = 0; component < components; component++) {
            members.add(new ResearchTreePresentation.Member(
                    id("child_" + component), 1, component));
        }
        ResearchTreePublication publication = new ResearchTreePublication(
                new ResearchTreeGraph(nodes, edges),
                new ResearchTreePresentation(List.of(new ResearchTreePresentation.Group(
                        id("mixed_components"),
                        "Mixed components",
                        Optional.empty(),
                        Optional.of(id("root_0")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        members))));

        ResearchTreeLayout layout = ResearchTreeUnifiedLayoutEngine.layout(publication);

        assertEquals(2L, layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::y)
                .distinct()
                .count());
        assertEquals(1L, layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::component)
                .distinct()
                .count());
        for (ResearchTreeGraph.Edge edge : edges) {
            ResearchTreeLayout.PositionedNode prerequisite =
                    layout.position(edge.prerequisiteId()).orElseThrow();
            ResearchTreeLayout.PositionedNode dependent =
                    layout.position(edge.dependentId()).orElseThrow();
            assertTrue(prerequisite.y() > dependent.y());
            assertTrue(prerequisite.tier() < dependent.tier());
        }
    }

    @Test
    void maximumPublishedNodeCountRemainsDeterministicAndWithinCanvasLimits() {
        List<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        List<ResearchTreePresentation.Member> members = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            String path = "maximum_" + ordinal;
            nodes.add(node(ordinal, path, 0));
            members.add(new ResearchTreePresentation.Member(id(path), 0, ordinal));
        }
        ResearchTreePublication publication = new ResearchTreePublication(
                new ResearchTreeGraph(nodes, List.of()),
                new ResearchTreePresentation(List.of(new ResearchTreePresentation.Group(
                        id("maximum_group"),
                        "Maximum",
                        Optional.empty(),
                        Optional.of(id("maximum_0")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        members))));

        ResearchTreeLayout[] layouts = org.junit.jupiter.api.Assertions.assertTimeout(
                Duration.ofSeconds(8),
                () -> new ResearchTreeLayout[] {
                        ResearchTreeUnifiedLayoutEngine.layout(publication),
                        ResearchTreeUnifiedLayoutEngine.layout(publication)
                });

        assertEquals(layouts[0], layouts[1]);
        assertEquals(ResearchTreeGraph.MAX_NODES, layouts[0].nodes().size());
        assertTrue(layouts[0].width() < 10_000);
        assertTrue(layouts[0].height() < 10_000);
        assertTrue(layouts[0].nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::y)
                .distinct()
                .count() > 1);
    }

    @Test
    void emptyAndInvalidInputsHaveExplicitResults() {
        assertEquals(
                ResearchTreeLayout.EMPTY,
                ResearchTreeUnifiedLayoutEngine.layout(ResearchTreePublication.EMPTY));
        assertThrows(
                IllegalArgumentException.class,
                () -> ResearchTreeUnifiedLayoutEngine.layout(null));
    }

    private static ResearchTreePublication crossingPublication() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "a", 0),
                        node(1, "b", 0),
                        node(2, "c", 1),
                        node(3, "d", 1)),
                List.of(
                        new ResearchTreeGraph.Edge(id("a"), id("d")),
                        new ResearchTreeGraph.Edge(id("b"), id("c"))));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("group"),
                        "Group",
                        Optional.empty(),
                        Optional.of(id("a")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(
                                new ResearchTreePresentation.Member(id("a"), 0, 0),
                                new ResearchTreePresentation.Member(id("b"), 0, 1),
                                new ResearchTreePresentation.Member(id("c"), 1, 0),
                                new ResearchTreePresentation.Member(id("d"), 1, 1)))));
        return new ResearchTreePublication(graph, presentation);
    }

    private static ResearchTreeGraph.Node node(int ordinal, String path, int prerequisites) {
        return new ResearchTreeGraph.Node(
                ordinal,
                id(path),
                "fixture." + path,
                "rifle",
                new ResourceLocation("minecraft", "paper"),
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

    private static ResourceLocation id(String path) {
        return new ResourceLocation("phase_zero", path);
    }
}
