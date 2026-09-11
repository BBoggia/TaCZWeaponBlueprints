package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ResearchTreeArtPreparationTest {
    @Test
    void saturationNeverRunsMetadataOnTheCallerAndRetriesAfterCapacityReturns() throws Exception {
        try (var preparation = new ResearchTreeArtPreparation(1)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            String caller = Thread.currentThread().getName();
            var first = preparation.submit(0, cancelled -> {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                return Thread.currentThread().getName();
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var second = preparation.submit(0, cancelled -> 2);
                assertNotNull(second);
                assertNull(preparation.submit(0, cancelled -> fail("must not run on caller")));
                assertEquals(2, preparation.pendingCount());
                release.countDown();
                assertNotEquals(caller, first.get(5, TimeUnit.SECONDS));
                assertEquals(2, second.get(5, TimeUnit.SECONDS));
                assertEquals(3, preparation.submit(0, cancelled -> 3).get(5, TimeUnit.SECONDS));
                assertEquals(0, preparation.pendingCount());
            } finally { release.countDown(); }
        }
    }

    @Test
    void reloadCancelsRunningAndQueuedWorkAndRejectsStaleSubmissions() throws Exception {
        try (var preparation = new ResearchTreeArtPreparation(1)) {
            var entered = new CountDownLatch(1);
            var stopped = new CountDownLatch(1);
            var first = preparation.submit(0, cancelled -> {
                entered.countDown();
                while (!cancelled.getAsBoolean()) Thread.onSpinWait();
                stopped.countDown();
                return "obsolete";
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = preparation.submit(0, cancelled -> "queued obsolete");
            preparation.invalidate();
            // Cooperative work may finish immediately after observing invalidation.
            assertTrue(first.isDone());
            assertTrue(second.isDone());
            assertTrue(stopped.await(5, TimeUnit.SECONDS));
            assertNull(preparation.submit(0, cancelled -> "stale"));
            assertEquals("current", preparation.submit(preparation.generation(), cancelled -> "current").get(5, TimeUnit.SECONDS));
            assertEquals(0, preparation.pendingCount());
        }
    }

    @Test
    void failedPreparationDoesNotPoisonTheWorkerOrSurviveRepeatedReloads() throws Exception {
        try (var preparation = new ResearchTreeArtPreparation(1)) {
            for (int i = 0; i < 5; i++) {
                var failure = preparation.submit(preparation.generation(), cancelled -> { throw new IllegalArgumentException("bad art"); });
                assertThrows(java.util.concurrent.ExecutionException.class, () -> failure.get(5, TimeUnit.SECONDS));
                preparation.invalidate();
                assertEquals(0, preparation.pendingCount());
                assertEquals("fallback", preparation.submit(preparation.generation(), cancelled -> "fallback").get(5, TimeUnit.SECONDS));
            }
        }
    }
}
