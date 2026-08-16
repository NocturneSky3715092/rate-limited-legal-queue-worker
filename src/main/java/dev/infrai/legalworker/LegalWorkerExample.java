package dev.infrai.legalworker;

import dev.infrai.legalworker.config.WorkerConfig;
import dev.infrai.legalworker.domain.LegalJobHandler;
import dev.infrai.legalworker.queue.InfraiQueueClient;
import dev.infrai.legalworker.queue.LegalQueueWorker;

public final class LegalWorkerExample {
    private LegalWorkerExample() {}

    public static void main(String[] args) throws Exception {
        WorkerConfig config = WorkerConfig.fromEnvironment();
        InfraiQueueClient queue = new InfraiQueueClient(config);
        new LegalQueueWorker(queue, new LegalJobHandler(), config).runOnce();
    }
}
