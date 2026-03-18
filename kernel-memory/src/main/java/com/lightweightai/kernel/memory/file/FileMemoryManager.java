package com.lightweightai.kernel.memory.file;

import com.lightweightai.kernel.memory.chunk.MarkdownChunker;
import com.lightweightai.kernel.memory.embedding.EmbeddingProvider;
import com.lightweightai.kernel.memory.index.HybridSearch;
import com.lightweightai.kernel.memory.index.MemoryIndex;
import com.lightweightai.kernel.memory.index.VectorIndex;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchOptions;
import com.lightweightai.kernel.memory.model.SearchResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-based memory manager implementing OpenClaw's three-tier memory architecture:
 *
 * 1. Ephemeral Memory - Daily logs (memory/YYYY-MM-DD.md)
 * 2. Durable Memory - Long-term knowledge (MEMORY.md)
 * 3. Session Memory - Conversation transcripts (sessions/*.jsonl)
 *
 * Memory is indexed using both BM25 (keyword) and vector (semantic) search.
 */
public class FileMemoryManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FileMemoryManager.class);

    private final Path agentRoot;
    private final String agentId;
    private final MarkdownChunker chunker;
    private final MemoryIndex memoryIndex;
    private final VectorIndex vectorIndex;
    private final HybridSearch hybridSearch;

    // Directory structure
    private final Path memoryDir;
    private final Path sessionsDir;
    private final Path indexDir;
    private final Path durableMemoryFile;

    public FileMemoryManager(Path agentRoot, String agentId, EmbeddingProvider embeddingProvider) {
        this.agentRoot = agentRoot;
        this.agentId = agentId;
        this.chunker = new MarkdownChunker();

        // Initialize directory structure
        this.memoryDir = agentRoot.resolve("memory");
        this.sessionsDir = agentRoot.resolve("sessions");
        this.indexDir = agentRoot.resolve("index");
        this.durableMemoryFile = agentRoot.resolve("MEMORY.md");

        ensureDirectories();

        // Initialize indexes
        Path sqliteFile = indexDir.resolve("memory.sqlite");
        this.memoryIndex = new MemoryIndex(sqliteFile, agentId);
        this.vectorIndex = new VectorIndex(embeddingProvider);
        this.hybridSearch = new HybridSearch(memoryIndex, vectorIndex);

        // Load existing memory into vector index
        loadExistingMemory();

        log.info("FileMemoryManager initialized for agent: {}", agentId);
    }

    // ==================== Ephemeral Memory (Daily Logs) ====================

    /**
     * Append content to today's ephemeral memory log.
     * Format: memory/YYYY-MM-DD.md
     */
    public void appendEphemeral(String content) {
        Path dailyFile = getEphemeralFile(LocalDate.now());

        try {
            String timestamp = Instant.now().toString();
            String entry = String.format("\n## %s\n\n%s\n", timestamp, content);

            Files.writeString(
                dailyFile,
                entry,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );

            // Index the new content
            indexFile(dailyFile, MemoryType.EPHEMERAL);

            log.debug("Appended ephemeral memory: {} chars", content.length());
        } catch (IOException e) {
            log.error("Failed to append ephemeral memory", e);
            throw new RuntimeException("Failed to write ephemeral memory", e);
        }
    }

    /**
     * Read today's ephemeral memory.
     */
    public String readTodayEphemeral() {
        return readEphemeral(LocalDate.now());
    }

    /**
     * Read ephemeral memory for a specific date.
     */
    public String readEphemeral(LocalDate date) {
        Path file = getEphemeralFile(date);
        if (!Files.exists(file)) {
            return "";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read ephemeral memory for {}", date, e);
            return "";
        }
    }

    private Path getEphemeralFile(LocalDate date) {
        String filename = date.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
        return memoryDir.resolve(filename);
    }

    // ==================== Durable Memory (Long-term) ====================

    /**
     * Write content to durable memory (MEMORY.md).
     * Replaces the entire file.
     */
    public void writeDurable(String content) {
        try {
            Files.writeString(durableMemoryFile, content, StandardCharsets.UTF_8);
            indexFile(durableMemoryFile, MemoryType.DURABLE);
            log.debug("Updated durable memory: {} chars", content.length());
        } catch (IOException e) {
            log.error("Failed to write durable memory", e);
            throw new RuntimeException("Failed to write durable memory", e);
        }
    }

    /**
     * Append content to a specific section in durable memory.
     */
    public void appendDurable(String section, String content) {
        String existing = readDurable();
        String sectionHeader = "## " + section;

        String newContent;
        if (existing.contains(sectionHeader)) {
            // Append to existing section
            int sectionStart = existing.indexOf(sectionHeader);
            int nextSection = existing.indexOf("\n## ", sectionStart + 1);
            if (nextSection == -1) {
                // Last section, append at end
                newContent = existing + "\n" + content;
            } else {
                // Insert before next section
                newContent = existing.substring(0, nextSection) +
                            content + "\n" +
                            existing.substring(nextSection);
            }
        } else {
            // Create new section
            newContent = existing + "\n\n" + sectionHeader + "\n\n" + content;
        }

        writeDurable(newContent);
    }

    /**
     * Read durable memory.
     */
    public String readDurable() {
        if (!Files.exists(durableMemoryFile)) {
            return "";
        }
        try {
            return Files.readString(durableMemoryFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read durable memory", e);
            return "";
        }
    }

    // ==================== Session Memory (Transcripts) ====================

    /**
     * Get or create a session transcript.
     */
    public SessionTranscript getSession(String sessionId) {
        return new SessionTranscript(sessionsDir, sessionId);
    }

    /**
     * Create a new session with auto-generated ID.
     */
    public SessionTranscript createSession() {
        return SessionTranscript.createWithTimestamp(sessionsDir);
    }

    /**
     * Create a new session with a descriptive slug.
     */
    public SessionTranscript createSession(String slug) {
        return SessionTranscript.create(sessionsDir, slug);
    }

    /**
     * List all session IDs.
     */
    public List<String> listSessions() {
        try {
            if (!Files.exists(sessionsDir)) {
                return List.of();
            }
            return Files.list(sessionsDir)
                .filter(p -> p.toString().endsWith(".jsonl"))
                .map(p -> {
                    String filename = p.getFileName().toString();
                    return filename.substring(0, filename.length() - 6);
                })
                .sorted()
                .toList();
        } catch (IOException e) {
            log.error("Failed to list sessions", e);
            return List.of();
        }
    }

    // ==================== Search ====================

    /**
     * Search memory using hybrid (BM25 + vector) search.
     */
    public List<SearchResult> search(String query, SearchOptions options) {
        return hybridSearch.search(query, options.getTopK(), options.getVectorWeight());
    }

    /**
     * Search with default options.
     */
    public List<SearchResult> search(String query) {
        return search(query, SearchOptions.defaults());
    }

    /**
     * Search using only keyword matching.
     */
    public List<SearchResult> searchKeywords(String query, int topK) {
        return hybridSearch.searchBM25Only(query, topK);
    }

    /**
     * Search using only semantic similarity.
     */
    public List<SearchResult> searchSemantic(String query, int topK) {
        return hybridSearch.searchVectorOnly(query, topK);
    }

    // ==================== Indexing ====================

    /**
     * Index a file into the memory system.
     */
    public void indexFile(Path file, MemoryType type) {
        if (!Files.exists(file)) {
            log.warn("Cannot index non-existent file: {}", file);
            return;
        }

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String hash = MarkdownChunker.computeHash(content);
            String relativePath = agentRoot.relativize(file).toString();

            // Check if file needs re-indexing
            if (!memoryIndex.fileNeedsReindex(relativePath, hash)) {
                log.debug("File unchanged, skipping: {}", relativePath);
                return;
            }

            // Delete old chunks for this file
            memoryIndex.deleteChunksBySourceFile(relativePath);

            // Chunk and index
            List<MemoryChunk> chunks = chunker.chunk(content, relativePath, type);

            if (!chunks.isEmpty()) {
                hybridSearch.indexChunks(chunks);
                memoryIndex.updateFileMetadata(relativePath, hash, chunks.size());
                log.info("Indexed {} chunks from: {}", chunks.size(), relativePath);
            }
        } catch (IOException e) {
            log.error("Failed to index file: {}", file, e);
        }
    }

    /**
     * Re-index all memory files.
     */
    public void reindexAll() {
        log.info("Re-indexing all memory files...");

        // Index MEMORY.md
        if (Files.exists(durableMemoryFile)) {
            indexFile(durableMemoryFile, MemoryType.DURABLE);
        }

        // Index daily memory files
        try {
            if (Files.exists(memoryDir)) {
                Files.list(memoryDir)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> indexFile(p, MemoryType.EPHEMERAL));
            }
        } catch (IOException e) {
            log.error("Failed to list memory directory", e);
        }

        log.info("Re-indexing complete. Total chunks: {}", memoryIndex.getChunkCount());
    }

    // ==================== Lifecycle ====================

    private void ensureDirectories() {
        try {
            Files.createDirectories(agentRoot);
            Files.createDirectories(memoryDir);
            Files.createDirectories(sessionsDir);
            Files.createDirectories(indexDir);
        } catch (IOException e) {
            log.error("Failed to create memory directories", e);
            throw new RuntimeException("Failed to create memory directories", e);
        }
    }

    private void loadExistingMemory() {
        // Load indexed chunks into vector index
        // This is done lazily through the MemoryIndex
        log.debug("Memory manager ready. SQLite index has {} chunks.", memoryIndex.getChunkCount());
    }

    @Override
    public void close() {
        if (memoryIndex != null) {
            memoryIndex.close();
        }
        log.info("FileMemoryManager closed for agent: {}", agentId);
    }

    // ==================== Getters ====================

    public Path getAgentRoot() {
        return agentRoot;
    }

    public String getAgentId() {
        return agentId;
    }

    public Path getMemoryDir() {
        return memoryDir;
    }

    public Path getSessionsDir() {
        return sessionsDir;
    }

    public HybridSearch.IndexStats getIndexStats() {
        return hybridSearch.getStats();
    }
}
