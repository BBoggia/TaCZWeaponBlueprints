package com.gamergaming.taczweaponblueprints.client.designer;

import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Bounded single worker keeps detached computation and IO away from native input. */
final class DesignerClientWork {
    private static final DesignerWorkQueue WORK = new DesignerWorkQueue(
            new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
                    r -> { var t = new Thread(r, "Research Designer"); t.setDaemon(true); return t; }),
            callback -> Minecraft.getInstance().execute(callback));
    static <T> DesignerWorkQueue.Handle submit(Callable<T> action, Consumer<T> success, Consumer<Exception> failure) {
        return WORK.submit(action, success, failure);
    }
    private DesignerClientWork() {}
}
