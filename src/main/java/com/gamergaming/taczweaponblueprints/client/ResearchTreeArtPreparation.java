package com.gamergaming.taczweaponblueprints.client;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Bounded metadata work only. No texture uploads, item rendering, or pack API calls. */
final class ResearchTreeArtPreparation implements AutoCloseable {
    private final ThreadPoolExecutor worker;
    private final Set<Future<?>> pending = new HashSet<>();
    private volatile long generation;

    ResearchTreeArtPreparation(int queuedLimit) {
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queuedLimit), runnable -> {
                    Thread thread = new Thread(runnable, "Research Tree artwork metadata");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    long generation() { return generation; }

    synchronized <T> Future<T> submit(long expected, Function<BooleanSupplier, T> preparation) {
        if (expected != generation) return null;
        pending.removeIf(Future::isDone);
        try {
            Future<T> future = worker.submit(() -> preparation.apply(
                    () -> expected != generation || Thread.currentThread().isInterrupted()));
            pending.add(future);
            return future;
        } catch (java.util.concurrent.RejectedExecutionException full) {
            // Visible artwork retries next frame, without doing work on the caller thread.
            return null;
        }
    }

    synchronized void invalidate() {
        generation++;
        pending.forEach(future -> future.cancel(true));
        pending.clear();
        worker.getQueue().clear();
    }

    synchronized int pendingCount() {
        pending.removeIf(Future::isDone);
        return pending.size();
    }

    @Override public synchronized void close() {
        invalidate();
        worker.shutdownNow();
    }
}
