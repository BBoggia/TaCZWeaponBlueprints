package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeFullscreenOverlayStateTest {
    private static final ResourceLocation NODE = new ResourceLocation("test:node");

    @Test
    void railRemainsDiscoverableUntilUsedAndNeverCollapsesWhileEngaged() {
        ResearchTreeFullscreenOverlayState state = new ResearchTreeFullscreenOverlayState();

        assertEquals(ResearchTreeFullscreenOverlayState.RailState.VISIBLE, state.railState());
        assertFalse(state.autoCollapse(false, false));

        state.markRailUsed();
        assertFalse(state.autoCollapse(true, false));
        assertFalse(state.autoCollapse(false, true));
        assertTrue(state.autoCollapse(false, false));
        assertEquals(ResearchTreeFullscreenOverlayState.RailState.EDGE_HANDLE, state.railState());

        state.revealRail();
        state.setRailPinned(true);
        assertFalse(state.autoCollapse(false, false));
        assertEquals(ResearchTreeFullscreenOverlayState.RailState.PINNED, state.railState());
    }

    @Test
    void searchKeepsTheRailAvailableAndTracksFocusExplicitly() {
        ResearchTreeFullscreenOverlayState state = new ResearchTreeFullscreenOverlayState();
        state.markRailUsed();
        assertTrue(state.autoCollapse(false, false));

        state.openSearch(true);
        assertEquals(ResearchTreeFullscreenOverlayState.RailState.VISIBLE, state.railState());
        assertEquals(ResearchTreeFullscreenOverlayState.SearchState.FOCUSED, state.searchState());
        assertFalse(state.canAutoCollapse(false, false));

        state.blurSearch();
        assertEquals(ResearchTreeFullscreenOverlayState.SearchState.OPEN, state.searchState());
        state.closeSearch();
        assertEquals(ResearchTreeFullscreenOverlayState.SearchState.CLOSED, state.searchState());
        assertTrue(state.canAutoCollapse(false, false));
    }

    @Test
    void escapeClosesOverlaysInThePublishedPriorityOrder() {
        ResearchTreeFullscreenOverlayState state = new ResearchTreeFullscreenOverlayState();
        state.pinNode(NODE);
        state.setGuidanceVisible(true);
        state.openSearch(true);

        assertEquals(
                ResearchTreeFullscreenOverlayState.EscapeResult.CLOSED_SEARCH,
                state.escape(true));
        assertEquals(
                ResearchTreeFullscreenOverlayState.EscapeResult.DISMISSED_GUIDANCE,
                state.escape(true));
        assertEquals(
                ResearchTreeFullscreenOverlayState.EscapeResult.CLOSED_CARD,
                state.escape(true));
        assertEquals(
                ResearchTreeFullscreenOverlayState.EscapeResult.EXIT_FULLSCREEN,
                state.escape(true));
        assertEquals(
                ResearchTreeFullscreenOverlayState.EscapeResult.DEFAULT,
                state.escape(false));
    }

    @Test
    void publicationChangesDiscardOnlyAnInvalidPinnedNode() {
        ResearchTreeFullscreenOverlayState state = new ResearchTreeFullscreenOverlayState();
        state.pinNode(NODE);

        state.retainVisibleNodes(Set.of(NODE));
        assertEquals(NODE, state.pinnedNodeId().orElseThrow());

        state.retainVisibleNodes(Set.of(new ResourceLocation("test:other")));
        assertTrue(state.pinnedNodeId().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> state.pinNode(null));
        assertThrows(IllegalArgumentException.class, () -> state.retainVisibleNodes(null));
    }

    @Test
    void reopeningGuidanceRestoresACollapsedRail() {
        ResearchTreeFullscreenOverlayState state = new ResearchTreeFullscreenOverlayState();
        state.markRailUsed();
        assertTrue(state.autoCollapse(false, false));

        state.setGuidanceVisible(true);

        assertTrue(state.guidanceVisible());
        assertEquals(ResearchTreeFullscreenOverlayState.RailState.VISIBLE, state.railState());
        assertFalse(state.canAutoCollapse(false, false));
    }

    @Test
    void immutableSnapshotCapturesEveryOverlayInputForWidgetInvalidation() {
        ResearchTreeFullscreenOverlayState state = new ResearchTreeFullscreenOverlayState();
        ResearchTreeFullscreenOverlayState.Snapshot initial = state.snapshot();

        state.markRailUsed();
        state.openSearch(true);
        state.pinNode(NODE);
        state.setGuidanceVisible(true);
        ResearchTreeFullscreenOverlayState.Snapshot changed = state.snapshot();

        assertFalse(initial.equals(changed));
        assertEquals(ResearchTreeFullscreenOverlayState.SearchState.FOCUSED,
                changed.searchState());
        assertEquals(NODE, changed.pinnedNodeId().orElseThrow());
        assertTrue(changed.guidanceVisible());
        assertTrue(changed.railUsed());
    }
}
