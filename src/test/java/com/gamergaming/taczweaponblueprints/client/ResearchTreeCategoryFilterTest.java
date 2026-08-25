package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeCategoryFilterTest {
    @Test
    void cyclesFromAllThroughPublishedCategoriesAndBackToAll() {
        List<String> categories = List.of("pistol", "rifle", "undisclosed");

        assertEquals("pistol", ResearchTreeCategoryFilter.next(categories, null).orElseThrow());
        assertEquals("rifle", ResearchTreeCategoryFilter.next(categories, "pistol").orElseThrow());
        assertEquals("undisclosed", ResearchTreeCategoryFilter.next(categories, "rifle").orElseThrow());
        assertTrue(ResearchTreeCategoryFilter.next(categories, "undisclosed").isEmpty());
        assertTrue(ResearchTreeCategoryFilter.next(categories, "stale").isEmpty());
    }

    @Test
    void matchingUsesOnlyTheDisclosureSafePublishedLane() {
        ResearchTreeGraph.Node full = node(JournalVisibility.FULL, "rifle");
        ResearchTreeGraph.Node anonymous = node(
                JournalVisibility.SILHOUETTE, ResearchTreeGraph.REDACTED_ITEM_TYPE);

        assertTrue(ResearchTreeCategoryFilter.matches(full, "rifle"));
        assertFalse(ResearchTreeCategoryFilter.matches(full, "pistol"));
        assertTrue(ResearchTreeCategoryFilter.matches(anonymous, "undisclosed"));
        assertFalse(ResearchTreeCategoryFilter.matches(anonymous, "rifle"));
        assertTrue(ResearchTreeCategoryFilter.matches(anonymous, null));
    }

    @Test
    void rejectsMalformedCategoryListsAndNullNodes() {
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeCategoryFilter.next(null, null));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeCategoryFilter.next(List.of("rifle", "rifle"), null));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeCategoryFilter.matches(null, "rifle"));
    }

    private static ResearchTreeGraph.Node node(JournalVisibility visibility, String itemType) {
        boolean disclosed = visibility.revealsIdentity();
        return new ResearchTreeGraph.Node(
                0,
                disclosed ? id("test:target") : ResearchTreeGraph.redactedNodeId(0),
                visibility.revealsName() ? "name.target" : ResearchTreeGraph.REDACTED_NAME_KEY,
                itemType,
                disclosed ? id("test:slot/target") : ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                visibility,
                false, false, disclosed, disclosed ? 8 : 0, 0, 0, 0,
                disclosed
                        ? ResearchTreeGraph.Availability.AVAILABLE
                        : ResearchTreeGraph.Availability.REDACTED);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
