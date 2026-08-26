package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutEngine;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeProjectionCacheTest {
    @Test
    void branchesContainOnlyTheirMembersAndRetainCrossGroupLinks() {
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        ResearchTreeLayout globalLayout = ResearchTreeLayoutEngine.layout(publication);
        cache.update(publication, globalLayout);

        assertEquals(0, cache.cachedProjectionCount());
        ResearchTreeProjection branch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));

        assertEquals(List.of(id("test:a"), id("test:b")), branch.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList());
        assertEquals(List.of(new ResearchTreeGraph.Edge(id("test:a"), id("test:b"))),
                branch.graph().edges());
        assertEquals(1, branch.crossGroupLinks().size());
        ResearchTreeProjection.CrossGroupLink unlockLink =
                new ResearchTreeProjection.CrossGroupLink(
                        id("test:b"),
                        id("test:c"),
                        id("test:second"),
                        ResearchTreeProjection.Direction.UNLOCK);
        assertEquals(unlockLink, branch.crossGroupLinks().get(0));
        assertTrue(cache.isPublishedCrossGroupLink(unlockLink));
        assertFalse(cache.isPublishedCrossGroupLink(new ResearchTreeProjection.CrossGroupLink(
                id("test:b"), id("test:c"), id("test:first"),
                ResearchTreeProjection.Direction.UNLOCK)));
        assertFalse(cache.isPublishedCrossGroupLink(new ResearchTreeProjection.CrossGroupLink(
                id("test:b"), id("test:c"), id("test:second"),
                ResearchTreeProjection.Direction.REQUIREMENT)));
        assertEquals(1, cache.cachedProjectionCount());
        assertEquals(List.of(id("test:first")), branch.layout().groupRegions().stream()
                .map(ResearchTreeLayout.GroupRegion::groupId)
                .toList());
        assertTrue(branch.layout().position(id("test:b")).orElseThrow().y()
                < branch.layout().position(id("test:a")).orElseThrow().y());

        ResearchTreeProjection second = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:second"));
        assertEquals(0, second.graph().nodes().get(0).prerequisiteCount());
        assertEquals(ResearchTreeProjection.Direction.REQUIREMENT,
                second.crossGroupLinks().get(0).direction());
        assertTrue(cache.isPublishedCrossGroupLink(second.crossGroupLinks().get(0)));
        assertFalse(cache.isPublishedCrossGroupLink(null));

        ResearchTreeProjection all = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                id("test:first"));
        assertSame(publication.graph(), all.graph());
        assertEquals(publication.graph().edges(), all.graph().edges());
        assertTrue(all.crossGroupLinks().isEmpty());
        assertTrue(all.groupId().isEmpty());
        assertSame(globalLayout, all.layout());
        assertTrue(all.layout().groupRegions().isEmpty());
        assertEquals(List.of("rifle"), all.layout().categoryLanes().stream()
                .map(ResearchTreeLayout.CategoryLane::key)
                .toList());
        assertThrows(IllegalArgumentException.class, () -> cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:missing")));
    }

    @Test
    void stateOnlyPublicationRebuildsNodesButReusesLazyLayouts() {
        ResearchTreePublication first = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        ResearchTreeLayout firstGlobalLayout = ResearchTreeLayoutEngine.layout(first);
        assertTrue(cache.update(first, firstGlobalLayout));
        ResearchTreeProjection firstAll = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        ResearchTreeProjection firstBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));
        int cachedLayouts = cache.cachedLayoutCount();

        ResearchTreePublication stateOnly = publication(
                ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE);
        assertFalse(cache.update(stateOnly, ResearchTreeLayoutEngine.layout(stateOnly)));
        assertEquals(0, cache.cachedProjectionCount());
        ResearchTreeProjection nextBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));

        assertSame(firstBranch.layout(), nextBranch.layout());
        assertSame(firstAll.layout(), cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null).layout());
        assertEquals(cachedLayouts, cache.cachedLayoutCount());
        assertEquals(ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE,
                nextBranch.graph().nodes().get(0).availability());
    }

    @Test
    void topologyChangeInvalidatesLayoutsAndEmptyPublicationHasAValidBranch() {
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        cache.update(publication, ResearchTreeLayoutEngine.layout(publication));
        cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));
        assertTrue(cache.cachedLayoutCount() > 1);

        assertTrue(cache.update(
                ResearchTreePublication.EMPTY,
                ResearchTreeLayoutEngine.layout(ResearchTreePublication.EMPTY)));
        assertEquals(1, cache.cachedLayoutCount());
        ResearchTreeProjection emptyBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                null);
        assertTrue(emptyBranch.graph().nodes().isEmpty());
        assertTrue(emptyBranch.layout().nodes().isEmpty());
        assertTrue(emptyBranch.groupId().isEmpty());
    }

    @Test
    void projectionContractsRejectGroupedGlobalAndMalformedBranchPortals() {
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreePresentation.Group first = publication.presentation()
                .group(id("test:first"))
                .orElseThrow();
        ResearchTreeGraph firstGraph = new ResearchTreeGraph(
                publication.graph().nodes().subList(0, 2),
                List.of(new ResearchTreeGraph.Edge(id("test:a"), id("test:b"))));
        ResearchTreeLayout branchLayout =
                com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGroupedLayoutEngine
                        .branch(firstGraph, first, 0);

        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeProjection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                Optional.empty(),
                firstGraph,
                branchLayout,
                List.of()));

        ResearchTreeProjection.CrossGroupLink duplicate =
                new ResearchTreeProjection.CrossGroupLink(
                        id("test:b"), id("test:c"), id("test:second"),
                        ResearchTreeProjection.Direction.UNLOCK);
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeProjection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                Optional.of(id("test:first")),
                firstGraph,
                branchLayout,
                List.of(duplicate, duplicate)));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeProjection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                Optional.of(id("test:first")),
                firstGraph,
                branchLayout,
                List.of(new ResearchTreeProjection.CrossGroupLink(
                        id("test:b"), id("test:c"), id("test:first"),
                        ResearchTreeProjection.Direction.UNLOCK))));
    }

    private static ResearchTreePublication publication(
            ResearchTreeGraph.Availability availability) {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0, availability),
                        node(1, "test:b", 1, availability),
                        node(2, "test:c", 1, availability)),
                List.of(
                        new ResearchTreeGraph.Edge(id("test:a"), id("test:b")),
                        new ResearchTreeGraph.Edge(id("test:b"), id("test:c"))));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:first"),
                        "First",
                        Optional.empty(),
                        Optional.of(id("test:a")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(
                                new ResearchTreePresentation.Member(id("test:a"), 0, 0),
                                new ResearchTreePresentation.Member(id("test:b"), 1, 0))),
                new ResearchTreePresentation.Group(
                        id("test:second"),
                        "Second",
                        Optional.empty(),
                        Optional.of(id("test:c")),
                        1,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(new ResearchTreePresentation.Member(id("test:c"), 2, 0)))));
        return new ResearchTreePublication(graph, presentation);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            String value,
            int prerequisites,
            ResearchTreeGraph.Availability availability) {
        ResourceLocation id = id(value);
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "name." + id.getPath(),
                "rifle",
                id("test:slot/" + id.getPath()),
                JournalVisibility.FULL,
                false,
                false,
                false,
                8,
                0,
                prerequisites,
                0,
                availability);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
