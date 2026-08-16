package dev.infrai.legalworker.queue;

import dev.infrai.legalworker.config.WorkerConfig;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class InfraiQueueClient {
    private final HttpClient http;
    private final WorkerConfig config;

    public InfraiQueueClient(WorkerConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    // Copyable capability idiom: infrai.queue.consume
    public List<QueueMessage> consume() throws IOException, InterruptedException {
        Map<String, Object> data = call("/v1/queue/consume", Map.of(
                "queue", config.queue(),
                "max_messages", config.maxMessages(),
                "visibility_timeout", config.visibilityTimeout()), null);
        Object raw = data.get("messages");
        if (!(raw instanceof List<?> messages)) return List.of();
        return messages.stream().map(this::message).toList();
    }

    public void ack(String messageId) throws IOException, InterruptedException {
        call("/v1/queue/ack", Map.of(
                "queue", config.queue(),
                "message_id", messageId), "legal-ack-" + messageId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String path, Map<String, Object> body, String idempotencyKey)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json");
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            HttpResponse<String> response = http.send(
                    request.method("POST", HttpRequest.BodyPublishers.ofString(Json.write(body))).build(),
                    HttpResponse.BodyHandlers.ofString());

            Object decoded = Json.parse(response.body());
            if (!(decoded instanceof Map<?, ?> envelope)) {
                throw new IOException("Infrai returned an invalid envelope");
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> value
                        ? (Map<String, Object>) value : Map.of();
                String code = String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED"));
                String message = String.valueOf(error.getOrDefault("message", "Request rejected"));
                if (response.statusCode() == 429 && attempt < 4) {
                    Thread.sleep(retryDelay(response, attempt));
                    continue;
                }
                throw new InfraiException(code, message, response.statusCode());
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport status " + response.statusCode());
            return envelope.get("data") instanceof Map<?, ?> value
                    ? (Map<String, Object>) value : Map.of();
        }
        throw new IOException("Retry budget exhausted");
    }

    private long retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> Math.max(1L, Long.parseLong(value)) * 1000L)
                .orElse(250L * (1L << attempt));
    }

    @SuppressWarnings("unchecked")
    private QueueMessage message(Object raw) {
        if (!(raw instanceof Map<?, ?> value)) throw new IllegalArgumentException("Queue message must be an object");
        Object payload = value.get("payload");
        if (!(payload instanceof Map<?, ?> fields)) throw new IllegalArgumentException("Queue payload must be an object");
        return new QueueMessage(String.valueOf(value.get("message_id")), (Map<String, Object>) fields);
    }

    public record QueueMessage(String messageId, Map<String, Object> payload) {}
}
