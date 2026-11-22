package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

class ResearchSheetSelectionTest {
    private static final ResourceLocation A = ResourceLocation.parse("test:a"), B = ResourceLocation.parse("test:b");
    private ResearchSheetSelection confirmed() {
        var state = new ResearchSheetSelection();
        state.choose(A); state.sentSelection(1);
        assertTrue(state.acceptSelection(1, Optional.of(A), true));
        return state;
    }
    @Test void successfulPrintingClearsOnlyTheCompletedSelection() {
        var state = confirmed(); state.sentResearch(2);
        state.clearedPreview(); // The server sends its empty preview before the action result.
        assertEquals(Optional.of(A), state.selected());
        assertEquals(Optional.of(true), state.acceptResearch(2, Optional.of(A), true));
        assertTrue(state.selected().isEmpty()); assertFalse(state.confirmed()); assertFalse(state.researching());
        state.restore(Optional.of(B)); // A later resize must not restore a stale purchase selection.
        assertTrue(state.selected().isEmpty());
    }
    @Test void delayedResearchResultPreservesNewerSelectionEvenWhenReturningToTheSameTarget() {
        for (var next : new ResourceLocation[]{A, B}) {
            var state = confirmed(); state.sentResearch(2);
            state.choose(next); state.sentSelection(3);
            state.clearedPreview();
            assertEquals(Optional.of(false), state.acceptResearch(2, Optional.of(A), true));
            assertEquals(Optional.of(next), state.selected());
            assertFalse(state.confirmed());
            assertTrue(state.acceptSelection(3, Optional.of(next), true));
            assertTrue(state.confirmed());
        }
    }
    @Test void rejectedSelectionHasAnExplicitRetryAndOldRepliesCannotConfirmIt() {
        var state = confirmed(); state.choose(B); state.sentSelection(2);
        assertFalse(state.acceptSelection(1, Optional.of(A), true));
        assertFalse(state.confirmed());
        assertTrue(state.acceptSelection(2, Optional.of(B), false));
        assertTrue(state.failed()); assertFalse(state.confirmed());
        state.sentSelection(3); assertFalse(state.failed());
        assertFalse(state.acceptSelection(2, Optional.of(B), true));
        assertTrue(state.acceptSelection(3, Optional.of(B), true));
        assertTrue(state.confirmed());
    }
    @Test void failedResearchRetainsTheTargetAndUnrelatedRepliesDoNotEndThePurchase() {
        var state = confirmed(); state.sentResearch(2);
        assertTrue(state.acceptResearch(1, Optional.of(A), true).isEmpty());
        assertTrue(state.researching());
        assertEquals(Optional.of(true), state.acceptResearch(2, Optional.of(A), false));
        assertEquals(Optional.of(A), state.selected()); assertTrue(state.confirmed());
    }

    @Test void switchingDomainsDiscardsDelayedSelectionAndPurchaseFeedback() {
        var state = confirmed();
        state.sentResearch(2);
        state.choose(B);
        state.sentSelection(3);
        state.clear();
        state.restore(Optional.of(A));
        assertTrue(state.selected().isEmpty());
        assertFalse(state.acceptSelection(3, Optional.of(B), true));
        assertEquals(Optional.of(false), state.acceptResearch(2, Optional.of(A), true));
        assertFalse(state.researching());
        assertTrue(state.selected().isEmpty());
    }
}
