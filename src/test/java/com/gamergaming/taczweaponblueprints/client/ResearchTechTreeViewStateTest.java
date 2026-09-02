package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

class ResearchTechTreeViewStateTest {
    @Test
    void defaultsToWeaponsAndUsesAValidPreferredFocus() {
        ResearchTechTreeProjectionCatalog catalog = catalog(Set.of(Domain.values()));
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();

        state.retain(catalog, ResearchTechTreeClientFixture.WEAPON_UPGRADE);

        assertEquals(Domain.WEAPONS, state.preferredDomain());
        assertEquals(Domain.WEAPONS, state.selectedDomain().orElseThrow());
        assertEquals(ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                state.focusedNode().orElseThrow());
    }

    @Test
    void retainsIndependentStateForEveryDomainAndSurface() {
        ResearchTechTreeProjectionCatalog catalog = catalog(Set.of(Domain.values()));
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        state.retain(catalog, null);
        assertTrue(state.selectDomain(Domain.AMMO, catalog));
        assertTrue(state.focus(ResearchTechTreeClientFixture.AMMO, catalog));
        state.setSearch("round", ResearchTechTreeClientFixture.AMMO, catalog);
        assertTrue(state.pin(ResearchTechTreeClientFixture.AMMO, catalog));
        ResearchTreeViewport.Snapshot compact =
                new ResearchTreeViewport.Snapshot(10.0D, 20.0D, 0.75D);
        state.saveCamera(ResearchTechTreeViewState.Surface.COMPACT, compact);

        assertTrue(state.selectDomain(Domain.WEAPONS, catalog));
        state.setSearch("pistol", ResearchTechTreeClientFixture.WEAPON_ROOT, catalog);
        ResearchTreeViewport.Snapshot fullscreen =
                new ResearchTreeViewport.Snapshot(30.0D, 40.0D, 1.25D);
        state.saveCamera(ResearchTechTreeViewState.Surface.FULLSCREEN, fullscreen);
        assertTrue(state.selectDomain(Domain.AMMO, catalog));

        assertEquals("round", state.searchQuery());
        assertEquals(ResearchTechTreeClientFixture.AMMO,
                state.activeSearchMatch().orElseThrow());
        assertEquals(ResearchTechTreeClientFixture.AMMO,
                state.pinnedNode().orElseThrow());
        assertEquals(compact,
                state.camera(ResearchTechTreeViewState.Surface.COMPACT).orElseThrow());
        assertTrue(state.camera(ResearchTechTreeViewState.Surface.FULLSCREEN).isEmpty());

        assertTrue(state.selectDomain(Domain.WEAPONS, catalog));
        assertEquals("pistol", state.searchQuery());
        assertEquals(fullscreen,
                state.camera(ResearchTechTreeViewState.Surface.FULLSCREEN).orElseThrow());
    }

    @Test
    void fallsBackWithoutForgettingPreferredDomainAndRestoresItLater() {
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        ResearchTechTreeProjectionCatalog all = catalog(Set.of(Domain.values()));
        state.retain(all, null);
        state.selectDomain(Domain.AMMO, all);
        state.setSearch("remembered only while published", null, all);

        ResearchTechTreeProjectionCatalog withoutAmmo = catalog(
                Set.of(Domain.WEAPONS, Domain.ATTACHMENTS));
        state.retain(withoutAmmo, null);

        assertEquals(Domain.AMMO, state.preferredDomain());
        assertEquals(Domain.WEAPONS, state.selectedDomain().orElseThrow());
        assertTrue(state.snapshot(Domain.AMMO).isEmpty());

        state.retain(all, null);
        assertEquals(Domain.AMMO, state.selectedDomain().orElseThrow());
        assertEquals("", state.searchQuery());
        assertEquals(ResearchTechTreeClientFixture.AMMO,
                state.focusedNode().orElseThrow());
    }

    @Test
    void fallsBackToFirstPublishedDomainWhenWeaponsIsMissing() {
        ResearchTechTreeProjectionCatalog catalog = catalog(
                Set.of(Domain.ATTACHMENTS, Domain.AMMO));
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();

        state.retain(catalog, null);

        assertEquals(Domain.ATTACHMENTS, state.selectedDomain().orElseThrow());
        assertEquals(ResearchTechTreeClientFixture.SCOPE,
                state.focusedNode().orElseThrow());
        assertEquals(Domain.AMMO, state.cycleDomain(1, catalog).orElseThrow());
        assertEquals(Domain.ATTACHMENTS, state.cycleDomain(1, catalog).orElseThrow());
    }

    @Test
    void nodeSelectionCrossesDomainsAndInvalidInputCannotCorruptState() {
        ResearchTechTreeProjectionCatalog catalog = catalog(Set.of(Domain.values()));
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        state.retain(catalog, null);

        assertEquals(Domain.ATTACHMENTS,
                state.selectNode(ResearchTechTreeClientFixture.SCOPE, catalog).orElseThrow());
        assertEquals(ResearchTechTreeClientFixture.SCOPE,
                state.focusedNode().orElseThrow());
        assertFalse(state.focus(ResearchTechTreeClientFixture.AMMO, catalog));
        assertFalse(state.pin(ResearchTechTreeClientFixture.OPAQUE, catalog));
        assertTrue(state.selectNode(ResearchTechTreeClientFixture.OPAQUE, catalog).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> state.setSearch(
                "x", ResearchTechTreeClientFixture.AMMO, catalog));
        assertThrows(IllegalArgumentException.class, () -> state.setSearch(
                "x".repeat(ResearchTechTreeViewState.MAX_SEARCH_LENGTH + 1), null, catalog));
        assertThrows(IllegalArgumentException.class, () -> state.setSearch(
                "bad\nquery", null, catalog));
    }

    @Test
    void reloadClearsVanishedNodeReferencesAndInvalidatesChangedGeometryCamera() {
        ResearchTechTreeProjectionCatalog initial = catalog(Set.of(Domain.values()));
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        state.retain(initial, ResearchTechTreeClientFixture.WEAPON_UPGRADE);
        state.setSearch("upgrade", ResearchTechTreeClientFixture.WEAPON_UPGRADE, initial);
        state.pin(ResearchTechTreeClientFixture.WEAPON_UPGRADE, initial);
        ResearchTreeViewport.Snapshot camera =
                new ResearchTreeViewport.Snapshot(1.0D, 2.0D, 0.5D);
        state.saveCamera(ResearchTechTreeViewState.Surface.COMPACT, camera);

        ResearchTechTreeProjectionCatalog replacement =
                ResearchTechTreeProjectionBuilder.build(
                        ResearchTechTreeClientFixture.publicationWithoutUpgradePlacement());
        state.retain(replacement, null);

        assertEquals(ResearchTechTreeClientFixture.WEAPON_ROOT,
                state.focusedNode().orElseThrow());
        assertEquals("upgrade", state.searchQuery());
        assertTrue(state.activeSearchMatch().isEmpty());
        assertTrue(state.pinnedNode().isEmpty());
        assertTrue(state.camera(ResearchTechTreeViewState.Surface.COMPACT).isEmpty());
    }

    @Test
    void stateOnlyPublicationRetainsCameraAgainstEquivalentGeometry() {
        ResearchTechTreeProjectionCatalog initial = catalog(Set.of(Domain.values()));
        ResearchTechTreeLayoutCatalog initialLayouts =
                ResearchTechTreeLayoutEngine.layoutCatalog(
                        initial, ResearchTechTreeLayoutPolicy.DEFAULT);
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        state.retain(initial, initialLayouts, null);
        ResearchTreeViewport.Snapshot camera =
                new ResearchTreeViewport.Snapshot(7.0D, 8.0D, 0.75D);
        state.saveCamera(ResearchTechTreeViewState.Surface.FULLSCREEN, camera);

        ResearchTechTreeProjectionCatalog stateOnly =
                ResearchTechTreeProjectionBuilder.build(
                        ResearchTechTreeClientFixture.publication(
                                Set.of(Domain.values()),
                                com.gamergaming.taczweaponblueprints.research.tree
                                        .ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE));
        ResearchTechTreeLayoutCatalog equivalentLayouts =
                ResearchTechTreeLayoutEngine.layoutCatalog(
                        stateOnly, ResearchTechTreeLayoutPolicy.DEFAULT);
        state.retain(stateOnly, equivalentLayouts, null);

        assertEquals(camera,
                state.camera(ResearchTechTreeViewState.Surface.FULLSCREEN).orElseThrow());
    }

    @Test
    void responsiveCameraRestoresOnlyAgainstItsExactSurfaceGeometry() {
        ResearchTechTreeProjectionCatalog catalog = catalog(Set.of(Domain.values()));
        ResearchTechTreeLayoutCatalog layouts = ResearchTechTreeLayoutEngine.layoutCatalog(
                catalog, ResearchTechTreeLayoutPolicy.DEFAULT);
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        state.retain(catalog, layouts, null);
        ResearchTechTreeProjection weapons = catalog.projection(
                Domain.WEAPONS).orElseThrow();
        ResearchTechTreeLayout defaultLayout = layouts.layout(
                Domain.WEAPONS).orElseThrow();
        ResearchTechTreeLayoutPolicy defaults = ResearchTechTreeLayoutPolicy.DEFAULT;
        ResearchTechTreeLayout reflowed = ResearchTechTreeLayoutEngine.layout(
                weapons,
                new ResearchTechTreeLayoutPolicy(
                        defaults.canvasPadding(),
                        2,
                        defaults.sameTierStepGap(),
                        defaults.tierGap() + 1,
                        defaults.portalPadding(),
                        ResearchTechTreeLayoutPolicy.rankBlockWidth(10, 2),
                        defaults.orderingSweeps(),
                        defaults.compactionSweeps()));
        ResearchTreeViewport.Snapshot camera =
                new ResearchTreeViewport.Snapshot(11.0D, 12.0D, 0.75D);

        state.saveCamera(
                ResearchTechTreeViewState.Surface.COMPACT,
                defaultLayout,
                camera);

        assertEquals(camera, state.camera(
                ResearchTechTreeViewState.Surface.COMPACT,
                defaultLayout).orElseThrow());
        assertTrue(state.camera(
                ResearchTechTreeViewState.Surface.COMPACT,
                reflowed).isEmpty());
    }

    @Test
    void emptyCatalogClearsSelectionAndStaleDomainState() {
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        ResearchTechTreeProjectionCatalog catalog = catalog(Set.of(Domain.values()));
        state.retain(catalog, null);
        state.pin(ResearchTechTreeClientFixture.WEAPON_ROOT, catalog);

        state.retain(ResearchTechTreeProjectionCatalog.EMPTY, null);

        assertTrue(state.selectedDomain().isEmpty());
        assertTrue(state.focusedNode().isEmpty());
        assertTrue(state.pinnedNode().isEmpty());
        assertTrue(state.snapshot(Domain.WEAPONS).isEmpty());
        assertThrows(IllegalStateException.class, () -> state.saveCamera(
                ResearchTechTreeViewState.Surface.COMPACT,
                new ResearchTreeViewport.Snapshot(0.0D, 0.0D, 1.0D)));
    }

    private static ResearchTechTreeProjectionCatalog catalog(Set<Domain> domains) {
        return ResearchTechTreeProjectionBuilder.build(
                ResearchTechTreeClientFixture.publication(domains));
    }
}
