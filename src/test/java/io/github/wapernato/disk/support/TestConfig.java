package io.github.wapernato.disk.support;

import java.time.Duration;
import java.util.Map;

public record TestConfig(String token, Duration operationTimeout) {
    public static final String API = "https://cloud-api.yandex.net";

    public static TestConfig fromEnvironment() {
        return from(System.getenv());
    }

    static TestConfig from(Map<String, String> env) {
        String token = env.getOrDefault("YANDEX_DISK_TOKEN", "").trim();
        if (token.isEmpty()) {
            throw new IllegalStateException("Set YANDEX_DISK_TOKEN from a dedicated Yandex test account.");
        }
        if (!"true".equals(env.get("YANDEX_DISK_TEST_ACCOUNT"))) {
            throw new IllegalStateException("Confirm the dedicated test account: YANDEX_DISK_TEST_ACCOUNT=true.");
        }
        int seconds;
        try {
            seconds = Integer.parseInt(env.getOrDefault("YANDEX_DISK_OPERATION_TIMEOUT_SECONDS", "60"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Operation timeout must be an integer between 1 and 300.");
        }
        if (seconds < 1 || seconds > 300) {
            throw new IllegalArgumentException("Operation timeout must be between 1 and 300 seconds.");
        }
        return new TestConfig(token, Duration.ofSeconds(seconds));
    }

    // A record's generated toString would disclose the token in diagnostics.
    @Override
    public String toString() {
        return "TestConfig[token=<redacted>, operationTimeout=" + operationTimeout + "]";
    }
}
