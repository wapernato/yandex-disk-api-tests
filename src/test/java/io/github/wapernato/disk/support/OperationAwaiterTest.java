package io.github.wapernato.disk.support;

import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class OperationAwaiterTest {
    private static final String LINK = "https://cloud-api.yandex.net/v1/disk/operations?id=test";
    private static Response response(int code, String json) {
        return new ResponseBuilder().setStatusCode(code).setContentType("application/json").setBody(json).build();
    }
    private static Response accepted() {
        return response(202, "{\"href\":\"" + LINK + "\",\"method\":\"GET\",\"templated\":false}");
    }
    private static Poller fakePoller() {
        AtomicLong time = new AtomicLong();
        return new Poller(time::get, ms -> time.addAndGet(ms * 1_000_000));
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 204})
    void synchronousResponseNeedsNoPolling(int code) {
        new OperationAwaiter(link -> { fail("Unexpected polling"); return null; }, fakePoller(), Duration.ofSeconds(1))
                .complete(response(code, ""), code);
    }

    @Test
    void pendingThenSuccessUsesReturnedHref() {
        AtomicInteger calls = new AtomicInteger();
        new OperationAwaiter(link -> {
            assertEquals(LINK, link);
            return response(200, calls.incrementAndGet() == 1 ? "{\"status\":\"in-progress\"}" : "{\"status\":\"success\"}");
        }, fakePoller(), Duration.ofSeconds(1)).complete(accepted(), 201);
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "unknown", ""})
    void failedOrMalformedStateFails(String state) {
        var awaiter = new OperationAwaiter(link -> response(200, "{\"status\":\"" + state + "\"}"),
                fakePoller(), Duration.ofSeconds(1));
        assertThrows(AssertionError.class, () -> awaiter.complete(accepted(), 201));
    }

    @Test
    void neverEndingOperationTimesOut() {
        var awaiter = new OperationAwaiter(link -> response(200, "{\"status\":\"in-progress\"}"),
                fakePoller(), Duration.ofMillis(500));
        assertThrows(AssertionError.class, () -> awaiter.complete(accepted(), 204));
    }

    @Test
    void failedStatusRequestFails() {
        var awaiter = new OperationAwaiter(link -> response(503, "{}"), fakePoller(), Duration.ofSeconds(1));
        assertThrows(AssertionError.class, () -> awaiter.complete(accepted(), 201));
    }

    @Test
    void refusesUnexpectedMutationResponse() {
        var awaiter = new OperationAwaiter(link -> { fail("Must not poll"); return null; }, fakePoller(), Duration.ofSeconds(1));
        assertThrows(AssertionError.class, () -> awaiter.complete(response(409, "{}"), 201));
    }

    @Test
    void refusesForeignHrefBeforeSendingCredentials() {
        var awaiter = new OperationAwaiter(link -> { fail("Must not fetch"); return null; }, fakePoller(), Duration.ofSeconds(1));
        Response r = response(202, "{\"href\":\"https://example.com/steal\",\"method\":\"GET\",\"templated\":false}");
        assertThrows(IllegalArgumentException.class, () -> awaiter.complete(r, 201));
    }
}
