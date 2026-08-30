package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTechTreeLayoutEngineTest {
    @Test
    void laysOutEveryDomainWithoutAllocatingEmptyLegacyBands() {
        ResearchTechTreeProjectionCatalog projections =
                ResearchTechTreeProjectionBuilder.build(
                        ResearchTechTreeClientFixture.publication());

        ResearchTechTreeLayoutCatalog layouts =
                ResearchTechTreeLayoutEngine.layoutCatalog(
                        projections, ResearchTechTreeLayoutPolicy.DEFAULT);

        assertTrue(layouts.available());
        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS, Domain.AMMO),
                layouts.domains());
        assertTrue(layouts.matches(projections));
        ResearchTechTreeLayout weapons = layouts.layout(Domain.WEAPONS).orElseThrow();
        assertEquals(List.of(Tier.STARTER, Tier.BASIC),
                weapons.tiers().stream().map(ResearchTechTreeLayout.TierBand::tier).toList());
        for (int ordinal = 1; ordinal < weapons.tiers().size(); ordinal++) {
            assertTrue(weapons.tiers().get(ordinal).y()
                    < weapons.tiers().get(ordinal - 1).y());
        }
        assertTrue(weapons.graphLayout().groupRegions().isEmpty());
        assertTrue(weapons.graphLayout().categoryLanes().isEmpty());
        ResearchTreeLayout.PositionedNode root = weapons.graphLayout()
                .position(ResearchTechTreeClientFixture.WEAPON_ROOT).orElseThrow();
        ResearchTreeLayout.PositionedNode upgrade = weapons.graphLayout()
                .position(ResearchTechTreeClientFixture.WEAPON_UPGRADE).orElseThrow();
        assertTrue(root.y() > upgrade.y());
        assertEquals(Tier.STARTER.ordinal(), root.tier());
        assertEquals(Tier.BASIC.ordinal(), upgrade.tier());
        ResearchTreeEdgeIndex edges = ResearchTreeEdgeIndex.create(
                projections.projection(Domain.WEAPONS).orElseThrow().graph(),
                weapons.graphLayout(),
                ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW);
        assertEquals(1, edges.visible(
                0, 0, weapons.graphLayout().width(), weapons.graphLayout().height()).size());

        ResearchTechTreeLayout.BoundaryPortal requirement = weapons.portal(
                ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                Domain.AMMO,
                ResearchTechTreeProjection.Direction.REQUIREMENT).orElseThrow();
        ResearchTechTreeLayout.BoundaryPortal unlock = weapons.portal(
                ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                Domain.ATTACHMENTS,
                ResearchTechTreeProjection.Direction.UNLOCK).orElseThrow();
        assertTrue(requirement.y() > upgrade.y() + ResearchTreeLayout.NODE_HEIGHT);
        assertTrue(unlock.y() + ResearchTreeLayout.PORTAL_SIZE < upgrade.y());
        assertEquals(1, requirement.target().connectionCount());
        assertEquals(1, unlock.target().connectionCount());
        assertEquals(ResearchTechTreeClientFixture.AMMO,
                requirement.target().primaryLink().remoteNodeId());
        assertEquals(ResearchTechTreeClientFixture.SCOPE,
                unlock.target().primaryLink().remoteNodeId());
    }

    @Test
    void expandsSameTierPrerequisiteChainsIntoReadableVerticalSubsteps() {
        ResearchTechTreeProjection weapons = ResearchTechTreeProjectionBuilder.build(
                ResearchTechTreeClientFixture.publicationWithSameTierWeaponChain())
                .projection(Domain.WEAPONS).orElseThrow();

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                weapons, ResearchTechTreeLayoutPolicy.DEFAULT);

        ResearchTreeLayout.PositionedNode root = layout.graphLayout()
                .position(ResearchTechTreeClientFixture.WEAPON_ROOT).orElseThrow();
        ResearchTreeLayout.PositionedNode upgrade = layout.graphLayout()
                .position(ResearchTechTreeClientFixture.WEAPON_UPGRADE).orElseThrow();
        assertEquals(0, root.tier());
        assertEquals(1, upgrade.tier());
        assertTrue(root.y() > upgrade.y());
        assertEquals(List.of(Tier.STARTER), layout.tiers().stream()
                .map(ResearchTechTreeLayout.TierBand::tier).toList());
        assertTrue(layout.tier(Tier.STARTER).orElseThrow().height()
                > ResearchTreeLayout.NODE_HEIGHT);
        assertTrue(layout.tier(Tier.BASIC).isEmpty());
        assertEquals(1, ResearchTreeEdgeIndex.create(
                weapons.graph(),
                layout.graphLayout(),
                ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW)
                .visible(0, 0, layout.graphLayout().width(), layout.graphLayout().height())
                .size());
    }

    @Test
    void preservesSparseAutomaticLevelsWithoutInventingPrerequisiteEdges() {
        ResourceLocation lower = id("test:auto_lower");
        ResourceLocation upper = id("test:auto_upper");
        ResourceLocation laneId = id("test:auto_lane");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, lower), node(1, upper)), List.of());
        List<ResearchTechTreePresentation.Member> members = List.of(
                new ResearchTechTreePresentation.Member(
                        lower, Tier.BASIC, 0, 4_000_000_000L,
                        PlacementOrigin.AUTOMATIC, Optional.empty()),
                new ResearchTechTreePresentation.Member(
                        upper, Tier.BASIC, 4, 5_000_000_000L,
                        PlacementOrigin.AUTOMATIC, Optional.empty()));
        ResearchTechTreePresentation.DomainView presentation =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.of(lower),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                laneId, "Automatic", Optional.empty(), Optional.of(lower), 0, members)));
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        placements.put(lower, new ResearchTechTreeProjection.Placement(
                lower, laneId, Tier.BASIC, 0, 0, 4_000_000_000L,
                PlacementOrigin.AUTOMATIC, Optional.empty()));
        placements.put(upper, new ResearchTechTreeProjection.Placement(
                upper, laneId, Tier.BASIC, 4, 0, 5_000_000_000L,
                PlacementOrigin.AUTOMATIC, Optional.empty()));
        ResearchTechTreeProjection projection = new ResearchTechTreeProjection(
                Domain.WEAPONS, presentation, graph, placements, List.of());

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);

        assertTrue(layout.graphLayout().position(lower).orElseThrow().y()
                > layout.graphLayout().position(upper).orElseThrow().y());
        assertTrue(projection.graph().edges().isEmpty());
        assertEquals(List.of(Tier.BASIC), layout.tiers().stream()
                .map(ResearchTechTreeLayout.TierBand::tier).toList());
        assertTrue(layout.tier(Tier.BASIC).orElseThrow().height()
                > ResearchTreeLayout.NODE_HEIGHT);
        assertTrue(layout.tier(Tier.STARTER).isEmpty());
    }

    @Test
    void rankControlsVerticalOrderWhenLegacyBandLabelsRunBackward() {
        ResourceLocation root = id("test:ranked_root");
        ResourceLocation dependent = id("test:ranked_dependent");
        ResourceLocation laneId = id("test:ranked_lane");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, root), node(1, dependent, 1)),
                List.of(new ResearchTreeGraph.Edge(root, dependent)));
        Optional<ResourceLocation> apexBand = Optional.of(
                ResearchTechTreeContract.legacyBandId(Tier.APEX));
        Optional<ResourceLocation> starterBand = Optional.of(
                ResearchTechTreeContract.legacyBandId(Tier.STARTER));
        ResearchTechTreePresentation.DomainView presentation =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.of(root),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                laneId,
                                "Ranked",
                                Optional.empty(),
                                Optional.of(root),
                                0,
                                List.of(
                                        new ResearchTechTreePresentation.Member(
                                                root, 73, 900, apexBand,
                                                PlacementOrigin.EXACT, Optional.empty()),
                                        new ResearchTechTreePresentation.Member(
                                                dependent, 74, 1, starterBand,
                                                PlacementOrigin.EXACT, Optional.empty())))));
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        placements.put(root, new ResearchTechTreeProjection.Placement(
                root, laneId, 73, 0, 900, apexBand,
                PlacementOrigin.EXACT, Optional.empty()));
        placements.put(dependent, new ResearchTechTreeProjection.Placement(
                dependent, laneId, 74, 0, 1, starterBand,
                PlacementOrigin.EXACT, Optional.empty()));

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                new ResearchTechTreeProjection(
                        Domain.WEAPONS, presentation, graph, placements, List.of()),
                ResearchTechTreeLayoutPolicy.DEFAULT);

        assertTrue(layout.graphLayout().position(root).orElseThrow().y()
                > layout.graphLayout().position(dependent).orElseThrow().y());
        assertTrue(layout.tiers().isEmpty(),
                "incoherent legacy labels must not become positional authority");
    }

    @Test
    void rendersAnyNumberOfCustomBandsAndOmitsUnusedBandGaps() {
        ResourceLocation laneId = id("test:custom_band_lane");
        ResourceLocation early = id("test:early");
        ResourceLocation late = id("test:late");
        List<ResourceLocation> ids = List.of(
                id("test:custom_0"),
                id("test:custom_1"),
                id("test:custom_2"),
                id("test:custom_3"));
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>();
        List<ResearchTechTreePresentation.Member> members = new ArrayList<>();
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            ResourceLocation nodeId = ids.get(index);
            Optional<ResourceLocation> band = Optional.of(index < 2 ? early : late);
            nodes.add(node(index, nodeId, index == 0 ? 0 : 1));
            if (index > 0) {
                edges.add(new ResearchTreeGraph.Edge(ids.get(index - 1), nodeId));
            }
            members.add(new ResearchTechTreePresentation.Member(
                    nodeId,
                    index,
                    index,
                    band,
                    PlacementOrigin.AUTOMATIC,
                    Optional.empty()));
            placements.put(nodeId, new ResearchTechTreeProjection.Placement(
                    nodeId,
                    laneId,
                    index,
                    0,
                    index,
                    band,
                    PlacementOrigin.AUTOMATIC,
                    Optional.empty()));
        }
        ResearchTechTreePresentation.DomainView domain =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.of(ids.get(0)),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                laneId,
                                "Custom bands",
                                Optional.empty(),
                                Optional.of(ids.get(0)),
                                0,
                                members)));
        ResearchTechTreeProjection projection = new ResearchTechTreeProjection(
                Domain.WEAPONS,
                domain,
                new ResearchTreeGraph(nodes, edges),
                placements,
                List.of(),
                List.of(
                        new ResearchTechTreePresentation.BandLabel(
                                early, "Early", Optional.empty(),
                                Optional.of(0x224466), Optional.of(ids.get(0))),
                        new ResearchTechTreePresentation.BandLabel(
                                id("test:unused"), "Unused", Optional.empty()),
                        new ResearchTechTreePresentation.BandLabel(
                                late, "Late", Optional.empty())));

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);

        assertTrue(layout.tiers().isEmpty());
        assertEquals(List.of(early, late), layout.bands().stream()
                .map(ResearchTechTreeLayout.ProgressionBand::id).toList());
        assertEquals(List.of(0, 2), layout.bands().stream()
                .map(ResearchTechTreeLayout.ProgressionBand::index).toList());
        assertEquals(Optional.of(0x224466), layout.bands().get(0).color());
        assertEquals(Optional.of(ids.get(0)), layout.bands().get(0).iconNodeId());
        assertTrue(layout.bands().get(1).y() < layout.bands().get(0).y());
    }

    @Test
    void usesAuthoredLaneOrderAsATieBreakWithoutCreatingLaneRegions() {
        ResearchTechTreeProjection projection = twoLaneProjection();

        ResearchTechTreeLayout first = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);
        ResearchTechTreeLayout second = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);

        assertEquals(first, second);
        assertTrue(first.graphLayout().groupRegions().isEmpty());
        assertTrue(first.graphLayout().categoryLanes().isEmpty());
        assertTrue(first.hasDisconnectedComponents());
        assertTrue(first.diagnostics().disconnected());
        assertEquals(2, first.layoutComponents().size());
        assertEquals(2, first.diagnostics().maximumNodesInRow());
        assertEquals(List.of(0, 1), first.graphLayout().nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::component).toList());
        assertEquals(
                first.graphLayout().position(id("test:first")).orElseThrow().y(),
                first.graphLayout().position(id("test:second")).orElseThrow().y(),
                "disconnected diagnostics must not disguise components with grid packing");
        assertTrue(first.graphLayout().position(id("test:first")).orElseThrow().x()
                < first.graphLayout().position(id("test:second")).orElseThrow().x());
    }

    @Test
    void rejectsOverlappingTierBandsEvenWhenTheirOriginsRemainOrdered() {
        ResearchTechTreeProjection projection = ResearchTechTreeProjectionBuilder.build(
                ResearchTechTreeClientFixture.publication())
                .projection(Domain.WEAPONS).orElseThrow();
        ResearchTechTreeLayout valid = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);
        List<ResearchTechTreeLayout.TierBand> overlapping =
                new ArrayList<>(valid.tiers());
        ResearchTechTreeLayout.TierBand starter = valid.tier(Tier.STARTER).orElseThrow();
        ResearchTechTreeLayout.TierBand basic = valid.tier(Tier.BASIC).orElseThrow();
        overlapping.set(Tier.BASIC.ordinal(), new ResearchTechTreeLayout.TierBand(
                Tier.BASIC,
                starter.y() - 1,
                basic.height()));

        assertThrows(IllegalArgumentException.class, () -> new ResearchTechTreeLayout(
                valid.domain(),
                valid.graphLayout(),
                overlapping,
                valid.portals()));
    }

    @Test
    void collapsesSeveralRemoteNodesInOneDomainIntoOnePortalTarget() {
        ResearchTechTreeProjection original = ResearchTechTreeProjectionBuilder.build(
                ResearchTechTreeClientFixture.publication())
                .projection(Domain.WEAPONS).orElseThrow();
        List<ResearchTechTreeProjection.BoundaryLink> links =
                new ArrayList<>(original.boundaryLinks());
        links.add(new ResearchTechTreeProjection.BoundaryLink(
                ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                id("test:second_scope"),
                Domain.ATTACHMENTS,
                ResearchTechTreeProjection.Direction.UNLOCK));
        ResearchTechTreeProjection expanded = new ResearchTechTreeProjection(
                original.domain(),
                original.presentation(),
                original.graph(),
                original.placements(),
                links);

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                expanded, ResearchTechTreeLayoutPolicy.DEFAULT);

        ResearchTechTreeLayout.BoundaryPortal portal = layout.portal(
                ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                Domain.ATTACHMENTS,
                ResearchTechTreeProjection.Direction.UNLOCK).orElseThrow();
        assertEquals(2, portal.target().connectionCount());
        assertEquals(2, layout.portals().size());
    }

    @Test
    void rejectsAnInvertedSameTierAuthoredOrder() {
        ResearchTechTreeProjection valid = ResearchTechTreeProjectionBuilder.build(
                ResearchTechTreeClientFixture.publicationWithSameTierWeaponChain())
                .projection(Domain.WEAPONS).orElseThrow();
        ResearchTechTreePresentation.LaneView originalLane =
                valid.presentation().lanes().get(0);
        ResearchTechTreePresentation.Member rootMember = originalLane.members().get(0);
        ResearchTechTreePresentation.Member upgradeMember = originalLane.members().get(1);
        ResearchTechTreePresentation.LaneView invalidLane =
                new ResearchTechTreePresentation.LaneView(
                        originalLane.id(),
                        originalLane.title(),
                        originalLane.translationKey(),
                        originalLane.iconNodeId(),
                        originalLane.order(),
                        List.of(
                                rootMember,
                                new ResearchTechTreePresentation.Member(
                                        upgradeMember.nodeId(),
                                        upgradeMember.tier(),
                                        0,
                                        upgradeMember.rating())));
        ResearchTechTreePresentation.DomainView invalidPresentation =
                new ResearchTechTreePresentation.DomainView(
                        valid.presentation().domain(),
                        valid.presentation().title(),
                        valid.presentation().translationKey(),
                        valid.presentation().iconNodeId(),
                        List.of(invalidLane));
        Map<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>(valid.placements());
        ResearchTechTreeProjection.Placement upgrade = placements.get(
                ResearchTechTreeClientFixture.WEAPON_UPGRADE);
        placements.put(ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                new ResearchTechTreeProjection.Placement(
                        upgrade.nodeId(), upgrade.laneId(), upgrade.tier(),
                        upgrade.laneOrder(), 0, upgrade.rating()));

        ResearchTechTreeProjection invalid = new ResearchTechTreeProjection(
                valid.domain(), invalidPresentation, valid.graph(), placements,
                valid.boundaryLinks());

        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeLayoutEngine.layout(
                        invalid, ResearchTechTreeLayoutPolicy.DEFAULT));
    }

    @Test
    void balancesCrowdedSemanticLevelsAcrossPresentationOnlyWrapRows() {
        ResearchTechTreeProjection projection = levelRowsProjection(41, 0);

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);

        Map<Integer, Long> countsByY = layout.graphLayout().nodes().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ResearchTreeLayout.PositionedNode::y,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        assertEquals(5, countsByY.size());
        assertEquals(List.of(8L, 8L, 8L, 8L, 9L),
                countsByY.values().stream().sorted().toList());
        assertTrue(projection.graph().edges().isEmpty());
        int horizontalPadding = ResearchTechTreeLayoutPolicy.DEFAULT.canvasPadding();
        assertTrue(layout.graphLayout().width()
                <= ResearchTechTreeLayoutPolicy.DEFAULT.maxRankBlockWidth()
                        + horizontalPadding * 2);
    }

    @Test
    void allocatesPortalClearanceOnlyOnRowsThatOwnActualPortals() {
        ResearchTechTreeProjection linked = ResearchTechTreeProjectionBuilder.build(
                ResearchTechTreeClientFixture.publication())
                .projection(Domain.WEAPONS).orElseThrow();
        ResearchTechTreeProjection unlinked = new ResearchTechTreeProjection(
                linked.domain(),
                linked.presentation(),
                linked.graph(),
                linked.placements(),
                List.of(),
                linked.bands(),
                linked.maxNodesPerLayer());

        ResearchTechTreeLayout linkedLayout = ResearchTechTreeLayoutEngine.layout(
                linked, ResearchTechTreeLayoutPolicy.DEFAULT);
        ResearchTechTreeLayout unlinkedLayout = ResearchTechTreeLayoutEngine.layout(
                unlinked, ResearchTechTreeLayoutPolicy.DEFAULT);

        assertEquals(
                2 * ResearchTechTreeLayoutPolicy.DEFAULT.portalClearance(),
                linkedLayout.graphLayout().height()
                        - unlinkedLayout.graphLayout().height());
        assertEquals(ResearchTreeLayout.NODE_HEIGHT,
                unlinkedLayout.tier(Tier.STARTER).orElseThrow().height());
        assertEquals(ResearchTreeLayout.NODE_HEIGHT,
                unlinkedLayout.tier(Tier.BASIC).orElseThrow().height());
    }

    @Test
    void supportedCompactAndFullscreenWidthsKeepTenNodeRowsReadable() {
        ResearchTechTreeProjection base = levelRowsProjection(10, 0);
        ResearchTechTreeProjection ten = new ResearchTechTreeProjection(
                base.domain(),
                base.presentation(),
                base.graph(),
                base.placements(),
                base.boundaryLinks(),
                base.bands(),
                10);

        for (int[] viewportSize : List.of(
                new int[] {294, 116},
                new int[] {800, 480})) {
            ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                    ten,
                    ResearchTechTreeLayoutPolicy.DEFAULT,
                    viewportSize[0]);
            assertEquals(1, layout.graphLayout().nodes().stream()
                    .map(ResearchTreeLayout.PositionedNode::y).distinct().count());
            ResearchTreeViewport viewport = new ResearchTreeViewport();
            viewport.configure(
                    viewportSize[0],
                    viewportSize[1],
                    layout.graphLayout().width(),
                    layout.graphLayout().height());
            viewport.fitReadable(
                    ResearchTreePresentationContract.MIN_READABLE_OVERVIEW_FIT_SCALE);
            assertTrue(viewport.scale()
                    > ResearchTreePresentationContract.MIN_READABLE_OVERVIEW_FIT_SCALE);
            assertTrue(Math.max(
                    ResearchTreeLayout.NODE_WIDTH * viewport.scale(),
                    ResearchTreeCanvas.MIN_NODE_HIT_SIZE)
                    >= ResearchTreeCanvas.MIN_NODE_HIT_SIZE);
        }
    }

    @Test
    void unusuallyNarrowViewportWrapsWithoutExceedingTreeCapacity() {
        ResearchTechTreeProjection base = levelRowsProjection(10, 0);
        ResearchTechTreeProjection ten = new ResearchTechTreeProjection(
                base.domain(), base.presentation(), base.graph(), base.placements(),
                base.boundaryLinks(), base.bands(), 10);

        ResearchTechTreeLayout narrow = ResearchTechTreeLayoutEngine.layout(
                ten, ResearchTechTreeLayoutPolicy.DEFAULT, 120);
        Map<Integer, Long> countsByY = narrow.graphLayout().nodes().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ResearchTreeLayout.PositionedNode::y,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));

        assertEquals(List.of(3L, 3L, 4L),
                countsByY.values().stream().sorted().toList());
        assertTrue(countsByY.values().stream().allMatch(count -> count <= 10));
    }

    @Test
    void longEdgesRouteWithoutWideningIntermediateRanks() {
        List<Integer> ranks = new ArrayList<>();
        ranks.add(0);
        for (int index = 0; index < 9; index++) {
            ranks.add(1);
        }
        ranks.add(2);
        ResourceLocation root = id("test:long/0");
        ResourceLocation dependent = id("test:long/10");
        ResearchTreeGraph.Edge edge = new ResearchTreeGraph.Edge(root, dependent);
        ResearchTechTreeProjection withLongEdge = rankedProjection(
                ranks, List.of(edge), 9, "long");
        ResearchTechTreeProjection withoutLongEdge = rankedProjection(
                ranks, List.of(), 9, "long");

        ResearchTechTreeLayout routed = ResearchTechTreeLayoutEngine.layout(
                withLongEdge, ResearchTechTreeLayoutPolicy.DEFAULT);
        ResearchTechTreeLayout baseline = ResearchTechTreeLayoutEngine.layout(
                withoutLongEdge, ResearchTechTreeLayoutPolicy.DEFAULT);

        assertEquals(baseline.graphLayout().width(), routed.graphLayout().width());
        assertTrue(routed.graphLayout().edgeRouteHints().isEmpty(),
                "long edges must not add width-bearing virtual rank vertices");
        assertEquals(1, ResearchTreeEdgeIndex.create(
                withLongEdge.graph(),
                routed.graphLayout(),
                ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW)
                .visible(0, 0, routed.graphLayout().width(),
                        routed.graphLayout().height()).size());
    }

    @Test
    void barycentricSweepsRemoveSimpleBranchCrossingDeterministically() {
        ResearchTreeGraph.Edge first = new ResearchTreeGraph.Edge(
                id("test:cross/0"), id("test:cross/3"));
        ResearchTreeGraph.Edge second = new ResearchTreeGraph.Edge(
                id("test:cross/1"), id("test:cross/2"));
        ResearchTechTreeProjection projection = rankedProjection(
                List.of(0, 0, 1, 1), List.of(first, second), 9, "cross");

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);
        ResearchTechTreeLayout repeated = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);

        assertEquals(layout, repeated);
        assertEquals(0, crossingCount(layout.graphLayout(), List.of(first, second)));
    }

    @Test
    void treeOwnedCapacityBoundsClientWrapRowsAtEightAndTen() {
        ResearchTechTreeProjection base = levelRowsProjection(9, 0);
        ResearchTechTreeProjection eight = new ResearchTechTreeProjection(
                base.domain(),
                base.presentation(),
                base.graph(),
                base.placements(),
                base.boundaryLinks(),
                base.bands(),
                8);
        ResearchTechTreeProjection ten = new ResearchTechTreeProjection(
                base.domain(),
                base.presentation(),
                base.graph(),
                base.placements(),
                base.boundaryLinks(),
                base.bands(),
                10);

        assertEquals(2, distinctRows(eight));
        assertEquals(1, distinctRows(ten));
    }

    @Test
    void centersSparseRowsInsteadOfLeavingThemAtTheCanvasEdge() {
        ResearchTechTreeProjection projection = levelRowsProjection(20, 1);

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);

        List<ResearchTreeLayout.PositionedNode> lower = layout.graphLayout().nodes().stream()
                .filter(node -> projection.placement(node.blueprintId()).orElseThrow().level() == 0)
                .toList();
        ResearchTreeLayout.PositionedNode upper = layout.graphLayout().nodes().stream()
                .filter(node -> projection.placement(node.blueprintId()).orElseThrow().level() == 1)
                .findFirst()
                .orElseThrow();
        int lowerCenter = (lower.stream().mapToInt(ResearchTreeLayout.PositionedNode::centerX)
                .min().orElseThrow()
                + lower.stream().mapToInt(ResearchTreeLayout.PositionedNode::centerX)
                        .max().orElseThrow()) / 2;
        assertTrue(Math.abs(lowerCenter - upper.centerX()) <= 1);
    }

    @Test
    void compactsSparseOuterBranchVoidsAndOpensProgressiveUpperGutters() {
        ResearchTechTreeProjection projection = branchingProjection();

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);
        ResearchTechTreeLayout repeated = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);

        assertEquals(layout, repeated);
        int nodePitch = ResearchTreeLayout.NODE_WIDTH
                + ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap();
        int maximumStep = nodePitch
                + ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap()
                + ResearchTreeLayout.NODE_WIDTH / 2;
        for (int rank = 0; rank < 6; rank++) {
            int currentRank = rank;
            List<ResearchTreeLayout.PositionedNode> row = nodesAtSemanticRank(
                    projection, layout.graphLayout(), currentRank);
            for (int index = 1; index < row.size(); index++) {
                assertTrue(row.get(index).centerX() - row.get(index - 1).centerX()
                        <= maximumStep,
                        () -> "semantic rank " + currentRank
                                + " retained an oversized void");
            }
        }

        List<ResearchTreeLayout.PositionedNode> shared = nodesAtSemanticRank(
                projection, layout.graphLayout(), 1);
        assertTrue(maximumCenterStep(shared) <= nodePitch);
        List<ResearchTreeLayout.PositionedNode> split = nodesAtSemanticRank(
                projection, layout.graphLayout(), 2);
        assertTrue(maximumCenterStep(split)
                >= nodePitch + ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap() / 2,
                "the first specialization rank should visibly separate branch families");

        List<ResearchTreeLayout.PositionedNode> anchor = nodesAtSemanticRank(
                projection, layout.graphLayout(), 1);
        ResearchTreeLayout.PositionedNode sparseTop = nodesAtSemanticRank(
                projection, layout.graphLayout(), 5).get(0);
        int anchorLeft = anchor.stream().mapToInt(
                ResearchTreeLayout.PositionedNode::x).min().orElseThrow();
        int anchorRight = anchor.stream().mapToInt(node ->
                node.x() + ResearchTreeLayout.NODE_WIDTH).max().orElseThrow();
        int outerVoid = Math.max(
                0,
                Math.max(
                        anchorLeft - (sparseTop.x() + ResearchTreeLayout.NODE_WIDTH),
                        sparseTop.x() - anchorRight));
        assertTrue(outerVoid <= 2 * ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap(),
                "a thin outer branch must not expand the full canvas with empty space");
    }

    @Test
    void branchGuttersRespondToMeasuredFanOutAndCrossingPressure() {
        assertEquals(14, ResearchTechTreeBranchCompactor
                .evidenceAdjustedInitialGutter(18, 2, false));
        assertEquals(17, ResearchTechTreeBranchCompactor
                .evidenceAdjustedInitialGutter(18, 3, false));
        assertEquals(17, ResearchTechTreeBranchCompactor
                .evidenceAdjustedInitialGutter(18, 2, true));
        assertEquals(18, ResearchTechTreeBranchCompactor
                .evidenceAdjustedInitialGutter(18, 3, true));
    }

    @Test
    void canonicalTwoFamilyTreeReceivesAGutterWithoutASeedCountThreshold() {
        ResearchTechTreeProjection projection = twoFamilyProjection();

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);
        int nodePitch = ResearchTreeLayout.NODE_WIDTH
                + ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap();
        int step = maximumCenterStep(nodesAtSemanticRank(
                projection, layout.graphLayout(), 0));
        int matureStep = maximumCenterStep(nodesAtSemanticRank(
                projection, layout.graphLayout(), 1));

        assertTrue(step > nodePitch);
        assertTrue(step <= nodePitch
                + ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap()
                + ResearchTreeLayout.NODE_WIDTH / 2);
        assertEquals(
                nodePitch
                        + ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap()
                        + ResearchTreeLayout.NODE_WIDTH / 2,
                matureStep,
                "a mature split should retain an overview-visible family envelope");
    }

    @Test
    void authoredNodesBetweenAutomaticFamiliesPreserveOneBoundedGutter() {
        ResearchTechTreeProjection projection = mixedFamilyProjection();

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);
        List<ResearchTreeLayout.PositionedNode> row = nodesAtSemanticRank(
                projection, layout.graphLayout(), 0);
        int nodePitch = ResearchTreeLayout.NODE_WIDTH
                + ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap();
        List<Integer> steps = java.util.stream.IntStream.range(1, row.size())
                .map(index -> row.get(index).centerX() - row.get(index - 1).centerX())
                .boxed()
                .toList();

        assertEquals(1L, steps.stream().filter(step -> step > nodePitch).count(),
                "a neutral authored run should preserve one family boundary");
        int totalGutter = steps.stream().mapToInt(Integer::intValue).sum()
                - nodePitch * (row.size() - 1);
        assertTrue(totalGutter >= ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap() / 2);
        assertTrue(totalGutter <= ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap()
                + ResearchTreeLayout.NODE_WIDTH / 2);
    }

    @Test
    void matureFamiliesRemainContiguousWhenBarycentersWouldInterleaveThem() {
        List<Integer> ranks = new ArrayList<>(List.of(0, 0));
        for (int index = 0; index < 8; index++) {
            ranks.add(1);
        }
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>();
        for (int ordinal = 2; ordinal < 10; ordinal++) {
            ResourceLocation target = id("test:interleaved_family/" + ordinal);
            edges.add(new ResearchTreeGraph.Edge(
                    id("test:interleaved_family/0"), target));
            edges.add(new ResearchTreeGraph.Edge(
                    id("test:interleaved_family/1"), target));
        }
        ResearchTechTreeProjection projection = rankedProjection(
                ranks,
                edges,
                10,
                "interleaved_family",
                true,
                (ordinal, rank) -> rank == 0 ? ordinal : (ordinal - 2) % 2,
                1,
                1);

        ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                projection, ResearchTechTreeLayoutPolicy.DEFAULT);
        List<ResearchTreeLayout.PositionedNode> row = nodesAtSemanticRank(
                projection, layout.graphLayout(), 1);
        List<Integer> families = row.stream().map(node -> projection
                .placement(node.blueprintId()).orElseThrow()
                .automaticBranch().orElseThrow().branchIndex()).toList();
        long familyRuns = java.util.stream.IntStream.range(0, families.size())
                .filter(index -> index == 0
                        || !families.get(index).equals(families.get(index - 1)))
                .count();

        assertEquals(2L, familyRuns);
        assertEquals(2L, families.stream().distinct().count());
        int nodePitch = ResearchTreeLayout.NODE_WIDTH
                + ResearchTechTreeLayoutPolicy.DEFAULT.nodeGap();
        assertEquals(1L, java.util.stream.IntStream.range(1, row.size())
                .filter(index -> row.get(index).centerX()
                        - row.get(index - 1).centerX() > nodePitch)
                .count());
    }

    @Test
    void mixedFiftyThreeAuthoredAndTwoHundredThirtyFourAutomaticCatalogStaysBounded() {
        assertTimeout(Duration.ofSeconds(10), () -> {
            ResearchTechTreeProjection projection = mixedCatalogProjection();
            ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                    projection, ResearchTechTreeLayoutPolicy.DEFAULT);
            ResearchTechTreeLayout repeated = ResearchTechTreeLayoutEngine.layout(
                    projection, ResearchTechTreeLayoutPolicy.DEFAULT);

            assertEquals(layout, repeated);
            assertEquals(287, layout.graphLayout().nodes().size());
            assertTrue(layout.graphLayout().width() <= ResearchTreeLayout.MAX_DIMENSION);
            assertTrue(layout.graphLayout().height() <= ResearchTreeLayout.MAX_DIMENSION);
            Map<Integer, Long> visualWidths = layout.graphLayout().nodes().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            ResearchTreeLayout.PositionedNode::y,
                            java.util.stream.Collectors.counting()));
            assertTrue(visualWidths.values().stream().allMatch(width -> width <= 20));

            for (int rank = 3; rank <= 14; rank++) {
                List<Integer> families = nodesAtSemanticRank(
                        projection, layout.graphLayout(), rank).stream()
                        .map(node -> projection.placement(node.blueprintId()).orElseThrow()
                                .automaticBranch().orElseThrow().branchIndex())
                        .toList();
                long runs = java.util.stream.IntStream.range(0, families.size())
                        .filter(index -> index == 0
                                || !families.get(index).equals(families.get(index - 1)))
                        .count();
                assertEquals(families.stream().distinct().count(), runs,
                        "each mature family must occupy one coherent visual block");
            }
        });
    }

    @Test
    void maximumPublicDomainRemainsBoundedAndDeterministic() {
        assertTimeout(Duration.ofSeconds(15), () -> {
            ResearchTechTreeProjection projection = maximumProjection();
            ResearchTechTreeLayout layout = ResearchTechTreeLayoutEngine.layout(
                    projection, ResearchTechTreeLayoutPolicy.DEFAULT);

            assertEquals(ResearchTreeGraph.MAX_NODES, layout.graphLayout().nodes().size());
            assertTrue(layout.graphLayout().width() <= ResearchTreeLayout.MAX_DIMENSION);
            assertTrue(layout.graphLayout().height() <= ResearchTreeLayout.MAX_DIMENSION);
            assertTrue(layout.graphLayout().groupRegions().isEmpty());
            assertTrue(layout.portals().isEmpty());
        });
    }

    private static ResearchTechTreeProjection twoLaneProjection() {
        ResourceLocation first = id("test:first");
        ResourceLocation second = id("test:second");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, first), node(1, second)), List.of());
        ResourceLocation laneA = id("test:lane/a");
        ResourceLocation laneB = id("test:lane/b");
        ResearchTechTreePresentation.DomainView presentation =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.of(first),
                        List.of(
                                lane(laneA, 10, first, Tier.STARTER),
                                lane(laneB, 20, second, Tier.STARTER)));
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        placements.put(first, new ResearchTechTreeProjection.Placement(
                first, laneA, Tier.STARTER, 10, 0, Optional.empty()));
        placements.put(second, new ResearchTechTreeProjection.Placement(
                second, laneB, Tier.STARTER, 20, 0, Optional.empty()));
        return new ResearchTechTreeProjection(
                Domain.WEAPONS, presentation, graph, placements, List.of());
    }

    private static ResearchTechTreeProjection maximumProjection() {
        ResourceLocation laneId = id("test:max_lane");
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(ResearchTreeGraph.MAX_NODES);
        List<ResearchTechTreePresentation.Member> members =
                new ArrayList<>(ResearchTreeGraph.MAX_NODES);
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            ResourceLocation nodeId = id("test:max/" + ordinal);
            nodes.add(node(ordinal, nodeId));
            members.add(new ResearchTechTreePresentation.Member(
                    nodeId, Tier.STARTER, ordinal, Optional.empty()));
            placements.put(nodeId, new ResearchTechTreeProjection.Placement(
                    nodeId, laneId, Tier.STARTER, 0, ordinal, Optional.empty()));
        }
        ResearchTechTreePresentation.LaneView lane =
                new ResearchTechTreePresentation.LaneView(
                        laneId,
                        "Maximum",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        0,
                        members);
        ResearchTechTreePresentation.DomainView domain =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        List.of(lane));
        return new ResearchTechTreeProjection(
                Domain.WEAPONS,
                domain,
                new ResearchTreeGraph(nodes, List.of()),
                placements,
                List.of());
    }

    private static ResearchTechTreeProjection mixedCatalogProjection() {
        int authoredCount = 53;
        int totalCount = 287;
        int capacity = 20;
        ResourceLocation laneId = id("test:mixed_catalog/lane");
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(totalCount);
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>(totalCount - capacity);
        List<ResearchTechTreePresentation.Member> members =
                new ArrayList<>(totalCount);
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < totalCount; ordinal++) {
            ResourceLocation nodeId = id("test:mixed_catalog/" + ordinal);
            int rank = ordinal / capacity;
            boolean automatic = ordinal >= authoredCount;
            Optional<ResearchTechTreePresentation.AutomaticBranchPlacement> branch =
                    automatic
                            ? Optional.of(new ResearchTechTreePresentation
                                    .AutomaticBranchPlacement(
                                            Math.floorMod(ordinal - authoredCount, 4),
                                            rank,
                                            3,
                                            5))
                            : Optional.empty();
            nodes.add(node(ordinal, nodeId, rank == 0 ? 0 : 1));
            members.add(new ResearchTechTreePresentation.Member(
                    nodeId,
                    rank,
                    ordinal,
                    Optional.empty(),
                    automatic ? PlacementOrigin.AUTOMATIC : PlacementOrigin.EXACT,
                    Optional.empty(),
                    branch));
            placements.put(nodeId, new ResearchTechTreeProjection.Placement(
                    nodeId,
                    laneId,
                    rank,
                    0,
                    ordinal,
                    Optional.empty(),
                    automatic ? PlacementOrigin.AUTOMATIC : PlacementOrigin.EXACT,
                    Optional.empty(),
                    branch));
            if (rank > 0) {
                int localIndex = ordinal - rank * capacity;
                int previousStart = (rank - 1) * capacity;
                int previousCount = Math.min(capacity, totalCount - previousStart);
                int currentCount = Math.min(capacity, totalCount - rank * capacity);
                int parentLocal = Math.min(
                        previousCount - 1,
                        Math.floorDiv(localIndex * previousCount,
                                Math.max(1, currentCount)));
                edges.add(new ResearchTreeGraph.Edge(
                        id("test:mixed_catalog/" + (previousStart + parentLocal)),
                        nodeId));
            }
        }
        ResearchTechTreePresentation.DomainView domain =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Mixed catalog",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                laneId,
                                "Mixed catalog",
                                Optional.empty(),
                                Optional.of(nodes.get(0).blueprintId()),
                                0,
                                members)));
        return new ResearchTechTreeProjection(
                Domain.WEAPONS,
                domain,
                new ResearchTreeGraph(nodes, edges),
                placements,
                List.of(),
                List.of(),
                capacity);
    }

    private static ResearchTechTreeProjection branchingProjection() {
        List<Integer> ranks = new ArrayList<>();
        ranks.addAll(List.of(0, 0));
        for (int rank = 1; rank <= 2; rank++) {
            for (int index = 0; index < 12; index++) {
                ranks.add(rank);
            }
        }
        for (int rank = 3; rank <= 4; rank++) {
            for (int index = 0; index < 8; index++) {
                ranks.add(rank);
            }
        }
        ranks.add(5);

        List<ResearchTreeGraph.Edge> edges = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            ResourceLocation child = id("test:branch_shape/" + (2 + index));
            edges.add(new ResearchTreeGraph.Edge(
                    id("test:branch_shape/" + index % 2), child));
            edges.add(new ResearchTreeGraph.Edge(
                    id("test:branch_shape/" + (1 - index % 2)), child));
        }
        for (int index = 0; index < 12; index++) {
            ResourceLocation child = id("test:branch_shape/" + (14 + index));
            edges.add(new ResearchTreeGraph.Edge(
                    id("test:branch_shape/" + (2 + index)), child));
            edges.add(new ResearchTreeGraph.Edge(
                    id("test:branch_shape/" + (2 + (index + 1) % 12)), child));
        }
        edges.add(new ResearchTreeGraph.Edge(
                id("test:branch_shape/14"), id("test:branch_shape/26")));
        for (int index = 1; index < 8; index++) {
            edges.add(new ResearchTreeGraph.Edge(
                    id("test:branch_shape/" + (14 + index + 4)),
                    id("test:branch_shape/" + (26 + index))));
        }
        for (int index = 0; index < 8; index++) {
            edges.add(new ResearchTreeGraph.Edge(
                    id("test:branch_shape/" + (26 + index)),
                    id("test:branch_shape/" + (34 + index))));
        }
        edges.add(new ResearchTreeGraph.Edge(
                id("test:branch_shape/34"), id("test:branch_shape/42")));
        return rankedProjection(ranks, edges, 12, "branch_shape", true);
    }

    private static ResearchTechTreeProjection twoFamilyProjection() {
        ResourceLocation laneId = id("test:two_family/lane");
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTechTreePresentation.Member> members = new ArrayList<>();
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < 4; ordinal++) {
            int rank = ordinal / 2;
            int branchIndex = ordinal % 2;
            long siblingOrder = branchIndex;
            ResourceLocation nodeId = id("test:two_family/" + ordinal);
            Optional<ResearchTechTreePresentation.AutomaticBranchPlacement> branch =
                    Optional.of(new ResearchTechTreePresentation.AutomaticBranchPlacement(
                            branchIndex, rank, 0, 1));
            nodes.add(node(ordinal, nodeId, rank == 0 ? 0 : 1));
            members.add(new ResearchTechTreePresentation.Member(
                    nodeId,
                    rank,
                    siblingOrder,
                    Optional.empty(),
                    PlacementOrigin.AUTOMATIC,
                    Optional.empty(),
                    branch));
            placements.put(nodeId, new ResearchTechTreeProjection.Placement(
                    nodeId,
                    laneId,
                    rank,
                    0,
                    siblingOrder,
                    Optional.empty(),
                    PlacementOrigin.AUTOMATIC,
                    Optional.empty(),
                    branch));
        }
        List<ResearchTreeGraph.Edge> edges = List.of(
                new ResearchTreeGraph.Edge(
                        id("test:two_family/0"), id("test:two_family/2")),
                new ResearchTreeGraph.Edge(
                        id("test:two_family/1"), id("test:two_family/3")));
        ResearchTechTreePresentation.DomainView domain =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Two families",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                laneId,
                                "Two families",
                                Optional.empty(),
                                Optional.of(nodes.get(0).blueprintId()),
                                0,
                                members)));
        return new ResearchTechTreeProjection(
                Domain.WEAPONS,
                domain,
                new ResearchTreeGraph(nodes, edges),
                placements,
                List.of(),
                List.of(),
                8);
    }

    private static ResearchTechTreeProjection mixedFamilyProjection() {
        ResourceLocation laneId = id("test:mixed_family/lane");
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTechTreePresentation.Member> members = new ArrayList<>();
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < 3; ordinal++) {
            ResourceLocation nodeId = id("test:mixed_family/" + ordinal);
            boolean automatic = ordinal != 1;
            Optional<ResearchTechTreePresentation.AutomaticBranchPlacement> branch = automatic
                    ? Optional.of(new ResearchTechTreePresentation.AutomaticBranchPlacement(
                            ordinal == 0 ? 0 : 1, 0, 0, 1))
                    : Optional.empty();
            PlacementOrigin origin = automatic
                    ? PlacementOrigin.AUTOMATIC : PlacementOrigin.EXACT;
            nodes.add(node(ordinal, nodeId));
            members.add(new ResearchTechTreePresentation.Member(
                    nodeId,
                    0,
                    ordinal,
                    Optional.empty(),
                    origin,
                    Optional.empty(),
                    branch));
            placements.put(nodeId, new ResearchTechTreeProjection.Placement(
                    nodeId,
                    laneId,
                    0,
                    0,
                    ordinal,
                    Optional.empty(),
                    origin,
                    Optional.empty(),
                    branch));
        }
        ResearchTechTreePresentation.DomainView domain =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Mixed families",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                laneId,
                                "Mixed families",
                                Optional.empty(),
                                Optional.of(nodes.get(0).blueprintId()),
                                0,
                                members)));
        return new ResearchTechTreeProjection(
                Domain.WEAPONS,
                domain,
                new ResearchTreeGraph(nodes, List.of()),
                placements,
                List.of(),
                List.of(),
                8);
    }

    private static List<ResearchTreeLayout.PositionedNode> nodesAtSemanticRank(
            ResearchTechTreeProjection projection,
            ResearchTreeLayout layout,
            int rank) {
        return layout.nodes().stream()
                .filter(node -> projection.placement(node.blueprintId()).orElseThrow().rank()
                        == rank)
                .sorted(java.util.Comparator.comparingInt(
                        ResearchTreeLayout.PositionedNode::x))
                .toList();
    }

    private static int maximumCenterStep(List<ResearchTreeLayout.PositionedNode> row) {
        int maximum = 0;
        for (int index = 1; index < row.size(); index++) {
            maximum = Math.max(
                    maximum,
                    row.get(index).centerX() - row.get(index - 1).centerX());
        }
        return maximum;
    }

    private static ResearchTechTreeProjection levelRowsProjection(
            int lowerCount,
            int upperCount) {
        ResourceLocation laneId = id("test:level_rows");
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(lowerCount + upperCount);
        List<ResearchTechTreePresentation.Member> members =
                new ArrayList<>(lowerCount + upperCount);
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < lowerCount + upperCount; ordinal++) {
            int level = ordinal < lowerCount ? 0 : 1;
            long siblingOrder = ordinal;
            ResourceLocation nodeId = id("test:level_rows/" + ordinal);
            nodes.add(node(ordinal, nodeId));
            members.add(new ResearchTechTreePresentation.Member(
                    nodeId,
                    Tier.BASIC,
                    level,
                    siblingOrder,
                    PlacementOrigin.EXACT,
                    Optional.empty()));
            placements.put(nodeId, new ResearchTechTreeProjection.Placement(
                    nodeId,
                    laneId,
                    Tier.BASIC,
                    level,
                    0,
                    siblingOrder,
                    PlacementOrigin.EXACT,
                    Optional.empty()));
        }
        ResearchTechTreePresentation.LaneView lane =
                new ResearchTechTreePresentation.LaneView(
                        laneId,
                        "Level rows",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        0,
                        members);
        ResearchTechTreePresentation.DomainView domain =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        List.of(lane));
        return new ResearchTechTreeProjection(
                Domain.WEAPONS,
                domain,
                new ResearchTreeGraph(nodes, List.of()),
                placements,
                List.of());
    }

    private static long distinctRows(ResearchTechTreeProjection projection) {
        return ResearchTechTreeLayoutEngine.layout(
                        projection, ResearchTechTreeLayoutPolicy.DEFAULT)
                .graphLayout().nodes().stream()
                .map(ResearchTreeLayout.PositionedNode::y)
                .distinct()
                .count();
    }

    private static ResearchTechTreeProjection rankedProjection(
            List<Integer> ranks,
            List<ResearchTreeGraph.Edge> edges,
            int maximumNodesPerLayer,
            String prefix) {
        return rankedProjection(ranks, edges, maximumNodesPerLayer, prefix, false);
    }

    private static ResearchTechTreeProjection rankedProjection(
            List<Integer> ranks,
            List<ResearchTreeGraph.Edge> edges,
            int maximumNodesPerLayer,
            String prefix,
            boolean canonicalBranches) {
        return rankedProjection(
                ranks,
                edges,
                maximumNodesPerLayer,
                prefix,
                canonicalBranches,
                ResearchTechTreeLayoutEngineTest::branchIndex,
                2,
                4);
    }

    private static ResearchTechTreeProjection rankedProjection(
            List<Integer> ranks,
            List<ResearchTreeGraph.Edge> edges,
            int maximumNodesPerLayer,
            String prefix,
            boolean canonicalBranches,
            java.util.function.IntBinaryOperator familyResolver,
            int familyStartIndex,
            int transitionEndIndex) {
        ResourceLocation laneId = id("test:" + prefix + "/lane");
        Map<ResourceLocation, Integer> prerequisiteCounts = new LinkedHashMap<>();
        edges.forEach(edge -> prerequisiteCounts.merge(
                edge.dependentId(), 1, Integer::sum));
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTechTreePresentation.Member> members = new ArrayList<>();
        LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement> placements =
                new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < ranks.size(); ordinal++) {
            ResourceLocation nodeId = id("test:" + prefix + "/" + ordinal);
            int rank = ranks.get(ordinal);
            nodes.add(node(
                    ordinal,
                    nodeId,
                    prerequisiteCounts.getOrDefault(nodeId, 0)));
            Optional<ResearchTechTreePresentation.AutomaticBranchPlacement> branch =
                    canonicalBranches
                            ? Optional.of(new ResearchTechTreePresentation
                                    .AutomaticBranchPlacement(
                                            familyResolver.applyAsInt(ordinal, rank),
                                            rank,
                                            familyStartIndex,
                                            transitionEndIndex))
                            : Optional.empty();
            members.add(new ResearchTechTreePresentation.Member(
                    nodeId,
                    rank,
                    ordinal,
                    Optional.empty(),
                    canonicalBranches
                            ? PlacementOrigin.AUTOMATIC : PlacementOrigin.EXACT,
                    Optional.empty(),
                    branch));
            placements.put(nodeId, new ResearchTechTreeProjection.Placement(
                    nodeId,
                    laneId,
                    rank,
                    0,
                    ordinal,
                    Optional.empty(),
                    canonicalBranches
                            ? PlacementOrigin.AUTOMATIC : PlacementOrigin.EXACT,
                    Optional.empty(),
                    branch));
        }
        ResearchTechTreePresentation.DomainView domain =
                new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Ranked",
                        Optional.empty(),
                        Optional.of(nodes.get(0).blueprintId()),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                laneId,
                                "Ranked",
                                Optional.empty(),
                                Optional.of(nodes.get(0).blueprintId()),
                                0,
                                members)));
        return new ResearchTechTreeProjection(
                Domain.WEAPONS,
                domain,
                new ResearchTreeGraph(nodes, edges),
                placements,
                List.of(),
                List.of(),
                maximumNodesPerLayer);
    }

    private static int branchIndex(int ordinal, int rank) {
        int firstOrdinal = switch (rank) {
            case 0 -> 0;
            case 1 -> 2;
            case 2 -> 14;
            case 3 -> 26;
            case 4 -> 34;
            default -> 42;
        };
        int localIndex = ordinal - firstOrdinal;
        int width = rank <= 2 ? 12 : rank <= 4 ? 8 : 1;
        return Math.min(3, Math.floorDiv(localIndex * 4, width));
    }

    private static int crossingCount(
            ResearchTreeLayout layout,
            List<ResearchTreeGraph.Edge> edges) {
        int crossings = 0;
        for (int left = 0; left < edges.size(); left++) {
            ResearchTreeGraph.Edge first = edges.get(left);
            int firstSource = layout.position(first.prerequisiteId()).orElseThrow().centerX();
            int firstTarget = layout.position(first.dependentId()).orElseThrow().centerX();
            for (int right = left + 1; right < edges.size(); right++) {
                ResearchTreeGraph.Edge second = edges.get(right);
                int secondSource = layout.position(
                        second.prerequisiteId()).orElseThrow().centerX();
                int secondTarget = layout.position(
                        second.dependentId()).orElseThrow().centerX();
                if ((long) (firstSource - secondSource)
                        * (firstTarget - secondTarget) < 0L) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    private static ResearchTechTreePresentation.LaneView lane(
            ResourceLocation laneId,
            int order,
            ResourceLocation nodeId,
            Tier tier) {
        return new ResearchTechTreePresentation.LaneView(
                laneId,
                laneId.getPath(),
                Optional.empty(),
                Optional.of(nodeId),
                order,
                List.of(new ResearchTechTreePresentation.Member(
                        nodeId, tier, 0, Optional.empty())));
    }

    private static ResearchTreeGraph.Node node(int ordinal, ResourceLocation nodeId) {
        return node(ordinal, nodeId, 0);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation nodeId,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                nodeId,
                "name." + ordinal,
                "gun",
                new ResourceLocation("minecraft", "paper"),
                JournalVisibility.FULL,
                false,
                true,
                false,
                1,
                0,
                prerequisiteCount,
                0,
                ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
