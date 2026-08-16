package dev.infrai.legalworker.domain;

import java.time.Instant;
import java.util.Map;

public record LegalJob(String matterId, Kind kind, Instant dueAt) implements Comparable<LegalJob> {
    public enum Kind { MATTER_INTAKE, SIGNED_DOCUMENT_DELIVERY, DEADLINE_FOLLOW_UP }

    public static LegalJob from(Map<String, Object> payload) {
        return new LegalJob(
                required(payload, "matter_id"),
                Kind.valueOf(required(payload, "kind")),
                Instant.parse(required(payload, "due_at")));
    }

    @Override
    public int compareTo(LegalJob other) {
        int byKind = Integer.compare(priority(kind), priority(other.kind));
        return byKind != 0 ? byKind : dueAt.compareTo(other.dueAt);
    }

    private static int priority(Kind kind) {
        return switch (kind) {
            case SIGNED_DOCUMENT_DELIVERY -> 0;
            case DEADLINE_FOLLOW_UP -> 1;
            case MATTER_INTAKE -> 2;
        };
    }

    private static String required(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " is required");
        return text;
    }
}
