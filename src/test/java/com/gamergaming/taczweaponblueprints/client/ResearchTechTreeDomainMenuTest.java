package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

class ResearchTechTreeDomainMenuTest {
    @Test
    void partialPublicationRetainsStableSlotsAndDisablesMissingDomain() {
        ResearchTechTreeProjectionCatalog catalog = catalog(
                Set.of(Domain.WEAPONS, Domain.AMMO));
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        state.retain(catalog, null);

        ResearchTechTreeDomainMenu menu = ResearchTechTreeDomainMenu.create(catalog, state);

        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS, Domain.AMMO),
                menu.entries().stream().map(ResearchTechTreeDomainMenu.Entry::domain).toList());
        assertTrue(menu.entry(Domain.WEAPONS).available());
        assertTrue(menu.entry(Domain.WEAPONS).selected());
        assertEquals(2, menu.entry(Domain.WEAPONS).visibleBlueprintCount());
        assertFalse(menu.entry(Domain.ATTACHMENTS).available());
        assertFalse(menu.entry(Domain.ATTACHMENTS).selected());
        assertEquals(0, menu.entry(Domain.ATTACHMENTS).visibleBlueprintCount());
        assertTrue(menu.entry(Domain.ATTACHMENTS).iconNodeId().isEmpty());
        assertTrue(menu.entry(Domain.AMMO).available());
    }

    @Test
    void keyboardCycleSkipsMissingSlotsAndWrapsBothDirections() {
        ResearchTechTreeProjectionCatalog catalog = catalog(
                Set.of(Domain.WEAPONS, Domain.AMMO));
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        state.retain(catalog, null);

        ResearchTechTreeDomainMenu weapons = ResearchTechTreeDomainMenu.create(catalog, state);
        assertEquals(Domain.AMMO, weapons.cycle(1).orElseThrow());
        assertEquals(Domain.AMMO, weapons.cycle(-1).orElseThrow());

        state.selectDomain(Domain.AMMO, catalog);
        ResearchTechTreeDomainMenu ammo = ResearchTechTreeDomainMenu.create(catalog, state);
        assertEquals(Domain.WEAPONS, ammo.cycle(1).orElseThrow());
        assertEquals(Domain.WEAPONS, ammo.cycle(-1).orElseThrow());
        assertEquals(Domain.AMMO, ammo.cycle(0).orElseThrow());
    }

    @Test
    void emptyCatalogProducesThreeSafeDisabledSlots() {
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        state.retain(ResearchTechTreeProjectionCatalog.EMPTY, null);

        ResearchTechTreeDomainMenu menu = ResearchTechTreeDomainMenu.create(
                ResearchTechTreeProjectionCatalog.EMPTY, state);

        assertTrue(menu.selectedDomain().isEmpty());
        assertTrue(menu.cycle(1).isEmpty());
        assertTrue(menu.entries().stream().noneMatch(ResearchTechTreeDomainMenu.Entry::available));
        assertTrue(menu.entries().stream().allMatch(entry ->
                entry.visibleBlueprintCount() == 0 && entry.iconNodeId().isEmpty()));
    }

    @Test
    void rejectsContradictoryOrOutOfRangeMenuData() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTechTreeDomainMenu.Entry(
                        Domain.WEAPONS, false, true, 0, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTechTreeDomainMenu.Entry(
                        Domain.WEAPONS, false, false, 1, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTechTreeDomainMenu(List.of(), Optional.empty()));

        ResearchTechTreeProjectionCatalog catalog = catalog(Set.of(Domain.WEAPONS));
        ResearchTechTreeViewState state = new ResearchTechTreeViewState();
        state.retain(catalog, null);
        ResearchTechTreeDomainMenu menu = ResearchTechTreeDomainMenu.create(catalog, state);
        assertThrows(IllegalArgumentException.class, () -> menu.entryAt(-1));
        assertThrows(IllegalArgumentException.class, () -> menu.entryAt(3));
    }

    private static ResearchTechTreeProjectionCatalog catalog(Set<Domain> domains) {
        return ResearchTechTreeProjectionBuilder.build(
                ResearchTechTreeClientFixture.publication(domains));
    }
}
