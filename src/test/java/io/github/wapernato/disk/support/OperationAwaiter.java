package io.github.wapernato.disk.support;

import io.restassured.response.Response;
import java.time.Duration;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public final class OperationAwaiter {
    private final Function<String, Response> fetch;
    private final Poller poller;
    private final Duration timeout;

    public OperationAwaiter(DiskClient client, Duration timeout) {
        this(client::operation, new Poller(), timeout);
    }

    OperationAwaiter(Function<String, Response> fetch, Poller poller, Duration timeout) {
        this.fetch = fetch;
        this.poller = poller;
        this.timeout = timeout;
    }

    public void complete(Response response, int synchronousStatus) {
        int code = response.statusCode();
        assertTrue(code == synchronousStatus || code == 202,
                "Expected " + synchronousStatus + " or 202, received " + code);
        if (code == synchronousStatus) return;
        assertEquals("GET", response.jsonPath().getString("method"));
        assertEquals(Boolean.FALSE, response.jsonPath().getBoolean("templated"));
        String href = response.jsonPath().getString("href");
        DiskClient.requireOperationLink(href);
        poller.await("asynchronous Disk operation", () -> {
            Response status = fetch.apply(href);
            assertEquals(200, status.statusCode(), "Operation status request failed");
            String state = status.jsonPath().getString("status");
            assertNotEquals("failed", state, "Disk operation failed");
            assertTrue("success".equals(state) || "in-progress".equals(state),
                    "Unexpected operation state: " + state);
            return state;
        }, "success"::equals, timeout, Duration.ofMillis(250));
    }
}
