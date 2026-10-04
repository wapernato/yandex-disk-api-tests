package io.github.wapernato.disk;

import io.github.wapernato.disk.support.DiskClient;
import io.github.wapernato.disk.support.TestConfig;
import io.github.wapernato.disk.support.TestNamespace;
import io.restassured.http.Method;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;

class AuthorizationIT {
    record Case(Method method, String endpoint, Map<String, ?> query, String token) {
        @Override public String toString() {
            return method + " " + endpoint + (token == null ? " without token" : " with invalid token");
        }
    }

    static Stream<Case> requests() {
        String root = TestNamespace.newRoot();
        return Stream.of("missing", "invalid").flatMap(mode -> {
            String token = "invalid".equals(mode) ? "intentionally-invalid-aqa-token" : null;
            return Stream.of(
                    new Case(Method.GET, "/v1/disk/resources", Map.of("path", root), token),
                    new Case(Method.PUT, "/v1/disk/resources", Map.of("path", root), token),
                    new Case(Method.POST, "/v1/disk/resources/copy", Map.of("from", root, "path", root + "/copy"), token),
                    new Case(Method.DELETE, "/v1/disk/resources", Map.of("path", root), token));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requests")
    void rejectsMissingAndInvalidCredentials(Case request) {
        new DiskClient(TestConfig.API, request.token()).send(request.method(), request.endpoint(), request.query())
                .then().statusCode(401).contentType("application/json")
                .body("error", not(emptyOrNullString()))
                .body("message", not(emptyOrNullString()))
                .body("description", not(emptyOrNullString()));
    }
}
