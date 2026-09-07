package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Adversarial combinations beyond the milestone acceptance examples. Seeds are fixed and reproducible. */
class ResearchTreeSceneAuditTest {
    private static final ResearchTreeBranchSceneEngine ENGINE = new ResearchTreeBranchSceneEngine();
    private static final ResearchTreeScenePolicy POLICY = ResearchTreeScenePolicy.compatibility(ResearchTechTreeLayoutPolicy.DEFAULT, 854);

    static Stream<Arguments> metrics() {
        var metrics = List.of(new ResearchTreeScenePolicy.NodeMetrics(24.1, 24.2, 4.3),
                new ResearchTreeScenePolicy.NodeMetrics(1, 1, 0),
                new ResearchTreeScenePolicy.NodeMetrics(120.1, 52.2, 6.3),
                new ResearchTreeScenePolicy.NodeMetrics(24, 24, 32));
        return Stream.concat(ResearchTreeBaselineFixtures.structural().stream(), ResearchTreeBaselineFixtures.routing().stream())
                .filter(f -> List.of("nested_split_merge", "unequal_branches", "fan_in_out", "multiple_boundaries", "anonymous_groups", "sparse_ranks").contains(f.input().name()))
                .flatMap(f -> metrics.stream().map(m -> Arguments.of(f, m)));
    }

    static Stream<Arguments> spacing() {
        var regions = List.of(new ResearchTreeScenePolicy.RegionMetrics(8, 1, 0, 0, 4, 8),
                new ResearchTreeScenePolicy.RegionMetrics(512, 128, 128, 2048, 128, 64));
        return Stream.concat(ResearchTreeBaselineFixtures.structural().stream(), ResearchTreeBaselineFixtures.routing().stream())
                .filter(f -> List.of("nested_split_merge", "unequal_branches", "fan_in_out", "multiple_boundaries", "anonymous_groups", "sparse_ranks").contains(f.input().name()))
                .flatMap(f -> regions.stream().flatMap(r -> Stream.of(POLICY.nodeMetrics(),
                        new ResearchTreeScenePolicy.NodeMetrics(24.1, 24.2, 0),
                        new ResearchTreeScenePolicy.NodeMetrics(24.1, 24.2, 4.3)).map(m -> Arguments.of(f, r, m))));
    }

    @ParameterizedTest(name = "spacing boundary combination {index}")
    @MethodSource("spacing")
    void zeroNodeGapsAndRegionMetricLimitsPreserveStructuralScenes(ResearchTreeBaselineFixtures.Fixture fixture,
            ResearchTreeScenePolicy.RegionMetrics regions, ResearchTreeScenePolicy.NodeMetrics metrics) {
        var spacing = new ResearchTechTreeLayoutPolicy(0, 0, 0, 0, 0, 1000, 0, 0);
        var policy = new ResearchTreeScenePolicy(spacing, 854, metrics, "audit-spacing", regions);
        checked(ResearchTreeSceneInput.from(fixture.input().projections().get(0).replay(), policy)
                .withBranchSeeds(ResearchTreeBranchFixtures.seeds(fixture)));
    }

    @ParameterizedTest(name = "metric combination {index}")
    @MethodSource("metrics")
    void supportedMetricsPreserveSmallStructuralScenes(ResearchTreeBaselineFixtures.Fixture fixture, ResearchTreeScenePolicy.NodeMetrics metrics) {
        var policy = new ResearchTreeScenePolicy(POLICY.spacing(), 854, metrics, "audit-metrics");
        checked(ResearchTreeSceneInput.from(fixture.input().projections().get(0).replay(), policy)
                .withBranchSeeds(ResearchTreeBranchFixtures.seeds(fixture)));
    }

    static IntStream seeds() { return IntStream.range(0, 48); }

    @ParameterizedTest(name = "small nested DAG seed {0}")
    @MethodSource("seeds")
    void smallNestedDagsWithRepeatedGroupsAreRoutable(int seed) {
        var random = new Random(seed);
        var builder = new ResearchTreeBaselineFixtures.Builder("audit_" + seed).parent("b1", "b0").parent("b2", "b0");
        for (int i = 0; i < 24; i++) {
            builder.node("n" + i, i / 4, "b" + i % 3);
            int available = i / 4 * 4;
            if (available == 0) continue;
            for (int group = 0, count = 1 + random.nextInt(3); group < count; group++) {
                var alternatives = new TreeSet<String>();
                for (int a = 0, countA = 1 + random.nextInt(4); a < countA; a++) alternatives.add("n" + random.nextInt(available));
                builder.requires("n" + i, alternatives.toArray(String[]::new));
            }
        }
        var fixture = builder.build();
        var input = ResearchTreeBranchFixtures.input(fixture, 854);
        var first = checked(input);
        assertEquals(first, checked(input));
    }

    @Test
    void sharedBoundaryMarkersAreBudgetedAsConnectionsRatherThanIndividualLinks() {
        var builder = new ResearchTreeBaselineFixtures.Builder("aggregated_links");
        for (int local = 0; local < 5; local++) {
            builder.node("local" + local, 0, "branch");
            for (int remote = 0; remote < 4000; remote++) builder.boundary("local" + local, "remote" + remote, Domain.AMMO);
        }
        var scene = checked(ResearchTreeBranchFixtures.input(builder.build(), 854));
        assertEquals(20_000, scene.boundaries().stream().mapToInt(b -> b.links().size()).sum());
        assertEquals(5, scene.routing().orElseThrow().connections().size());
    }

    @Test
    void primaryFactoryFailureIsContainedAndInvalidatesThePreviousResult() {
        var fail = new AtomicBoolean();
        var cache = new ResearchTreeSceneCache(p -> {
            if (fail.get()) throw new IllegalArgumentException("factory rejected current context");
            return new ResearchTreeCompatibilitySceneEngine(p);
        }, new ResearchTreeSimpleSceneEngine());
        var publication = ResearchTechTreeClientFixture.publication(); cache.bind(publication);
        var first = cache.scene(publication, Domain.WEAPONS, POLICY);
        var ammo = cache.scene(publication, Domain.AMMO, POLICY);
        fail.set(true);
        var nextPolicy = new ResearchTreeScenePolicy(POLICY.spacing(), 855, POLICY.nodeMetrics(), "new-context");
        var next = assertDoesNotThrow(() -> cache.scene(publication, Domain.WEAPONS, nextPolicy));
        assertFalse(cache.isCurrent(first));
        assertTrue(cache.isCurrent(ammo));
        assertEquals(ResearchTreeSceneCache.Status.UNAVAILABLE, next.status());
        assertTrue(cache.isCurrent(next));
        assertTrue(next.diagnostics().stream().anyMatch(d -> d.contains("factory")));
    }

    @Test
    void aNewFailedRequestInvalidatesAnOlderUnavailableResult() {
        var engine = new ResearchTreeSceneEngine() {
            public String revision() { return "audit-failure"; }
            public ResearchTreeScene layout(ResearchTreeSceneInput input) { throw new IllegalArgumentException("full"); }
        };
        var cache = new ResearchTreeSceneCache(p -> engine, engine);
        var publication = ResearchTechTreeClientFixture.publication(); cache.bind(publication);
        var first = cache.scene(publication, Domain.WEAPONS, POLICY);
        var next = cache.scene(publication, Domain.WEAPONS, new ResearchTreeScenePolicy(POLICY.spacing(), 855, POLICY.nodeMetrics(), "new-context"));
        assertEquals(ResearchTreeSceneCache.Status.UNAVAILABLE, next.status());
        assertFalse(cache.isCurrent(first)); assertTrue(cache.isCurrent(next));
    }

    @Test
    void groupedRoutingContractCannotReuseAnEarlierStructuralScene() {
        var grouped = new AtomicBoolean();
        var simple = new ResearchTreeSimpleSceneEngine();
        var engine = new ResearchTreeSceneEngine() {
            public String revision() { return simple.revision(); }
            public boolean requiresGroupedRouting() { return grouped.get(); }
            public ResearchTreeScene layout(ResearchTreeSceneInput input) { return simple.layout(input); }
        };
        var cache = new ResearchTreeSceneCache(p -> engine, simple);
        var publication = ResearchTechTreeClientFixture.publication(); cache.bind(publication);
        var first = cache.scene(publication, Domain.WEAPONS, POLICY);
        var reused = cache.scene(publication, Domain.WEAPONS, POLICY);
        assertTrue(reused.reused()); assertTrue(cache.isCurrent(first));
        grouped.set(true);
        var next = cache.scene(publication, Domain.WEAPONS, POLICY);
        assertFalse(next.reused()); assertFalse(cache.isCurrent(first));
        assertEquals(ResearchTreeSceneCache.Status.UNAVAILABLE, next.status());
    }

    @Test
    void nullFactoryResultIsUnavailableInsteadOfSilentlyChangingEngines() {
        var cache = new ResearchTreeSceneCache(p -> null, new ResearchTreeSimpleSceneEngine());
        var publication = ResearchTechTreeClientFixture.publication(); cache.bind(publication);
        assertEquals(ResearchTreeSceneCache.Status.UNAVAILABLE, cache.scene(publication, Domain.WEAPONS, POLICY).status());
    }

    @Test
    void unsupportedTypedProjectionEvictsGeometryWithoutBreakingPublicationBinding() {
        // The typed projection constructor validates membership; scene input additionally validates edge ranks.
        var before = new ResearchTreeBaselineFixtures.Builder("rank_change").node("a", 0, "branch")
                .node("b", 1, "branch").requires("b", "a").build().input().projections().get(0).replay();
        var after = new ResearchTreeBaselineFixtures.Builder("rank_change").node("a", 0, "branch")
                .node("b", 0, "branch").requires("b", "a").build().input().projections().get(0).replay();
        var publication = ResearchTechTreeClientFixture.publication();
        var cache = new ResearchTreeSceneCache();
        cache.bind(publication, catalog(before));
        var first = cache.scene(publication, Domain.WEAPONS, POLICY);
        assertEquals(ResearchTreeSceneCache.Status.VALID, first.status());
        assertDoesNotThrow(() -> cache.bind(publication, catalog(after)));
        var next = assertDoesNotThrow(() -> cache.scene(publication, Domain.WEAPONS, POLICY));
        assertEquals(ResearchTreeSceneCache.Status.UNAVAILABLE, next.status());
        assertSame(after, next.presentation().orElseThrow());
        assertFalse(cache.isCurrent(first)); assertTrue(cache.isCurrent(next));
        assertTrue(next.diagnostics().get(0).contains("ranks"));
    }

    @Test
    void arithmeticToleranceDoesNotHideMeasurableContainmentViolations() {
        var bounds = new ResearchTreeScene.Bounds(100, 100, 100, 100);
        assertTrue(bounds.contains(new ResearchTreeScene.Bounds(100, 100, 100.000000001, 100)));
        assertFalse(bounds.contains(new ResearchTreeScene.Bounds(100, 100, 100.00001, 100)));
        assertFalse(bounds.contains(new ResearchTreeScene.Point(200.00001, 200)));
    }

    private static ResearchTechTreeProjectionCatalog catalog(ResearchTechTreeProjection projection) {
        var presentation = new com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation(
                java.util.Optional.of(new net.minecraft.resources.ResourceLocation("baseline", "audit")), "Audit",
                java.util.Optional.empty(), java.util.Optional.empty(), List.of(), List.of(), List.of(projection.presentation()));
        return new ResearchTechTreeProjectionCatalog(presentation, List.of(projection));
    }

    private static ResearchTreeScene checked(ResearchTreeSceneInput input) {
        return ResearchTreeSceneValidator.validate(input, ENGINE.revision(), ENGINE.layout(input));
    }
}
