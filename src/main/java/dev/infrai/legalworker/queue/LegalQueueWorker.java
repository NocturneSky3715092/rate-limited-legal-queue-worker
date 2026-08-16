package dev.infrai.legalworker.queue;

import dev.infrai.legalworker.config.WorkerConfig;
import dev.infrai.legalworker.domain.LegalJob;
import dev.infrai.legalworker.domain.LegalJobHandler;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class LegalQueueWorker {
    private final InfraiQueueClient queue;
    private final LegalJobHandler handler;
    private final WorkerConfig config;
    private final AtomicLong nextPermit = new AtomicLong();

    public LegalQueueWorker(InfraiQueueClient queue, LegalJobHandler handler, WorkerConfig config) {
        this.queue = queue;
        this.handler = handler;
        this.config = config;
    }

    public void runOnce() throws Exception {
        var messages = queue.consume().stream()
                .map(message -> new Work(message, LegalJob.from(message.payload())))
                .sorted(Comparator.comparing(Work::job))
                .toList();
        ExecutorService pool = Executors.newFixedThreadPool(config.concurrency());
        for (Work work : messages) pool.submit(() -> process(work));
        pool.shutdown();
        pool.awaitTermination(config.visibilityTimeout(), TimeUnit.SECONDS);
    }

    private void process(Work work) {
        try {
            awaitPermit();
            String outcome = handler.handle(work.job());
            queue.ack(work.message().messageId());
            System.out.println(outcome);
        } catch (Exception exception) {
            System.err.println("matter " + work.job().matterId() + " remains queued: " + exception.getMessage());
        }
    }

    private void awaitPermit() throws InterruptedException {
        long interval = config.permitInterval().toMillis();
        long now = System.currentTimeMillis();
        long slot = nextPermit.getAndUpdate(previous -> Math.max(previous, now) + interval);
        TimeUnit.MILLISECONDS.sleep(Math.max(0, slot - now));
    }

    private record Work(InfraiQueueClient.QueueMessage message, LegalJob job) {}
}
