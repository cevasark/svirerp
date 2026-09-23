package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/** Serializes outbound Zeffy calls and leaves at least one second between request starts. */
@Component
public class ZeffyRequestPacer {

    static final long INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    @FunctionalInterface
    interface Sleeper {
        void sleep(long nanos) throws InterruptedException;
    }

    private final ReentrantLock lock = new ReentrantLock(true);
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private long nextRequestAt;

    public ZeffyRequestPacer() {
        this(System::nanoTime, nanos -> TimeUnit.NANOSECONDS.sleep(nanos));
    }

    ZeffyRequestPacer(LongSupplier nanoTime, Sleeper sleeper) {
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    public void awaitPermit() {
        lock.lock();
        try {
            long remaining = nextRequestAt - nanoTime.getAsLong();
            if (remaining > 0) {
                try {
                    sleeper.sleep(remaining);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while pacing Zeffy API requests", ex);
                }
            }
            nextRequestAt = nanoTime.getAsLong() + INTERVAL_NANOS;
        } finally {
            lock.unlock();
        }
    }

    /** Applies a server-provided Retry-After delay to every caller sharing the API client. */
    public void defer(Duration delay) {
        if (delay == null || delay.isNegative() || delay.isZero()) return;
        lock.lock();
        try {
            nextRequestAt = Math.max(nextRequestAt, nanoTime.getAsLong() + delay.toNanos());
        } finally {
            lock.unlock();
        }
    }
}
