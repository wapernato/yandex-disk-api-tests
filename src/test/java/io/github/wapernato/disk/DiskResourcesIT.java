package io.github.wapernato.disk;

import io.github.wapernato.disk.support.*;
import io.restassured.http.Method;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DiskResourcesIT {
    private static TestConfig config;
    private DiskClient client;
    private OperationAwaiter operations;
    private String root;

    @BeforeAll
    static void requireTestAccount() {
        // Fail explicitly instead of silently skipping all live scenarios.
        config = TestConfig.fromEnvironment();
    }

    @BeforeEach
    void isolateData() {
        client = new DiskClient(TestConfig.API, config.token());
        operations = new OperationAwaiter(client, config.operationTimeout());
        root = TestNamespace.newRoot();
        create(root);
    }

    @AfterEach
    void cleanUp() {
        if (root == null) return;
        TestNamespace.requireOwnedPath(root);
        try {
            Response response = client.delete(root, true);
            if (response.statusCode() != 404) operations.complete(response, 204);
            awaitAbsent(root);
        } catch (AssertionError | RuntimeException failure) {
            throw new AssertionError("Cleanup failed for disposable test path " + root, failure);
        }
        // A cleanup failure is a test failure and includes the disposable path.
    }

    private String child(String name) { return root + "/" + name; }

    private void create(String path) {
        client.createFolder(path).then().statusCode(201).contentType("application/json")
                .body("method", equalTo("GET")).body("templated", equalTo(false))
                .body("href", startsWith(TestConfig.API + "/v1/disk/resources?"));
        metadata(path);
    }

    private Response metadata(String path) {
        return new Poller().await("visible resource " + path, () -> {
            Response r = client.metadata(path);
            assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
                    "Unexpected metadata HTTP status: " + r.statusCode());
            return r;
        }, r -> r.statusCode() == 200, config.operationTimeout(), Duration.ofMillis(250));
    }

    private void awaitAbsent(String path) {
        new Poller().await("deleted resource " + path, () -> {
            Response r = client.metadata(path);
            assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
                    "Unexpected metadata HTTP status: " + r.statusCode());
            return r;
        }, r -> r.statusCode() == 404, config.operationTimeout(), Duration.ofMillis(250));
    }

    private void error(Response response, int status) {
        response.then().statusCode(status).contentType("application/json")
                .body("error", not(emptyOrNullString())).body("message", not(emptyOrNullString()))
                .body("description", not(emptyOrNullString()));
    }

    @Test
    @DisplayName("GET: метаданные созданной папки")
    void getsFolderMetadata() {
        metadata(root).then().statusCode(200).body("path", equalTo(root))
                .body("name", equalTo(root.substring("disk:/".length()))).body("type", equalTo("dir"));
    }

    @Test
    @DisplayName("GET: пагинация возвращает обе папки без повторов")
    void listsChildrenWithPagination() {
        create(child("alpha"));
        create(child("beta"));
        Response first = client.send(Method.GET, "/v1/disk/resources",
                Map.of("path", root, "limit", 1, "offset", 0, "sort", "name"));
        first.then().statusCode(200).body("_embedded.total", equalTo(2))
                .body("_embedded.limit", equalTo(1)).body("_embedded.offset", equalTo(0))
                .body("_embedded.items", hasSize(1)).body("_embedded.items[0].path", equalTo(child("alpha")));
        client.send(Method.GET, "/v1/disk/resources",
                Map.of("path", root, "limit", 1, "offset", 1, "sort", "name"))
                .then().statusCode(200).body("_embedded.items", hasSize(1))
                .body("_embedded.items[0].path", equalTo(child("beta")));
    }

    @Test
    @DisplayName("GET: отсутствующая папка — 404")
    void missingMetadata() { error(client.metadata(child("missing")), 404); }

    @Test
    @DisplayName("GET: отсутствующий обязательный path — 400")
    void metadataWithoutPath() { error(client.send(Method.GET, "/v1/disk/resources", Map.of()), 400); }

    @Test
    @DisplayName("PUT: создание папки и проверка состояния")
    void createsFolder() {
        create(child("created"));
        metadata(child("created")).then().body("type", equalTo("dir")).body("path", equalTo(child("created")));
    }

    @Test
    @DisplayName("PUT/GET: кириллица, пробелы, плюс и амперсанд в имени")
    void supportsUnicodeAndReservedCharacters() {
        String name = "Тестовая папка + & данные";
        create(child(name));
        metadata(child(name)).then().body("name", equalTo(name)).body("path", equalTo(child(name)));
    }

    @Test
    @DisplayName("PUT: повторное создание — 409, исходная папка сохранена")
    void duplicateFolder() {
        create(child("duplicate"));
        error(client.createFolder(child("duplicate")), 409);
        metadata(child("duplicate")).then().body("type", equalTo("dir"));
    }

    @Test
    @DisplayName("PUT: отсутствующий path — 400")
    void createWithoutPath() { error(client.send(Method.PUT, "/v1/disk/resources", Map.of()), 400); }

    @Test
    @DisplayName("PUT: имя длиннее 255 символов — 400")
    void rejectsTooLongName() { error(client.createFolder(child("x".repeat(256))), 400); }

    @Test
    @DisplayName("POST: копирование пустой папки, источник сохранён")
    void copiesEmptyFolder() {
        create(child("source"));
        operations.complete(client.copy(child("source"), child("copy"), false), 201);
        metadata(child("copy")).then().body("type", equalTo("dir")).body("path", equalTo(child("copy")));
        metadata(child("source")).then().body("type", equalTo("dir"));
    }

    @Test
    @DisplayName("POST: принудительно асинхронное копирование дерева")
    void copiesTreeAsynchronously() {
        create(child("source"));
        create(child("source/nested"));
        Response response = client.copy(child("source"), child("copy"), true);
        response.then().statusCode(202);
        operations.complete(response, 201);
        metadata(child("copy/nested")).then().body("path", equalTo(child("copy/nested"))).body("type", equalTo("dir"));
        metadata(child("source/nested")).then().body("type", equalTo("dir"));
    }

    @Test
    @DisplayName("POST: перемещение, старый путь недоступен")
    void movesFolder() {
        create(child("source"));
        operations.complete(client.move(child("source"), child("moved")), 201);
        metadata(child("moved")).then().body("type", equalTo("dir"));
        awaitAbsent(child("source"));
    }

    @Test
    @DisplayName("POST: копирование отсутствующего источника — 404")
    void copiesMissingSource() {
        error(client.copy(child("missing"), child("copy"), false), 404);
        error(client.metadata(child("copy")), 404);
    }

    @Test
    @DisplayName("POST: overwrite=false защищает существующее дерево — 409")
    void refusesOverwrite() {
        create(child("source"));
        create(child("destination"));
        create(child("destination/keep"));
        error(client.copy(child("source"), child("destination"), false), 409);
        metadata(child("destination/keep")).then().body("type", equalTo("dir"));
    }

    @Test
    @DisplayName("POST: отсутствующий from — 400")
    void copyWithoutSourceParameter() {
        error(client.send(Method.POST, "/v1/disk/resources/copy", Map.of("path", child("copy"))), 400);
    }

    @Test
    @DisplayName("DELETE: удаление пустой папки и повторный DELETE — 404")
    void deletesFolderAndRejectsSecondDelete() {
        create(child("deleted"));
        operations.complete(client.delete(child("deleted"), false), 204);
        awaitAbsent(child("deleted"));
        error(client.delete(child("deleted"), false), 404);
    }

    @Test
    @DisplayName("DELETE: принудительно асинхронное удаление дерева")
    void deletesTreeAsynchronously() {
        create(child("tree"));
        create(child("tree/nested"));
        Response response = client.delete(child("tree"), true);
        response.then().statusCode(202);
        operations.complete(response, 204);
        awaitAbsent(child("tree"));
        error(client.metadata(child("tree/nested")), 404);
    }

    @Test
    @DisplayName("DELETE: отсутствующая папка — 404")
    void deletesMissingFolder() { error(client.delete(child("missing"), false), 404); }

    @Test
    @DisplayName("DELETE: отсутствующий path — 400")
    void deleteWithoutPath() { error(client.send(Method.DELETE, "/v1/disk/resources", Map.of()), 400); }
}
