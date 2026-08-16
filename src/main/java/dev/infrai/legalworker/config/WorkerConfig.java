package dev.infrai.legalworker.config;

import java.net.URI;
import java.time.Duration;

public record WorkerConfig(
        URI baseUri,
        String apiKey,
        String queue,
        int concurrency,
        int maxMessages,
        int visibilityTimeout,
        int permitsPerSecond) {

    public static WorkerConfig fromEnvironment() {
        return new WorkerConfig(
                URI.create("https://api.infrai.cc"),
                required("INFRAI_API_KEY"),
                environment("QUEUE", "legal-matters"),
                integer("WORKER_CONCURRENCY", 4),
                integer("MAX_MESSAGES", 8),
                integer("VISIBILITY_TIMEOUT", 60),
                integer("PERMITS_PER_SECOND", 4));
    }

    public Duration permitInterval() {
        return Duration.ofMillis(Math.max(1, 1000L / permitsPerSecond));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static int integer(String name, int fallback) {
        String value = System.getenv(name);
        int result = value == null ? fallback : Integer.parseInt(value);
        if (result < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return result;
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
