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

    @Test
    void fullscreenPointerRoutingAlwaysPrefersTheFrontmostOverlay() {
        ResearchTreeInteractionPolicy.PointerLayers everyLayer =
                new ResearchTreeInteractionPolicy.PointerLayers(
                        true, true, true, true, true, true, true);
        assertEquals(
                ResearchTreeInteractionPolicy.PointerTarget.GUIDANCE,
                ResearchTreeInteractionPolicy.route(everyLayer));
        assertEquals(
                ResearchTreeInteractionPolicy.PointerTarget.CONTEXT_CARD,
                ResearchTreeInteractionPolicy.route(new ResearchTreeInteractionPolicy.PointerLayers(
                        false, true, true, true, true, true, true)));
        assertEquals(
                ResearchTreeInteractionPolicy.PointerTarget.SEARCH,
                ResearchTreeInteractionPolicy.route(new ResearchTreeInteractionPolicy.PointerLayers(
                        false, false, true, true, true, true, true)));
        assertEquals(
                ResearchTreeInteractionPolicy.PointerTarget.SIDEBAR,
                ResearchTreeInteractionPolicy.route(new ResearchTreeInteractionPolicy.PointerLayers(
                        false, false, false, true, true, true, true)));
        assertEquals(
                ResearchTreeInteractionPolicy.PointerTarget.CLOSE,
                ResearchTreeInteractionPolicy.route(new ResearchTreeInteractionPolicy.PointerLayers(
                        false, false, false, false, true, true, true)));
        assertEquals(
                ResearchTreeInteractionPolicy.PointerTarget.GRAPH_ELEMENT,
                ResearchTreeInteractionPolicy.route(new ResearchTreeInteractionPolicy.PointerLayers(
                        false, false, false, false, false, true, true)));
        assertEquals(
                ResearchTreeInteractionPolicy.PointerTarget.GRAPH_BACKGROUND,
                ResearchTreeInteractionPolicy.route(new ResearchTreeInteractionPolicy.PointerLayers(
                        false, false, false, false, false, false, true)));
    }

    @Test
    void overlaysOwnScrollAndSuppressGraphHover() {
        assertEquals(
                ResearchTreeInteractionPolicy.ScrollTarget.SIDEBAR,
                ResearchTreeInteractionPolicy.scrollTarget(
                        ResearchTreeInteractionPolicy.PointerTarget.SIDEBAR, false));
        assertEquals(
                ResearchTreeInteractionPolicy.ScrollTarget.BLOCKED,
                ResearchTreeInteractionPolicy.scrollTarget(
                        ResearchTreeInteractionPolicy.PointerTarget.SEARCH, false));
        assertEquals(
                ResearchTreeInteractionPolicy.ScrollTarget.CONTEXT_CARD,
                ResearchTreeInteractionPolicy.scrollTarget(
                        ResearchTreeInteractionPolicy.PointerTarget.CONTEXT_CARD, true));
        assertEquals(
                ResearchTreeInteractionPolicy.ScrollTarget.GRAPH,
                ResearchTreeInteractionPolicy.scrollTarget(
                        ResearchTreeInteractionPolicy.PointerTarget.GRAPH_ELEMENT, false));
        assertFalse(ResearchTreeInteractionPolicy.allowsGraphHover(
                ResearchTreeInteractionPolicy.PointerTarget.CONTEXT_CARD));
        assertTrue(ResearchTreeInteractionPolicy.allowsGraphHover(
                ResearchTreeInteractionPolicy.PointerTarget.GRAPH_BACKGROUND));
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
