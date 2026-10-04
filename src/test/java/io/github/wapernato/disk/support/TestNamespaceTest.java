package io.github.wapernato.disk.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TestNamespaceTest {
    @Test
    void acceptsOnlyUniqueDisposableRootsAndChildren() {
        String root = TestNamespace.newRoot();
        assertNotEquals(root, TestNamespace.newRoot());
        assertDoesNotThrow(() -> TestNamespace.requireOwnedPath(root));
        assertDoesNotThrow(() -> TestNamespace.requireOwnedPath(root + "/Тест + &"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"disk:/", "disk:/Documents", "disk:/aqa-it-invalid", "disk:/aqa-it-00000000-0000-0000-0000-000000000000/../Documents",
            "disk:/aqa-it-00000000-0000-0000-0000-000000000000/%2e%2e/Documents"})
    void refusesUnsafeDeletionPaths(String path) {
        assertThrows(IllegalArgumentException.class, () -> TestNamespace.requireOwnedPath(path));
    }
}
