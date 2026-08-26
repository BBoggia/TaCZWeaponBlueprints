package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeGestureTrackerTest {
    private static final ResourceLocation NODE = new ResourceLocation("test:node");

    @Test
    void nodeSelectionIsDeferredUntilACompletedRelease() {
        ResearchTreeGestureTracker tracker = new ResearchTreeGestureTracker();

        assertTrue(tracker.press(10, 20, ResearchTreeGestureTracker.LEFT_BUTTON, NODE));
        assertTrue(tracker.active());
        assertEquals(ResearchTreeGestureTracker.Movement.NONE, tracker.move(12, 21));

        ResearchTreeGestureTracker.Outcome outcome = tracker.release(
                12, 21, ResearchTreeGestureTracker.LEFT_BUTTON);
        assertEquals(ResearchTreeGestureTracker.Type.NODE_CLICK, outcome.type());
        assertEquals(NODE, outcome.nodeId().orElseThrow());
        assertFalse(tracker.active());
    }

    @Test
    void draggingFromANodeCancelsItsClickCandidate() {
        ResearchTreeGestureTracker tracker = new ResearchTreeGestureTracker();
        tracker.press(10, 10, ResearchTreeGestureTracker.LEFT_BUTTON, NODE);

        assertEquals(ResearchTreeGestureTracker.Movement.STARTED_DRAG, tracker.move(13, 10));
        assertEquals(ResearchTreeGestureTracker.Movement.DRAGGING, tracker.move(20, 12));
        ResearchTreeGestureTracker.Outcome outcome = tracker.release(
                20, 12, ResearchTreeGestureTracker.LEFT_BUTTON);

        assertEquals(ResearchTreeGestureTracker.Type.PAN_END, outcome.type());
        assertTrue(outcome.nodeId().isEmpty());
    }

    @Test
    void releaseChecksDistanceEvenWhenNoDragCallbackWasDelivered() {
        ResearchTreeGestureTracker tracker = new ResearchTreeGestureTracker();
        tracker.press(0, 0, ResearchTreeGestureTracker.LEFT_BUTTON, NODE);

        ResearchTreeGestureTracker.Outcome outcome = tracker.release(
                100, 100, ResearchTreeGestureTracker.LEFT_BUTTON);

        assertEquals(ResearchTreeGestureTracker.Type.PAN_END, outcome.type());
        assertTrue(outcome.nodeId().isEmpty());
    }

    @Test
    void backgroundAndMiddleButtonGesturesRemainDistinct() {
        ResearchTreeGestureTracker tracker = new ResearchTreeGestureTracker();
        tracker.press(5, 5, ResearchTreeGestureTracker.LEFT_BUTTON, null);
        assertEquals(
                ResearchTreeGestureTracker.Type.BACKGROUND_CLICK,
                tracker.release(5, 5, ResearchTreeGestureTracker.LEFT_BUTTON).type());

        tracker.press(5, 5, ResearchTreeGestureTracker.MIDDLE_BUTTON, NODE);
        assertEquals(
                ResearchTreeGestureTracker.Type.NONE,
                tracker.release(5, 5, ResearchTreeGestureTracker.MIDDLE_BUTTON).type());

        tracker.press(5, 5, ResearchTreeGestureTracker.MIDDLE_BUTTON, NODE);
        assertEquals(ResearchTreeGestureTracker.Movement.STARTED_DRAG, tracker.move(5, 9));
        assertEquals(
                ResearchTreeGestureTracker.Type.PAN_END,
                tracker.release(5, 9, ResearchTreeGestureTracker.MIDDLE_BUTTON).type());
    }

    @Test
    void unsupportedButtonsAndInvalidCoordinatesCannotStartGestures() {
        ResearchTreeGestureTracker tracker = new ResearchTreeGestureTracker();

        assertFalse(tracker.press(0, 0, 1, NODE));
        assertEquals(ResearchTreeGestureTracker.Outcome.NONE, tracker.release(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTreeGestureTracker(0.0D));
        assertThrows(IllegalArgumentException.class, () ->
                tracker.press(Double.NaN, 0, ResearchTreeGestureTracker.LEFT_BUTTON, NODE));
    }

    @Test
    void aSecondButtonCannotReplaceAnActiveGesture() {
        ResearchTreeGestureTracker tracker = new ResearchTreeGestureTracker();
        assertTrue(tracker.press(4, 4, ResearchTreeGestureTracker.LEFT_BUTTON, NODE));
        assertTrue(tracker.ownsButton(ResearchTreeGestureTracker.LEFT_BUTTON));
        assertFalse(tracker.ownsButton(ResearchTreeGestureTracker.MIDDLE_BUTTON));

        assertFalse(tracker.press(4, 4, ResearchTreeGestureTracker.MIDDLE_BUTTON, null));
        ResearchTreeGestureTracker.Outcome outcome = tracker.release(
                4, 4, ResearchTreeGestureTracker.LEFT_BUTTON);

        assertEquals(ResearchTreeGestureTracker.Type.NODE_CLICK, outcome.type());
        assertEquals(NODE, outcome.nodeId().orElseThrow());
        assertFalse(tracker.ownsButton(ResearchTreeGestureTracker.LEFT_BUTTON));
    }
}
