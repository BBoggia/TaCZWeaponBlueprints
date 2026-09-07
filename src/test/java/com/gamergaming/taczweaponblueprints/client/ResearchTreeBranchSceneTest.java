package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ResearchTreeBranchSceneTest {
    private static final ResearchTreeBranchSceneEngine ENGINE = new ResearchTreeBranchSceneEngine();

    static Stream<ResearchTreeBaselineFixtures.Fixture> fixtures() {
        return ResearchTreeBaselineFixtures.structural().stream();
    }

    @ParameterizedTest(name = "branch allocation {index}")
    @MethodSource("fixtures")
    void finalEnvelopesAndMembershipValidateForStructuralFixtures(ResearchTreeBaselineFixtures.Fixture fixture) {
        var input = ResearchTreeBranchFixtures.input(fixture, 854);
        var scene = checked(input);
        assertEquals(scene, checked(input));
        assertEquals(input.topology().nodes().size(), scene.nodes().size());
        assertEquals(input.topology().edges().size(), scene.routes().size());
        assertEquals(input.topology().nodes().size(), scene.regions().stream().mapToInt(r -> r.seed().directMembers().size()).sum());
        assertEquals(input.topology().nodes().stream().map(p -> p.rank()).distinct().count(), scene.occupiedRanks().size());
    }

    @Test
    void unequalBranchesEndAtTheirOwnRanksAndSharedBaseDoesNotConsumeAChildColumn() {
        var input = input("unequal_branches");
        var scene = checked(input);
        var base = region(scene, "base");
        var a = region(scene, "branch_0");
        var b = region(scene, "branch_1");
        var c = region(scene, "branch_2");
        assertEquals(2, a.maximumRank());
        assertEquals(4, b.maximumRank());
        assertEquals(6, c.maximumRank());
        assertTrue(a.bounds().height() < b.bounds().height());
        assertTrue(b.bounds().height() < c.bounds().height());
        assertTrue(a.bounds().y() > b.bounds().y());
        assertTrue(base.bounds().contains(a.bounds()));
        double baseCenter = node(scene, "base").body().centerX();
        assertEquals(base.bounds().centerX(), baseCenter);
        assertTrue(b.bounds().x() - a.bounds().right() >= input.policy().regionMetrics().gap());
        assertTrue(c.bounds().x() - b.bounds().right() >= input.policy().regionMetrics().gap());
    }

    @Test
    void nestedSplitAndLaterParentMergeStayContained() {
        var scene = checked(input("nested_split_merge"));
        var left = region(scene, "left");
        assertEquals(Optional.of("base"), left.seed().parentId());
        assertEquals(Optional.of("left"), region(scene, "left_inner_a").seed().parentId());
        assertTrue(left.bounds().contains(region(scene, "left_inner_b").bounds()));
        assertTrue(left.seed().directMembers().stream().anyMatch(id -> id.getPath().endsWith("/left_merge")));
        assertTrue(node(scene, "left_merge").paint().bottom() < region(scene, "left_inner_a").bounds().y());
    }

    @Test
    void parentOwnedMemberAtChildOccupiedRankGetsAnIndependentStrip() {
        var original = input("and_of_or");
        var ids = original.topology().nodes().stream().map(p -> p.nodeId()).toList();
        var parent = new ResearchTreeBranchStructure.Seed("parent", Optional.empty(), 0, List.of(ids.get(0), ids.get(3)));
        var child = new ResearchTreeBranchStructure.Seed("child", Optional.of("parent"), 0, List.of(ids.get(1), ids.get(2)));
        var scene = checked(original.withBranchSeeds(List.of(parent, child)));
        assertTrue(node(scene, "a").paint().right() < region(scene, "child").bounds().x());
        assertEquals(node(scene, "a").body().y(), node(scene, "b").body().y());
    }

    @Test
    void returningRegionConnectionsDoNotBecomeContainmentCycles() {
        var input = input("region_return");
        var forest = ResearchTreeBranchStructure.resolve(input);
        assertEquals(2, forest.roots().size());
        assertFalse(forest.ancestor("a", "b"));
        assertFalse(forest.ancestor("b", "a"));
        var scene = checked(input);
        assertEquals(2, scene.routes().size());
        assertTrue(node(scene, "a1").body().y() > node(scene, "b2").body().y());
        assertTrue(node(scene, "b2").body().y() > node(scene, "a3").body().y());
    }

    @Test
    void sparseRanksAreCompactedWithoutCreatingIntermediateRanks() {
        var scene = checked(input("sparse_ranks"));
        assertEquals(List.of(0, 400_000, 1_000_000), scene.occupiedRanks().stream().map(r -> r.rank()).toList());
        assertTrue(scene.extent().height() < 1000);
    }

    @Test
    void viewportWidthCannotRepackOrMoveBranchNodes() {
        var fixture = fixture("unequal_branches");
        var wide = checked(ResearchTreeBranchFixtures.input(fixture, 1600));
        var narrow = checked(ResearchTreeBranchFixtures.input(fixture, 180));
        assertEquals(wide.nodes(), narrow.nodes());
        assertEquals(wide.regions(), narrow.regions());
        assertEquals(wide.occupiedRanks(), narrow.occupiedRanks());
        assertEquals(wide.routes(), narrow.routes());
    }

    @Test
    void seedAndMembershipInsertionOrderCannotChangeGeometry() {
        var input = input("nested_split_merge");
        var reordered = new ArrayList<ResearchTreeBranchStructure.Seed>();
        for (var seed : input.topology().branchSeeds()) {
            var members = new ArrayList<>(seed.directMembers()); Collections.reverse(members);
            reordered.add(new ResearchTreeBranchStructure.Seed(seed.id(), seed.parentId(), seed.order(), members));
        }
        Collections.reverse(reordered);
        assertEquals(input.key(ENGINE.revision()), input.withBranchSeeds(reordered).key(ENGINE.revision()));
        assertEquals(checked(input), checked(input.withBranchSeeds(reordered)));
    }

    @Test
    void neutralAuthoredGroupingDoesNotTreatLanesAsBranches() {
        var fixture = fixture("nested_split_merge");
        var input = ResearchTreeSceneInput.from(fixture.input().projections().get(0).replay(),
                ResearchTreeScenePolicy.compatibility(fixture.input().policy(), 854));
        var scene = checked(input);
        assertEquals(1, scene.regions().size());
        assertTrue(scene.regions().get(0).seed().id().startsWith("component/"));
        var independent = fixture("independent_roots");
        var other = ResearchTreeSceneInput.from(independent.input().projections().get(0).replay(), input.policy());
        assertEquals(3, checked(other).regions().size());
    }

    @Test
    void actualCatalogSeparatesSevenPostTransitionFamiliesAndKeepsSharedFoundation() throws Exception {
        var input = liveInput();
        var forest = ResearchTreeBranchStructure.resolve(input);
        assertEquals(8, forest.seeds().size());
        assertEquals(7, forest.children("automatic/shared").size());
        assertEquals(234, forest.seed("automatic/shared").directMembers().size());
        for (var p : input.topology().nodes()) {
            var hint = p.automaticBranch().orElseThrow();
            String expected = hint.rankIndex() <= hint.transitionEndIndex() ? "automatic/shared" : "automatic/family/" + hint.branchIndex();
            assertEquals(expected, forest.owner(p.nodeId()));
        }
        var scene = checked(input);
        assertEquals(287, scene.nodes().size());
        assertEquals(454, scene.routes().size());
        assertEquals(286, scene.requirements().size());
        assertEquals(19, scene.occupiedRanks().size());
        for (var child : forest.children("automatic/shared")) {
            assertTrue(region(scene, child.id()).bounds().height() < region(scene, "automatic/shared").bounds().height());
        }
    }

    @Test
    void stateOnlyCacheUpdatesReuseBranchGeometry() {
        var cache = new ResearchTreeSceneCache(p -> ENGINE, new ResearchTreeSimpleSceneEngine());
        var before = ResearchTechTreeClientFixture.publication();
        var after = ResearchTechTreeClientFixture.publication(Set.of(Domain.values()), ResearchTreeGraph.Availability.AVAILABLE);
        var policy = ResearchTreeScenePolicy.compatibility(ResearchTechTreeLayoutPolicy.DEFAULT, 854);
        cache.bind(before);
        var a = cache.scene(before, Domain.WEAPONS, policy);
        cache.bind(after);
        var b = cache.scene(after, Domain.WEAPONS, policy);
        assertEquals(ResearchTreeSceneCache.Status.VALID, a.status());
        assertSame(a.geometry().orElseThrow(), b.geometry().orElseThrow());
        assertTrue(b.reused());
    }

    @Test
    void rejectsUnknownDuplicateMissingAndCyclicBranchOwnership() {
        var input = input("single");
        var member = input.topology().nodes().get(0).nodeId();
        var valid = new ResearchTreeBranchStructure.Seed("a", Optional.empty(), 0, List.of(member));
        assertThrows(IllegalArgumentException.class, () -> input.withBranchSeeds(List.of(valid, valid)));
        assertThrows(IllegalArgumentException.class, () -> input.withBranchSeeds(List.of(
                new ResearchTreeBranchStructure.Seed("a", Optional.empty(), 0, List.of()))));
        assertThrows(IllegalArgumentException.class, () -> input.withBranchSeeds(List.of(
                new ResearchTreeBranchStructure.Seed("a", Optional.of("missing"), 0, List.of(member)))));
        assertThrows(IllegalArgumentException.class, () -> input.withBranchSeeds(List.of(
                new ResearchTreeBranchStructure.Seed("a", Optional.of("b"), 0, List.of(member)),
                new ResearchTreeBranchStructure.Seed("b", Optional.of("a"), 0, List.of()))));
        assertThrows(IllegalArgumentException.class, () -> input.withBranchSeeds(List.of(
                new ResearchTreeBranchStructure.Seed("a", Optional.empty(), 0, List.of(ResearchTechTreeClientFixture.OPAQUE)))));
        var chain = new ArrayList<ResearchTreeBranchStructure.Seed>();
        for (int i = 0; i <= ResearchTreeBranchStructure.MAX_DEPTH; i++) chain.add(new ResearchTreeBranchStructure.Seed(
                "r" + i, i == 0 ? Optional.empty() : Optional.of("r" + (i - 1)), 0,
                i == ResearchTreeBranchStructure.MAX_DEPTH ? List.of(member) : List.of()));
        assertThrows(IllegalArgumentException.class, () -> input.withBranchSeeds(chain));
    }

    @Test
    void finalValidatorRejectsMissingRegionsLostGapsAndExtendedEmptyBranchHeight() {
        var input = input("unequal_branches");
        var scene = checked(input);
        var missing = new ResearchTreeScene(scene.key(), scene.extent(), scene.nodes(), scene.requirements(),
                scene.routes(), scene.boundaries(), scene.bands(), List.of(), scene.occupiedRanks(), scene.routing());
        assertEquals("scene region membership mismatch", assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeSceneValidator.validate(input, ENGINE.revision(), missing)).getMessage());
        var regions = new ArrayList<>(scene.regions());
        var original = region(scene, "branch_0");
        regions.set(regions.indexOf(original), new ResearchTreeScene.Region(original.seed(),
                new ResearchTreeScene.Bounds(original.bounds().x(), original.bounds().y() - 1,
                        original.bounds().width(), original.bounds().height() + 1), original.minimumRank(), original.maximumRank()));
        var stretched = new ResearchTreeScene(scene.key(), scene.extent(), scene.nodes(), scene.requirements(),
                scene.routes(), scene.boundaries(), scene.bands(), regions, scene.occupiedRanks(), scene.routing());
        assertEquals("region is not bounded by its occupied content", assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeSceneValidator.validate(input, ENGINE.revision(), stretched)).getMessage());
        regions = new ArrayList<>(scene.regions());
        regions.set(regions.indexOf(original), new ResearchTreeScene.Region(original.seed(),
                new ResearchTreeScene.Bounds(original.bounds().x(), original.bounds().y(),
                        original.bounds().width() + input.policy().regionMetrics().gap(), original.bounds().height()),
                original.minimumRank(), original.maximumRank()));
        var lostGap = new ResearchTreeScene(scene.key(), scene.extent(), scene.nodes(), scene.requirements(),
                scene.routes(), scene.boundaries(), scene.bands(), regions, scene.occupiedRanks(), scene.routing());
        assertEquals("branch sibling order or reserved gap changed", assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeSceneValidator.validate(input, ENGINE.revision(), lostGap)).getMessage());
    }

    @Test
    void nodeLimitRemainsBoundedAndEdgeLimitReportsTheGroupedRoutingBudget() {
        var input = ResearchTreeBranchFixtures.input(ResearchTreeBaselineFixtures.maximumNodes(), 854);
        assertEquals(4096, checked(input).nodes().size());
        var maximumEdges = ResearchTreeBranchFixtures.input(ResearchTreeBaselineFixtures.maximumEdges(), 854);
        var error = assertThrows(IllegalArgumentException.class, () -> ENGINE.layout(maximumEdges));
        assertTrue(error.getMessage().contains("connection budget exceeded"));
    }

    @Test
    void largerMeasuredCardsAndRegionAllowancesRemainSeparated() {
        var fixture = fixture("nested_split_merge");
        var policy = new ResearchTreeScenePolicy(fixture.input().policy(), 854,
                new ResearchTreeScenePolicy.NodeMetrics(120, 52, 6), "cards-test-v1",
                new ResearchTreeScenePolicy.RegionMetrics(64, 16, 20, 180, 24, 12));
        var input = ResearchTreeSceneInput.from(fixture.input().projections().get(0).replay(), policy)
                .withBranchSeeds(ResearchTreeBranchFixtures.seeds(fixture));
        assertEquals(10, checked(input).nodes().size());
    }

    @Test
    void boundaryMarkersAreIncludedInTheirOwnersEnvelopeEvenForTinyNodeBodies() {
        var catalog = ResearchTechTreeProjectionBuilder.build(ResearchTechTreeClientFixture.publication());
        var policy = new ResearchTreeScenePolicy(ResearchTechTreeLayoutPolicy.DEFAULT, 854,
                new ResearchTreeScenePolicy.NodeMetrics(24, 1, 0), "tiny-metrics-test");
        for (var projection : catalog.projections()) {
            var input = ResearchTreeSceneInput.from(projection, policy);
            var forest = ResearchTreeBranchStructure.resolve(input);
            var scene = checked(input);
            for (var marker : scene.boundaries()) assertTrue(region(scene,
                    forest.owner(marker.links().get(0).localNodeId())).bounds().contains(marker.bounds()));
        }
    }

    @Test
    void contradictoryHintsAndUnfinishedTransitionUseNeutralGrouping() throws Exception {
        var resource = new String(getClass().getResourceAsStream("/research-tree-baseline/live-weapons-287.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        for (boolean inconsistent : List.of(true, false)) {
            var json = com.google.gson.JsonParser.parseString(resource).getAsJsonObject();
            var lanes = json.getAsJsonArray("projections").get(0).getAsJsonObject()
                    .getAsJsonObject("presentation").getAsJsonArray("lanes");
            boolean first = true;
            for (var lane : lanes) for (var member : lane.getAsJsonObject().getAsJsonArray("members")) {
                var hint = member.getAsJsonObject().getAsJsonObject("automaticBranch");
                if (!inconsistent || first) hint.addProperty("transitionEndIndex", 1_000_000);
                first = false;
            }
            var replay = ResearchTreeBaselineInput.fromJson(json.toString());
            var input = ResearchTreeSceneInput.from(replay.projections().get(0).replay(),
                    ResearchTreeScenePolicy.compatibility(replay.policy(), 854));
            assertEquals(1, checked(input).regions().size());
            assertTrue(checked(input).regions().get(0).seed().id().startsWith("component/"));
        }
    }

    @Test
    void validMaximumDepthIsBoundedAndOversizedAllocationRejectsWithoutPartialScene() {
        var fixture = ResearchTreeBaselineFixtures.maximumNodes();
        var members = fixture.input().projections().get(0).replay().graph().nodes().stream().map(n -> n.blueprintId()).toList();
        var chain = new ArrayList<ResearchTreeBranchStructure.Seed>();
        for (int i = 0; i < ResearchTreeBranchStructure.MAX_DEPTH; i++) chain.add(new ResearchTreeBranchStructure.Seed(
                "depth/" + i, i == 0 ? Optional.empty() : Optional.of("depth/" + (i - 1)), 0,
                i == ResearchTreeBranchStructure.MAX_DEPTH - 1 ? members : List.of()));
        var policy = new ResearchTreeScenePolicy(fixture.input().policy(), 854,
                ResearchTreeScenePolicy.NodeMetrics.COMPATIBILITY, "deep-test-v1",
                new ResearchTreeScenePolicy.RegionMetrics(40, 128, 128, 80, 16, 10));
        var input = ResearchTreeSceneInput.from(fixture.input().projections().get(0).replay(), policy).withBranchSeeds(chain);
        assertEquals(64, ResearchTreeBranchStructure.resolve(input).seeds().size());
        assertThrows(IllegalArgumentException.class, () -> ENGINE.layout(input));
        var simple = new ResearchTreeSimpleSceneEngine();
        assertEquals(4096, ResearchTreeSceneValidator.validate(input, simple.revision(), simple.layout(input)).nodes().size());
    }

    static ResearchTreeSceneInput liveInput() throws Exception {
        try (var stream = ResearchTreeBranchSceneTest.class.getResourceAsStream("/research-tree-baseline/live-weapons-287.json")) {
            var replay = ResearchTreeBaselineInput.fromJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return ResearchTreeSceneInput.from(replay.projections().get(0).replay(),
                    ResearchTreeScenePolicy.compatibility(replay.policy(), replay.viewportWidth()));
        }
    }
    private static ResearchTreeBaselineFixtures.Fixture fixture(String name) {
        return fixtures().filter(f -> f.input().name().equals(name)).findFirst().orElseThrow();
    }
    private static ResearchTreeSceneInput input(String name) { return ResearchTreeBranchFixtures.input(fixture(name), 854); }
    private static ResearchTreeScene checked(ResearchTreeSceneInput input) {
        return ResearchTreeSceneValidator.validate(input, ENGINE.revision(), ENGINE.layout(input));
    }
    private static ResearchTreeScene.Region region(ResearchTreeScene scene, String id) {
        return scene.regions().stream().filter(r -> r.seed().id().equals(id)).findFirst().orElseThrow();
    }
    private static ResearchTreeScene.Node node(ResearchTreeScene scene, String path) {
        return scene.nodes().stream().filter(n -> n.id().getPath().endsWith("/" + path)).findFirst().orElseThrow();
    }
}
