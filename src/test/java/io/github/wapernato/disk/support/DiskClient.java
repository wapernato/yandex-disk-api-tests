package io.github.wapernato.disk.support;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.net.URI;
import java.util.Map;

import static io.restassured.RestAssured.given;

public final class DiskClient {
    private final String baseUri;
    private final String token;

    public DiskClient(String baseUri, String token) {
        this.baseUri = baseUri;
        this.token = token;
    }

    private RequestSpecification request() {
        var config = RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", 5000)
                .setParam("http.socket.timeout", 10000)
                .setParam("http.connection-manager.timeout", 5000L));
        var builder = new RequestSpecBuilder().setBaseUri(baseUri)
                .setAccept(ContentType.JSON).setContentType(ContentType.JSON).setConfig(config);
        if (token != null && !token.isBlank()) {
            builder.addHeader("Authorization", "OAuth " + token);
        }
        // No request/response logging: OAuth headers must never reach test reports.
        return given().spec(builder.build()).redirects().follow(false);
    }

    public Response send(Method method, String endpoint, Map<String, ?> query) {
        return request().queryParams(query).request(method, endpoint);
    }

    public Response metadata(String path) {
        return send(Method.GET, "/v1/disk/resources", Map.of("path", path));
    }

    public Response createFolder(String path) {
        return send(Method.PUT, "/v1/disk/resources", Map.of("path", path));
    }

    public Response copy(String from, String path, boolean forceAsync) {
        return send(Method.POST, "/v1/disk/resources/copy",
                Map.of("from", from, "path", path, "overwrite", false, "force_async", forceAsync));
    }

    public Response move(String from, String path) {
        return send(Method.POST, "/v1/disk/resources/move",
                Map.of("from", from, "path", path, "overwrite", false));
    }

    public Response delete(String path, boolean forceAsync) {
        TestNamespace.requireOwnedPath(path);
        return send(Method.DELETE, "/v1/disk/resources",
                Map.of("path", path, "permanently", true, "force_async", forceAsync));
    }

    public Response operation(String href) {
        requireOperationLink(href);
        return request().get(href);
    }

    static void requireOperationLink(String href) {
        if (href == null) throw new IllegalArgumentException("Missing operation href.");
        URI uri = URI.create(href);
        String path = uri.getPath();
        if (!"https".equals(uri.getScheme()) || !"cloud-api.yandex.net".equals(uri.getHost())
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getFragment() != null || path == null
                || !(path.equals("/v1/disk/operations") || path.startsWith("/v1/disk/operations/"))
                || path.contains("..")) {
            throw new IllegalArgumentException("Untrusted operation link; OAuth token was not sent.");
        }
    }
}
