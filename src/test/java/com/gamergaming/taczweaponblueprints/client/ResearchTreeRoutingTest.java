package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ResearchTreeRoutingTest {
    private static final ResearchTreeBranchSceneEngine ENGINE = new ResearchTreeBranchSceneEngine();

    static Stream<ResearchTreeBaselineFixtures.Fixture> fixtures() { return ResearchTreeBaselineFixtures.routing().stream(); }

    @ParameterizedTest
    @MethodSource("fixtures")
    void groupedFixturesPreserveAllConnectionsAndClearance(ResearchTreeBaselineFixtures.Fixture fixture) {
        var input = ResearchTreeBranchFixtures.input(fixture, 854);
        var scene = checked(input);
        assertEquals(scene, checked(input));
        var r = scene.routing().orElseThrow();
        int alternatives = input.topology().groups().stream().mapToInt(g -> g.alternatives().size()).sum();
        assertEquals(alternatives + input.topology().groups().size() + scene.boundaries().size(), r.connections().size());
        assertTrue(r.statistics().work() <= ResearchTreeRouting.MAX_WORK);
    }

    @Test
    void andOfOrAndSingleOrHaveDifferentJunctionsAndTruthTables() {
        var and = checked(input("and_of_or")).routing().orElseThrow();
        var or = checked(input("single_or")).routing().orElseThrow();
        assertEquals(2, and.junctions().size()); assertEquals(1, or.junctions().size());
        for (int bits = 0; bits < 8; bits++) {
            Set<String> learned = new HashSet<>();
            for (int i = 0; i < 3; i++) if ((bits & 1 << i) != 0) learned.add("/" + (char) ('a' + i));
            assertEquals(((bits & 3) != 0) && (bits & 4) != 0, truth(and, learned));
            assertEquals(bits != 0, truth(or, learned));
        }
        assertEquals(2, and.connections().stream().filter(c -> c.owner().kind() == ResearchTreeRouting.Kind.GROUP_TRUNK).count());
    }

    @Test
    void repeatedAlternativeHasSeparateGroupOwnershipAndOnlyOneCanonicalEdge() {
        var scene = checked(input("repeated_alternatives"));
        var r = scene.routing().orElseThrow();
        assertEquals(3, scene.routes().size());
        assertEquals(4, r.connections().stream().filter(c -> c.owner().kind() == ResearchTreeRouting.Kind.ALTERNATIVE).count());
        var a = r.connections().stream().filter(c -> c.owner().prerequisite().map(id -> id.getPath().endsWith("/a")).orElse(false)).toList();
        assertEquals(2, a.size()); assertNotEquals(a.get(0).owner().group(), a.get(1).owner().group());
    }

    @Test
    void sixtyFourGroupsRemainSixtyFourRequirements() {
        var scene = checked(input("many_groups"));
        assertEquals(1, scene.routes().size());
        assertEquals(64, scene.routing().orElseThrow().junctions().size());
        assertEquals(128, scene.routing().orElseThrow().connections().size());
    }

    @Test
    void anonymousAlternativesRemainCountsWithoutInventedPorts() {
        var scene = checked(input("anonymous_groups"));
        var routing = scene.routing().orElseThrow();
        assertTrue(routing.junctions().stream().allMatch(j -> j.inputPorts().isEmpty()));
        assertEquals(2, routing.junctions().get(0).group().hiddenCount());
        assertEquals(3, routing.junctions().get(1).group().externalCount());
        assertTrue(routing.connections().stream().allMatch(c -> c.owner().kind() == ResearchTreeRouting.Kind.GROUP_TRUNK));
        assertEquals(1, scene.nodes().size());
    }

    @Test
    void boundaryNavigationDoesNotInventExternalGroupAssociation() {
        var input = input("multiple_boundaries"); var scene = checked(input); var r = scene.routing().orElseThrow();
        assertEquals(3, scene.boundaries().size());
        assertEquals(4, scene.boundaries().stream().mapToInt(b -> b.links().size()).sum());
        assertEquals(new HashSet<>(input.topology().boundaries()), new HashSet<>(scene.boundaries().stream().flatMap(b -> b.links().stream()).toList()));
        var boundary = r.connections().stream().filter(c -> c.owner().kind() == ResearchTreeRouting.Kind.BOUNDARY).toList();
        assertEquals(3, boundary.size());
        assertTrue(boundary.stream().allMatch(c -> c.owner().group().isEmpty() && c.owner().prerequisite().isEmpty()));
    }

    @Test
    void segmentIndexFindsSelectedLongRouteWithBothBlueprintsOffscreen() {
        var scene = checked(input("long_crossings")); var r = scene.routing().orElseThrow();
        var index = new ResearchTreeRoutingIndex(r);
        var segment = index.segments().stream().filter(s -> !s.horizontal() && s.bounds().height() > 300)
                .max(java.util.Comparator.comparingDouble(s -> s.bounds().height())).orElseThrow();
        var viewport = new ResearchTreeScene.Bounds(segment.from().x() - 2,
                segment.bounds().y() + segment.bounds().height() / 2 - 2, 4, 4);
        var owner = r.connections().get(segment.connection()).owner();
        var endpoints = scene.nodes().stream().filter(n -> owner.prerequisite().filter(n.id()::equals).isPresent()
                || owner.group().map(g -> g.dependentId().equals(n.id())).orElse(false)).toList();
        assertEquals(2, endpoints.size());
        assertTrue(endpoints.stream().noneMatch(n -> ResearchTreeRoutingIndex.intersects(n.paint(), viewport)));
        assertTrue(index.query(viewport).contains(segment));
        assertTrue(index.selected(viewport, Set.of(segment.connection())).contains(segment));
        assertEquals(List.of(), index.selected(viewport, Set.of()));
    }

    @Test
    void indexQueriesMatchBruteForceSegmentBounds() {
        var index = new ResearchTreeRoutingIndex(checked(input("long_crossings")).routing().orElseThrow());
        for (int x = 0; x < 800; x += 93) for (int y = 0; y < 1400; y += 111) {
            var view = new ResearchTreeScene.Bounds(x, y, 70, 80);
            assertEquals(index.segments().stream().filter(s -> ResearchTreeRoutingIndex.intersects(s.bounds(), view)).toList(), index.query(view));
        }
    }

    @Test
    void crossingsAreExplicitAndCannotBeDroppedOrInvented() {
        var input = input("long_crossings"); var scene = checked(input); var routing = scene.routing().orElseThrow();
        assertFalse(routing.crossings().isEmpty());
        rejects(input, scene, new ResearchTreeRouting(routing.junctions(), routing.connections(), List.of(), routing.statistics()));
        var extra = new ArrayList<>(routing.crossings()); extra.add(extra.get(0));
        rejects(input, scene, new ResearchTreeRouting(routing.junctions(), routing.connections(), extra, routing.statistics()));
    }

    @Test
    void validatorRejectsDroppedAndDuplicatedConnections() {
        var input = input("repeated_alternatives"); var scene = checked(input); var r = scene.routing().orElseThrow();
        var connections = new ArrayList<>(r.connections()); connections.remove(connections.size() - 1);
        rejects(input, scene, new ResearchTreeRouting(r.junctions(), connections, r.crossings(), r.statistics()));
        connections = new ArrayList<>(r.connections()); connections.add(connections.get(0));
        rejects(input, scene, new ResearchTreeRouting(r.junctions(), connections, r.crossings(), r.statistics()));
    }

    @Test
    void validatorRejectsChangedOrPortAndOverlappingJunctions() {
        var input = input("and_of_or"); var scene = checked(input); var r = scene.routing().orElseThrow();
        var junctions = new ArrayList<>(r.junctions()); var j = junctions.get(0);
        var ports = new ArrayList<>(j.inputPorts()); java.util.Collections.reverse(ports);
        junctions.set(0, new ResearchTreeRouting.Junction(j.group(), j.bounds(), ports, j.outputPort()));
        rejects(input, scene, new ResearchTreeRouting(junctions, r.connections(), r.crossings(), r.statistics()));
        var other = junctions.get(1);
        junctions.set(1, new ResearchTreeRouting.Junction(other.group(), j.bounds(), List.of(j.inputPorts().get(0)), j.outputPort()));
        rejects(input, scene, new ResearchTreeRouting(junctions, r.connections(), r.crossings(), r.statistics()));
    }

    @Test
    void finalValidationRejectsRouteThroughAnUnrelatedNode() {
        var input = input("and_of_or"); var scene = checked(input); var r = scene.routing().orElseThrow();
        var connections = new ArrayList<>(r.connections());
        int index = 0; while (connections.get(index).owner().kind() != ResearchTreeRouting.Kind.ALTERNATIVE) index++;
        var connection = connections.get(index); var points = new ArrayList<>(connection.points());
        var obstacle = scene.nodes().stream().filter(n -> n.id().getPath().endsWith("/b")).findFirst().orElseThrow();
        var start = points.get(0); var end = points.get(points.size() - 1);
        // Keep the blueprint and OR endpoints correct, but detour across B's painted body.
        points = new ArrayList<>(List.of(start, new ResearchTreeScene.Point(start.x(), start.y() - 30),
                new ResearchTreeScene.Point(obstacle.body().centerX(), start.y() - 30),
                new ResearchTreeScene.Point(obstacle.body().centerX(), obstacle.body().bottom() + 10),
                new ResearchTreeScene.Point(end.x(), obstacle.body().bottom() + 10), end));
        connections.set(index, new ResearchTreeRouting.Connection(connection.owner(), points));
        var corrupt = new ResearchTreeRouting(r.junctions(), connections, r.crossings(), r.statistics());
        assertFalse(new ResearchTreeRoutingValidator.Obstacles(scene.nodes(), r.junctions(), scene.boundaries(), List.of())
                .clear(connections.get(index), new ResearchTreeSceneRouter.Budget()));
        rejects(input, scene, corrupt);
    }

    @Test
    void largerSpacingRetryPreservesOwnershipRanksAndOrdering() {
        var input = input("nested_split_merge"); var original = checked(input);
        var expanded = ResearchTreeSceneValidator.validate(input, ENGINE.revision(),
                ResearchTreeBranchSceneEngine.candidate(input, 1, new ResearchTreeSceneRouter.Budget()));
        assertEquals(original.key(), expanded.key());
        assertEquals(original.occupiedRanks(), expanded.occupiedRanks());
        assertEquals(original.regions().stream().map(ResearchTreeScene.Region::seed).toList(),
                expanded.regions().stream().map(ResearchTreeScene.Region::seed).toList());
        assertEquals(original.nodes().stream().map(ResearchTreeScene.Node::id).toList(), expanded.nodes().stream().map(ResearchTreeScene.Node::id).toList());
        assertTrue(expanded.extent().width() > original.extent().width());
        assertEquals(1, expanded.routing().orElseThrow().statistics().spacingRetries());
    }

    @Test
    void retryControllerStopsAfterTwoRetriesAndDoesNotRetryHardBudgets() {
        var attempts = new AtomicInteger();
        var failure = assertThrows(IllegalArgumentException.class, () -> ResearchTreeBranchSceneEngine.withSpacingRetries(i -> {
            assertEquals(attempts.getAndIncrement(), i); throw new ResearchTreeSceneRouter.CapacityException("full");
        }));
        assertEquals(3, attempts.get()); assertTrue(failure.getMessage().contains("two spacing retries"));
        attempts.set(0);
        assertThrows(IllegalArgumentException.class, () -> ResearchTreeBranchSceneEngine.withSpacingRetries(i -> {
            attempts.incrementAndGet(); throw new IllegalArgumentException("hard budget");
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void routedRequestCannotFallBackToAnUnroutedSceneOrRetainStaleGeometry() {
        var publication = ResearchTechTreeClientFixture.publication();
        var primary = new ResearchTreeSceneEngine() {
            @Override public String revision() { return ENGINE.revision(); }
            @Override public boolean requiresGroupedRouting() { return true; }
            @Override public ResearchTreeScene layout(ResearchTreeSceneInput input) {
                if (input.policy().viewportWidth() == 855) throw new IllegalArgumentException("routing exhausted");
                return ENGINE.layout(input);
            }
        };
        var cache = new ResearchTreeSceneCache(p -> primary, new ResearchTreeSimpleSceneEngine()); cache.bind(publication);
        var first = cache.scene(publication, Domain.WEAPONS, ResearchTreeScenePolicy.compatibility(ResearchTechTreeLayoutPolicy.DEFAULT, 854));
        assertEquals(ResearchTreeSceneCache.Status.VALID, first.status());
        var failed = cache.scene(publication, Domain.WEAPONS, ResearchTreeScenePolicy.compatibility(ResearchTechTreeLayoutPolicy.DEFAULT, 855));
        assertEquals(ResearchTreeSceneCache.Status.UNAVAILABLE, failed.status()); assertTrue(failed.geometry().isEmpty());
        assertFalse(cache.isCurrent(first)); assertEquals(2, failed.diagnostics().size());
        assertTrue(failed.diagnostics().get(1).contains("grouped routing"));
    }

    @Test
    void validGroupedFallbackIsValidatedAgainstCurrentInput() {
        var primary = new ResearchTreeSceneEngine() {
            @Override public String revision() { return "unavailable-group-router"; }
            @Override public boolean requiresGroupedRouting() { return true; }
            @Override public ResearchTreeScene layout(ResearchTreeSceneInput input) { throw new IllegalArgumentException("full"); }
        };
        var publication = ResearchTechTreeClientFixture.publication();
        var cache = new ResearchTreeSceneCache(p -> primary, ENGINE); cache.bind(publication);
        var result = cache.scene(publication, Domain.WEAPONS, ResearchTreeScenePolicy.compatibility(ResearchTechTreeLayoutPolicy.DEFAULT, 854));
        assertEquals(ResearchTreeSceneCache.Status.FALLBACK, result.status()); assertTrue(result.geometry().orElseThrow().routing().isPresent());
    }

    @Test
    void doubleBackAndNonFiniteRoutesAreRejected() {
        var owner = ResearchTreeRouting.Owner.boundary(0);
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeRouting.Connection(owner, List.of(
                new ResearchTreeScene.Point(0, 0), new ResearchTreeScene.Point(0, 10), new ResearchTreeScene.Point(0, 5))));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeScene.Point(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeScene.Point(0, Double.POSITIVE_INFINITY));
    }

    @Test
    void unrelatedParallelTracksCannotBecomeSharedTrunksButCrossingsRemainAllowed() {
        var a = new ResearchTreeRouting.Connection(ResearchTreeRouting.Owner.boundary(0), List.of(
                new ResearchTreeScene.Point(0, 0), new ResearchTreeScene.Point(0, 100)));
        var b = new ResearchTreeRouting.Connection(ResearchTreeRouting.Owner.boundary(1), List.of(
                new ResearchTreeScene.Point(0, 50), new ResearchTreeScene.Point(0, 150)));
        var crossing = new ResearchTreeRouting.Connection(ResearchTreeRouting.Owner.boundary(2), List.of(
                new ResearchTreeScene.Point(-10, 50), new ResearchTreeScene.Point(10, 50)));
        var reservations = new ResearchTreeRoutingValidator.Reservations(); reservations.add(a);
        var budget = new ResearchTreeSceneRouter.Budget();
        assertFalse(reservations.clear(b, budget)); assertTrue(reservations.clear(crossing, budget));
        var marks = ResearchTreeRoutingValidator.crossings(List.of(a, crossing), budget);
        assertEquals(1, marks.size()); assertEquals(new ResearchTreeScene.Point(0, 50), marks.get(0).point());
    }

    @Test
    void fractionalMeasuredCardsCanBeRouted() {
        var fixture = fixtures().filter(f -> f.input().name().equals("repeated_alternatives")).findFirst().orElseThrow();
        var policy = new ResearchTreeScenePolicy(fixture.input().policy(), 854,
                new ResearchTreeScenePolicy.NodeMetrics(120.1, 52.2, 6.3), "fractional-card");
        var input = ResearchTreeSceneInput.from(fixture.input().projections().get(0).replay(), policy)
                .withBranchSeeds(ResearchTreeBranchFixtures.seeds(fixture));
        assertEquals(4, checked(input).nodes().size());
    }

    @Test
    void simpleLocalChainsUseStraightConnectionsWhenClear() {
        var scene = checked(input("sparse_ranks"));
        var alternatives = scene.routing().orElseThrow().connections().stream()
                .filter(c -> c.owner().kind() == ResearchTreeRouting.Kind.ALTERNATIVE).toList();
        assertEquals(2, alternatives.size());
        assertTrue(alternatives.stream().allMatch(c -> c.points().size() == 2));
        assertEquals(0, scene.routing().orElseThrow().statistics().usedVerticalTracks());
    }

    private static boolean truth(ResearchTreeRouting routing, Set<String> learned) {
        return routing.junctions().stream().allMatch(j -> j.group().alternatives().stream()
                .anyMatch(id -> learned.stream().anyMatch(suffix -> id.getPath().endsWith(suffix))));
    }
    private static ResearchTreeSceneInput input(String name) {
        var f = Stream.concat(ResearchTreeBaselineFixtures.structural().stream(), fixtures())
                .filter(fixture -> fixture.input().name().equals(name)).findFirst().orElseThrow();
        return ResearchTreeBranchFixtures.input(f, 854);
    }
    private static ResearchTreeScene checked(ResearchTreeSceneInput input) {
        return ResearchTreeSceneValidator.validate(input, ENGINE.revision(), ENGINE.layout(input));
    }
    private static void rejects(ResearchTreeSceneInput input, ResearchTreeScene scene, ResearchTreeRouting routing) {
        var corrupt = new ResearchTreeScene(scene.key(), scene.extent(), scene.nodes(), scene.requirements(), scene.routes(),
                scene.boundaries(), scene.bands(), scene.regions(), scene.occupiedRanks(), Optional.of(routing));
        assertThrows(IllegalArgumentException.class, () -> ResearchTreeSceneValidator.validate(input, ENGINE.revision(), corrupt));
    }
}
