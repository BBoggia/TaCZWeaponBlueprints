package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchTreeOverviewBuilder;
import com.gamergaming.taczweaponblueprints.client.ResearchTreePresentationContract;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeViewport;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeRedesignBaselineTest {
    @Test
    void denseFixtureMatchesTheReportedRuntimeCatalogShape() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.denseGeneratedCatalog();

        assertEquals(
                ResearchTreeRedesignFixture.ACTIVE_CATALOG_NODES,
                publication.graph().nodes().size());
        assertEquals(
                ResearchTreeRedesignFixture.CURATED_DEFAULT_NODES,
                publication.presentation().groups().stream()
                        .filter(group -> group.kind() == ResearchTreePresentation.Kind.AUTHORED)
                        .flatMap(group -> group.members().stream())
                        .count());
        assertEquals(
                ResearchTreeRedesignFixture.GENERATED_NODES,
                publication.presentation().groups().stream()
                        .filter(group -> group.kind() == ResearchTreePresentation.Kind.UNDISCLOSED)
                        .flatMap(group -> group.members().stream())
                        .count());
    }

    @Test
    void legacyGlobalLayoutReproducesTheTallUndisclosedColumn() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.denseGeneratedCatalog();
        ResearchTreeLayout layout = assertTimeout(
                Duration.ofSeconds(3),
                () -> ResearchTreeLayoutEngine.layout(publication));
        Set<ResourceLocation> generatedIds = publication.presentation().groups().stream()
                .filter(group -> group.kind() == ResearchTreePresentation.Kind.UNDISCLOSED)
                .flatMap(group -> group.members().stream())
                .map(ResearchTreePresentation.Member::nodeId)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(ResearchTreeLayoutEngine.MAX_COLUMNS, layout.categoryLanes().size());
        assertEquals(672, layout.width());
        assertEquals(17_112, layout.height());
        assertEquals(
                1L,
                layout.nodes().stream()
                        .filter(node -> generatedIds.contains(node.blueprintId()))
                        .map(ResearchTreeLayout.PositionedNode::x)
                        .distinct()
                        .count());
        assertTrue(layout.height() > 10_000);
        assertTrue(layout.height() > layout.width() * 10);

        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(1_920, 1_080, layout.width(), layout.height());
        viewport.fit();
        assertTrue(viewport.scale() < ResearchTreePresentationContract.MIN_COMPACT_CARD_SCALE);
    }

    @Test
    void curatedOverviewRemovesGeneratedOverflowWithoutChangingThePublication() {
        ResearchTreePublication full = ResearchTreeRedesignFixture.denseGeneratedCatalog();

        ResearchTreeOverviewBuilder.Result overview = assertTimeout(
                Duration.ofSeconds(3),
                () -> ResearchTreeOverviewBuilder.build(full));

        assertEquals(ResearchTreeRedesignFixture.ACTIVE_CATALOG_NODES,
                full.graph().nodes().size());
        assertEquals(ResearchTreeRedesignFixture.CURATED_DEFAULT_NODES,
                overview.publication().graph().nodes().size());
        assertEquals(7, overview.publication().presentation().groups().size());
        assertTrue(overview.publication().presentation().groups().stream()
                .allMatch(ResearchTreePresentation.Group::includedInOverview));
        assertTrue(overview.publication().graph().nodes().stream()
                .allMatch(node -> node.visibility().revealsIdentity()));
    }

    @Test
    void connectedFixtureProvidesOneTruthfulBottomToTopProgression() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.connectedProgression();
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(publication);

        assertEquals(1, rootCount(publication.graph()));
        assertEquals(publication.graph().nodes().size(), reachableFromRoot(publication.graph()));
        for (ResearchTreeGraph.Edge edge : publication.graph().edges()) {
            assertTrue(
                    layout.position(edge.prerequisiteId()).orElseThrow().y()
                            > layout.position(edge.dependentId()).orElseThrow().y());
        }
    }

    private static int rootCount(ResearchTreeGraph graph) {
        Set<ResourceLocation> dependents = graph.edges().stream()
                .map(ResearchTreeGraph.Edge::dependentId)
                .collect(java.util.stream.Collectors.toSet());
        return (int) graph.nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .filter(id -> !dependents.contains(id))
                .count();
    }

    private static int reachableFromRoot(ResearchTreeGraph graph) {
        Set<ResourceLocation> dependents = graph.edges().stream()
                .map(ResearchTreeGraph.Edge::dependentId)
                .collect(java.util.stream.Collectors.toSet());
        ResourceLocation root = graph.nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .filter(id -> !dependents.contains(id))
                .findFirst()
                .orElseThrow();
        Set<ResourceLocation> visited = new HashSet<>();
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            ResourceLocation next = pending.removeFirst();
            if (!visited.add(next)) {
                continue;
            }
            graph.edges().stream()
                    .filter(edge -> edge.prerequisiteId().equals(next))
                    .map(ResearchTreeGraph.Edge::dependentId)
                    .forEach(pending::addLast);
        }
        return visited.size();
    }
}
