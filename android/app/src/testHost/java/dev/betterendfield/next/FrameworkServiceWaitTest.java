package dev.betterendfield.next;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Connection timing regression checks, runnable with javac/java. */
public final class FrameworkServiceWaitTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Object monitor=new Object();
        check(FrameworkServiceWait.await(monitor,()->true,0,()->{}),"ready service rejected");
        check(!FrameworkServiceWait.await(monitor,()->false,30,()->{}),"missing service accepted");
        delayedConnection();
        cancellation();
        interruption();
        System.out.println("FrameworkServiceWaitTest: " + checks + " checks passed");
    }

    private static void delayedConnection() throws Exception {
        Object monitor=new Object();
        AtomicBoolean connected=new AtomicBoolean(),success=new AtomicBoolean();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        CountDownLatch waiting=new CountDownLatch(1),rechecked=new CountDownLatch(1);
        AtomicBoolean first=new AtomicBoolean(true);
        Thread worker=start(()->{
            try {
                success.set(FrameworkServiceWait.await(monitor,connected::get,2000,()->{
                    if(first.getAndSet(false)) waiting.countDown(); else rechecked.countDown();
                }));
            } catch(Throwable error) {failure.set(error);}
        });
        check(waiting.await(1,TimeUnit.SECONDS),"wait did not start");
        synchronized(monitor) {monitor.notifyAll();}
        check(rechecked.await(1,TimeUnit.SECONDS),"spurious wake was not checked");
        check(!success.get(),"spurious wake accepted without connection");
        synchronized(monitor) {connected.set(true);monitor.notifyAll();}
        join(worker);
        check(failure.get()==null && success.get(),"late connection rejected: "+failure.get());
    }

    private static void cancellation() throws Exception {
        AtomicBoolean cancelled=new AtomicBoolean();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        CountDownLatch waiting=new CountDownLatch(1);
        Thread worker=start(()->{
            try {FrameworkServiceWait.await(new Object(),()->false,10000,()->{
                waiting.countDown();if(cancelled.get()) throw new IOException("cancelled");
            });} catch(Throwable error) {failure.set(error);}
        });
        check(waiting.await(1,TimeUnit.SECONDS),"cancellable wait did not start");
        cancelled.set(true);join(worker);
        check(failure.get() instanceof IOException && "cancelled".equals(failure.get().getMessage()),
                "cancellation lost: "+failure.get());
    }

    private static void interruption() throws Exception {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        AtomicBoolean interrupted=new AtomicBoolean();
        CountDownLatch waiting=new CountDownLatch(1);
        Thread worker=start(()->{
            try {FrameworkServiceWait.await(new Object(),()->false,10000,waiting::countDown);}
            catch(Throwable error) {failure.set(error);interrupted.set(Thread.currentThread().isInterrupted());}
        });
        check(waiting.await(1,TimeUnit.SECONDS),"interruptible wait did not start");
        worker.interrupt();join(worker);
        check(failure.get() instanceof IOException && interrupted.get(),"interruption lost");
    }

    private static Thread start(Runnable work) {
        Thread worker=new Thread(work,"framework-wait-test");worker.setDaemon(true);worker.start();return worker;
    }
    private static void join(Thread worker) throws InterruptedException {
        worker.join(2000);
        if(worker.isAlive()) {worker.interrupt();throw new AssertionError("connection wait stalled");}
    }
    private static void check(boolean valid,String reason) {checks++;if(!valid) throw new AssertionError(reason);}
}
