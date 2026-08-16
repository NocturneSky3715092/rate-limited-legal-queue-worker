package dev.infrai.legalworker.domain;

public final class LegalJobHandler {
    public String handle(LegalJob job) {
        return switch (job.kind()) {
            case MATTER_INTAKE -> "matter " + job.matterId() + " intake recorded";
            case SIGNED_DOCUMENT_DELIVERY -> "matter " + job.matterId() + " signed document delivered";
            case DEADLINE_FOLLOW_UP -> "matter " + job.matterId() + " deadline follow-up recorded";
        };
    }
}
