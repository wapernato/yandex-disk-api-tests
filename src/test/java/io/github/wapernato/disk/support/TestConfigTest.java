package io.github.wapernato.disk.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestConfigTest {
    @Test
    void refusesMissingToken() {
        assertThrows(IllegalStateException.class, () -> TestConfig.from(Map.of("YANDEX_DISK_TEST_ACCOUNT", "true")));
    }
    @Test
    void refusesUnconfirmedAccount() {
        assertThrows(IllegalStateException.class, () -> TestConfig.from(Map.of("YANDEX_DISK_TOKEN", "test-placeholder")));
    }
    @Test
    void acceptsConfirmedAccountAndRedactsToken() {
        var c = TestConfig.from(Map.of("YANDEX_DISK_TOKEN", "test-placeholder", "YANDEX_DISK_TEST_ACCOUNT", "true"));
        assertEquals(Duration.ofSeconds(60), c.operationTimeout());
        assertFalse(c.toString().contains("test-placeholder"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "301", "not-a-number"})
    void refusesInvalidTimeout(String value) {
        assertThrows(IllegalArgumentException.class, () -> TestConfig.from(Map.of("YANDEX_DISK_TOKEN", "test-placeholder",
                "YANDEX_DISK_TEST_ACCOUNT", "true", "YANDEX_DISK_OPERATION_TIMEOUT_SECONDS", value)));
    }
}
