package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.research.tree.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.progression.*;

class ResearchTreeSceneMigrationTest {
    @Test void searchAndHelpCoverTheDockedInspectorForInputAsWellAsPainting() {
        assertEquals(ResearchTreeInteractionPolicy.PointerTarget.SEARCH,ResearchTreeInteractionPolicy.routeScene(
                new ResearchTreeInteractionPolicy.PointerLayers(false,true,true,false,false,true,true)));
        assertEquals(ResearchTreeInteractionPolicy.PointerTarget.GUIDANCE,ResearchTreeInteractionPolicy.routeScene(
                new ResearchTreeInteractionPolicy.PointerLayers(true,true,true,false,false,true,true)));
    }
    @Test void observedPreviewCannotSurvivePublicationReplacement() {
        var binding=new ResearchTreePreviewBinding();var first=new Object();var second=new Object();
        var preview=new com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview(
                Optional.of(ResearchTechTreeClientFixture.WEAPON_UPGRADE),4,8,true,true,true,true,false,List.of());
        assertSame(preview,binding.resolve(first,preview));
        assertSame(com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview.EMPTY,binding.resolve(second,preview));
        assertSame(com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview.EMPTY,binding.resolve(second,preview));
        var refreshed=new com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview(
                Optional.of(ResearchTechTreeClientFixture.WEAPON_UPGRADE),4,9,true,true,true,true,false,List.of());
        assertSame(refreshed,binding.resolve(second,refreshed));
    }
    private static ResearchTreeScenePolicy policy() {
        return new ResearchTreeScenePolicy(new ResearchTechTreeLayoutPolicy(20,18,8,20,4,1000,6,6),1024,
                new ResearchTreeScenePolicy.NodeMetrics(96,44,3),"migration",new ResearchTreeScenePolicy.RegionMetrics(44,12,16,80,16,10,2));
    }
    private static ResearchTreeCanvas canvas() {
        var c=new ResearchTreeCanvas(new ResearchTreeViewState(),new ResearchTreeCanvas.Style(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24));
        c.setBounds(ResearchTreeScreenLayout.ViewMode.FULLSCREEN,new ResearchTreeScreenLayout.Rect(36,48,400,280));
        var cache=new ResearchTreeSceneCache(p -> new ResearchTreeBranchSceneEngine(),new ResearchTreeSimpleSceneEngine());
        var publication=ResearchTechTreeClientFixture.publication();cache.bind(publication);
        c.setSceneContent(cache.scene(publication,Domain.WEAPONS,policy()),Map.of(),null,null);
        c.viewport().setAnimated(false);return c;
    }
    @Test void independentDecorationsDoNotReplaceFocusOrGeometry() {
        var c=canvas();var id=ResearchTechTreeClientFixture.WEAPON_UPGRADE;var geometry=c.scene().orElseThrow();
        c.focusTarget(id);var camera=c.viewport().snapshot();c.setPinnedNode(ResearchTechTreeClientFixture.WEAPON_ROOT);
        c.setSearchMatches(Set.of(id));c.setActiveSearchMatch(id);
        var body=c.nodeBounds(id).orElseThrow();c.updateHover(c.bounds().x()+c.viewport().viewportX(body.centerX()),c.bounds().y()+c.viewport().viewportY(body.centerY()));
        var d=c.sceneDecoration(id);assertTrue(d.focused());assertTrue(d.hovered());assertTrue(d.activeSearch());assertFalse(d.pinned());
        assertTrue(c.sceneDecoration(ResearchTechTreeClientFixture.WEAPON_ROOT).pinned());
        assertSame(geometry,c.scene().orElseThrow());assertEquals(camera,c.viewport().snapshot());
    }
    @Test void affordabilityDimsOnlyUnprotectedChoices() {
        var c=canvas();var id=ResearchTechTreeClientFixture.WEAPON_UPGRADE;c.setFocusedNode(null);
        c.setAffordabilityFilter(true,Map.of(id,new ResearchAffordabilitySnapshot.Entry(id,ResearchGuidanceSnapshot.State.MISSING_MATERIALS,false)));
        assertTrue(c.sceneDecoration(id).dimmed());c.setPinnedNode(id);assertFalse(c.sceneDecoration(id).dimmed());
        c.setPinnedNode(null);c.setSearchMatches(Set.of(id));assertFalse(c.sceneDecoration(id).dimmed());
        c.setSearchMatches(Set.of());c.setAffordabilityFilter(false,Map.of());assertFalse(c.sceneDecoration(id).dimmed());
    }
    @Test void trackedGoalAndPathHaveIndependentMarks() {
        var c=canvas();var id=ResearchTechTreeClientFixture.WEAPON_UPGRADE;
        var plan=ResearchTreePlanner.plan(c.graph(),id,100).orElseThrow();var geometry=c.scene().orElseThrow();
        c.setTrackedPlan(plan);assertTrue(c.sceneDecoration(id).trackedTarget());assertTrue(c.sceneDecoration(id).trackedPath());
        c.setTrackedPlan(null);assertFalse(c.sceneDecoration(id).trackedTarget());assertSame(geometry,c.scene().orElseThrow());
    }
    @Test void portalsAreValidatedAgainstDisplayedSceneMembership() {
        var c=canvas();var boundary=c.scene().orElseThrow().boundaries().get(0);var link=boundary.links().get(0);
        var target=new ResearchTechTreeLayout.PortalTarget(link.localNodeId(),link.remoteDomain(),link.direction(),boundary.links());
        assertTrue(c.ownsScenePortal(target));
        var b=boundary.bounds();var extent=c.scene().orElseThrow().extent();
        c.viewport().restore(new ResearchTreeViewport.Snapshot(b.centerX()-extent.x()-200,b.centerY()-extent.y()-140,1));
        var hit=c.techTreePortalTargetAt(c.bounds().x()+c.viewport().viewportX(b.centerX()-extent.x()),c.bounds().y()+c.viewport().viewportY(b.centerY()-extent.y()));
        assertEquals(target,hit.orElseThrow());
        c.setContent(ResearchTreeGraph.EMPTY,ResearchTreeLayout.EMPTY,Map.of(),null,null);assertFalse(c.ownsScenePortal(target));
    }
    @Test void withdrawalPurgesCanvasDecorationsAndNavigationTargets() {
        var c=canvas();var id=ResearchTechTreeClientFixture.WEAPON_UPGRADE;
        c.setPinnedNode(id);c.setSearchMatches(Set.of(id));c.setActiveSearchMatch(id);
        c.setTrackedPlan(ResearchTreePlanner.plan(c.graph(),id,100).orElseThrow());
        c.setContent(ResearchTreeGraph.EMPTY,ResearchTreeLayout.EMPTY,Map.of(),null,null);c.setTrackedPlan(null);
        assertTrue(c.scene().isEmpty());assertTrue(c.focusedId().isEmpty());assertTrue(c.searchMatches().isEmpty());
        assertTrue(c.activeSearchMatch().isEmpty());assertFalse(c.sceneDecoration(id).pinned());assertFalse(c.sceneDecoration(id).trackedTarget());
    }
    @Test void failureNeverReusesIncompatibleOrPreviousPublicationContentAndCanRecover() {
        var fail=new AtomicBoolean();var engine=new ResearchTreeBranchSceneEngine();
        ResearchTreeSceneEngine controlled=new ResearchTreeSceneEngine(){
            public String revision(){return engine.revision();}public boolean requiresGroupedRouting(){return true;}
            public ResearchTreeScene layout(ResearchTreeSceneInput input){if(fail.get())throw new IllegalArgumentException("injected");return engine.layout(input);}
        };
        var cache=new ResearchTreeSceneCache(p -> controlled,new ResearchTreeSimpleSceneEngine());
        var first=ResearchTechTreeClientFixture.publication();cache.bind(first);var old=cache.scene(first,Domain.WEAPONS,policy());
        fail.set(true);var next=ResearchTechTreeClientFixture.publicationWithSameTierWeaponChain();cache.bind(next);
        var rejected=cache.scene(next,Domain.WEAPONS,policy());assertEquals(ResearchTreeSceneCache.Status.UNAVAILABLE,rejected.status());
        assertFalse(cache.isCurrent(old));assertTrue(rejected.geometry().isEmpty());assertSame(next,rejected.publication());
        fail.set(false);var restored=cache.scene(next,Domain.WEAPONS,policy());assertEquals(ResearchTreeSceneCache.Status.VALID,restored.status());
        cache.clear();assertFalse(cache.isCurrent(restored));assertThrows(IllegalArgumentException.class,()->cache.scene(next,Domain.WEAPONS,policy()));
    }
    @Test void connectionScopeResetDropsCamerasQueriesAndPins() {
        var catalog=ResearchTechTreeProjectionBuilder.build(ResearchTechTreeClientFixture.publication());
        var state=new ResearchTechTreeViewState();state.retain(catalog,null);state.selectDomain(Domain.WEAPONS,catalog);
        var scene=canvas().scene().orElseThrow();state.saveSceneCamera(ResearchTechTreeViewState.Surface.FULLSCREEN,scene.key(),new ResearchTreeViewport.Snapshot(40,50,.8));
        state.clear();assertTrue(state.selectedDomain().isEmpty());assertTrue(state.snapshot(Domain.WEAPONS).isEmpty());
        state.retain(catalog,null);assertTrue(state.sceneCamera(ResearchTechTreeViewState.Surface.FULLSCREEN,scene.key()).isEmpty());
    }
    @Test void changingUnusedRowLayoutCannotInvalidateAMatchingSceneCamera() {
        var catalog = ResearchTechTreeProjectionBuilder.build(ResearchTechTreeClientFixture.publication());
        var state = new ResearchTechTreeViewState();
        state.retain(catalog, null);
        var key = canvas().scene().orElseThrow().key();
        var camera = new ResearchTreeViewport.Snapshot(50, 70, .7);
        state.saveSceneCamera(ResearchTechTreeViewState.Surface.FULLSCREEN, key, camera);
        var changedRows = ResearchTechTreeLayoutEngine.layoutCatalog(catalog,
                new ResearchTechTreeLayoutPolicy(30, 22, 12, 25, 6, 500, 4, 4));
        state.retain(catalog, changedRows, null);
        assertEquals(camera, state.sceneCamera(ResearchTechTreeViewState.Surface.FULLSCREEN, key).orElseThrow());
    }
    @Test void responsiveHelpFitsWithFixedDismissAction() {
        for(int w:List.of(320,420,667))for(int h:List.of(240,434)) {
            var help=ResearchTreeWorkbenchLayout.create(w,h).help();assertTrue(help.panel().inside(w,h));assertTrue(help.panel().contains(help.dismiss()));
            assertTrue(help.dismiss().y()-help.panel().y()>=100);
        }
    }
}
