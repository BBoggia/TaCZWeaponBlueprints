package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchTreeOverviewBuilder;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeLayeredLayoutEngineTest {
    private static final ResearchTreeLayoutStrategy DEFAULT_STRATEGY =
            ResearchTreeLayoutStrategy.layered(ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);

    @Test
    void strategyRemainsTheLocalKernelWhileTheAtlasAdapterIsDeterministic() {
        List<ResearchTreePublication> publications = List.of(
                ResearchTreeRedesignFixture.connectedProgression(),
                ResearchTreeRedesignFixture.defaultPistolProgression(),
                ResearchTreeRedesignFixture.alternatingGroupDependencies(),
                ResearchTreeRedesignFixture.maximumDepthProgression(),
                ResearchTreeOverviewBuilder.build(
                        ResearchTreeRedesignFixture.denseGeneratedCatalog()).publication());

        for (ResearchTreePublication publication : publications) {
            ResearchTreeLayout direct = ResearchTreeLayeredLayoutEngine.layout(
                    publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);
            ResearchTreeLayout atlas = ResearchTreeUnifiedLayoutEngine.layout(publication);

            assertEquals(direct, DEFAULT_STRATEGY.layout(publication));
            assertEquals(direct, ResearchTreeLayeredLayoutEngine.layout(
                    publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));
            assertEquals(atlas, ResearchTreeUnifiedLayoutEngine.layout(publication));
            ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                    publication.graph(), direct);
            ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                    publication.graph(), atlas);
        }
    }

    @Test
    void phaseZeroConnectedGeometryRemainsPixelStable() {
        ResearchTreeLayout layout = DEFAULT_STRATEGY.layout(
                ResearchTreeRedesignFixture.connectedProgression());

        assertEquals(
                "112x336/5:root@44,292,0,0,0;left@20,224,0,1,0;"
                        + "right@68,224,0,1,1;left_leaf@20,156,0,2,0;"
                        + "right_leaf@68,156,0,2,1;merge@44,88,0,3,0;"
                        + "top@44,20,0,4,0",
                signature(layout));
    }

    @Test
    void aCompactPolicyChangesOnlyPresentationGeometry() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.connectedProgression();
        ResearchTreeLayoutPolicy compact = new ResearchTreeLayoutPolicy(
                8,
                10,
                20,
                12,
                12,
                30,
                8,
                16,
                2,
                4_096,
                4,
                4);

        ResearchTreeLayout defaultLayout = DEFAULT_STRATEGY.layout(publication);
        ResearchTreeLayout compactLayout = ResearchTreeLayoutStrategy.layered(compact)
                .layout(publication);

        ResearchTreeLayoutContractAssertions.assertFaithfulBottomToTopLayout(
                publication.graph(), compactLayout);
        assertEquals(compactLayout,
                ResearchTreeLayeredLayoutEngine.layout(publication, compact));
        assertEquals(defaultLayout.nodes().stream()
                        .map(ResearchTreeLayout.PositionedNode::blueprintId)
                        .toList(),
                compactLayout.nodes().stream()
                        .map(ResearchTreeLayout.PositionedNode::blueprintId)
                        .toList());
        assertTrue(compactLayout.width() < defaultLayout.width());
        assertTrue(compactLayout.height() < defaultLayout.height());
    }

    @Test
    void intraGroupGapControlsDisconnectedBranchComponentsIndependently() {
        ResearchTreeLayoutInput input = new ResearchTreeLayoutInput(
                List.of(
                        new ResearchTreeLayoutInput.Node(
                                0, new net.minecraft.resources.ResourceLocation("test", "a"),
                                0, 0, 0, 0),
                        new ResearchTreeLayoutInput.Node(
                                1, new net.minecraft.resources.ResourceLocation("test", "b"),
                                0, 0, 1, 1)),
                List.of());
        ResearchTreeLayoutPolicy base = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;
        ResearchTreeLayoutPolicy expanded = new ResearchTreeLayoutPolicy(
                base.canvasPadding(), base.nodeGap(), base.tierGap(), base.componentGap(),
                80, 80, base.groupPadding(), base.groupHeaderHeight(),
                base.portalPadding(), base.maxRankBlockWidth(),
                base.orderingSweeps(), base.compactionSweeps());

        ResearchTreeLayout normal = ResearchTreeLayeredLayoutEngine.layoutInput(input, base);
        ResearchTreeLayout padded = ResearchTreeLayeredLayoutEngine.layoutInput(input, expanded);

        assertEquals(56, padded.width() - normal.width());
        assertEquals(normal.height(), padded.height());
    }

    @Test
    void skippedRanksCreateInternalCorridorWaypointsWithoutPublishingDummyNodes() {
        ResourceLocation bottom = new ResourceLocation("test", "bottom");
        ResourceLocation top = new ResourceLocation("test", "top");
        ResearchTreeLayoutInput input = new ResearchTreeLayoutInput(
                List.of(
                        new ResearchTreeLayoutInput.Node(0, bottom, 0, 0, 0, 0),
                        new ResearchTreeLayoutInput.Node(1, top, 4, 0, 0, 0)),
                List.of(new ResearchTreeLayoutInput.Edge(bottom, top)));

        ResearchTreeLayout layout = ResearchTreeLayeredLayoutEngine.layoutInput(
                input, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);

        assertEquals(2, layout.nodes().size());
        ResearchTreeLayout.EdgeRouteHint hint = layout.edgeRouteHint(bottom, top).orElseThrow();
        assertEquals(List.of(1, 2, 3), hint.waypoints().stream()
                .map(ResearchTreeLayout.RouteWaypoint::rank)
                .toList());
        assertEquals(layout, ResearchTreeLayeredLayoutEngine.layoutInput(
                input, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));
    }

    @Test
    void anExtremeAuthoredRankGapFallsBackWithoutCreatingAnOversizedRouteHint() {
        ResourceLocation bottom = new ResourceLocation("test", "wide_gap_bottom");
        ResourceLocation top = new ResourceLocation("test", "wide_gap_top");
        ResearchTreeLayoutInput input = new ResearchTreeLayoutInput(
                List.of(
                        new ResearchTreeLayoutInput.Node(0, bottom, 0, 0, 0, 0),
                        new ResearchTreeLayoutInput.Node(
                                1, top, ResearchTreeGraph.MAX_NODES - 1, 0, 0, 0)),
                List.of(new ResearchTreeLayoutInput.Edge(bottom, top)));

        ResearchTreeLayout layout = ResearchTreeLayeredLayoutEngine.layoutInput(
                input, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);

        assertEquals(2, layout.nodes().size());
        assertTrue(layout.edgeRouteHint(bottom, top).isEmpty());
    }

    @Test
    void sparseRanksAreCenteredAgainstTheWidestRank() {
        java.util.ArrayList<ResearchTreeLayoutInput.Node> nodes = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < 5; ordinal++) {
            nodes.add(new ResearchTreeLayoutInput.Node(
                    ordinal,
                    new ResourceLocation("test", "wide/" + ordinal),
                    0,
                    0,
                    ordinal,
                    0));
        }
        ResourceLocation sparse = new ResourceLocation("test", "sparse");
        nodes.add(new ResearchTreeLayoutInput.Node(5, sparse, 1, 0, 0, 0));

        ResearchTreeLayout layout = ResearchTreeLayeredLayoutEngine.layoutInput(
                new ResearchTreeLayoutInput(nodes, List.of()),
                ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);

        List<ResearchTreeLayout.PositionedNode> wide = layout.nodes().stream()
                .filter(node -> node.tier() == 0)
                .toList();
        int wideCenter = (wide.stream().mapToInt(ResearchTreeLayout.PositionedNode::centerX)
                .min().orElseThrow()
                + wide.stream().mapToInt(ResearchTreeLayout.PositionedNode::centerX)
                        .max().orElseThrow()) / 2;
        assertEquals(wideCenter, layout.position(sparse).orElseThrow().centerX());
    }

    @Test
    void maximumRankWidthAlsoBoundsInvisibleRoutingWaypoints() {
        ResourceLocation bottom = new ResourceLocation("test", "bounded_bottom");
        ResourceLocation top = new ResourceLocation("test", "bounded_top");
        ResearchTreeLayoutInput input = new ResearchTreeLayoutInput(
                List.of(
                        new ResearchTreeLayoutInput.Node(0, bottom, 0, 0, 0, 0),
                        new ResearchTreeLayoutInput.Node(
                                1, new ResourceLocation("test", "filler_1"), 1, 0, 0, 0),
                        new ResearchTreeLayoutInput.Node(
                                2, new ResourceLocation("test", "filler_2"), 2, 0, 0, 0),
                        new ResearchTreeLayoutInput.Node(
                                3, new ResourceLocation("test", "filler_3"), 3, 0, 0, 0),
                        new ResearchTreeLayoutInput.Node(4, top, 4, 0, 0, 0)),
                List.of(new ResearchTreeLayoutInput.Edge(bottom, top)));
        ResearchTreeLayoutPolicy base = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;
        ResearchTreeLayoutPolicy oneNodeRows = new ResearchTreeLayoutPolicy(
                base.canvasPadding(), base.nodeGap(), base.tierGap(), base.componentGap(),
                base.intraGroupGap(), base.interGroupGap(), base.groupPadding(),
                base.groupHeaderHeight(), base.portalPadding(), ResearchTreeLayout.NODE_WIDTH,
                base.orderingSweeps(), base.compactionSweeps());

        ResearchTreeLayout layout = ResearchTreeLayeredLayoutEngine.layoutInput(
                input, oneNodeRows);

        assertTrue(layout.edgeRouteHint(bottom, top).isEmpty());
        assertEquals(ResearchTreeLayout.NODE_WIDTH + base.canvasPadding() * 2, layout.width());
    }

    @Test
    void emptyAndMissingInputsHaveExplicitResults() {
        assertEquals(ResearchTreeLayout.EMPTY,
                DEFAULT_STRATEGY.layout(ResearchTreePublication.EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeLayeredLayoutEngine.layout(
                        null, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeLayeredLayoutEngine.layout(
                        ResearchTreePublication.EMPTY, null));
    }

    private static String signature(ResearchTreeLayout layout) {
        return layout.width() + "x" + layout.height() + "/" + layout.tierCount() + ":"
                + layout.nodes().stream()
                        .map(node -> node.blueprintId().getPath()
                                + "@" + node.x() + "," + node.y()
                                + "," + node.component() + "," + node.tier()
                                + "," + node.orderInTier())
                        .collect(java.util.stream.Collectors.joining(";"));
    }
}
