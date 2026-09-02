package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBranchLayoutComposer;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGroupSkeletonCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeOverviewLayoutComposer;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeProjectionCacheTest {
    @Test
    void legacyProjectionApiRejectsTheTypedTechTreeView() {
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        assertThrows(IllegalArgumentException.class, () -> cache.projection(
                ResearchTreePresentationContract.BrowseView.TECH_TREE,
                null));
    }

    @Test
    void unseededCacheHasAValidEmptyAllWeaponsProjection() {
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();

        ResearchTreeProjection projection = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);

        assertEquals(ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                projection.view());
        assertTrue(projection.graph().nodes().isEmpty());
        assertTrue(projection.layout().nodes().isEmpty());
        assertTrue(projection.groupId().isEmpty());
        assertEquals(1, cache.cachedLayoutCount());
    }

    @Test
    void mixedAuthoritativeGraphKeepsLegacyProjectionsWeaponScoped() {
        ResearchTreePublication publication = ResearchTechTreeClientFixture.publication();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();

        cache.update(publication);

        assertEquals(5, cache.publication().graph().nodes().size());
        assertEquals(3, cache.publication().legacyGraph().nodes().size());
        assertEquals(3, cache.techTreeProjections().domains().size());
        ResearchTreeProjection allWeapons = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        assertEquals(
                List.of(
                        ResearchTechTreeClientFixture.WEAPON_ROOT,
                        ResearchTechTreeClientFixture.WEAPON_UPGRADE),
                allWeapons.graph().nodes().stream()
                        .map(ResearchTreeGraph.Node::blueprintId)
                        .toList());
        assertFalse(allWeapons.graph().nodes().stream().anyMatch(node ->
                node.blueprintId().equals(ResearchTechTreeClientFixture.AMMO)
                        || node.blueprintId().equals(ResearchTechTreeClientFixture.SCOPE)));
        assertEquals(allWeapons.graph().nodes().size(), allWeapons.layout().nodes().size());
    }

    @Test
    void branchesContainOnlyTheirMembersAndRetainCrossGroupLinks() {
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        cache.update(publication);

        assertEquals(0, cache.cachedProjectionCount());
        assertEquals(2, cache.groupSkeletons().groups().size());
        assertEquals(1, cache.groupSkeletons().crossGroupEdges().size());
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
        ResearchTreeCanvas branchCanvas = canvas();
        branchCanvas.setContent(
                branch.graph(),
                branch.layout(),
                Map.of(),
                null,
                null,
                branch.crossGroupLinks());
        assertEquals(1, branchCanvas.portalPlacements().size());
        ResearchTreeCanvas.PortalPlacement branchPortal =
                branchCanvas.portalPlacements().get(0);
        assertTrue(branchPortal.x() >= 0);
        assertTrue(branchPortal.y() >= 0);
        assertTrue(branchPortal.x() + 14 <= branch.layout().width());
        assertTrue(branchPortal.y() + 14 <= branch.layout().height());

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
        assertTrue(all.layout().groupRegions().isEmpty());
        assertTrue(all.layout().categoryLanes().isEmpty());
        assertTrue(all.layout().position(id("test:b")).orElseThrow().y()
                < all.layout().position(id("test:a")).orElseThrow().y());
        assertEquals(
                ResearchTreeOverviewLayoutComposer.compose(
                        publication,
                        cache.groupSkeletons(),
                        ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW),
                all.layout());
        assertThrows(IllegalArgumentException.class, () -> cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:missing")));
    }

    @Test
    void branchPortalWidthIncludesBothRequiredSideInsets() {
        AtomicReference<Integer> minimumWidth = new AtomicReference<>();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                ResearchTreeOverviewLayoutComposer::compose,
                (skeleton, requestedWidth, policy) -> {
                    minimumWidth.set(requestedWidth);
                    return com.gamergaming.taczweaponblueprints.research.tree
                            .ResearchTreeBranchLayoutComposer.compose(
                                    skeleton, requestedWidth, policy);
                });
        cache.update(publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED));

        ResearchTreeProjection branch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));

        assertEquals(
                ResearchTreeCanvas.PORTAL_SIZE
                        + 2 * ResearchTreeLayout.PORTAL_BANK_SIDE_PADDING,
                minimumWidth.get());
        assertTrue(branch.layout().width() >= minimumWidth.get());
    }

    @Test
    void allWeaponsUsesTheCuratedSubsetWhileExcludedBranchesRemainAvailable() {
        ResearchTreePublication base = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreePresentation.Group first = base.presentation().groups().get(0);
        ResearchTreePresentation.Group second = base.presentation().groups().get(1);
        ResearchTreePublication publication = new ResearchTreePublication(
                base.graph(),
                new ResearchTreePresentation(List.of(
                        first,
                        new ResearchTreePresentation.Group(
                                second.id(),
                                second.title(),
                                second.translationKey(),
                                second.iconNodeId(),
                                second.order(),
                                second.kind(),
                                false,
                                second.members()))));
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        cache.update(publication);

        ResearchTreeProjection overview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        ResearchTreeProjection.CrossGroupLink boundary =
                new ResearchTreeProjection.CrossGroupLink(
                        id("test:b"),
                        id("test:c"),
                        id("test:second"),
                        ResearchTreeProjection.Direction.UNLOCK);
        assertEquals(List.of(id("test:a"), id("test:b")), overview.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList());
        assertEquals(List.of(new ResearchTreeGraph.Edge(id("test:a"), id("test:b"))),
                overview.graph().edges());
        assertEquals(List.of(boundary), overview.crossGroupLinks());
        assertTrue(cache.isPublishedCrossGroupLink(boundary));
        assertEquals(overview.graph().nodes().size(), overview.layout().nodes().size());
        assertTrue(overview.layout().categoryLanes().isEmpty());
        assertTrue(overview.layout().groupRegions().isEmpty());
        ResearchTreeCanvas canvas = canvas();
        canvas.setContent(
                overview.graph(),
                overview.layout(),
                Map.of(),
                null,
                null,
                overview.crossGroupLinks());
        assertEquals(1, canvas.portalPlacements().size());

        ResearchTreeProjection excludedBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:second"));
        assertEquals(List.of(id("test:c")), excludedBranch.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList());
    }

    private static ResearchTreeCanvas canvas() {
        return new ResearchTreeCanvas(
                new ResearchTreeViewState(),
                new ResearchTreeCanvas.Style(
                        1, 2, 3, 4, 5, 6, 7, 8, 9,
                        10, 11, 12, 13, 14, 15, 16,
                        17, 18, 19, 20, 21, 22, 23, 24));
    }

    @Test
    void overviewMembershipReloadInvalidatesOnlyTheAffectedProjectionTopology() {
        ResearchTreePublication included = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        assertTrue(cache.update(included));
        ResearchTreeProjection initialOverview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        ResearchTreeLayout initialLayout = initialOverview.layout();

        ResearchTreePresentation.Group first = included.presentation().groups().get(0);
        ResearchTreePresentation.Group second = included.presentation().groups().get(1);
        ResearchTreePublication excluded = new ResearchTreePublication(
                included.graph(),
                new ResearchTreePresentation(List.of(
                        first,
                        new ResearchTreePresentation.Group(
                                second.id(),
                                second.title(),
                                second.translationKey(),
                                second.iconNodeId(),
                                second.order(),
                                second.kind(),
                                false,
                                second.members()))));

        assertTrue(cache.update(excluded));
        ResearchTreeProjection reduced = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        assertEquals(2, reduced.graph().nodes().size());
        assertNotSame(initialLayout, reduced.layout());
        assertEquals(1, reduced.crossGroupLinks().size());

        assertTrue(cache.update(included));
        ResearchTreeProjection restored = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        assertEquals(3, restored.graph().nodes().size());
        assertTrue(restored.crossGroupLinks().isEmpty());
    }

    @Test
    void stateOnlyPublicationRebuildsNodesButReusesLazyLayouts() {
        ResearchTreePublication first = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        assertTrue(cache.update(first));
        ResearchTreeProjection firstAll = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        ResearchTreeProjection firstBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));
        assertEquals(1, cache.unlocks().unlocksAfterLearning(id("test:a")));
        var firstSkeletons = cache.groupSkeletons();
        int cachedLayouts = cache.cachedLayoutCount();

        ResearchTreePublication stateOnly = publication(
                ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE);
        assertFalse(cache.update(stateOnly));
        assertEquals(0, cache.cachedProjectionCount());
        ResearchTreeProjection nextBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));

        assertSame(firstBranch.layout(), nextBranch.layout());
        assertSame(firstSkeletons, cache.groupSkeletons());
        assertSame(firstAll.layout(), cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null).layout());
        assertEquals(cachedLayouts, cache.cachedLayoutCount());
        assertEquals(ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE,
                nextBranch.graph().nodes().get(0).availability());
        assertEquals(0, cache.unlocks().unlocksAfterLearning(id("test:a")));
    }

    @Test
    void topologyChangeInvalidatesLayoutsAndEmptyPublicationHasAValidBranch() {
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        cache.update(publication);
        cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));
        assertTrue(cache.cachedLayoutCount() > 1);

        assertTrue(cache.update(ResearchTreePublication.EMPTY));
        assertSame(ResearchTreeGroupSkeletonCatalog.EMPTY, cache.groupSkeletons());
        assertEquals(1, cache.cachedLayoutCount());
        ResearchTreeProjection emptyBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                null);
        assertTrue(emptyBranch.graph().nodes().isEmpty());
        assertTrue(emptyBranch.layout().nodes().isEmpty());
        assertTrue(emptyBranch.groupId().isEmpty());

        ResearchTreeProjection emptyOverview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        assertTrue(emptyOverview.graph().nodes().isEmpty());
        assertTrue(emptyOverview.layout().nodes().isEmpty());
    }

    @Test
    void rejectedAtlasCompositionLeavesTheLastValidPublicationFullyIntact() {
        AtomicBoolean rejectComposition = new AtomicBoolean();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> {
                    if (rejectComposition.get()) {
                        throw new IllegalStateException("fixture composition failure");
                    }
                    return ResearchTreeOverviewLayoutComposer.compose(
                            publication, skeletons, policy);
                },
                (publication, policy) -> {
                    throw new IllegalStateException("fixture fallback failure");
                });
        ResearchTreePublication initial = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        cache.update(initial);
        ResearchTreeProjection initialOverview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS, null);
        ResearchTreeGroupSkeletonCatalog initialSkeletons = cache.groupSkeletons();
        int initialLayoutCount = cache.cachedLayoutCount();

        ResearchTreePresentation.Group first = initial.presentation().groups().get(0);
        ResearchTreePresentation.Group second = initial.presentation().groups().get(1);
        ResearchTreePublication replacement = new ResearchTreePublication(
                initial.graph(),
                new ResearchTreePresentation(List.of(
                        new ResearchTreePresentation.Group(
                                first.id(),
                                "Replacement",
                                first.translationKey(),
                                first.iconNodeId(),
                                first.order(),
                                first.kind(),
                                first.includedInOverview(),
                                first.members()),
                        second)));
        rejectComposition.set(true);

        assertThrows(IllegalStateException.class, () -> cache.update(replacement));
        assertSame(initial, cache.publication());
        assertSame(initialSkeletons, cache.groupSkeletons());
        assertEquals(initialLayoutCount, cache.cachedLayoutCount());
        ResearchTreeProjection retained = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS, null);
        assertSame(initialOverview.layout(), retained.layout());
        assertSame(initialOverview.graph(), retained.graph());
    }

    @Test
    void firstPublicationUsesSamePublicationFallbackWhenThePolishedAtlasFails() {
        AtomicBoolean rejectComposition = new AtomicBoolean(true);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> {
                    if (rejectComposition.get()) {
                        throw new IllegalStateException("fixture primary composition failure");
                    }
                    return ResearchTreeOverviewLayoutComposer.compose(
                            publication, skeletons, policy);
                });
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);

        assertTrue(cache.update(publication));
        assertTrue(cache.overviewFallbackActive());
        ResearchTreeProjection overview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS, null);
        assertEquals(publication.graph().nodes().size(), overview.graph().nodes().size());
        assertEquals(publication.graph().nodes().size(), overview.layout().nodes().size());
        ResearchTreeProjection branch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));
        assertFalse(branch.graph().nodes().isEmpty());

        rejectComposition.set(false);
        assertTrue(cache.update(publication, policyWithNodeGap(2)));
        assertFalse(cache.overviewFallbackActive());
        assertEquals(publication.graph().nodes().size(), cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null).layout().nodes().size());
    }

    @Test
    void firstPublicationFallbackReservesAndPlacesOverviewBoundaryPortals() {
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> {
                    throw new IllegalStateException("fixture primary composition failure");
                });
        ResearchTreePublication publication = publicationWithOverviewBoundary();

        assertTrue(cache.update(publication));
        assertTrue(cache.overviewFallbackActive());
        ResearchTreeProjection overview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        assertEquals(List.of(id("test:a"), id("test:b")), overview.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList());
        assertEquals(1, overview.crossGroupLinks().size());

        ResearchTreeCanvas canvas = new ResearchTreeCanvas(
                new ResearchTreeViewState(),
                new ResearchTreeCanvas.Style(
                        1, 2, 3, 4, 5, 6, 7, 8,
                        9, 10, 11, 12, 13, 14, 15, 16,
                        17, 18, 19, 20, 21, 22, 23, 24));
        canvas.setContent(
                overview.graph(),
                overview.layout(),
                Map.of(),
                null,
                null,
                overview.crossGroupLinks());

        assertEquals(1, canvas.portalPlacements().size());
        ResearchTreeCanvas.PortalPlacement portal = canvas.portalPlacements().get(0);
        assertTrue(portal.x() >= 0);
        assertTrue(portal.x() + ResearchTreeLayout.PORTAL_SIZE <= overview.layout().width());
        assertTrue(portal.y() >= 0);
        assertTrue(portal.y() + ResearchTreeLayout.PORTAL_SIZE <= overview.layout().height());
    }

    @Test
    void cacheRejectsAMissingOverviewLayoutFactory() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeProjectionCache(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeProjectionCache(
                        ResearchTreeOverviewLayoutComposer::compose,
                        (ResearchTreeProjectionCache.BranchLayoutFactory) null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeProjectionCache(
                        ResearchTreeOverviewLayoutComposer::compose,
                        (ResearchTreeProjectionCache.FallbackLayoutFactory) null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeProjectionCache().update(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeProjectionCache().update(
                        ResearchTreePublication.EMPTY, null));
    }

    @Test
    void missingEmptyLayoutIsPreparedBeforeTheFirstCacheCommit() {
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> {
                    throw new IllegalStateException("fixture empty-layout failure");
                },
                (publication, policy) -> {
                    throw new IllegalStateException("fixture empty-fallback failure");
                });

        assertThrows(IllegalStateException.class,
                () -> cache.update(ResearchTreePublication.EMPTY));
        assertSame(ResearchTreePublication.EMPTY, cache.publication());
        assertSame(ResearchTreeGroupSkeletonCatalog.EMPTY, cache.groupSkeletons());
        assertEquals(0, cache.cachedProjectionCount());
        assertEquals(0, cache.cachedLayoutCount());
    }

    @Test
    void policyChangeAtomicallyInvalidatesOverviewBranchesAndSkeletons() {
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        assertTrue(cache.update(publication));
        ResearchTreeProjection initialOverview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS, null);
        ResearchTreeProjection initialBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));
        ResearchTreeGroupSkeletonCatalog initialSkeletons = cache.groupSkeletons();

        ResearchTreeLayoutPolicy compact = policyWithNodeGap(2);
        assertTrue(cache.update(publication, compact));
        assertNotSame(initialSkeletons, cache.groupSkeletons());
        assertEquals(1, cache.cachedLayoutCount());

        ResearchTreeProjection compactOverview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS, null);
        ResearchTreeProjection compactBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));
        assertNotSame(initialOverview.layout(), compactOverview.layout());
        assertNotSame(initialBranch.layout(), compactBranch.layout());
        assertEquals(
                ResearchTreeOverviewLayoutComposer.compose(
                        publication, cache.groupSkeletons(), compact),
                compactOverview.layout());

        ResearchTreePublication stateOnly = publication(
                ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE);
        assertFalse(cache.update(stateOnly, policyWithNodeGap(2)));
        assertSame(compactOverview.layout(), cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null).layout());
        assertSame(compactBranch.layout(), cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first")).layout());
    }

    @Test
    void rejectedPolicyChangeRetainsTheCompleteLastValidCache() {
        AtomicBoolean rejectComposition = new AtomicBoolean();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> {
                    if (rejectComposition.get()) {
                        throw new IllegalStateException("fixture policy failure");
                    }
                    return ResearchTreeOverviewLayoutComposer.compose(
                            publication, skeletons, policy);
                },
                (publication, policy) -> {
                    throw new IllegalStateException("fixture policy fallback failure");
                });
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        cache.update(publication);
        ResearchTreeProjection initialOverview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS, null);
        ResearchTreeProjection initialBranch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));
        ResearchTreeGroupSkeletonCatalog initialSkeletons = cache.groupSkeletons();
        int initialLayoutCount = cache.cachedLayoutCount();

        rejectComposition.set(true);
        ResearchTreeLayoutPolicy replacement = policyWithNodeGap(2);
        assertThrows(IllegalStateException.class,
                () -> cache.update(publication, replacement));
        assertSame(publication, cache.publication());
        assertSame(initialSkeletons, cache.groupSkeletons());
        assertEquals(initialLayoutCount, cache.cachedLayoutCount());
        assertSame(initialOverview.layout(), cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null).layout());
        assertSame(initialBranch.layout(), cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first")).layout());

        rejectComposition.set(false);
        assertTrue(cache.update(publication, replacement));
    }

    @Test
    void rejectedClientLayoutRecoversWithBalancedBeforeTheFirstCommit() {
        ResearchTechTreeLayoutPolicy balancedTechPolicy =
                ResearchTechTreeLayoutPolicy.fromShared(
                        ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                ResearchTreeOverviewLayoutComposer::compose,
                ResearchTreeBranchLayoutComposer::compose,
                (projections, policy) -> {
                    if (!balancedTechPolicy.equals(policy)) {
                        throw new IllegalStateException("fixture requested-layout failure");
                    }
                    return ResearchTechTreeLayoutEngine.layoutCatalog(projections, policy);
                });
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);

        ResearchTreeProjectionCache.UpdateOutcome outcome =
                cache.updateWithBalancedFallback(publication, policyWithNodeGap(2));

        assertTrue(outcome.geometryChanged());
        assertTrue(outcome.usedBalancedFallback());
        assertEquals("fixture requested-layout failure",
                outcome.recoveredLayoutFailure().orElseThrow().getMessage());
        assertSame(publication, cache.publication());
        assertFalse(cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null).graph().nodes().isEmpty());
    }

    @Test
    void injectedBranchComposerReceivesTheSamePolicyAsTheOverview() {
        AtomicReference<ResearchTreeLayoutPolicy> observed = new AtomicReference<>();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                ResearchTreeOverviewLayoutComposer::compose,
                (skeleton, minimumPortalWidth, policy) -> {
                    observed.set(policy);
                    return com.gamergaming.taczweaponblueprints.research.tree
                            .ResearchTreeBranchLayoutComposer.compose(
                                    skeleton, minimumPortalWidth, policy);
                });
        ResearchTreeLayoutPolicy policy = policyWithNodeGap(2);
        cache.update(publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED), policy);

        cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));

        assertSame(policy, observed.get());
    }

    @Test
    void maximumCrossGroupCatalogBuildsEveryBranchWithBoundedIndexedWork() {
        ResearchTreePublication publication = maximumCrossGroupPublication();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();

        assertTimeout(Duration.ofSeconds(15), () -> {
            assertTrue(cache.update(publication));
            for (ResearchTreePresentation.Group group
                    : publication.presentation().groups()) {
                ResearchTreeProjection branch = cache.projection(
                        ResearchTreePresentationContract.BrowseView.BRANCHES,
                        group.id());
                branch.crossGroupLinks().forEach(link ->
                        assertTrue(cache.isPublishedCrossGroupLink(link)));
            }
        });

        assertEquals(ResearchTreeGraph.MAX_NODES, cache.cachedProjectionCount());
        assertEquals(ResearchTreeGraph.MAX_NODES + 1, cache.cachedLayoutCount());
        assertEquals(ResearchTreeGraph.MAX_EDGES,
                cache.groupSkeletons().crossGroupEdges().size());
        ResearchTreeProjection firstRoot = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("phase_seven:group/0"));
        assertFalse(firstRoot.crossGroupLinks().isEmpty());
    }

    @Test
    void rejectedLazyBranchCompositionDoesNotPoisonEitherCache() {
        AtomicBoolean rejectBranch = new AtomicBoolean(true);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                ResearchTreeOverviewLayoutComposer::compose,
                (skeleton, minimumPortalWidth, policy) -> {
                    if (rejectBranch.get()) {
                        throw new IllegalStateException("fixture branch failure");
                    }
                    return com.gamergaming.taczweaponblueprints.research.tree
                            .ResearchTreeBranchLayoutComposer.compose(
                                    skeleton, minimumPortalWidth, policy);
                });
        cache.update(publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED));

        assertThrows(IllegalStateException.class, () -> cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first")));
        assertEquals(0, cache.cachedProjectionCount());
        assertEquals(1, cache.cachedLayoutCount());

        rejectBranch.set(false);
        ResearchTreeProjection recovered = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                id("test:first"));
        assertEquals(id("test:first"), recovered.groupId().orElseThrow());
        assertEquals(1, cache.cachedProjectionCount());
        assertEquals(2, cache.cachedLayoutCount());
    }

    @Test
    void repeatedPolicyAndTopologyChurnRetainsOnlyCurrentGeometry() {
        ResearchTreePublication publication = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        for (int iteration = 0; iteration < 64; iteration++) {
            ResearchTreeLayoutPolicy policy = policyWithNodeGap(iteration % 8);
            assertTrue(cache.update(publication, policy));
            cache.projection(
                    ResearchTreePresentationContract.BrowseView.BRANCHES,
                    id("test:first"));
            cache.projection(
                    ResearchTreePresentationContract.BrowseView.BRANCHES,
                    id("test:second"));
            assertEquals(3, cache.cachedLayoutCount());
            assertEquals(2, cache.cachedProjectionCount());

            assertTrue(cache.update(ResearchTreePublication.EMPTY, policy));
            assertEquals(1, cache.cachedLayoutCount());
            assertEquals(0, cache.cachedProjectionCount());
        }
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

    private static ResearchTreeLayoutPolicy policyWithNodeGap(int nodeGap) {
        ResearchTreeLayoutPolicy defaults = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;
        return new ResearchTreeLayoutPolicy(
                defaults.canvasPadding(),
                nodeGap,
                defaults.tierGap(),
                defaults.componentGap(),
                defaults.intraGroupGap(),
                defaults.interGroupGap(),
                defaults.groupPadding(),
                defaults.groupHeaderHeight(),
                defaults.portalPadding(),
                defaults.maxRankBlockWidth(),
                defaults.orderingSweeps(),
                defaults.compactionSweeps());
    }

    private static ResearchTreePublication maximumCrossGroupPublication() {
        int rootCount = 1_024;
        int dependentCount = ResearchTreeGraph.MAX_EDGES
                / BlueprintResearchRule.MAX_PREREQUISITES;
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(ResearchTreeGraph.MAX_NODES);
        List<ResearchTreePresentation.Group> groups =
                new ArrayList<>(ResearchTreePresentation.MAX_GROUPS);
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>(ResearchTreeGraph.MAX_EDGES);
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            ResourceLocation nodeId = id("phase_seven:node/" + ordinal);
            boolean dependent = ordinal >= rootCount
                    && ordinal < rootCount + dependentCount;
            nodes.add(new ResearchTreeGraph.Node(
                    ordinal,
                    nodeId,
                    "fixture.phase_seven.maximum",
                    "rifle",
                    id("minecraft:paper"),
                    JournalVisibility.FULL,
                    false,
                    false,
                    true,
                    1,
                    0,
                    dependent ? BlueprintResearchRule.MAX_PREREQUISITES : 0,
                    0,
                    ResearchTreeGraph.Availability.AVAILABLE));
            groups.add(new ResearchTreePresentation.Group(
                    id("phase_seven:group/" + ordinal),
                    "Group " + ordinal,
                    Optional.empty(),
                    Optional.of(nodeId),
                    ordinal,
                    ResearchTreePresentation.Kind.AUTHORED,
                    false,
                    List.of(new ResearchTreePresentation.Member(
                            nodeId, dependent ? 1 : 0, 0))));
        }
        for (int dependent = 0; dependent < dependentCount; dependent++) {
            ResourceLocation dependentId = nodes.get(rootCount + dependent).blueprintId();
            for (int offset = 0; offset < BlueprintResearchRule.MAX_PREREQUISITES; offset++) {
                ResourceLocation prerequisiteId = nodes.get(
                        (dependent + offset) % rootCount).blueprintId();
                edges.add(new ResearchTreeGraph.Edge(prerequisiteId, dependentId));
            }
        }
        return new ResearchTreePublication(
                new ResearchTreeGraph(nodes, edges),
                new ResearchTreePresentation(groups));
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

    private static ResearchTreePublication publicationWithOverviewBoundary() {
        ResearchTreePublication source = publication(
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
        ResearchTreePresentation.Group included = source.presentation().groups().get(0);
        ResearchTreePresentation.Group excluded = source.presentation().groups().get(1);
        return new ResearchTreePublication(
                source.graph(),
                new ResearchTreePresentation(List.of(
                        included,
                        new ResearchTreePresentation.Group(
                                excluded.id(),
                                excluded.title(),
                                excluded.translationKey(),
                                excluded.iconNodeId(),
                                excluded.order(),
                                excluded.kind(),
                                false,
                                excluded.members()))));
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
