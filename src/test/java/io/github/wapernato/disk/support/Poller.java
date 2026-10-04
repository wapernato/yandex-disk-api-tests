package io.github.wapernato.disk.support;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Poller {
    @FunctionalInterface
    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    private final LongSupplier clock;
    private final Sleeper sleeper;

    public Poller() { this(System::nanoTime, Thread::sleep); }

    Poller(LongSupplier clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public <T> T await(String description, Supplier<T> read, Predicate<T> completed,
                       Duration timeout, Duration interval) {
        if (timeout.isZero() || timeout.isNegative() || interval.toMillis() < 1) {
            throw new IllegalArgumentException("Positive timeout and polling interval required.");
        }
        long started = clock.getAsLong();
        while (true) {
            T value = read.get();
            if (completed.test(value)) return value;
            long remaining = timeout.toNanos() - (clock.getAsLong() - started);
            if (remaining <= 0) throw new AssertionError("Timed out waiting for " + description);
            try {
                sleeper.sleep(Math.min(interval.toMillis(), Math.max(1, remaining / 1_000_000)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + description);
            }
            if (clock.getAsLong() - started >= timeout.toNanos()) {
                throw new AssertionError("Timed out waiting for " + description);
            }
        }
    }
}
