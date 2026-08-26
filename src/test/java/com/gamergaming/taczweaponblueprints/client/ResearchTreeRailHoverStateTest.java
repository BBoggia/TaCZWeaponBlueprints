package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResearchTreeRailHoverStateTest {
    @Test
    void revealedLabelRetainsPointerOwnershipAcrossTheIconGap() {
        ResearchTreeRailHoverState state = new ResearchTreeRailHoverState();

        state.update(true, false, true, false);
        assertTrue(state.labelVisible(true, false));

        state.update(true, false, false, true);
        assertTrue(state.labelVisible(true, false));
        assertTrue(state.ownsRevealedLabel(true, false, true));

        state.update(true, false, false, false);
        assertFalse(state.labelVisible(true, false));
        assertFalse(state.ownsRevealedLabel(true, false, true));
    }

    @Test
    void hiddenLabelsDoNotClaimGraphSpaceUntilRevealed() {
        ResearchTreeRailHoverState state = new ResearchTreeRailHoverState();

        state.update(true, false, false, true);
        assertFalse(state.labelVisible(true, false));
        assertFalse(state.ownsRevealedLabel(true, false, true));

        state.update(true, true, false, true);
        assertTrue(state.labelVisible(true, true));
        assertTrue(state.ownsRevealedLabel(true, true, true));

        state.update(false, true, false, true);
        assertFalse(state.labelVisible(false, true));
    }
}
