package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ResearchTreeBaselineTest {
    static Stream<ResearchTreeBaselineFixtures.Fixture> fixtures() {
        return ResearchTreeBaselineFixtures.structural().stream();
    }

    @ParameterizedTest(name = "fixture {index}")
    @MethodSource("fixtures")
    void replaysExactPublicInputAndCurrentGeometry(ResearchTreeBaselineFixtures.Fixture fixture) {
        var original = fixture.input();
        var restored = ResearchTreeBaselineInput.fromJson(original.toJson());
        assertEquals(original, restored);
        assertEquals(original.sha256(), restored.sha256());
        for (int i = 0; i < original.projections().size(); i++) {
            var before = original.projections().get(i).replay();
            var after = restored.projections().get(i).replay();
            assertEquals(before, after);
            var layout = ResearchTechTreeLayoutEngine.layout(before, original.policy(), original.viewportWidth());
            assertEquals(layout, ResearchTechTreeLayoutEngine.layout(after, restored.policy(), restored.viewportWidth()));
            assertEquals(before.graph().nodes().size(), layout.graphLayout().nodes().size());
            assertTrue(layout.graphLayout().width() <= ResearchTreeLayout.MAX_DIMENSION);
            var edges = ResearchTreeEdgeIndex.create(before.graph(), layout.graphLayout(),
                    ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW);
            assertEquals(before.graph().edges().size(), edges.visible(-1_000_000, -1_000_000, 1_000_000, 1_000_000).size());
            var owned = fixture.branches().stream().flatMap(b -> b.directMembers().stream()).toList();
            assertEquals(owned.size(), Set.copyOf(owned).size());
            assertEquals(before.placements().keySet(), Set.copyOf(owned));
        }
    }

    @Test
    void typedCaptureOmitsAnonymousPopulationAndRetainsCrossDomainRequirements() {
        var catalog = ResearchTechTreeProjectionBuilder.build(ResearchTechTreeClientFixture.publication());
        var input = ResearchTreeBaselineInput.capture("three_domains", 854, ResearchTechTreeLayoutPolicy.DEFAULT, catalog);
        var restored = ResearchTreeBaselineInput.fromJson(input.toJson());
        assertEquals(3, restored.projections().size());
        assertFalse(input.toJson().contains(ResearchTechTreeClientFixture.OPAQUE.toString()));
        assertTrue(restored.projections().stream().flatMap(p -> p.nodes().stream()).allMatch(n -> n.visibility().revealsIdentity()));
        assertTrue(restored.projections().stream().anyMatch(p -> !p.boundaryLinks().isEmpty()));
        for (int i = 0; i < catalog.projections().size(); i++) {
            assertEquals(catalog.projections().get(i), restored.projections().get(i).replay());
        }
    }

    @Test
    void alternativesAndRequiredGroupsAreNotFlattened() {
        var graph = fixture("and_of_or").input().projections().get(0).replay().graph();
        assertEquals(3, graph.edges().size());
        assertEquals(List.of(2, 1), graph.requirementGroups().stream().map(g -> g.visibleAlternativeIds().size()).toList());
        var external = fixture("external_unknown").input().projections().get(0);
        assertEquals(1, external.groups().get(0).hiddenAlternativeCount());
        assertEquals(1, external.groups().get(0).externalAlternativeCount());
        assertEquals(1, external.boundaryLinks().size());
    }

    @Test
    void nestedAndReturningRegionsExpressTheMissingStructuralCases() {
        var nested = fixture("nested_split_merge");
        assertEquals(2, nested.branches().stream().filter(b -> b.parent().isPresent()).count());
        var returning = fixture("region_return");
        assertEquals(2, returning.branches().size());
        assertEquals(2, returning.branches().get(0).directMembers().size());
        assertEquals(2, returning.input().projections().get(0).edges().size());
        var ranks = fixture("sparse_ranks").input().projections().get(0).replay().placements().values();
        assertEquals(List.of(0, 400_000, 1_000_000), ranks.stream().map(p -> p.rank()).toList());
    }

    @Test
    void stressFixturesSeparateNodeAndEdgeLimits() {
        var nodes = ResearchTreeBaselineFixtures.maximumNodes().input().projections().get(0);
        assertEquals(ResearchTreeGraph.MAX_NODES, nodes.nodes().size());
        assertTrue(nodes.edges().isEmpty());
        var edges = ResearchTreeBaselineFixtures.maximumEdges().input().projections().get(0);
        assertEquals(ResearchTreeGraph.MAX_EDGES, edges.edges().size());
        assertEquals(1088, edges.nodes().size());
        assertEquals(1024, edges.groups().size());
        assertEquals(64, edges.groups().get(0).visibleAlternativeIds().size());
    }

    @Test
    void rejectsUnknownFormatAndCorruptGraphInsteadOfTimingDifferentData() {
        var json = JsonParser.parseString(fixture("and_of_or").input().toJson()).getAsJsonObject();
        json.addProperty("format", 99);
        assertThrows(RuntimeException.class, () -> ResearchTreeBaselineInput.fromJson(json.toString()));
        json.addProperty("format", 1);
        json.getAsJsonArray("projections").get(0).getAsJsonObject().getAsJsonArray("edges").remove(0);
        assertThrows(RuntimeException.class, () -> ResearchTreeBaselineInput.fromJson(json.toString()));
    }

    @Test
    void reportBindsMeasurementsToInputAndUsesDocumentedPercentiles() {
        var input = fixture("and_of_or").input();
        var report = ResearchTreeBaselineTool.measure(input, 0, 2);
        assertEquals(input.sha256(), report.get("input_sha256").getAsString());
        var domain = report.getAsJsonArray("domains").get(0).getAsJsonObject();
        assertEquals(2, domain.getAsJsonObject("layout").get("samples").getAsInt());
        assertEquals(3, domain.getAsJsonArray("edge_routes").size());
        var stats = ResearchTreeBaselineTool.statistics(List.of(3_000_000L, 1_000_000L, 2_000_000L));
        assertEquals(2.0, stats.get("median_ms").getAsDouble());
        assertEquals(3.0, stats.get("p95_ms").getAsDouble());
        assertEquals(1.5, ResearchTreeBaselineTool.statistics(List.of(1_000_000L, 2_000_000L))
                .get("median_ms").getAsDouble());
    }

    @Test
    void actualClientReplayRetainsConnectedCatalogAndAutomaticHints() throws Exception {
        try (var stream = getClass().getResourceAsStream("/research-tree-baseline/live-weapons-287.json")) {
            assertNotNull(stream);
            var input = ResearchTreeBaselineInput.fromJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("378c48b56b5cd1843cd64addb0bf837f112d7ec32f7ebab4733f1e9082edf074", input.sha256());
            var projection = input.projections().get(0).replay();
            assertEquals(287, projection.graph().nodes().size());
            assertEquals(454, projection.graph().edges().size());
            assertEquals(286, projection.graph().requirementGroups().size());
            assertEquals(168, projection.graph().requirementGroups().stream()
                    .filter(g -> g.visibleAlternativeIds().size() > 1).count());
            assertEquals(19, projection.placements().values().stream().map(p -> p.rank()).distinct().count());
            assertEquals(287, projection.placements().values().stream().filter(p -> p.automaticBranch().isPresent()).count());
            assertEquals(7, projection.placements().values().stream().flatMap(p -> p.automaticBranch().stream())
                    .map(p -> p.branchIndex()).distinct().count());
            var layout = ResearchTechTreeLayoutEngine.layout(projection, input.policy(), input.viewportWidth());
            assertEquals(layout, ResearchTechTreeLayoutEngine.layout(
                    ResearchTreeBaselineInput.fromJson(input.toJson()).projections().get(0).replay(),
                    input.policy(), input.viewportWidth()));
            assertEquals(1, layout.layoutComponents().size());
        }
    }

    private static ResearchTreeBaselineFixtures.Fixture fixture(String name) {
        return fixtures().filter(f -> f.input().name().equals(name)).findFirst().orElseThrow();
    }
}
