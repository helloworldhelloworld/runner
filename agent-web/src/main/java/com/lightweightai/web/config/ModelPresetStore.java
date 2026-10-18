package com.lightweightai.web.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ModelPresetStore {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Map<String, String>> presets = new LinkedHashMap<>();

    public ModelPresetStore(Path file) { this.file = Objects.requireNonNull(file); load(); }
    public synchronized Map<String, Map<String, String>> snapshot() { return Map.copyOf(presets); }
    public synchronized void put(String name, Map<String, String> config) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (String key : List.of("providerType", "model", "baseUrl")) {
            if (config.containsKey(key)) sanitized.put(key, Objects.toString(config.get(key), ""));
        }
        presets.put(name, Map.copyOf(sanitized));
        persist();
    }
    public synchronized boolean remove(String name) { boolean removed = presets.remove(name) != null; if (removed) persist(); return removed; }
    public synchronized Map<String, String> get(String name) { return presets.get(name); }

    private void load() {
        if (!Files.exists(file)) return;
        try { presets.putAll(mapper.readValue(file.toFile(), new TypeReference<Map<String, Map<String, String>>>() {})); }
        catch (IOException e) { throw new IllegalStateException("Cannot load model presets", e); }
    }
    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp"); mapper.writeValue(temp.toFile(), presets);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new IllegalStateException("Cannot persist model presets", e); }
    }
}
