package dev.infrai.legalworker.domain;

import java.time.Instant;
import java.util.List;

public final class LegalJobPriorityTest {
    public static void main(String[] args) {
        LegalJob intake = new LegalJob("MAT-17", LegalJob.Kind.MATTER_INTAKE, Instant.parse("2026-08-17T09:00:00Z"));
        LegalJob delivery = new LegalJob("MAT-22", LegalJob.Kind.SIGNED_DOCUMENT_DELIVERY, Instant.parse("2026-08-18T09:00:00Z"));
        LegalJob followUp = new LegalJob("MAT-19", LegalJob.Kind.DEADLINE_FOLLOW_UP, Instant.parse("2026-08-17T08:00:00Z"));
        List<LegalJob> ordered = List.of(intake, delivery, followUp).stream().sorted().toList();
        List<String> actual = ordered.stream().map(LegalJob::matterId).toList();
        List<String> expected = List.of("MAT-22", "MAT-19", "MAT-17");
        if (!actual.equals(expected)) throw new AssertionError("Expected " + expected + " but got " + actual);
        System.out.println("PASS signed delivery, deadline follow-up, matter intake");
    }
}
