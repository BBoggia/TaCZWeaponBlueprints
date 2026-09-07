package com.gamergaming.taczweaponblueprints.client.designer;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bounded worker with cancellation that also suppresses already queued completion callbacks. */
final class DesignerWorkQueue {
    interface Handle { void cancel(); }
    private final ThreadPoolExecutor worker;
    private final Executor callbacks;
    DesignerWorkQueue(ThreadPoolExecutor worker, Executor callbacks) {
        this.worker = worker;
        this.callbacks = callbacks;
    }
    <T> Handle submit(Callable<T> action, Consumer<T> success, Consumer<Exception> failure) {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var task = new FutureTask<T>(action) {
            @Override protected void done() {
                if (cancelled.get() || isCancelled()) return;
                callbacks.execute(() -> {
                    if (cancelled.get() || isCancelled()) return;
                    try { success.accept(get()); }
                    catch (ExecutionException error) {
                        Throwable cause = error.getCause();
                        failure.accept(cause instanceof Exception e ? e : new IllegalStateException(cause));
                    } catch (Exception error) { failure.accept(error); }
                });
            }
        };
        try { worker.execute(task); }
        catch (RejectedExecutionException error) { failure.accept(error); }
        return () -> { cancelled.set(true); task.cancel(true); worker.remove(task); };
    }
}
