package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ResearchTreeSceneTest {
    private static final ResearchTreeScenePolicy POLICY = ResearchTreeScenePolicy.compatibility(
            ResearchTechTreeLayoutPolicy.DEFAULT, 854);

    static Stream<ResearchTreeBaselineFixtures.Fixture> fixtures() {
        return ResearchTreeBaselineFixtures.structural().stream();
    }

    @ParameterizedTest(name = "scene fixture {index}")
    @MethodSource("fixtures")
    void bothEnginesPreserveCanonicalPublicMembership(ResearchTreeBaselineFixtures.Fixture fixture) {
        if (fixture.input().projections().isEmpty()) {
            var input = ResearchTreeSceneInput.empty(Domain.WEAPONS, POLICY);
            var scene = checked(input, new ResearchTreeSimpleSceneEngine());
            assertTrue(scene.nodes().isEmpty());
            assertEquals(new ResearchTreeScene.Bounds(0, 0, 0, 0), scene.extent());
        }
        for (var data : fixture.input().projections()) {
            var projection = data.replay();
            var input = ResearchTreeSceneInput.from(projection, POLICY);
            for (var engine : List.of(new ResearchTreeCompatibilitySceneEngine(projection), new ResearchTreeSimpleSceneEngine())) {
                var first = checked(input, engine);
                assertEquals(first, checked(input, engine));
                assertEquals(input.topology().groups(), first.requirements().stream().map(r -> r.group()).toList());
                assertEquals(projection.graph().nodes().size(), first.nodes().size());
            }
        }
    }

    @Test
    void adapterOmitsAnonymousNodesAndPreservesExternalAndUnknownAlternatives() {
        var catalog = ResearchTechTreeProjectionBuilder.build(ResearchTechTreeClientFixture.publication());
        for (var projection : catalog.projections()) {
            var input = ResearchTreeSceneInput.from(projection, POLICY);
            assertFalse(input.topology().nodes().stream().anyMatch(p -> p.nodeId().equals(ResearchTechTreeClientFixture.OPAQUE)));
            assertTrue(input.topology().nodes().stream().allMatch(p -> p.rating().isEmpty()));
            for (var engine : List.of(new ResearchTreeCompatibilitySceneEngine(projection), new ResearchTreeSimpleSceneEngine())) {
                checked(input, engine);
            }
        }
        var input = ResearchTreeSceneInput.from(projection("external_unknown"), POLICY);
        var group = input.topology().groups().get(0);
        assertEquals(1, group.hiddenCount());
        assertEquals(1, group.externalCount());
        assertEquals(1, input.topology().boundaries().size());
    }

    @Test
    void canonicalInputAndBothEnginesIgnoreGraphTableOrder() {
        var original = projection("nested_split_merge");
        var ids = new ArrayList<>(original.placements().keySet());
        Collections.reverse(ids);
        var graph = original.graph().orderedInducedSubgraph(ids);
        var placements = new LinkedHashMap<net.minecraft.resources.ResourceLocation, ResearchTechTreeProjection.Placement>();
        ids.forEach(id -> placements.put(id, original.placements().get(id)));
        var reordered = new ResearchTechTreeProjection(original.domain(), original.presentation(), graph, placements,
                original.boundaryLinks(), original.bands(), original.maxNodesPerLayer());
        var a = ResearchTreeSceneInput.from(original, POLICY);
        var b = ResearchTreeSceneInput.from(reordered, POLICY);
        assertEquals(a.key("test"), b.key("test"));
        assertEquals(checked(a, new ResearchTreeCompatibilitySceneEngine(original)),
                checked(b, new ResearchTreeCompatibilitySceneEngine(reordered)));
        assertEquals(checked(a, new ResearchTreeSimpleSceneEngine()), checked(b, new ResearchTreeSimpleSceneEngine()));
    }

    @Test
    void stateOnlyUpdateReusesGeometryAndRefreshesPresentation() {
        var before = ResearchTechTreeClientFixture.publication();
        var after = ResearchTechTreeClientFixture.publication(Set.of(Domain.values()), ResearchTreeGraph.Availability.AVAILABLE);
        var cache = new ResearchTreeProjectionCache();
        cache.update(before);
        var a = cache.techTreeScene(before, Domain.WEAPONS, 854, POLICY.nodeMetrics(), "compact-v1");
        cache.update(after);
        var b = cache.techTreeScene(after, Domain.WEAPONS, 854, POLICY.nodeMetrics(), "compact-v1");
        assertEquals(ResearchTreeSceneCache.Status.VALID, a.status());
        assertTrue(b.reused());
        assertSame(a.geometry().orElseThrow(), b.geometry().orElseThrow());
        assertNotEquals(a.presentation().orElseThrow().graph(), b.presentation().orElseThrow().graph());
        assertSame(after, b.publication());
        assertFalse(cache.isCurrentScene(a));
        assertTrue(cache.isCurrentScene(b));
        assertThrows(IllegalArgumentException.class, () -> cache.techTreeScene(before, Domain.WEAPONS,
                854, POLICY.nodeMetrics(), "compact-v1"));
        cache.clear();
        assertFalse(cache.isCurrentScene(b));
    }

    @Test
    void metricsChangeInvalidatesGeometryAndLegacyAdapterCannotPretendLargerNodesFit() {
        var publication = ResearchTechTreeClientFixture.publication();
        var cache = new ResearchTreeSceneCache();
        cache.bind(publication);
        var before = cache.scene(publication, Domain.WEAPONS, POLICY);
        var larger = new ResearchTreeScenePolicy(POLICY.spacing(), 854,
                new ResearchTreeScenePolicy.NodeMetrics(120, 48, 8), "card-v1");
        var after = cache.scene(publication, Domain.WEAPONS, larger);
        assertEquals(ResearchTreeSceneCache.Status.FALLBACK, after.status());
        assertFalse(after.reused());
        assertNotSame(before.geometry().orElseThrow(), after.geometry().orElseThrow());
        assertFalse(cache.isCurrent(before));
        assertTrue(cache.isCurrent(after));
        assertTrue(after.geometry().orElseThrow().nodes().stream().allMatch(n -> n.body().width() == 120));
        assertTrue(cache.scene(publication, Domain.WEAPONS, larger).reused());
    }

    @Test
    void failedCurrentLayoutNeverReturnsPreviouslyPermittedNodes() {
        var fail = new boolean[] {false};
        ResearchTreeSceneEngine engine = new ResearchTreeSceneEngine() {
            public String revision() { return "failure-test-v1"; }
            public ResearchTreeScene layout(ResearchTreeSceneInput input) {
                if (fail[0]) throw new IllegalArgumentException("injected failure");
                var simple = new ResearchTreeSimpleSceneEngine().layout(input);
                return copy(simple, input.key(revision()), simple.nodes(), simple.requirements(), simple.routes());
            }
        };
        var cache = new ResearchTreeSceneCache(p -> engine, engine);
        var old = ResearchTechTreeClientFixture.publication();
        cache.bind(old);
        assertEquals(ResearchTreeSceneCache.Status.VALID, cache.scene(old, Domain.WEAPONS, POLICY).status());
        var current = ResearchTechTreeClientFixture.publicationWithoutUpgradePlacement();
        cache.bind(current);
        fail[0] = true;
        var result = cache.scene(current, Domain.WEAPONS, POLICY);
        assertEquals(ResearchTreeSceneCache.Status.UNAVAILABLE, result.status());
        assertTrue(result.geometry().isEmpty());
        assertFalse(result.presentation().orElseThrow().placements().containsKey(ResearchTechTreeClientFixture.WEAPON_UPGRADE));
        assertEquals(2, result.diagnostics().size());
        fail[0] = false;
        assertEquals(ResearchTreeSceneCache.Status.VALID, cache.scene(current, Domain.WEAPONS, POLICY).status());
    }

    @Test
    void absentDomainReturnsValidEmptyGeometryAndNoPresentation() {
        var cache = new ResearchTreeSceneCache();
        cache.bind(ResearchTreePublication.EMPTY);
        var result = cache.scene(ResearchTreePublication.EMPTY, Domain.WEAPONS, POLICY);
        assertEquals(ResearchTreeSceneCache.Status.VALID, result.status());
        assertTrue(result.presentation().isEmpty());
        assertTrue(result.geometry().orElseThrow().nodes().isEmpty());
        var otherScope = new ResearchTreeSceneCache();
        otherScope.bind(ResearchTreePublication.EMPTY);
        assertFalse(otherScope.isCurrent(result));
    }

    @Test
    void learnedCostsAndRequirementSatisfactionRefreshWithoutMovingNodes() {
        var before = ResearchTechTreeClientFixture.publication();
        var nodes = before.graph().nodes().stream().map(n -> {
            if (!n.blueprintId().equals(ResearchTechTreeClientFixture.WEAPON_UPGRADE)) return n;
            return new ResearchTreeGraph.Node(n.ordinal(), n.sourceOrdinal(), n.blueprintId(),
                    n.nameKey(), n.itemType(), n.displaySlotId(), n.visibility(), true, true, false,
                    n.pointCost() + 5, n.ingredientTypeCount(), n.prerequisiteCount(),
                    n.hiddenPrerequisiteCount(), ResearchTreeGraph.Availability.LEARNED);
        }).toList();
        var groups = before.graph().requirementGroups().stream().map(g -> new ResearchTreeGraph.RequirementGroup(
                g.dependentId(), g.ordinal(), g.visibleAlternativeIds(), g.hiddenAlternativeCount(),
                g.externalAlternativeCount(), g.satisfactionDisclosed(), g.satisfactionDisclosed())).toList();
        var after = new ResearchTreePublication(new ResearchTreeGraph(nodes, before.graph().edges(), groups),
                before.presentation(), before.techTree());
        var cache = new ResearchTreeSceneCache();
        cache.bind(before);
        var a = cache.scene(before, Domain.WEAPONS, POLICY);
        cache.bind(after);
        var b = cache.scene(after, Domain.WEAPONS, POLICY);
        assertTrue(b.reused());
        assertSame(a.geometry().orElseThrow(), b.geometry().orElseThrow());
        assertTrue(b.presentation().orElseThrow().graph().node(ResearchTechTreeClientFixture.WEAPON_UPGRADE)
                .orElseThrow().learned());
        // Recomputing either engine also ignores the changed presentation state.
        for (var revision : List.of("compatibility", "simple")) {
            ResearchTreeSceneEngine engineA = revision.equals("simple") ? new ResearchTreeSimpleSceneEngine()
                    : new ResearchTreeCompatibilitySceneEngine(a.presentation().orElseThrow());
            ResearchTreeSceneEngine engineB = revision.equals("simple") ? new ResearchTreeSimpleSceneEngine()
                    : new ResearchTreeCompatibilitySceneEngine(b.presentation().orElseThrow());
            assertEquals(checked(ResearchTreeSceneInput.from(a.presentation().orElseThrow(), POLICY), engineA),
                    checked(ResearchTreeSceneInput.from(b.presentation().orElseThrow(), POLICY), engineB));
        }
    }

    @Test
    void boundsRemainImmutableAndHitPaddingDoesNotAlterGeometry() {
        var scene = checked(ResearchTreeSceneInput.from(projection("single"), POLICY), new ResearchTreeSimpleSceneEngine());
        var node = scene.nodes().get(0);
        assertEquals(node.body().inflate(10), node.hitBounds(10));
        assertNotEquals(node.paint(), node.hitBounds(10));
        assertThrows(UnsupportedOperationException.class, () -> scene.nodes().clear());
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeScene.Route(
                new ResearchTreeGraph.Edge(ResearchTechTreeClientFixture.WEAPON_ROOT, ResearchTechTreeClientFixture.WEAPON_UPGRADE),
                Collections.nCopies(ResearchTreeScenePolicy.MAX_ROUTE_POINTS + 1, new ResearchTreeScene.Point(0, 0))));
        var tooManyNodes = Collections.nCopies(ResearchTreeGraph.MAX_NODES + 1, node);
        assertThrows(IllegalArgumentException.class, () -> copy(scene, scene.key(), tooManyNodes, List.of(), List.of()));
    }

    @Test
    void changingMetricsRevisionViewportOrSpacingInvalidatesTheKey() {
        var input = ResearchTreeSceneInput.from(projection("single"), POLICY);
        var newRevision = new ResearchTreeScenePolicy(POLICY.spacing(), 854, POLICY.nodeMetrics(), "resources-v2");
        var newWidth = new ResearchTreeScenePolicy(POLICY.spacing(), 400, POLICY.nodeMetrics(), POLICY.metricsRevision());
        var s = POLICY.spacing();
        var newSpacing = new ResearchTreeScenePolicy(new ResearchTechTreeLayoutPolicy(s.canvasPadding(),
                s.nodeGap() + 2, s.sameTierStepGap(), s.tierGap(), s.portalPadding(), s.maxRankBlockWidth(),
                s.orderingSweeps(), s.compactionSweeps()), 854, POLICY.nodeMetrics(), POLICY.metricsRevision());
        for (var policy : List.of(newRevision, newWidth, newSpacing)) {
            assertNotEquals(input.key("engine-v1"), ResearchTreeSceneInput.from(projection("single"), policy).key("engine-v1"));
        }
        assertNotEquals(input.key("engine-v1"), input.key("engine-v2"));
    }

    @Test
    void missingBoundaryAndBandGeometryIsRejected() {
        var catalog = ResearchTechTreeProjectionBuilder.build(ResearchTechTreeClientFixture.publication());
        var projection = catalog.projection(Domain.WEAPONS).orElseThrow();
        var input = ResearchTreeSceneInput.from(projection, POLICY);
        var engine = new ResearchTreeSimpleSceneEngine();
        var scene = checked(input, engine);
        assertFalse(scene.boundaries().isEmpty());
        assertFalse(scene.bands().isEmpty());
        var noBoundaries = new ResearchTreeScene(scene.key(), scene.extent(), scene.nodes(), scene.requirements(),
                scene.routes(), List.of(), scene.bands());
        var noBands = new ResearchTreeScene(scene.key(), scene.extent(), scene.nodes(), scene.requirements(),
                scene.routes(), scene.boundaries(), List.of());
        for (var bad : List.of(noBoundaries, noBands)) {
            assertThrows(IllegalArgumentException.class, () -> ResearchTreeSceneValidator.validate(input, engine.revision(), bad));
        }
    }

    @Test
    void rejectsMismatchedKeyMissingNodesEdgesGroupsAndInventedRelationships() {
        var input = ResearchTreeSceneInput.from(projection("and_of_or"), POLICY);
        var engine = new ResearchTreeSimpleSceneEngine();
        var scene = checked(input, engine);
        var noNodes = copy(scene, scene.key(), List.of(), scene.requirements(), scene.routes());
        var noEdges = copy(scene, scene.key(), scene.nodes(), scene.requirements(), List.of());
        var noGroups = copy(scene, scene.key(), scene.nodes(), List.of(), scene.routes());
        var duplicateNodes = new ArrayList<>(scene.nodes()); duplicateNodes.add(scene.nodes().get(0));
        var duplicates = copy(scene, scene.key(), duplicateNodes, scene.requirements(), scene.routes());
        var wrongKey = copy(scene, input.key("different-engine"), scene.nodes(), scene.requirements(), scene.routes());
        var alteredGroups = new ArrayList<>(scene.requirements());
        var group = alteredGroups.get(0).group();
        alteredGroups.set(0, new ResearchTreeScene.Requirement(new ResearchTreeSceneInput.Group(group.id(),
                group.alternatives(), group.hiddenCount() + 1, group.externalCount()), Optional.empty()));
        var changedSemantics = copy(scene, scene.key(), scene.nodes(), alteredGroups, scene.routes());
        for (var invalid : List.of(noNodes, noEdges, noGroups, duplicates, wrongKey, changedSemantics)) {
            assertThrows(IllegalArgumentException.class, () -> ResearchTreeSceneValidator.validate(input, engine.revision(), invalid));
        }
    }

    @Test
    void rejectsNonFiniteCoordinatesOverlapBadMetricsAndUnattachedRoutes() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeScene.Bounds(Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeScene.Point(0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeScene.Bounds(999_999, 0, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeScenePolicy.NodeMetrics(0, 24, 4));
        var input = ResearchTreeSceneInput.from(projection("and_of_or"), POLICY);
        var engine = new ResearchTreeSimpleSceneEngine();
        var scene = checked(input, engine);
        var nodes = new ArrayList<>(scene.nodes());
        nodes.set(1, new ResearchTreeScene.Node(nodes.get(1).id(), nodes.get(0).body(), nodes.get(0).paint()));
        var overlapping = copy(scene, scene.key(), nodes, scene.requirements(), scene.routes());
        assertThrows(IllegalArgumentException.class, () -> ResearchTreeSceneValidator.validate(input, engine.revision(), overlapping));
        var routes = new ArrayList<>(scene.routes());
        routes.set(0, new ResearchTreeScene.Route(routes.get(0).edge(), List.of(
                new ResearchTreeScene.Point(0, 0), new ResearchTreeScene.Point(0, 1))));
        var detached = copy(scene, scene.key(), scene.nodes(), scene.requirements(), routes);
        assertThrows(IllegalArgumentException.class, () -> ResearchTreeSceneValidator.validate(input, engine.revision(), detached));
    }

    @Test
    void acceptsRealCatalogAndKeepsIndependentFallbackBoundedAtGraphLimits() throws Exception {
        try (var stream = getClass().getResourceAsStream("/research-tree-baseline/live-weapons-287.json")) {
            var replay = ResearchTreeBaselineInput.fromJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            var projection = replay.projections().get(0).replay();
            var input = ResearchTreeSceneInput.from(projection, ResearchTreeScenePolicy.compatibility(replay.policy(), replay.viewportWidth()));
            assertEquals(287, checked(input, new ResearchTreeCompatibilitySceneEngine(projection)).nodes().size());
            assertEquals(454, checked(input, new ResearchTreeSimpleSceneEngine()).routes().size());
        }
        for (var fixture : List.of(ResearchTreeBaselineFixtures.maximumNodes(), ResearchTreeBaselineFixtures.maximumEdges())) {
            var projection = fixture.input().projections().get(0).replay();
            var input = ResearchTreeSceneInput.from(projection, POLICY);
            var scene = checked(input, new ResearchTreeSimpleSceneEngine());
            assertEquals(projection.graph().nodes().size(), scene.nodes().size());
            assertEquals(projection.graph().edges().size(), scene.routes().size());
        }
    }

    private static ResearchTechTreeProjection projection(String name) {
        return fixtures().filter(f -> f.input().name().equals(name)).findFirst().orElseThrow().input().projections().get(0).replay();
    }
    private static ResearchTreeScene checked(ResearchTreeSceneInput input, ResearchTreeSceneEngine engine) {
        return ResearchTreeSceneValidator.validate(input, engine.revision(), engine.layout(input));
    }
    private static ResearchTreeScene copy(ResearchTreeScene source, ResearchTreeSceneInput.Key key,
            List<ResearchTreeScene.Node> nodes, List<ResearchTreeScene.Requirement> groups, List<ResearchTreeScene.Route> routes) {
        return new ResearchTreeScene(key, source.extent(), nodes, groups, routes, source.boundaries(), source.bands());
    }
}
