package io.github.wapernato.disk.support;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PollerTest {
    @Test
    void returnsImmediatelyWhenComplete() {
        Poller p = new Poller(() -> 0, ms -> fail("Must not sleep after completion"));
        assertEquals("done", p.await("test", () -> "done", "done"::equals,
                Duration.ofSeconds(1), Duration.ofMillis(100)));
    }

    @Test
    void pollsUntilCompletion() {
        AtomicLong time = new AtomicLong();
        AtomicInteger reads = new AtomicInteger();
        Poller p = new Poller(time::get, ms -> time.addAndGet(ms * 1_000_000));
        assertEquals(3, p.await("test", reads::incrementAndGet, n -> n == 3,
                Duration.ofSeconds(1), Duration.ofMillis(100)));
        assertEquals(200_000_000L, time.get());
    }

    @Test
    void deadlineStopsPollingWithoutRealSleep() {
        AtomicLong time = new AtomicLong();
        AtomicInteger reads = new AtomicInteger();
        Poller p = new Poller(time::get, ms -> time.addAndGet(ms * 1_000_000));
        assertThrows(AssertionError.class, () -> p.await("test", reads::incrementAndGet, n -> false,
                Duration.ofMillis(250), Duration.ofMillis(100)));
        assertEquals(3, reads.get());
        assertEquals(250_000_000L, time.get());
    }

    @Test
    void restoresInterruptFlag() {
        Poller p = new Poller(() -> 0, ms -> { throw new InterruptedException(); });
        try {
            assertThrows(AssertionError.class, () -> p.await("test", () -> false, b -> b,
                    Duration.ofSeconds(1), Duration.ofMillis(100)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test
    void propagatesReadFailuresWithoutRetry() {
        AtomicInteger reads = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> new Poller().await("test", () -> {
            reads.incrementAndGet(); throw new IllegalStateException("failed");
        }, value -> true, Duration.ofSeconds(1), Duration.ofMillis(100)));
        assertEquals(1, reads.get());
    }
}
