package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeFeedbackStateTest {
    private static final ResourceLocation NODE = new ResourceLocation("test:node");

    @Test
    void feedbackTransitionsAreExplicitAndResettable() {
        ResearchTreeFeedbackState feedback = new ResearchTreeFeedbackState();

        assertEquals(ResearchTreeFeedbackState.Status.IDLE, feedback.snapshot().status());
        feedback.pending(NODE);
        assertEquals(ResearchTreeFeedbackState.Status.PENDING, feedback.snapshot().status());
        assertTrue(feedback.pending());
        feedback.succeeded(NODE);
        assertEquals(ResearchTreeFeedbackState.Status.SUCCESS, feedback.snapshot().status());
        feedback.failed(NODE, "points_required");
        assertEquals(ResearchTreeFeedbackState.Status.FAILURE, feedback.snapshot().status());
        assertEquals("points_required", feedback.snapshot().resultKey().orElseThrow());
        feedback.clear();
        assertEquals(ResearchTreeFeedbackState.Status.IDLE, feedback.snapshot().status());
        assertFalse(feedback.pending());
        assertTrue(feedback.snapshot().blueprintId().isEmpty());
    }

    @Test
    void malformedFeedbackCannotBePublished() {
        ResearchTreeFeedbackState feedback = new ResearchTreeFeedbackState();
        assertThrows(IllegalArgumentException.class, () -> feedback.pending(null));
        assertThrows(IllegalArgumentException.class, () -> feedback.failed(NODE, " "));
    }

    @Test
    void correlatedResultsIgnoreStaleRepliesAndTimeoutPendingWork() {
        ResearchTreeFeedbackState feedback = new ResearchTreeFeedbackState();
        feedback.pending(NODE, 7, 1_000L);

        assertFalse(feedback.succeeded(NODE, 6, 1_100L));
        assertEquals(ResearchTreeFeedbackState.Status.PENDING, feedback.snapshot().status());
        assertFalse(feedback.expirePending(5_999L, 5_000L, "timeout"));
        assertTrue(feedback.expirePending(6_000L, 5_000L, "timeout"));
        assertEquals(ResearchTreeFeedbackState.Status.FAILURE, feedback.snapshot().status());
        assertEquals("timeout", feedback.snapshot().resultKey().orElseThrow());

        feedback.pending(NODE, 8, 7_000L);
        assertTrue(feedback.expirePending(12_000L, 5_000L, "request_timeout"));
        assertTrue(feedback.succeeded(NODE, 8, 12_100L));
        assertEquals(ResearchTreeFeedbackState.Status.SUCCESS, feedback.snapshot().status());
    }
}
