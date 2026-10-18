package com.lightweightai.kernel.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FileTaskDeliveryStore implements TaskDeliveryStore {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, TaskDelivery> deliveries = new ConcurrentHashMap<>();

    public FileTaskDeliveryStore(Path file) { this.file = Objects.requireNonNull(file); load(); }

    @Override public synchronized TaskDelivery enqueue(String taskId, String principalId, String channel, String payload) {
        String key = taskId + "\u0000" + channel;
        TaskDelivery existing = deliveries.get(key);
        if (existing != null) return existing;
        TaskDelivery created = new TaskDelivery(UUID.randomUUID().toString(), taskId, principalId, channel,
                payload, false, 0, System.currentTimeMillis());
        deliveries.put(key, created); persist(); return created;
    }

    @Override public List<TaskDelivery> pending() { return deliveries.values().stream().filter(d -> !d.delivered()).toList(); }

    @Override public synchronized TaskDelivery markDelivered(TaskDelivery expected) {
        String key = expected.taskId() + "\u0000" + expected.channel();
        TaskDelivery current = deliveries.get(key);
        if (current == null || current.version() != expected.version()) throw new IllegalStateException("stale delivery");
        TaskDelivery done = new TaskDelivery(current.deliveryId(), current.taskId(), current.principalId(), current.channel(),
                current.payload(), true, current.version() + 1, System.currentTimeMillis());
        deliveries.put(key, done); persist(); return done;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try { deliveries.putAll(mapper.readValue(file.toFile(), new TypeReference<Map<String, TaskDelivery>>() {})); }
        catch (IOException e) { throw new IllegalStateException("Cannot load delivery store", e); }
    }
    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp"); mapper.writeValue(temp.toFile(), deliveries);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new IllegalStateException("Cannot persist delivery store", e); }
    }
}
