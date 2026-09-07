package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.research.tree.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

class ResearchTreeFinalScenePathTest {
    @Test void compatibilityUpdateRebuildsLayoutsAfterSceneOnlyPreparation() {
        var cache = new ResearchTreeProjectionCache();
        var publication = AuthoredBranchFixture.publication(true);
        cache.updateForScene(publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);
        assertFalse(cache.techTreeLayouts().available());
        cache.update(publication);
        assertTrue(cache.techTreeLayouts().matches(cache.techTreeProjections()));
        assertEquals(8, cache.techTreeLayouts().layout(Domain.WEAPONS).orElseThrow().graphLayout().nodes().size());
    }
    @Test void groupedFailureSkipsIncapableSimpleFallback() {
        var publication=AuthoredBranchFixture.publication(true);
        var cache=new ResearchTreeSceneCache(p -> new ResearchTreeSceneEngine() {
            public String revision() { return "rejected-grouped"; }
            public boolean requiresGroupedRouting() { return true; }
            public ResearchTreeScene layout(ResearchTreeSceneInput input) { throw new IllegalArgumentException("budget exceeded"); }
        },new ResearchTreeSimpleSceneEngine());
        cache.bind(publication);
        var result=cache.scene(publication,Domain.WEAPONS,ResearchTreeScenePolicy.compatibility(ResearchTechTreeLayoutPolicy.DEFAULT,1024));
        assertEquals(ResearchTreeSceneCache.Status.UNAVAILABLE,result.status());
        assertTrue(result.geometry().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("simple fallback cannot provide")));
        assertTrue(cache.isCurrent(result));
    }
    @Test void typedUpdatesNeverInvokeLegacyLayoutFactories() {
        var cache = new ResearchTreeProjectionCache(
                (a,b,c) -> { throw new AssertionError("dormant overview"); },
                (a,b) -> { throw new AssertionError("dormant fallback"); },
                (a,b,c) -> { throw new AssertionError("dormant branch composer"); },
                (a,b) -> { throw new AssertionError("dormant responsive compactor"); });
        var publication = AuthoredBranchFixture.publication(true);
        assertTrue(cache.updateForScene(publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW).geometryChanged());
        assertSame(publication,cache.publication());
        assertEquals(0,cache.cachedLayoutCount());
        assertEquals(8,cache.techTreeProjections().projection(Domain.WEAPONS).orElseThrow().graph().nodes().size());
        assertFalse(cache.updateForScene(publication,ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW).geometryChanged());
        assertEquals(0,cache.cachedLayoutCount());
        var state = new ResearchTechTreeViewState();
        state.retainScenes(cache.techTreeProjections(),publication.graph().nodes().get(0).blueprintId());
        assertEquals(Optional.of(Domain.WEAPONS),state.selectedDomain());
        assertTrue(state.focusedNode().isPresent());
        state.retainScenes(ResearchTechTreeProjectionCatalog.EMPTY,null);
        assertTrue(state.selectedDomain().isEmpty());assertTrue(state.focusedNode().isEmpty());
    }

    @Test void containmentChangesInvalidateGeometryButLabelsDoNot() {
        var publication = AuthoredBranchFixture.publication(true);
        var cache = new ResearchTreeProjectionCache();
        cache.updateForScene(publication,ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);
        for (boolean structural : List.of(false,true)) {
            var tree = publication.techTree(); var domain = tree.domains().get(0);
            var branches = domain.branches().stream().map(b -> new ResearchTechTreeBranch(b.id(),b.parent(),
                    "Renamed",b.translationKey(),structural?b.order()+1:b.order(),b.anchor(),b.members())).toList();
            var next = new ResearchTreePublication(publication.graph(),publication.presentation(),
                    new ResearchTechTreePresentation(tree.treeId(),tree.title(),tree.translationKey(),tree.iconNodeId(),
                            tree.tiers(),tree.bands(),tree.maxNodesPerLayer(),List.of(new ResearchTechTreePresentation.DomainView(
                            domain.domain(),domain.title(),domain.translationKey(),domain.iconNodeId(),domain.lanes(),branches))));
            assertEquals(structural,cache.updateForScene(next,ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW).geometryChanged());
        }
    }
}
