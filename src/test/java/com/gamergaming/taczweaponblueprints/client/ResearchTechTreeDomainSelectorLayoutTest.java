package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

class ResearchTechTreeDomainSelectorLayoutTest {
    @Test
    void splitsCompactSelectorIntoThreeStableContiguousDomains() {
        ResearchTreeScreenLayout.Rect bounds = new ResearchTreeScreenLayout.Rect(
                10, 20, 50, 18);

        List<ResearchTechTreeDomainSelectorLayout.Entry> entries =
                ResearchTechTreeDomainSelectorLayout.forBounds(bounds);

        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS, Domain.AMMO),
                entries.stream()
                        .map(ResearchTechTreeDomainSelectorLayout.Entry::domain)
                        .toList());
        assertEquals(List.of(17, 17, 16), entries.stream()
                .map(entry -> entry.bounds().width())
                .toList());
        assertEquals(bounds.x(), entries.get(0).bounds().x());
        assertEquals(entries.get(0).bounds().right(), entries.get(1).bounds().x());
        assertEquals(entries.get(1).bounds().right(), entries.get(2).bounds().x());
        assertEquals(bounds.right(), entries.get(2).bounds().right());
    }

    @Test
    void rejectsMissingOrImpossiblyNarrowBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeDomainSelectorLayout.forBounds(null));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeDomainSelectorLayout.forBounds(
                        new ResearchTreeScreenLayout.Rect(0, 0, 2, 18)));
    }
}
