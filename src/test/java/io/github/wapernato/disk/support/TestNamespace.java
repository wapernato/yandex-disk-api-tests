package io.github.wapernato.disk.support;

import java.util.UUID;

public final class TestNamespace {
    private TestNamespace() { }

    public static String newRoot() {
        return "disk:/aqa-it-" + UUID.randomUUID();
    }

    public static void requireOwnedPath(String path) {
        if (path == null || !path.matches("disk:/aqa-it-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(/[^\\r\\n]*)?")
                || path.contains("/../") || path.endsWith("/..") || path.contains("/./")
                || path.endsWith("/.") || path.contains("%") || path.contains("\\")) {
            throw new IllegalArgumentException("Refusing to delete a path outside the test namespace.");
        }
    }
}
