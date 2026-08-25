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
}
