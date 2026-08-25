package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/** Pure, client-only state machine for the future fullscreen Research Tree overlays. */
public final class ResearchTreeFullscreenOverlayState {
    private RailState railState = RailState.VISIBLE;
    private SearchState searchState = SearchState.CLOSED;
    private ResourceLocation pinnedNodeId;
    private boolean guidanceVisible;
    private boolean railUsed;

    public RailState railState() {
        return railState;
    }

    public SearchState searchState() {
        return searchState;
    }

    public Optional<ResourceLocation> pinnedNodeId() {
        return Optional.ofNullable(pinnedNodeId);
    }

    public boolean guidanceVisible() {
        return guidanceVisible;
    }

    public boolean railUsed() {
        return railUsed;
    }

    public void markRailUsed() {
        railUsed = true;
        revealRail();
    }

    public void revealRail() {
        if (railState == RailState.EDGE_HANDLE) {
            railState = RailState.VISIBLE;
        }
    }

    public void setRailPinned(boolean pinned) {
        railState = pinned ? RailState.PINNED : RailState.VISIBLE;
    }

    public boolean canAutoCollapse(boolean pointerOverRail, boolean railHasFocus) {
        return railUsed
                && railState == RailState.VISIBLE
                && searchState == SearchState.CLOSED
                && !guidanceVisible
                && !pointerOverRail
                && !railHasFocus;
    }

    public boolean autoCollapse(boolean pointerOverRail, boolean railHasFocus) {
        if (!canAutoCollapse(pointerOverRail, railHasFocus)) {
            return false;
        }
        railState = RailState.EDGE_HANDLE;
        return true;
    }

    public void openSearch(boolean focus) {
        searchState = focus ? SearchState.FOCUSED : SearchState.OPEN;
        revealRail();
    }

    public void focusSearch() {
        searchState = SearchState.FOCUSED;
        revealRail();
    }

    public void blurSearch() {
        if (searchState == SearchState.FOCUSED) {
            searchState = SearchState.OPEN;
        }
    }

    public void closeSearch() {
        searchState = SearchState.CLOSED;
    }

    public void pinNode(ResourceLocation nodeId) {
        if (nodeId == null) {
            throw new IllegalArgumentException("pinned Research Tree node cannot be null");
        }
        pinnedNodeId = nodeId;
    }

    public void clearPinnedNode() {
        pinnedNodeId = null;
    }

    public void retainVisibleNodes(Set<ResourceLocation> visibleNodeIds) {
        if (visibleNodeIds == null || visibleNodeIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("visible Research Tree node IDs cannot be null");
        }
        if (pinnedNodeId != null && !visibleNodeIds.contains(pinnedNodeId)) {
            pinnedNodeId = null;
        }
    }

    public void setGuidanceVisible(boolean visible) {
        guidanceVisible = visible;
        if (visible) {
            revealRail();
        }
    }

    public EscapeResult escape(boolean fullscreen) {
        if (searchState != SearchState.CLOSED) {
            closeSearch();
            return EscapeResult.CLOSED_SEARCH;
        }
        if (guidanceVisible) {
            guidanceVisible = false;
            return EscapeResult.DISMISSED_GUIDANCE;
        }
        if (pinnedNodeId != null) {
            pinnedNodeId = null;
            return EscapeResult.CLOSED_CARD;
        }
        return fullscreen ? EscapeResult.EXIT_FULLSCREEN : EscapeResult.DEFAULT;
    }

    public enum RailState {
        VISIBLE,
        EDGE_HANDLE,
        PINNED
    }

    public enum SearchState {
        CLOSED,
        OPEN,
        FOCUSED
    }

    public enum EscapeResult {
        CLOSED_SEARCH,
        DISMISSED_GUIDANCE,
        CLOSED_CARD,
        EXIT_FULLSCREEN,
        DEFAULT
    }
}
