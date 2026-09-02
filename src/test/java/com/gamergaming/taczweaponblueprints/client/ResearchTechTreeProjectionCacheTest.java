package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBranchLayoutComposer;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

class ResearchTechTreeProjectionCacheTest {
    @Test
    void publishesTechCatalogAndReportsTypedGeometryChanges() {
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> ResearchTreeLayout.EMPTY);
        ResearchTreePublication initial = ResearchTechTreeClientFixture.publication();

        assertTrue(cache.update(initial));
        ResearchTechTreeProjectionCatalog initialTech = cache.techTreeProjections();
        ResearchTechTreeLayoutCatalog initialLayouts = cache.techTreeLayouts();
        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS, Domain.AMMO),
                initialTech.domains());
        assertEquals(initialTech.domains(), initialLayouts.domains());

        ResearchTreePublication stateOnly = ResearchTechTreeClientFixture.publication(
                Set.of(Domain.values()),
                com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph
                        .Availability.CONTENT_UNAVAILABLE);
        assertFalse(cache.update(stateOnly));
        assertTrue(initialTech.hasSameTopology(cache.techTreeProjections()));
        assertSame(initialLayouts, cache.techTreeLayouts());
        assertSame(stateOnly, cache.publication());

        ResearchTreePublication withoutAmmo = ResearchTechTreeClientFixture.publication(
                Set.of(Domain.WEAPONS, Domain.ATTACHMENTS));
        assertTrue(cache.update(withoutAmmo));
        assertNotSame(initialLayouts, cache.techTreeLayouts());
        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS),
                cache.techTreeLayouts().domains());

        cache.clear();
        assertSame(ResearchTechTreeProjectionCatalog.EMPTY, cache.techTreeProjections());
        assertSame(ResearchTechTreeLayoutCatalog.EMPTY, cache.techTreeLayouts());
    }

    @Test
    void sharedPolicyChangesAtomicallyReflowEveryTechDomain() {
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> ResearchTreeLayout.EMPTY);
        ResearchTreePublication publication = ResearchTechTreeClientFixture.publication();
        assertTrue(cache.update(publication));
        ResearchTechTreeLayoutCatalog initial = cache.techTreeLayouts();

        ResearchTreeLayoutPolicy defaults = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;
        ResearchTreeLayoutPolicy compact = new ResearchTreeLayoutPolicy(
                defaults.canvasPadding(),
                2,
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
        assertTrue(cache.update(publication, compact));
        assertNotSame(initial, cache.techTreeLayouts());
        ResearchTechTreeLayoutCatalog compactLayouts = cache.techTreeLayouts();

        ResearchTreePublication stateOnly = ResearchTechTreeClientFixture.publication(
                Set.of(Domain.values()),
                com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph
                        .Availability.CONTENT_UNAVAILABLE);
        assertFalse(cache.update(stateOnly, compact));
        assertSame(compactLayouts, cache.techTreeLayouts());
    }

    @Test
    void responsiveTechLayoutsReuseDefaultWidthsAndCacheNarrowCapacities() {
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> ResearchTreeLayout.EMPTY);
        assertTrue(cache.update(ResearchTechTreeClientFixture.publication()));

        ResearchTechTreeLayout defaultLayout = cache.techTreeLayouts()
                .layout(Domain.WEAPONS).orElseThrow();
        assertSame(defaultLayout,
                cache.techTreeLayout(Domain.WEAPONS, 294).orElseThrow());

        ResearchTechTreeLayout narrow = cache.techTreeLayout(
                Domain.WEAPONS, 120).orElseThrow();
        assertNotSame(defaultLayout, narrow);
        assertSame(narrow,
                cache.techTreeLayout(Domain.WEAPONS, 120).orElseThrow());
        assertThrows(IllegalArgumentException.class, () ->
                cache.techTreeLayout(Domain.WEAPONS, 0));
    }

    @Test
    void failedExistingLayoutPreparationRetainsThePriorTechCatalogAtomically() {
        AtomicBoolean reject = new AtomicBoolean();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> {
                    if (reject.get()) {
                        throw new IllegalStateException("fixture layout rejection");
                    }
                    return ResearchTreeLayout.EMPTY;
                },
                (publication, policy) -> {
                    throw new IllegalStateException("fixture fallback rejection");
                });
        ResearchTreePublication initial = ResearchTechTreeClientFixture.publication();
        cache.update(initial);
        ResearchTechTreeProjectionCatalog initialTech = cache.techTreeProjections();
        ResearchTechTreeLayoutCatalog initialLayouts = cache.techTreeLayouts();

        ResearchTreePublication noAmmo = ResearchTechTreeClientFixture.publication(
                Set.of(Domain.WEAPONS, Domain.ATTACHMENTS));
        ResearchTreePresentation replacementPresentation = new ResearchTreePresentation(
                noAmmo.presentation().groups().stream()
                        .map(group -> group.kind() == ResearchTreePresentation.Kind.AUTHORED
                                ? new ResearchTreePresentation.Group(
                                        group.id(),
                                        "Replacement",
                                        Optional.empty(),
                                        group.iconNodeId(),
                                        group.order(),
                                        group.kind(),
                                        group.includedInOverview(),
                                        group.members())
                                : group)
                        .toList());
        ResearchTreePublication replacement = new ResearchTreePublication(
                noAmmo.graph(), replacementPresentation, noAmmo.techTree());

        reject.set(true);
        assertThrows(IllegalStateException.class, () -> cache.update(
                replacement, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));

        assertSame(initial, cache.publication());
        assertSame(initialTech, cache.techTreeProjections());
        assertSame(initialLayouts, cache.techTreeLayouts());
        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS, Domain.AMMO),
                cache.techTreeProjections().domains());
    }

    @Test
    void failedTechLayoutPreparationCannotPartiallyCommitAStateOnlyReplacement() {
        AtomicBoolean reject = new AtomicBoolean();
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache(
                (publication, skeletons, policy) -> ResearchTreeLayout.EMPTY,
                ResearchTreeBranchLayoutComposer::compose,
                (projections, policy) -> {
                    if (reject.get()) {
                        throw new IllegalStateException("fixture Tech layout rejection");
                    }
                    return ResearchTechTreeLayoutEngine.layoutCatalog(projections, policy);
                });
        ResearchTreePublication initial = ResearchTechTreeClientFixture.publication();
        cache.update(initial);
        ResearchTechTreeProjectionCatalog initialProjections = cache.techTreeProjections();
        ResearchTechTreeLayoutCatalog initialLayouts = cache.techTreeLayouts();

        reject.set(true);
        ResearchTreePublication replacement = ResearchTechTreeClientFixture.publication(
                Set.of(Domain.WEAPONS, Domain.ATTACHMENTS));
        assertThrows(IllegalStateException.class, () -> cache.update(replacement));

        assertSame(initial, cache.publication());
        assertSame(initialProjections, cache.techTreeProjections());
        assertSame(initialLayouts, cache.techTreeLayouts());
    }
}
