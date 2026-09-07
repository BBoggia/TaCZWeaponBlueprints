package com.gamergaming.taczweaponblueprints.client.designer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DesignerWorkQueueTest {
    @Test void obsoleteQueuedWorkIsRemovedAndNeverRuns() throws Exception {
        var worker = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1));
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(1);var calls=new AtomicInteger();
        try {
            worker.execute(()->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            assertTrue(started.await(2,TimeUnit.SECONDS));var queue=new DesignerWorkQueue(worker,Runnable::run);
            var obsolete=queue.submit(()->calls.incrementAndGet(),v->fail("obsolete result"),e->fail(e));obsolete.cancel();assertEquals(0,worker.getQueue().size());
            queue.submit(()->calls.incrementAndGet(),v->done.countDown(),e->fail(e));release.countDown();assertTrue(done.await(2,TimeUnit.SECONDS));assertEquals(1,calls.get());
        }finally{release.countDown();worker.shutdownNow();}
    }
    @Test void cancellationSuppressesCompletionAlreadyAwaitingUiThread()throws Exception{
        var worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1));var callbacks=new LinkedBlockingQueue<Runnable>();
        try{var queue=new DesignerWorkQueue(worker,callbacks::add);var h=queue.submit(()->1,v->fail("cancelled completion"),e->fail(e));var callback=callbacks.poll(2,TimeUnit.SECONDS);assertNotNull(callback);h.cancel();callback.run();}finally{worker.shutdownNow();}
    }
    @Test void cancellingRunningWorkInterruptsItWithoutReportingFailure()throws Exception{
        var worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1));var entered=new CountDownLatch(1);var interrupted=new CountDownLatch(1);
        try{var queue=new DesignerWorkQueue(worker,Runnable::run);var h=queue.submit(()->{entered.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){interrupted.countDown();throw e;}return 1;},v->fail("cancelled completion"),e->fail(e));assertTrue(entered.await(2,TimeUnit.SECONDS));h.cancel();assertTrue(interrupted.await(2,TimeUnit.SECONDS));}finally{worker.shutdownNow();}
    }
}
