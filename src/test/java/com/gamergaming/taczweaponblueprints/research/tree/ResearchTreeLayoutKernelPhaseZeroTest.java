package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchTreePresentationContract;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeProjection;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeProjectionCache;

import net.minecraft.resources.ResourceLocation;

/** Phase 0 contracts for the shared layered-layout migration. */
class ResearchTreeLayoutKernelPhaseZeroTest {
    @Test
    void packagedPistolShapeIsAConnectedFourForkProgression() {
        ResearchTreePublication publication =
                ResearchTreeRedesignFixture.defaultPistolProgression();

        assertEquals(14, publication.graph().nodes().size());
        assertEquals(13, publication.graph().edges().size());
        assertEquals(Set.of(
                        taczEdge("taurus943", "glock_17"),
                        taczEdge("taurus943", "m9a4"),
                        taczEdge("glock_17", "m1911"),
                        taczEdge("glock_17", "cz75"),
                        taczEdge("glock_17", "p320"),
                        taczEdge("glock_17", "hk_mk23"),
                        taczEdge("m9a4", "rhino357"),
                        taczEdge("m1911", "lonetrail"),
                        taczEdge("cz75", "b93r"),
                        taczEdge("p320", "deagle"),
                        taczEdge("p320", "deagle_golden"),
                        taczEdge("p320", "timeless50"),
                        taczEdge("rhino357", "taurus500")),
                new LinkedHashSet<>(publication.graph().edges()));
        assertEquals(1, rootCount(publication.graph()));
        assertEquals(Set.of(tacz("taurus943")), roots(publication.graph()));
        assertEquals(
                List.of(tacz("glock_17"), tacz("m9a4")),
                publication.graph().edges().stream()
                        .filter(edge -> edge.prerequisiteId().equals(tacz("taurus943")))
                        .map(ResearchTreeGraph.Edge::dependentId)
                        .sorted()
                        .toList());
        assertEquals(
                List.of(tacz("rhino357")),
                publication.graph().prerequisitesOf(tacz("taurus500")));
        assertEquals(
                List.of(tacz("m9a4")),
                publication.graph().prerequisitesOf(tacz("rhino357")));
    }

    @Test
    void migratedPistolBranchReusesTheSharedSingleRowSkeleton() {
        ResearchTreePublication publication =
                ResearchTreeRedesignFixture.defaultPistolProgression();
        ResearchTreePresentation.Group pistols = publication.presentation()
                .group(ResearchTreeRedesignFixture.PISTOL_GROUP_ID)
                .orElseThrow();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        cache.update(publication);

        ResearchTreeProjection branchProjection = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                pistols.id());
        ResearchTreeLayout branch = branchProjection.layout();
        ResearchTreeLayout overview = ResearchTreeUnifiedLayoutEngine.layout(publication);

        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                branchProjection.graph(), branch);
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), overview);
        assertEquals(branch, cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                pistols.id()).layout());
        assertEquals(overview, ResearchTreeUnifiedLayoutEngine.layout(publication));

        Set<ResourceLocation> rankTwo = pistols.members().stream()
                .filter(member -> member.rank() == 2)
                .map(ResearchTreePresentation.Member::nodeId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(1L, distinctY(branch, rankTwo),
                "Branches and All Weapons must preserve one shared authored-rank row");
        assertEquals(1L, distinctY(overview, rankTwo),
                "the unified engine keeps the authored rank on one visual row");
    }

    @Test
    void projectionsRemainTruthfulAcrossAnExcludedRedactedBoundary() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.disclosureBoundary();
        ResourceLocation publicGroup = id("public_group");
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        cache.update(publication);

        ResearchTreeProjection overview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS, null);
        Set<ResourceLocation> publicIds = Set.of(id("public_root"), id("public_child"));
        ResearchTreeLayoutContractAssertions.assertInducedSubgraph(
                publication.graph(), overview.graph(), publicIds);
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                overview.graph(), overview.layout());
        assertEquals(1, overview.crossGroupLinks().size());
        assertEquals(ResearchTreeProjection.Direction.UNLOCK,
                overview.crossGroupLinks().get(0).direction());

        ResearchTreeProjection branch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES, publicGroup);
        ResearchTreeLayoutContractAssertions.assertInducedSubgraph(
                publication.graph(), branch.graph(), publicIds);
        assertEquals(1, branch.crossGroupLinks().size());

        ResearchTreeGraph.Node undisclosed = publication.graph().nodes().stream()
                .filter(node -> !node.visibility().revealsIdentity())
                .findFirst()
                .orElseThrow();
        assertFalse(undisclosed.visibility().revealsIdentity());
        assertEquals(ResearchTreeGraph.REDACTED_NAME_KEY,
                undisclosed.nameKey());
        assertTrue(overview.graph().node(undisclosed.blueprintId()).isEmpty());
    }

    @Test
    void redactedBranchPreservesItsOpaqueSourceIdentityAfterLocalReindexing() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.disclosureBoundary();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        cache.update(publication);

        ResearchTreeProjection branch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID);
        ResearchTreeGraph.Node source = publication.graph().nodes().get(2);
        ResearchTreeGraph.Node projected = branch.graph().nodes().get(0);

        assertEquals(0, projected.ordinal());
        assertEquals(source.ordinal(), projected.sourceOrdinal());
        assertEquals(source.blueprintId(), projected.blueprintId());
        assertFalse(projected.visibility().revealsIdentity());
        assertEquals(projected.blueprintId(), branch.layout().nodes().get(0).blueprintId());
        assertEquals(1, branch.crossGroupLinks().size());
        assertEquals(ResearchTreeProjection.Direction.REQUIREMENT,
                branch.crossGroupLinks().get(0).direction());
        assertEquals(id("public_group"), branch.crossGroupLinks().get(0).remoteGroupId());
    }

    @Test
    void nodeDagMayContainOpposingGroupLevelDirections() {
        ResearchTreePublication publication =
                ResearchTreeRedesignFixture.alternatingGroupDependencies();

        Set<GroupDirection> groupDirections = publication.graph().edges().stream()
                .map(edge -> new GroupDirection(
                        publication.presentation().membership(edge.prerequisiteId())
                                .orElseThrow().groupId(),
                        publication.presentation().membership(edge.dependentId())
                                .orElseThrow().groupId()))
                .filter(direction -> !direction.source().equals(direction.target()))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                new GroupDirection(id("group_a"), id("group_b")),
                new GroupDirection(id("group_b"), id("group_a"))), groupDirections);

        ResearchTreeLayout layout = ResearchTreeUnifiedLayoutEngine.layout(publication);
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), layout);
        assertEquals(layout, ResearchTreeUnifiedLayoutEngine.layout(publication));
    }

    @Test
    void maximumValidDepthRemainsBoundedAndFast() {
        ResearchTreePublication publication =
                ResearchTreeRedesignFixture.maximumDepthProgression();

        ResearchTreeLayout layout = assertTimeout(
                Duration.ofSeconds(2),
                () -> ResearchTreeUnifiedLayoutEngine.layout(publication));

        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), layout);
        assertTrue(layout.width() <= ResearchTreeLayout.MAX_DIMENSION);
        assertTrue(layout.height() <= ResearchTreeLayout.MAX_DIMENSION);
    }

    private static long distinctY(
            ResearchTreeLayout layout,
            Set<ResourceLocation> nodeIds) {
        return nodeIds.stream()
                .map(layout::position)
                .map(java.util.Optional::orElseThrow)
                .map(ResearchTreeLayout.PositionedNode::y)
                .distinct()
                .count();
    }

    private static int rootCount(ResearchTreeGraph graph) {
        return roots(graph).size();
    }

    private static Set<ResourceLocation> roots(ResearchTreeGraph graph) {
        Set<ResourceLocation> dependents = graph.edges().stream()
                .map(ResearchTreeGraph.Edge::dependentId)
                .collect(java.util.stream.Collectors.toSet());
        return graph.nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .filter(nodeId -> !dependents.contains(nodeId))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static ResourceLocation tacz(String path) {
        return new ResourceLocation("tacz", path);
    }

    private static ResearchTreeGraph.Edge taczEdge(String prerequisite, String dependent) {
        return new ResearchTreeGraph.Edge(tacz(prerequisite), tacz(dependent));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("phase_zero", path);
    }

    private record GroupDirection(ResourceLocation source, ResourceLocation target) {
    }
}
