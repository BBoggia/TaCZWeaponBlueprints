package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

class ResearchTreeWorkbenchSceneTest {
    @Test void liveCatalogCanBindToTheProductionCanvas() throws Exception {
        var source = ResearchTreeBaselineInput.read(java.nio.file.Path.of("src/test/resources/research-tree-baseline/live-weapons-287.json"));
        var projection = source.projections().get(0).replay(); var input = ResearchTreeSceneInput.from(projection,policy());
        var engine = new ResearchTreeBranchSceneEngine(); var scene = ResearchTreeSceneValidator.validate(input,engine.revision(),engine.layout(input));
        var canvas = new ResearchTreeCanvas(new ResearchTreeViewState(),new ResearchTreeCanvas.Style(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24));
        var result = new ResearchTreeSceneCache.Result(new Object(),1,ResearchTechTreeClientFixture.publication(),Domain.WEAPONS,
                Optional.of(projection),ResearchTreeSceneCache.Status.VALID,Optional.of(scene),List.of(),false);
        assertTrue(canvas.setSceneContent(result,Map.of(),null,null)); assertEquals(287,canvas.graph().nodes().size());
        canvas.setBounds(ResearchTreeScreenLayout.ViewMode.FULLSCREEN,new ResearchTreeScreenLayout.Rect(36,48,215,184));
        canvas.viewport().setAnimated(false);
        canvas.viewport().restore(new ResearchTreeViewport.Snapshot(0,0,.05));
        assertTrue(canvas.canZoom(-1), "scene controls must permit the same overview zoom as the wheel");
        canvas.zoomAtCenter(-1);
        assertTrue(canvas.viewport().scale() < .05);
        var id = scene.nodes().get(0).id(); canvas.focusTarget(id);
        var before = canvas.nodeBounds(id).orElseThrow();
        double x = canvas.viewport().viewportX(before.centerX()), y = canvas.viewport().viewportY(before.centerY());
        var larger = new ResearchTreeScenePolicy(policy().spacing(),1024,
                new ResearchTreeScenePolicy.NodeMetrics(96,48,3),"larger-font",policy().regionMetrics());
        var nextInput = ResearchTreeSceneInput.from(projection,larger);
        var nextScene = ResearchTreeSceneValidator.validate(nextInput,engine.revision(),engine.layout(nextInput));
        var nextResult = new ResearchTreeSceneCache.Result(new Object(),2,ResearchTechTreeClientFixture.publication(),Domain.WEAPONS,
                Optional.of(projection),ResearchTreeSceneCache.Status.VALID,Optional.of(nextScene),List.of(),false);
        canvas.setSceneContent(nextResult,Map.of(),id,null);
        var after = canvas.nodeBounds(id).orElseThrow();
        assertEquals(x,canvas.viewport().viewportX(after.centerX()),1e-9);
        assertEquals(y,canvas.viewport().viewportY(after.centerY()),1e-9);
        var minimap = new ResearchTreeMinimap(); var layout = ResearchTreeWorkbenchLayout.create(427,240);
        minimap.prepare(ResearchTreeMinimapMode.ALWAYS,canvas,true,List.of(layout.targetClearance()));
        assertTrue(!minimap.visible() || !minimap.panelBounds().overlaps(layout.targetClearance()));
    }
    static Stream<Arguments> sizes() {
        return Stream.of(320, 360, 419, 420, 494, 640, 1600).flatMap(w -> Stream.of(240, 273, 480).map(h -> Arguments.of(w, h)));
    }
    @ParameterizedTest @MethodSource("sizes") void persistentPanelsAndControlsNeverCoverTheCanvas(int w, int h) {
        var l = ResearchTreeWorkbenchLayout.create(w, h);
        for (var b : List.of(l.header(), l.canvas(), l.inspector(), l.action(), l.track(), l.target(), l.branch())) assertTrue(b.inside(w, h));
        assertFalse(l.canvas().overlaps(l.inspector())); assertFalse(l.canvas().overlaps(l.header()));
        assertFalse(l.canvas().overlaps(l.overlays().rail())); assertFalse(l.canvas().overlaps(l.branch()));
        assertFalse(l.action().overlaps(l.target())); assertFalse(l.action().overlaps(l.track()));
        var filter = ResearchTreeFullscreenRailLayout.forLayout(l.overlays()).affordability();
        assertFalse(l.branch().overlaps(filter)); assertFalse(l.branch().overlaps(l.overlays().searchField()));
        assertTrue(l.canvas().contains(l.targetClearance()));
    }
    @ParameterizedTest @MethodSource("sizes") void unselectedTreeReclaimsInspectorSpace(int w, int h) {
        var selected = ResearchTreeWorkbenchLayout.create(w, h, true);
        var empty = ResearchTreeWorkbenchLayout.create(w, h, false);
        assertFalse(empty.inspectorVisible());
        assertTrue(selected.inspectorVisible());
        assertTrue(empty.canvas().contains(selected.canvas()));
        assertTrue(empty.canvas().contains(selected.inspector()));
        assertEquals(w - 8, empty.canvas().right());
        assertEquals(h - 8, empty.canvas().bottom());
        assertFalse(empty.canvas().overlaps(empty.header()));
        assertFalse(empty.canvas().overlaps(empty.overlays().rail()));
        assertEquals(selected, ResearchTreeWorkbenchLayout.create(w, h, true));
    }
    private static ResearchTreeScenePolicy policy() {
        return new ResearchTreeScenePolicy(new ResearchTechTreeLayoutPolicy(20,18,8,20,4,1000,6,6),1024,
                new ResearchTreeScenePolicy.NodeMetrics(96,44,3), "test-tile", new ResearchTreeScenePolicy.RegionMetrics(44,12,16,80,16,10,2));
    }
    private static ResearchTreeScene scene() {
        var projection = ResearchTechTreeProjectionBuilder.build(ResearchTechTreeClientFixture.publication()).projection(Domain.WEAPONS).orElseThrow();
        var input = ResearchTreeSceneInput.from(projection, policy()); var engine = new ResearchTreeBranchSceneEngine();
        return ResearchTreeSceneValidator.validate(input, engine.revision(), engine.layout(input));
    }
    @Test void variableBodiesPaintPaddingAndNormalizedNegativeCoordinatesShareOneMapping() {
        var source = scene(); var original = source.nodes().get(0);
        var node = ResearchTreeSceneGeometry.node(original.id(), -200, -300, policy().nodeMetrics());
        var negative = new ResearchTreeScene(source.key(), node.paint(), List.of(node), List.of(), List.of(), List.of(), List.of());
        var view = new ResearchTreeSceneView(negative); var body = view.body(node.id()).orElseThrow();
        assertEquals(3, body.x()); assertEquals(3, body.y());
        assertEquals(node.id(), view.hit(body.right() - 1, body.bottom() - 1, 1).orElseThrow());
        assertEquals(List.of(node), view.query(node.paint().x(), node.paint().y(), node.body().x() - 1, node.body().y() - 1));
        assertEquals(body.centerX(), view.navigation.nodes().get(0).centerX());
    }
    @Test void bodyHitsBeatNeighborPaddingAndTiesAreStable() {
        var source = scene(); var ids = source.nodes().stream().map(ResearchTreeScene.Node::id).toList();
        var a = ResearchTreeSceneGeometry.node(ids.get(0), 10, 10, policy().nodeMetrics());
        var b = ResearchTreeSceneGeometry.node(ids.get(1), 108, 10, policy().nodeMetrics());
        var test = new ResearchTreeScene(source.key(), a.paint().union(b.paint()), List.of(a,b), List.of(), List.of(), List.of(), List.of());
        var view = new ResearchTreeSceneView(test);
        assertEquals(b.id(), view.hit(109 + view.offsetX, 30 + view.offsetY, .1).orElseThrow());
    }
    @Test void proportionalZoomAndViewportResizePreserveTheirAnchors() {
        var v = new ResearchTreeViewport(); v.configure(400,300,10000,10000);
        v.restore(new ResearchTreeViewport.Snapshot(1000,1000,.7));
        double x=v.canvasX(90), y=v.canvasY(120);
        v.zoomProportional(1,90,120); assertEquals(.826,v.scale(),1e-9);
        assertEquals(x,v.canvasX(90),1e-9); assertEquals(y,v.canvasY(120),1e-9);
        x=v.canvasX(200); y=v.canvasY(150); v.configureAnchored(300,200,10000,10000);
        assertEquals(x,v.canvasX(150),1e-9); assertEquals(y,v.canvasY(100),1e-9);
        v.restore(new ResearchTreeViewport.Snapshot(1000,1000,.05)); v.zoomProportional(1,150,100); assertEquals(.059,v.scale(),1e-9);
    }
    @Test void sceneCamerasRemainPerDomainAndRejectDifferentMetrics() {
        var catalog=ResearchTechTreeProjectionBuilder.build(ResearchTechTreeClientFixture.publication());
        var state=new ResearchTechTreeViewState(); state.retain(catalog,null); state.selectDomain(Domain.WEAPONS,catalog);
        var key=scene().key(); var camera=new ResearchTreeViewport.Snapshot(30,50,.8);
        state.saveSceneCamera(ResearchTechTreeViewState.Surface.FULLSCREEN,key,camera);
        state.selectDomain(Domain.AMMO,catalog); assertTrue(state.sceneCamera(ResearchTechTreeViewState.Surface.FULLSCREEN,key).isEmpty());
        state.selectDomain(Domain.WEAPONS,catalog); assertEquals(camera,state.sceneCamera(ResearchTechTreeViewState.Surface.FULLSCREEN,key).orElseThrow());
        assertTrue(state.sceneCamera(ResearchTechTreeViewState.Surface.COMPACT,key).isEmpty());
        var projection = catalog.projection(Domain.WEAPONS).orElseThrow();
        var changedPolicy = new ResearchTreeScenePolicy(policy().spacing(),1024,
                new ResearchTreeScenePolicy.NodeMetrics(96,46,3), "larger-font",policy().regionMetrics());
        var engine = new ResearchTreeBranchSceneEngine();
        var changedInput = ResearchTreeSceneInput.from(projection,changedPolicy);
        var changedScene = ResearchTreeSceneValidator.validate(changedInput,engine.revision(),engine.layout(changedInput));
        assertTrue(state.sceneCamera(ResearchTechTreeViewState.Surface.FULLSCREEN,changedScene.key()).isEmpty());
    }
    @Test void canvasProgressRefreshRetainsGeometryCameraAndCurrentNodeState() {
        var cache = new ResearchTreeSceneCache(p -> new ResearchTreeBranchSceneEngine(), new ResearchTreeSimpleSceneEngine());
        var initial = ResearchTechTreeClientFixture.publication(); cache.bind(initial);
        var first = cache.scene(initial, Domain.WEAPONS, policy());
        var canvas = new ResearchTreeCanvas(new ResearchTreeViewState(), new ResearchTreeCanvas.Style(
                1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24));
        canvas.setBounds(ResearchTreeScreenLayout.ViewMode.FULLSCREEN, new ResearchTreeScreenLayout.Rect(40,48,180,150));
        canvas.setDisplayPolicy(ResearchTreeDisplayPolicy.DEFAULT);
        assertTrue(canvas.setSceneContent(first,Map.of(),ResearchTechTreeClientFixture.WEAPON_UPGRADE,null));
        canvas.viewport().setAnimated(false); canvas.focusTarget(ResearchTechTreeClientFixture.WEAPON_UPGRADE);
        canvas.panByScreenDelta(10,20); var camera=canvas.viewport().snapshot(); var geometry=canvas.scene().orElseThrow();
        var next = ResearchTechTreeClientFixture.publication(Set.of(Domain.values()),
                com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph.Availability.AVAILABLE);
        cache.bind(next); var second=cache.scene(next,Domain.WEAPONS,policy());
        assertSame(geometry,second.geometry().orElseThrow());
        assertFalse(canvas.setSceneContent(second,Map.of(),ResearchTechTreeClientFixture.WEAPON_UPGRADE,null));
        assertEquals(camera,canvas.viewport().snapshot()); assertEquals(com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph.Availability.AVAILABLE,
                canvas.graph().node(ResearchTechTreeClientFixture.WEAPON_UPGRADE).orElseThrow().availability());
        var b=canvas.nodeBounds(ResearchTechTreeClientFixture.WEAPON_UPGRADE).orElseThrow();
        canvas.focusTarget(ResearchTechTreeClientFixture.WEAPON_UPGRADE);
        double x=canvas.bounds().x()+canvas.viewport().viewportX(b.centerX()), y=canvas.bounds().y()+canvas.viewport().viewportY(b.y()+b.height()/2);
        assertEquals(ResearchTechTreeClientFixture.WEAPON_UPGRADE,canvas.nodeAt(x,y).orElseThrow().blueprintId());
        assertTrue(canvas.nodeAt(canvas.bounds().right()+10,y).isEmpty());
    }
}
