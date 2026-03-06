package com.lightweightai.kernel.memory.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lightweightai.kernel.memory.model.TranscriptEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * JSONL-based session transcript for audit logging.
 *
 * Each session is stored as a .jsonl file where each line is a JSON object
 * representing a single event (user message, assistant response, tool call, etc.).
 *
 * File naming: {date}-{slug}.jsonl
 * Example: 2026-02-06-code-review.jsonl
 */
public class SessionTranscript {

    private static final Logger log = LoggerFactory.getLogger(SessionTranscript.class);
    private static final ObjectMapper mapper = createMapper();

    private final Path sessionFile;
    private final String sessionId;

    public SessionTranscript(Path sessionFile) {
        this.sessionFile = sessionFile;
        this.sessionId = extractSessionId(sessionFile);
    }

    public SessionTranscript(Path sessionsDir, String sessionId) {
        this.sessionId = sessionId;
        this.sessionFile = sessionsDir.resolve(sessionId + ".jsonl");
    }

    /**
     * Append an entry to the transcript.
     * Thread-safe through file locking.
     */
    public synchronized void append(TranscriptEntry entry) {
        try {
            ensureParentDirs();
            String json = mapper.writeValueAsString(entry);

            Files.writeString(
                sessionFile,
                json + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );

            log.debug("Appended entry to session {}: type={}", sessionId, entry.getType());
        } catch (IOException e) {
            log.error("Failed to append to session transcript: {}", sessionFile, e);
            throw new RuntimeException("Failed to write transcript", e);
        }
    }

    /**
     * Read all entries from the transcript.
     */
    public List<TranscriptEntry> readAll() {
        List<TranscriptEntry> entries = new ArrayList<>();

        if (!Files.exists(sessionFile)) {
            return entries;
        }

        try (BufferedReader reader = Files.newBufferedReader(sessionFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) continue;

                try {
                    TranscriptEntry entry = mapper.readValue(line, TranscriptEntry.class);
                    entries.add(entry);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse transcript line {} in {}: {}", lineNum, sessionFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to read session transcript: {}", sessionFile, e);
            throw new RuntimeException("Failed to read transcript", e);
        }

        return entries;
    }

    /**
     * Stream entries from the transcript (for large files).
     */
    public Stream<TranscriptEntry> stream() {
        if (!Files.exists(sessionFile)) {
            return Stream.empty();
        }

        try {
            return Files.lines(sessionFile, StandardCharsets.UTF_8)
                .filter(line -> !line.isBlank())
                .map(line -> {
                    try {
                        return mapper.readValue(line, TranscriptEntry.class);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse transcript line: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(entry -> entry != null);
        } catch (IOException e) {
            log.error("Failed to stream session transcript: {}", sessionFile, e);
            return Stream.empty();
        }
    }

    /**
     * Get the number of entries in the transcript.
     */
    public long getEntryCount() {
        if (!Files.exists(sessionFile)) {
            return 0;
        }

        try {
            return Files.lines(sessionFile, StandardCharsets.UTF_8)
                .filter(line -> !line.isBlank())
                .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Get file size in bytes.
     */
    public long getFileSize() {
        try {
            return Files.exists(sessionFile) ? Files.size(sessionFile) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Check if this session needs indexing based on delta thresholds.
     * Thresholds from OpenClaw: 100KB new data OR 50 new messages.
     */
    public boolean needsIndexing(long lastIndexedBytes, long lastIndexedCount) {
        long currentSize = getFileSize();
        long currentCount = getEntryCount();

        return (currentSize - lastIndexedBytes > 100 * 1024) ||
               (currentCount - lastIndexedCount > 50);
    }

    /**
     * Generate a descriptive slug from the session content.
     * In production, this would use an LLM. For now, uses simple heuristics.
     */
    public String generateSlug() {
        List<TranscriptEntry> entries = readAll();
        if (entries.isEmpty()) {
            return "empty-session";
        }

        // Find first user message
        String firstUserMsg = entries.stream()
            .filter(e -> "user".equals(e.getRole()) && e.getContent() != null)
            .map(TranscriptEntry::getContent)
            .findFirst()
            .orElse("conversation");

        // Simple slug generation: take first few words, lowercase, hyphenate
        String slug = firstUserMsg
            .toLowerCase()
            .replaceAll("[^a-z0-9\\s]", "")
            .trim()
            .replaceAll("\\s+", "-");

        if (slug.length() > 30) {
            slug = slug.substring(0, 30);
        }

        return slug.isEmpty() ? "conversation" : slug;
    }

    /**
     * Create a new session file with date prefix.
     */
    public static SessionTranscript create(Path sessionsDir, String slug) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String sessionId = date + "-" + slug;
        return new SessionTranscript(sessionsDir, sessionId);
    }

    /**
     * Create with auto-generated session ID.
     */
    public static SessionTranscript createWithTimestamp(Path sessionsDir) {
        String timestamp = Instant.now().toString()
            .replace(":", "-")
            .replace(".", "-")
            .substring(0, 19);
        return new SessionTranscript(sessionsDir, timestamp);
    }

    public Path getSessionFile() { return sessionFile; }
    public String getSessionId() { return sessionId; }

    public boolean exists() {
        return Files.exists(sessionFile);
    }

    private void ensureParentDirs() throws IOException {
        Path parent = sessionFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private static String extractSessionId(Path file) {
        String filename = file.getFileName().toString();
        if (filename.endsWith(".jsonl")) {
            return filename.substring(0, filename.length() - 6);
        }
        return filename;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }
}
