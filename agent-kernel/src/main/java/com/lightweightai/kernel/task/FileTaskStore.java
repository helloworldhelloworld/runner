package com.lightweightai.kernel.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Small durable store for task metadata; atomic replacement prevents partial snapshots. */
public final class FileTaskStore implements TaskStore {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Map<String, DurableTask> tasks = new ConcurrentHashMap<>();

    public FileTaskStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        load();
    }

    @Override public synchronized DurableTask create(String principalId, String idempotencyKey, String goal) {
        if (idempotencyKey != null) {
            Optional<DurableTask> existing = findByIdempotency(principalId, idempotencyKey);
            if (existing.isPresent()) return existing.get();
        }
        DurableTask task = new DurableTask(UUID.randomUUID().toString(), principalId, idempotencyKey,
                Objects.requireNonNull(goal, "goal"), TaskStatus.QUEUED, 0, null, null, Map.of(), null, 0,
                System.currentTimeMillis());
        tasks.put(task.taskId(), task);
        persist();
        return task;
    }

    @Override public Optional<DurableTask> get(String taskId) { return Optional.ofNullable(tasks.get(taskId)); }

    @Override public List<DurableTask> list() { return List.copyOf(tasks.values()); }

    @Override public Optional<DurableTask> findByIdempotency(String principalId, String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        return tasks.values().stream().filter(t -> Objects.equals(principalId, t.principalId())
                && Objects.equals(idempotencyKey, t.idempotencyKey())).findFirst();
    }

    @Override public synchronized DurableTask compareAndSet(DurableTask expected, DurableTask replacement) {
        DurableTask current = tasks.get(expected.taskId());
        if (current == null || current.version() != expected.version())
            throw new IllegalStateException("stale task version: " + expected.taskId());
        tasks.put(expected.taskId(), replacement);
        persist();
        return replacement;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            Map<String, DurableTask> loaded = mapper.readValue(file.toFile(), new TypeReference<>() {});
            tasks.putAll(loaded);
        } catch (IOException e) { throw new IllegalStateException("Cannot load task store " + file, e); }
    }

    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writeValue(temp.toFile(), tasks);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new IllegalStateException("Cannot persist task store " + file, e); }
    }
}
