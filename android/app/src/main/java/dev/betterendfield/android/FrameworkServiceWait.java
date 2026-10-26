package dev.betterendfield.android;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Bounded connection wait; callers run on a worker thread. */
final class FrameworkServiceWait {
    interface Checkpoint { void check() throws IOException; }

    static boolean await(Object monitor, BooleanSupplier connected, long timeoutMillis,
            Checkpoint checkpoint) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (monitor) {
            while (true) {
                checkpoint.check();
                if (connected.getAsBoolean()) return true;
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                try {
                    monitor.wait(Math.min(250, Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining))));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("等待框架服务连接已中断", error);
                }
            }
        }
    }
}
