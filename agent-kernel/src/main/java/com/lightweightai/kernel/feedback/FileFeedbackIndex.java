package com.lightweightai.kernel.feedback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FileFeedbackIndex implements FeedbackIndex {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, FeedbackRecord> records = new ConcurrentHashMap<>();

    public FileFeedbackIndex(Path file) { this.file = Objects.requireNonNull(file); load(); }

    @Override public synchronized FeedbackRecord append(FeedbackRecord feedback) {
        FeedbackRecord existing = records.get(feedback.feedbackId());
        if (existing != null) return existing;
        records.put(feedback.feedbackId(), feedback); persist(); return feedback;
    }

    @Override public List<FeedbackRecord> search(String candidateDigest, String kind) {
        return records.values().stream().filter(f -> (candidateDigest == null || candidateDigest.equals(f.candidateDigest()))
                && (kind == null || kind.equals(f.kind()))).sorted(Comparator.comparingLong(FeedbackRecord::createdAt)).toList();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try { records.putAll(mapper.readValue(file.toFile(), new TypeReference<Map<String, FeedbackRecord>>() {})); }
        catch (IOException e) { throw new IllegalStateException("Cannot load feedback index", e); }
    }
    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp"); mapper.writeValue(temp.toFile(), records);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new IllegalStateException("Cannot persist feedback index", e); }
    }
}
