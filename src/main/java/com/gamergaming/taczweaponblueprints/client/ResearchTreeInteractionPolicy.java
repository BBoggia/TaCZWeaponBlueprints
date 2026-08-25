package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

/** Pure keyboard and disclosure routing rules for the Research Tree screen. */
public final class ResearchTreeInteractionPolicy {
    private ResearchTreeInteractionPolicy() {
    }

    public static KeyboardTarget route(
            boolean searchFocused,
            boolean hasSearchMatches,
            KeyIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("Research Tree key intent cannot be null");
        }
        if (searchFocused) {
            if (hasSearchMatches && (intent == KeyIntent.UP || intent == KeyIntent.DOWN)) {
                return KeyboardTarget.SEARCH_RESULTS;
            }
            if (hasSearchMatches && intent == KeyIntent.ENTER) {
                return KeyboardTarget.SEARCH_SELECTION;
            }
            return KeyboardTarget.SEARCH_FIELD;
        }
        return intent.isArrow() ? KeyboardTarget.TREE : KeyboardTarget.DEFAULT;
    }

    public static boolean allowsServerSelection(ResearchTreeGraph.Node node) {
        return node != null && node.visibility().allowsServerSelection();
    }

    /** Routes intentionally overlapping fullscreen layers from front to back. */
    public static PointerTarget route(PointerLayers layers) {
        if (layers == null) {
            throw new IllegalArgumentException("Research Tree pointer layers cannot be null");
        }
        if (layers.guidance()) {
            return PointerTarget.GUIDANCE;
        }
        if (layers.contextCard()) {
            return PointerTarget.CONTEXT_CARD;
        }
        if (layers.search()) {
            return PointerTarget.SEARCH;
        }
        if (layers.sidebar()) {
            return PointerTarget.SIDEBAR;
        }
        if (layers.close()) {
            return PointerTarget.CLOSE;
        }
        if (layers.graphElement()) {
            return PointerTarget.GRAPH_ELEMENT;
        }
        return layers.graphCanvas() ? PointerTarget.GRAPH_BACKGROUND : PointerTarget.NONE;
    }

    /** An overlay owns its wheel even when it does not currently scroll. */
    public static ScrollTarget scrollTarget(PointerTarget pointerTarget, boolean cardScrollable) {
        if (pointerTarget == null) {
            throw new IllegalArgumentException("Research Tree pointer target cannot be null");
        }
        return switch (pointerTarget) {
            case CONTEXT_CARD -> cardScrollable ? ScrollTarget.CONTEXT_CARD : ScrollTarget.BLOCKED;
            case SIDEBAR -> ScrollTarget.SIDEBAR;
            case GRAPH_ELEMENT, GRAPH_BACKGROUND -> ScrollTarget.GRAPH;
            case GUIDANCE, SEARCH, CLOSE -> ScrollTarget.BLOCKED;
            case NONE -> ScrollTarget.NONE;
        };
    }

    public static boolean allowsGraphHover(PointerTarget pointerTarget) {
        return pointerTarget == PointerTarget.GRAPH_ELEMENT
                || pointerTarget == PointerTarget.GRAPH_BACKGROUND;
    }

    public enum KeyIntent {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        ENTER,
        OTHER;

        private boolean isArrow() {
            return this == UP || this == DOWN || this == LEFT || this == RIGHT;
        }
    }

    public enum KeyboardTarget {
        SEARCH_RESULTS,
        SEARCH_SELECTION,
        SEARCH_FIELD,
        TREE,
        DEFAULT
    }

    public record PointerLayers(
            boolean guidance,
            boolean contextCard,
            boolean search,
            boolean sidebar,
            boolean close,
            boolean graphElement,
            boolean graphCanvas) {
    }

    public enum PointerTarget {
        GUIDANCE,
        CONTEXT_CARD,
        SEARCH,
        SIDEBAR,
        CLOSE,
        GRAPH_ELEMENT,
        GRAPH_BACKGROUND,
        NONE
    }

    public enum ScrollTarget {
        CONTEXT_CARD,
        SIDEBAR,
        GRAPH,
        BLOCKED,
        NONE
    }
}
