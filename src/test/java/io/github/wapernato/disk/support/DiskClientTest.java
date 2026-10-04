package io.github.wapernato.disk.support;

import com.sun.net.httpserver.HttpServer;
import io.restassured.http.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DiskClientTest {
    @Test
    void encodesPathAndAddsOAuthHeaderOverRealHttp() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/disk/resources", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            query.set(exchange.getRequestURI().getRawQuery());
            method.set(exchange.getRequestMethod());
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String path = "disk:/Тест + & данные";
            var client = new DiskClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-placeholder");
            assertEquals(200, client.metadata(path).statusCode());
            assertEquals("GET", method.get());
            assertEquals("OAuth test-placeholder", auth.get());
            assertEquals("path=" + path, URLDecoder.decode(query.get(), StandardCharsets.UTF_8));
            assertFalse(query.get().contains("test-placeholder"));
            client = new DiskClient("http://127.0.0.1:" + server.getAddress().getPort(), null);
            client.send(Method.GET, "/v1/disk/resources", Map.of("path", path));
            assertNull(auth.get());
        } finally { server.stop(0); }
    }

    @Test
    void acceptsBothDocumentedOperationLinkForms() {
        assertDoesNotThrow(() -> DiskClient.requireOperationLink("https://cloud-api.yandex.net/v1/disk/operations?id=test"));
        assertDoesNotThrow(() -> DiskClient.requireOperationLink("https://cloud-api.yandex.net/v1/disk/operations/test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://cloud-api.yandex.net/v1/disk/operations/test",
            "https://cloud-api.yandex.net.evil.example/v1/disk/operations/test",
            "https://evil.example/v1/disk/operations/test",
            "https://cloud-api.yandex.net/v1/disk/resources",
            "https://cloud-api.yandex.net:444/v1/disk/operations/test",
            "https://user@cloud-api.yandex.net/v1/disk/operations/test",
            "https://cloud-api.yandex.net/v1/disk/operations/../resources"})
    void rejectsUntrustedOperationLinks(String href) {
        assertThrows(IllegalArgumentException.class, () -> DiskClient.requireOperationLink(href));
    }
}
