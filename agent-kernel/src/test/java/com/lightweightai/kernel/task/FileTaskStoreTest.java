package com.lightweightai.kernel.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileTaskStoreTest {
    @Test void idempotencyAndVersionedCheckpointSurviveRestart(@TempDir Path dir) {
        Path file = dir.resolve("tasks.json");
        FileTaskStore first = new FileTaskStore(file);
        DurableTask created = first.create("alice", "import-42", "整理资料");
        assertSame(created, first.create("alice", "import-42", "重复触发"));
        DurableTask running = created.startAttempt("run-1");
        first.compareAndSet(created, running);
        DurableTask checkpointed = running.checkpoint(new TaskCheckpoint("fetch", "parse", Map.of("page", 3), 1));
        first.compareAndSet(running, checkpointed);

        FileTaskStore restarted = new FileTaskStore(file);
        DurableTask restored = restarted.get(created.taskId()).orElseThrow();
        assertEquals(TaskStatus.RUNNING, restored.status());
        assertEquals("fetch", restored.checkpoint().stepId());
        assertEquals(3, restored.checkpoint().data().get("page"));
        assertThrows(IllegalStateException.class, () -> restarted.compareAndSet(running,
                running.transition(TaskStatus.SUCCEEDED, "run-1", null)));
    }

    @Test void dispatcherAcknowledgesOnlySuccessfulConsumer() throws Exception {
        Path dir = Files.createTempDirectory("task-delivery-dispatch");
        FileTaskDeliveryStore store = new FileTaskDeliveryStore(dir.resolve("deliveries.json"));
        TaskDelivery delivery = store.enqueue("task-2", "alice", "web", "payload");
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        TaskDeliveryDispatcher dispatcher = new TaskDeliveryDispatcher(store, item -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("peer down");
        });
        assertEquals(0, dispatcher.dispatchPending());
        assertEquals(1, store.pending().size());
        assertEquals(1, dispatcher.dispatchPending());
        assertTrue(store.pending().isEmpty());
    }

    @Test void restartMovesUnknownInFlightAttemptToRecoveryRequired(@TempDir Path dir) {
        FileTaskStore store = new FileTaskStore(dir.resolve("tasks.json"));
        DurableTaskService service = new DurableTaskService(store);
        DurableTask task = service.create("alice", "job-7", "同步资料");
        service.start(task.taskId());
        assertEquals(TaskStatus.RECOVERY_REQUIRED, service.recoverAfterRestart().getFirst().status());
        assertThrows(IllegalStateException.class, () -> service.start(task.taskId()));
    }

    @Test void runnerPersistsSuccessAndRejectsRecoveredReplay(@TempDir Path dir) {
        DurableTaskService service = new DurableTaskService(new FileTaskStore(dir.resolve("tasks.json")));
        DurableTask task = service.create("alice", "job-8", "生成报告");
        try (DurableTaskRunner runner = new DurableTaskRunner(service, 1)) {
            assertTrue(runner.submit(task.taskId(), (current, checkpoint) -> Map.of("artifact", "report.md")));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (service.get(task.taskId()).orElseThrow().status() == TaskStatus.RUNNING
                    && System.nanoTime() < deadline) Thread.yield();
        }
        assertEquals(TaskStatus.SUCCEEDED, service.get(task.taskId()).orElseThrow().status());
        DurableTask recovered = service.create("alice", "job-9", "需要核对副作用");
        service.start(recovered.taskId());
        service.recoverAfterRestart();
        try (DurableTaskRunner runner = new DurableTaskRunner(service, 1)) {
            assertThrows(IllegalStateException.class, () -> runner.submit(recovered.taskId(), (t, c) -> Map.of()));
        }
    }

    @Test void deliveryIsEnqueuedOnceAndRequiresVersionedAcknowledgement(@TempDir Path dir) {
        FileTaskDeliveryStore store = new FileTaskDeliveryStore(dir.resolve("deliveries.json"));
        TaskDelivery first = store.enqueue("task-1", "alice", "web", "done");
        assertEquals(first.deliveryId(), store.enqueue("task-1", "alice", "web", "duplicate").deliveryId());
        assertEquals(1, store.pending().size());
        TaskDelivery delivered = store.markDelivered(first);
        assertTrue(delivered.delivered());
        assertTrue(store.pending().isEmpty());
        assertThrows(IllegalStateException.class, () -> store.markDelivered(first));
    }
}
