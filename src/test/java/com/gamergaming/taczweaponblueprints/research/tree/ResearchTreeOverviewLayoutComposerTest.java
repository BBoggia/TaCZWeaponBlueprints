package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchTreeEdgeIndex;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeOverviewBuilder;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeOverviewLayoutComposerTest {
    private static final ResearchTreeLayoutPolicy POLICY =
            ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;

    @Test
    void connectedGroupsBecomeOneTruthfulAtlasWithContiguousRankBlocks() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.connectedProgression();
        ResearchTreeGroupSkeletonCatalog skeletons = skeletons(publication);

        ResearchTreeLayout layout = ResearchTreeOverviewLayoutComposer.compose(
                publication, skeletons, POLICY);

        assertEquals(layout, ResearchTreeOverviewLayoutComposer.compose(
                publication, skeletons, POLICY));
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), layout);
        assertEquals(1L, layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::component)
                .distinct()
                .count());
        assertTrue(layout.categoryLanes().isEmpty());
        assertTrue(layout.groupRegions().isEmpty());
        assertGroupBlocksAreContiguous(publication, layout);
        assertEquals(publication.graph().edges().size(), ResearchTreeEdgeIndex
                .create(publication.graph(), layout)
                .visible(0, 0, layout.width(), layout.height())
                .size());

        ResearchTreeLayout.PositionedNode left = layout.position(id("left")).orElseThrow();
        ResearchTreeLayout.PositionedNode right = layout.position(id("right")).orElseThrow();
        assertTrue(right.x() - left.x() >= ResearchTreeLayout.NODE_WIDTH + POLICY.interGroupGap());
    }

    @Test
    void oneGroupKeepsItsSkeletonSiblingShapeWhenNoWrappingIsNeeded() {
        ResearchTreePublication publication =
                ResearchTreeRedesignFixture.defaultPistolProgression();
        ResearchTreeGroupSkeletonCatalog skeletons = skeletons(publication);
        ResearchTreeGroupSkeleton pistols = skeletons.group(
                ResearchTreeRedesignFixture.PISTOL_GROUP_ID).orElseThrow();

        ResearchTreeLayout layout = ResearchTreeOverviewLayoutComposer.compose(
                publication, skeletons, POLICY);

        for (int rank = 0; rank < 5; rank++) {
            int selectedRank = rank;
            List<ResearchTreeGroupSkeleton.PositionedNode> local = pistols.nodes().stream()
                    .filter(node -> node.authoredRank() == selectedRank)
                    .sorted(java.util.Comparator.comparingInt(
                            ResearchTreeGroupSkeleton.PositionedNode::x))
                    .toList();
            List<ResearchTreeLayout.PositionedNode> atlas = local.stream()
                    .map(node -> layout.position(node.nodeId()).orElseThrow())
                    .sorted(java.util.Comparator.comparingInt(
                            ResearchTreeLayout.PositionedNode::x))
                    .toList();
            int localOrigin = local.get(0).x();
            int atlasOrigin = atlas.get(0).x();
            for (int index = 0; index < local.size(); index++) {
                assertEquals(local.get(index).x() - localOrigin,
                        atlas.get(index).x() - atlasOrigin);
            }
        }
        ResearchTreeLayout.EdgeRouteHint route = layout.edgeRouteHint(
                new ResourceLocation("tacz", "rhino357"),
                new ResourceLocation("tacz", "taurus500")).orElseThrow();
        assertEquals(3, route.waypoints().get(0).rank());
        assertTrue(layout.nodes().stream()
                .filter(node -> node.tier() == 3)
                .noneMatch(node -> route.waypoints().get(0).x() > node.x()
                        && route.waypoints().get(0).x()
                                < node.x() + ResearchTreeLayout.NODE_WIDTH));
    }

    @Test
    void opposingGroupDirectionsDoNotRequireAGroupLevelDag() {
        ResearchTreePublication publication =
                ResearchTreeRedesignFixture.alternatingGroupDependencies();

        ResearchTreeLayout layout = ResearchTreeOverviewLayoutComposer.compose(
                publication, skeletons(publication), POLICY);

        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), layout);
        assertEquals(1L, layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::component)
                .distinct()
                .count());
        assertGroupBlocksAreContiguous(publication, layout);
    }

    @Test
    void disconnectedAuthoredGroupsPackAsReadableStableIslands() {
        ResearchTreeOverviewBuilder.Result overview = ResearchTreeOverviewBuilder.build(
                ResearchTreeRedesignFixture.denseGeneratedCatalog());
        ResearchTreePublication publication = overview.publication();
        ResearchTreeGroupSkeletonCatalog fullSkeletons = skeletons(
                ResearchTreeRedesignFixture.denseGeneratedCatalog());

        ResearchTreeLayout layout = assertTimeout(
                Duration.ofSeconds(3),
                () -> ResearchTreeOverviewLayoutComposer.compose(
                        publication, fullSkeletons, POLICY));

        assertEquals(publication.presentation().groups().size(), layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::component)
                .distinct()
                .count());
        assertTrue(layout.width() < 1_000);
        assertTrue(layout.height() < 1_000);
        assertGroupBlocksAreContiguous(publication, layout);
    }

    @Test
    void disconnectedFallbackComponentsKeepTheirTwoDimensionalRankGeometry() {
        ResearchTreePublication publication = disconnectedFallbackRankPublication(32);
        ResearchTreeGroupSkeletonCatalog skeletons = skeletons(publication);
        ResearchTreeGroupSkeleton skeleton = skeletons.group(
                id("addon_fallback")).orElseThrow();
        assertTrue(skeleton.nodes().stream()
                .map(ResearchTreeGroupSkeleton.PositionedNode::y)
                .distinct()
                .count() > 1, "the fixture must exercise component rows inside one rank");

        ResearchTreeLayout layout = ResearchTreeOverviewLayoutComposer.compose(
                publication, skeletons, POLICY);

        assertEquals(publication.graph().nodes().size(), layout.nodes().size());
        assertTrue(layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::y)
                .distinct()
                .count() > 1);
        int localMinimumX = skeleton.nodes().stream()
                .mapToInt(ResearchTreeGroupSkeleton.PositionedNode::x)
                .min()
                .orElseThrow();
        int localMinimumY = skeleton.nodes().stream()
                .mapToInt(ResearchTreeGroupSkeleton.PositionedNode::y)
                .min()
                .orElseThrow();
        int atlasMinimumX = layout.nodes().stream()
                .mapToInt(ResearchTreeLayout.PositionedNode::x)
                .min()
                .orElseThrow();
        int atlasMinimumY = layout.nodes().stream()
                .mapToInt(ResearchTreeLayout.PositionedNode::y)
                .min()
                .orElseThrow();
        for (ResearchTreeGroupSkeleton.PositionedNode local : skeleton.nodes()) {
            ResearchTreeLayout.PositionedNode atlas = layout.position(
                    local.nodeId()).orElseThrow();
            assertEquals(local.x() - localMinimumX, atlas.x() - atlasMinimumX);
            assertEquals(local.y() - localMinimumY, atlas.y() - atlasMinimumY);
        }
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), layout);
    }

    @Test
    void curatedSubsetCanReuseTheCompletePublicationSkeletonCatalog() {
        ResearchTreePublication full = ResearchTreeRedesignFixture.disclosureBoundary();
        ResearchTreePublication overview = ResearchTreeOverviewBuilder.build(full)
                .publication();

        ResearchTreeLayout layout = ResearchTreeOverviewLayoutComposer.compose(
                overview, skeletons(full), POLICY);

        assertEquals(List.of(id("public_root"), id("public_child")), layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::blueprintId)
                .toList());
        assertFalse(layout.nodes().stream().anyMatch(node ->
                node.blueprintId().getPath().startsWith("undisclosed/")));
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                overview.graph(), layout);
    }

    @Test
    void zeroPaddingPolicyStillReservesOverviewBoundaryPortals() {
        ResearchTreePublication full = ResearchTreeRedesignFixture.disclosureBoundary();
        ResearchTreePublication overview = ResearchTreeOverviewBuilder.build(full)
                .publication();
        ResearchTreeLayoutPolicy compact = new ResearchTreeLayoutPolicy(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                ResearchTreeLayout.NODE_WIDTH, 0, 0);

        ResearchTreeLayout layout = ResearchTreeOverviewLayoutComposer.compose(
                overview,
                ResearchTreeGroupSkeletonBuilder.build(full, compact),
                compact);
        int clearance = ResearchTreeLayout.PORTAL_SIZE
                + ResearchTreeLayout.PORTAL_NODE_GAP;

        assertTrue(layout.nodes().stream().allMatch(node -> node.y() >= clearance));
        assertTrue(layout.nodes().stream().allMatch(node ->
                node.y() + ResearchTreeLayout.NODE_HEIGHT + clearance <= layout.height()));
    }

    @Test
    void includedRedactedIslandPreservesOnlyItsPublishedOpaqueIdentity() {
        ResourceLocation excludedId = new ResourceLocation("phase_four", "excluded");
        ResourceLocation anonymousId = ResearchTreeGraph.redactedNodeId(1);
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        disclosedNode(0, excludedId),
                        new ResearchTreeGraph.Node(
                                1,
                                anonymousId,
                                ResearchTreeGraph.REDACTED_NAME_KEY,
                                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                                JournalVisibility.SILHOUETTE,
                                false,
                                false,
                                false,
                                0,
                                0,
                                0,
                                0,
                                ResearchTreeGraph.Availability.REDACTED)),
                List.of());
        ResearchTreePublication full = new ResearchTreePublication(
                graph,
                new ResearchTreePresentation(List.of(
                        new ResearchTreePresentation.Group(
                                new ResourceLocation("phase_four", "excluded_group"),
                                "Excluded",
                                Optional.empty(),
                                Optional.of(excludedId),
                                0,
                                ResearchTreePresentation.Kind.AUTHORED,
                                false,
                                List.of(new ResearchTreePresentation.Member(excludedId, 0, 0))),
                        new ResearchTreePresentation.Group(
                                ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID,
                                ResearchTreePresentation.UNDISCLOSED_TITLE,
                                Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                                Optional.empty(),
                                1,
                                ResearchTreePresentation.Kind.UNDISCLOSED,
                                true,
                                List.of(new ResearchTreePresentation.Member(
                                        anonymousId, 0, 0))))));
        ResearchTreePublication overview = ResearchTreeOverviewBuilder.build(full).publication();

        ResearchTreeLayout layout = ResearchTreeOverviewLayoutComposer.compose(
                overview, skeletons(full), POLICY);

        assertEquals(1, layout.nodes().size());
        assertEquals(anonymousId, layout.nodes().get(0).blueprintId());
        assertEquals(1, overview.graph().nodes().get(0).sourceOrdinal());
        assertFalse(overview.graph().nodes().get(0).visibility().revealsIdentity());
    }

    @Test
    void maximumSingleGroupRankWrapsToABoundedReadableIsland() {
        ResearchTreePublication publication = maximumSingleGroupPublication();

        ResearchTreeLayout layout = assertTimeout(
                Duration.ofSeconds(8),
                () -> ResearchTreeOverviewLayoutComposer.compose(
                        publication, skeletons(publication), POLICY));

        assertEquals(ResearchTreeGraph.MAX_NODES, layout.nodes().size());
        assertTrue(layout.width() < 10_000);
        assertTrue(layout.height() < 10_000);
        assertTrue(layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::y)
                .distinct()
                .count() > 1);
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), layout);
    }

    @Test
    void maximumValidatedClientPolicyStillFitsTheLogicalCanvasBoundary() {
        ResearchTreePublication publication = maximumSingleGroupPublication();
        ResearchTreeLayoutPolicy maximumPolicy = new ResearchTreeLayoutPolicy(
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayoutPolicy.MAX_SPACING,
                ResearchTreeLayout.MAX_DIMENSION,
                ResearchTreeLayoutPolicy.MAX_SWEEPS,
                ResearchTreeLayoutPolicy.MAX_SWEEPS);

        ResearchTreeLayout layout = assertTimeout(
                Duration.ofSeconds(15),
                () -> ResearchTreeOverviewLayoutComposer.compose(
                        publication,
                        ResearchTreeGroupSkeletonBuilder.build(
                                publication, maximumPolicy),
                        maximumPolicy));

        assertEquals(ResearchTreeGraph.MAX_NODES, layout.nodes().size());
        assertTrue(layout.width() <= ResearchTreeLayout.MAX_DIMENSION);
        assertTrue(layout.height() <= ResearchTreeLayout.MAX_DIMENSION);
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), layout);
    }

    @Test
    void maximumDisconnectedGroupsUseAspectAwareBoundedPacking() {
        ResearchTreePublication publication = maximumSeparatedGroupsPublication();

        ResearchTreeLayout layout = assertTimeout(
                Duration.ofSeconds(8),
                () -> ResearchTreeOverviewLayoutComposer.compose(
                        publication, skeletons(publication), POLICY));

        assertEquals(ResearchTreeGraph.MAX_NODES, layout.nodes().size());
        assertEquals(ResearchTreePresentation.MAX_GROUPS, layout.nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::component)
                .distinct()
                .count());
        assertTrue(layout.width() < 10_000);
        assertTrue(layout.height() < 10_000);
        assertTrue(Math.max(layout.width(), layout.height())
                < Math.min(layout.width(), layout.height()) * 2);
    }

    @Test
    void maximumSupportedInternalEdgeSetUsesBoundedIndexedValidation() {
        ResearchTreePublication publication = maximumInternalEdgesPublication();
        ResearchTreeGroupSkeletonCatalog catalog = skeletons(publication);

        ResearchTreeLayout layout = assertTimeout(
                Duration.ofSeconds(8),
                () -> ResearchTreeOverviewLayoutComposer.compose(
                        publication, catalog, POLICY));

        assertEquals(ResearchTreeGraph.MAX_EDGES, publication.graph().edges().size());
        assertEquals(publication.graph().nodes().size(), layout.nodes().size());
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), layout);
    }

    @Test
    void rankWrappingThresholdIsControlledByTheLayoutPolicy() {
        ResearchTreePublication publication =
                ResearchTreeRedesignFixture.defaultPistolProgression();
        ResearchTreeLayoutPolicy wrappedPolicy = new ResearchTreeLayoutPolicy(
                POLICY.canvasPadding(),
                POLICY.nodeGap(),
                POLICY.tierGap(),
                POLICY.componentGap(),
                POLICY.intraGroupGap(),
                POLICY.interGroupGap(),
                POLICY.groupPadding(),
                POLICY.groupHeaderHeight(),
                POLICY.portalPadding(),
                96,
                POLICY.orderingSweeps(),
                POLICY.compactionSweeps());

        ResearchTreeLayout ordinary = ResearchTreeOverviewLayoutComposer.compose(
                publication, skeletons(publication), POLICY);
        ResearchTreeLayout wrapped = ResearchTreeOverviewLayoutComposer.compose(
                publication,
                ResearchTreeGroupSkeletonBuilder.build(publication, wrappedPolicy),
                wrappedPolicy);
        Set<ResourceLocation> rankTwoIds = publication.presentation().groups().get(0)
                .members().stream()
                .filter(member -> member.rank() == 2)
                .map(ResearchTreePresentation.Member::nodeId)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(1L, ordinary.nodes().stream()
                .filter(node -> rankTwoIds.contains(node.blueprintId()))
                .map(ResearchTreeLayout.PositionedNode::y)
                .distinct()
                .count());
        assertTrue(wrapped.nodes().stream()
                .filter(node -> rankTwoIds.contains(node.blueprintId()))
                .map(ResearchTreeLayout.PositionedNode::y)
                .distinct()
                .count() > 1);
        ResourceLocation rhino = new ResourceLocation("tacz", "rhino357");
        ResourceLocation taurus = new ResourceLocation("tacz", "taurus500");
        assertTrue(ordinary.edgeRouteHint(rhino, taurus).isPresent());
        assertTrue(wrapped.edgeRouteHint(rhino, taurus).isEmpty(),
                "wrapped ranks must fall back to obstacle routing instead of stale skeleton geometry");
        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), wrapped);
    }

    @Test
    void emptyAndMismatchedInputsAreExplicit() {
        assertEquals(ResearchTreeLayout.EMPTY, ResearchTreeOverviewLayoutComposer.compose(
                ResearchTreePublication.EMPTY,
                ResearchTreeGroupSkeletonCatalog.EMPTY,
                POLICY));
        ResearchTreePublication publication = ResearchTreeRedesignFixture.connectedProgression();
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeOverviewLayoutComposer.compose(null, skeletons(publication), POLICY));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeOverviewLayoutComposer.compose(publication, null, POLICY));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeOverviewLayoutComposer.compose(
                        publication, ResearchTreeGroupSkeletonCatalog.EMPTY, POLICY));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeOverviewLayoutComposer.compose(
                        publication, skeletons(publication), null));
    }

    private static void assertGroupBlocksAreContiguous(
            ResearchTreePublication publication,
            ResearchTreeLayout layout) {
        Map<ResourceLocation, ResourceLocation> groups = publication.presentation().groups().stream()
                .flatMap(group -> group.members().stream().map(member ->
                        Map.entry(member.nodeId(), group.id())))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
        for (int tier = 0; tier < layout.tierCount(); tier++) {
            int selectedTier = tier;
            List<ResearchTreeLayout.PositionedNode> row = layout.tier(tier).stream()
                    .sorted(java.util.Comparator
                            .comparingInt(ResearchTreeLayout.PositionedNode::y)
                            .thenComparingInt(ResearchTreeLayout.PositionedNode::x))
                    .toList();
            Set<ResourceLocation> completed = new LinkedHashSet<>();
            ResourceLocation active = null;
            for (ResearchTreeLayout.PositionedNode node : row) {
                ResourceLocation group = groups.get(node.blueprintId());
                if (!group.equals(active)) {
                    if (active != null) {
                        completed.add(active);
                    }
                    assertFalse(completed.contains(group),
                            () -> "group block reappears in tier "
                                    + selectedTier + ": " + group);
                    active = group;
                }
            }
        }
    }

    private static ResearchTreeGroupSkeletonCatalog skeletons(
            ResearchTreePublication publication) {
        return ResearchTreeGroupSkeletonBuilder.build(publication, POLICY);
    }

    private static ResearchTreePublication maximumSingleGroupPublication() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(ResearchTreeGraph.MAX_NODES);
        List<ResearchTreePresentation.Member> members = new ArrayList<>(
                ResearchTreeGraph.MAX_NODES);
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            ResourceLocation nodeId = new ResourceLocation("phase_four", "maximum/" + ordinal);
            nodes.add(new ResearchTreeGraph.Node(
                    ordinal,
                    nodeId,
                    "fixture.phase_four.maximum",
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
            members.add(new ResearchTreePresentation.Member(nodeId, 0, ordinal));
        }
        return new ResearchTreePublication(
                new ResearchTreeGraph(nodes, List.of()),
                new ResearchTreePresentation(List.of(new ResearchTreePresentation.Group(
                        new ResourceLocation("phase_four", "maximum"),
                        "Maximum",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        members))));
    }

    private static ResearchTreePublication disconnectedFallbackRankPublication(int count) {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(count);
        List<ResearchTreePresentation.Member> members = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            ResourceLocation nodeId = id("addon/independent_" + ordinal);
            nodes.add(disclosedNode(ordinal, nodeId));
            members.add(new ResearchTreePresentation.Member(nodeId, 0, ordinal));
        }
        return new ResearchTreePublication(
                new ResearchTreeGraph(nodes, List.of()),
                new ResearchTreePresentation(List.of(new ResearchTreePresentation.Group(
                        id("addon_fallback"),
                        "Add-on Weapons",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        0,
                        ResearchTreePresentation.Kind.ITEM_TYPE_FALLBACK,
                        List.copyOf(members)))));
    }

    private static ResearchTreePublication maximumInternalEdgesPublication() {
        int rootCount = 1_024;
        int dependentCount = ResearchTreeGraph.MAX_EDGES
                / BlueprintResearchRule.MAX_PREREQUISITES;
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(rootCount + dependentCount);
        List<ResearchTreePresentation.Member> members = new ArrayList<>(
                rootCount + dependentCount);
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>(ResearchTreeGraph.MAX_EDGES);
        for (int root = 0; root < rootCount; root++) {
            ResourceLocation nodeId = new ResourceLocation("phase_four", "dense/root_" + root);
            nodes.add(denseNode(nodes.size(), nodeId, 0));
            members.add(new ResearchTreePresentation.Member(nodeId, 0, root));
        }
        for (int dependent = 0; dependent < dependentCount; dependent++) {
            ResourceLocation dependentId = new ResourceLocation(
                    "phase_four", "dense/dependent_" + dependent);
            nodes.add(denseNode(
                    nodes.size(),
                    dependentId,
                    BlueprintResearchRule.MAX_PREREQUISITES));
            members.add(new ResearchTreePresentation.Member(dependentId, 1, dependent));
            for (int offset = 0; offset < BlueprintResearchRule.MAX_PREREQUISITES; offset++) {
                ResourceLocation prerequisiteId = nodes.get(
                        (dependent + offset) % rootCount).blueprintId();
                edges.add(new ResearchTreeGraph.Edge(prerequisiteId, dependentId));
            }
        }
        ResearchTreeGraph graph = new ResearchTreeGraph(nodes, edges);
        return new ResearchTreePublication(
                graph,
                new ResearchTreePresentation(List.of(new ResearchTreePresentation.Group(
                        new ResourceLocation("phase_four", "dense"),
                        "Dense",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.copyOf(members)))));
    }

    private static ResearchTreeGraph.Node denseNode(
            int ordinal,
            ResourceLocation nodeId,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                nodeId,
                "fixture.phase_four.dense",
                "rifle",
                new ResourceLocation("minecraft", "paper"),
                JournalVisibility.FULL,
                false,
                false,
                true,
                1,
                0,
                prerequisiteCount,
                0,
                ResearchTreeGraph.Availability.AVAILABLE);
    }

    private static ResearchTreeGraph.Node disclosedNode(
            int ordinal,
            ResourceLocation nodeId) {
        return new ResearchTreeGraph.Node(
                ordinal,
                nodeId,
                "fixture.phase_four.disclosed",
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
                ResearchTreeGraph.Availability.AVAILABLE);
    }

    private static ResearchTreePublication maximumSeparatedGroupsPublication() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(ResearchTreeGraph.MAX_NODES);
        List<ResearchTreePresentation.Group> groups = new ArrayList<>(
                ResearchTreePresentation.MAX_GROUPS);
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            ResourceLocation nodeId = new ResourceLocation(
                    "phase_four", "separated/" + ordinal);
            ResearchTreeGraph.Node node = disclosedNode(ordinal, nodeId);
            nodes.add(node);
            groups.add(new ResearchTreePresentation.Group(
                    new ResourceLocation("phase_four", "separated_group/" + ordinal),
                    "Separated " + ordinal,
                    Optional.empty(),
                    Optional.of(nodeId),
                    ordinal,
                    ResearchTreePresentation.Kind.AUTHORED,
                    List.of(new ResearchTreePresentation.Member(nodeId, 0, 0))));
        }
        return new ResearchTreePublication(
                new ResearchTreeGraph(nodes, List.of()),
                new ResearchTreePresentation(groups));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("phase_zero", path);
    }
}
