package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeInteractionPolicyTest {
    @Test
    void focusedSearchKeepsArrowsWhenThereAreNoMatches() {
        for (ResearchTreeInteractionPolicy.KeyIntent intent
                : ResearchTreeInteractionPolicy.KeyIntent.values()) {
            assertEquals(
                    ResearchTreeInteractionPolicy.KeyboardTarget.SEARCH_FIELD,
                    ResearchTreeInteractionPolicy.route(true, false, intent));
        }
    }

    @Test
    void focusedSearchRoutesOnlyResultNavigationAndSelection() {
        assertEquals(
                ResearchTreeInteractionPolicy.KeyboardTarget.SEARCH_RESULTS,
                ResearchTreeInteractionPolicy.route(
                        true, true, ResearchTreeInteractionPolicy.KeyIntent.UP));
        assertEquals(
                ResearchTreeInteractionPolicy.KeyboardTarget.SEARCH_RESULTS,
                ResearchTreeInteractionPolicy.route(
                        true, true, ResearchTreeInteractionPolicy.KeyIntent.DOWN));
        assertEquals(
                ResearchTreeInteractionPolicy.KeyboardTarget.SEARCH_SELECTION,
                ResearchTreeInteractionPolicy.route(
                        true, true, ResearchTreeInteractionPolicy.KeyIntent.ENTER));
        assertEquals(
                ResearchTreeInteractionPolicy.KeyboardTarget.SEARCH_FIELD,
                ResearchTreeInteractionPolicy.route(
                        true, true, ResearchTreeInteractionPolicy.KeyIntent.LEFT));
    }

    @Test
    void unfocusedArrowsNavigateTheTree() {
        assertEquals(
                ResearchTreeInteractionPolicy.KeyboardTarget.TREE,
                ResearchTreeInteractionPolicy.route(
                        false, false, ResearchTreeInteractionPolicy.KeyIntent.RIGHT));
        assertEquals(
                ResearchTreeInteractionPolicy.KeyboardTarget.DEFAULT,
                ResearchTreeInteractionPolicy.route(
                        false, true, ResearchTreeInteractionPolicy.KeyIntent.ENTER));
    }

    @Test
    void anonymousNodesNeverBecomeServerSelections() {
        assertFalse(ResearchTreeInteractionPolicy.allowsServerSelection(
                node(JournalVisibility.NAME, ResearchTreeGraph.redactedNodeId(0))));
        assertTrue(ResearchTreeInteractionPolicy.allowsServerSelection(
                node(JournalVisibility.PREVIEW, new ResourceLocation("test:preview"))));
        assertTrue(ResearchTreeInteractionPolicy.allowsServerSelection(
                node(JournalVisibility.FULL, new ResourceLocation("test:full"))));
    }

    private static ResearchTreeGraph.Node node(
            JournalVisibility visibility,
            ResourceLocation blueprintId) {
        return new ResearchTreeGraph.Node(
                0,
                blueprintId,
                visibility.revealsName() ? "name.test" : ResearchTreeGraph.REDACTED_NAME_KEY,
                visibility.revealsIdentity() ? "rifle" : ResearchTreeGraph.REDACTED_ITEM_TYPE,
                visibility.revealsIcon()
                        ? new ResourceLocation("test:slot/test")
                        : ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                visibility,
                false,
                false,
                visibility.revealsExactPolicy(),
                visibility.revealsResearchSummary() ? 1 : 0,
                0,
                0,
                0,
                visibility.revealsExactPolicy()
                        ? ResearchTreeGraph.Availability.AVAILABLE
                        : visibility.revealsResearchSummary()
                                ? ResearchTreeGraph.Availability.PREVIEW
                                : ResearchTreeGraph.Availability.REDACTED);
    }
}
