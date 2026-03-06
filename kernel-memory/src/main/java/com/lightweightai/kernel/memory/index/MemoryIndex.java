package com.lightweightai.kernel.memory.index;

import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-based memory index with FTS5 full-text search.
 *
 * Provides BM25 keyword search for exact token matching.
 * Vector search is handled by VectorIndex separately.
 */
public class MemoryIndex implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MemoryIndex.class);

    private final Connection connection;
    private final String agentId;

    public MemoryIndex(Path indexPath, String agentId) {
        this.agentId = agentId;
        try {
            // Ensure parent directory exists
            Path parent = indexPath.getParent();
            if (parent != null) {
                parent.toFile().mkdirs();
            }

            String url = "jdbc:sqlite:" + indexPath.toAbsolutePath();
            this.connection = DriverManager.getConnection(url);

            // Enable WAL mode for better concurrent access
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }

            initSchema();
            log.info("MemoryIndex initialized for agent: {}", agentId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize MemoryIndex", e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Main chunks table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chunks (
                    id TEXT PRIMARY KEY,
                    content TEXT NOT NULL,
                    source_file TEXT,
                    start_line INTEGER,
                    end_line INTEGER,
                    hash TEXT NOT NULL,
                    memory_type TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    embedding BLOB
                )
            """);

            // FTS5 virtual table for full-text search
            stmt.execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
                    id,
                    content,
                    source_file,
                    content='chunks',
                    content_rowid='rowid'
                )
            """);

            // Triggers to keep FTS in sync
            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS chunks_ai AFTER INSERT ON chunks BEGIN
                    INSERT INTO chunks_fts(rowid, id, content, source_file)
                    VALUES (NEW.rowid, NEW.id, NEW.content, NEW.source_file);
                END
            """);

            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS chunks_ad AFTER DELETE ON chunks BEGIN
                    INSERT INTO chunks_fts(chunks_fts, rowid, id, content, source_file)
                    VALUES ('delete', OLD.rowid, OLD.id, OLD.content, OLD.source_file);
                END
            """);

            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS chunks_au AFTER UPDATE ON chunks BEGIN
                    INSERT INTO chunks_fts(chunks_fts, rowid, id, content, source_file)
                    VALUES ('delete', OLD.rowid, OLD.id, OLD.content, OLD.source_file);
                    INSERT INTO chunks_fts(rowid, id, content, source_file)
                    VALUES (NEW.rowid, NEW.id, NEW.content, NEW.source_file);
                END
            """);

            // Index for hash-based deduplication
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunks_hash ON chunks(hash)");

            // Index for memory type filtering
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunks_type ON chunks(memory_type)");

            // Files metadata table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS files (
                    path TEXT PRIMARY KEY,
                    hash TEXT NOT NULL,
                    last_indexed TEXT NOT NULL,
                    chunk_count INTEGER DEFAULT 0
                )
            """);
        }
    }

    /**
     * Insert or update a chunk in the index.
     */
    public void upsertChunk(MemoryChunk chunk) {
        String sql = """
            INSERT OR REPLACE INTO chunks
            (id, content, source_file, start_line, end_line, hash, memory_type, created_at, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, chunk.getId());
            pstmt.setString(2, chunk.getContent());
            pstmt.setString(3, chunk.getSourceFile());
            pstmt.setInt(4, chunk.getStartLine());
            pstmt.setInt(5, chunk.getEndLine());
            pstmt.setString(6, chunk.getHash());
            pstmt.setString(7, chunk.getType().name());
            pstmt.setString(8, chunk.getCreatedAt().toString());

            if (chunk.hasEmbedding()) {
                pstmt.setBytes(9, floatArrayToBytes(chunk.getEmbedding()));
            } else {
                pstmt.setNull(9, Types.BLOB);
            }

            pstmt.executeUpdate();
            log.debug("Upserted chunk: {}", chunk.getId());
        } catch (SQLException e) {
            log.error("Failed to upsert chunk: {}", chunk.getId(), e);
            throw new RuntimeException("Failed to upsert chunk", e);
        }
    }

    /**
     * Batch insert chunks for better performance.
     */
    public void insertChunks(List<MemoryChunk> chunks) {
        if (chunks.isEmpty()) return;

        String sql = """
            INSERT OR REPLACE INTO chunks
            (id, content, source_file, start_line, end_line, hash, memory_type, created_at, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                for (MemoryChunk chunk : chunks) {
                    pstmt.setString(1, chunk.getId());
                    pstmt.setString(2, chunk.getContent());
                    pstmt.setString(3, chunk.getSourceFile());
                    pstmt.setInt(4, chunk.getStartLine());
                    pstmt.setInt(5, chunk.getEndLine());
                    pstmt.setString(6, chunk.getHash());
                    pstmt.setString(7, chunk.getType().name());
                    pstmt.setString(8, chunk.getCreatedAt().toString());

                    if (chunk.hasEmbedding()) {
                        pstmt.setBytes(9, floatArrayToBytes(chunk.getEmbedding()));
                    } else {
                        pstmt.setNull(9, Types.BLOB);
                    }

                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            connection.commit();
            log.info("Inserted {} chunks", chunks.size());
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                log.error("Rollback failed", ex);
            }
            throw new RuntimeException("Failed to insert chunks", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                log.error("Failed to reset autocommit", e);
            }
        }
    }

    /**
     * BM25 full-text search using FTS5.
     */
    public List<SearchResult> searchBM25(String query, int topK) {
        List<SearchResult> results = new ArrayList<>();

        // FTS5 query with BM25 ranking
        String sql = """
            SELECT c.*, bm25(chunks_fts) as score
            FROM chunks_fts fts
            JOIN chunks c ON fts.id = c.id
            WHERE chunks_fts MATCH ?
            ORDER BY score
            LIMIT ?
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Escape special FTS5 characters and create query
            String ftsQuery = escapeFtsQuery(query);
            pstmt.setString(1, ftsQuery);
            pstmt.setInt(2, topK);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    MemoryChunk chunk = chunkFromResultSet(rs);
                    float score = -rs.getFloat("score"); // BM25 returns negative scores

                    String snippet = extractSnippet(chunk.getContent(), query, 700);

                    results.add(SearchResult.builder()
                        .chunk(chunk)
                        .score(score)
                        .bm25Score(score)
                        .vectorScore(0f)
                        .snippet(snippet)
                        .build());
                }
            }
        } catch (SQLException e) {
            log.error("BM25 search failed for query: {}", query, e);
        }

        return results;
    }

    /**
     * Get chunk by ID.
     */
    public MemoryChunk getChunk(String id) {
        String sql = "SELECT * FROM chunks WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return chunkFromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get chunk: {}", id, e);
        }

        return null;
    }

    /**
     * Get all chunks for a source file.
     */
    public List<MemoryChunk> getChunksBySourceFile(String sourceFile) {
        List<MemoryChunk> chunks = new ArrayList<>();
        String sql = "SELECT * FROM chunks WHERE source_file = ? ORDER BY start_line";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sourceFile);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    chunks.add(chunkFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get chunks for file: {}", sourceFile, e);
        }

        return chunks;
    }

    /**
     * Delete chunks for a source file (before re-indexing).
     */
    public void deleteChunksBySourceFile(String sourceFile) {
        String sql = "DELETE FROM chunks WHERE source_file = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sourceFile);
            int deleted = pstmt.executeUpdate();
            log.debug("Deleted {} chunks for file: {}", deleted, sourceFile);
        } catch (SQLException e) {
            log.error("Failed to delete chunks for file: {}", sourceFile, e);
        }
    }

    /**
     * Check if a chunk with the given hash already exists.
     */
    public boolean existsByHash(String hash) {
        String sql = "SELECT 1 FROM chunks WHERE hash = ? LIMIT 1";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, hash);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check hash existence: {}", hash, e);
            return false;
        }
    }

    /**
     * Get total chunk count.
     */
    public int getChunkCount() {
        String sql = "SELECT COUNT(*) FROM chunks";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Failed to get chunk count", e);
        }

        return 0;
    }

    /**
     * Update file metadata after indexing.
     */
    public void updateFileMetadata(String path, String hash, int chunkCount) {
        String sql = """
            INSERT OR REPLACE INTO files (path, hash, last_indexed, chunk_count)
            VALUES (?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.setString(2, hash);
            pstmt.setString(3, Instant.now().toString());
            pstmt.setInt(4, chunkCount);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update file metadata: {}", path, e);
        }
    }

    /**
     * Check if file needs re-indexing based on hash.
     */
    public boolean fileNeedsReindex(String path, String currentHash) {
        String sql = "SELECT hash FROM files WHERE path = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, path);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("hash");
                    return !currentHash.equals(storedHash);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to check file reindex: {}", path, e);
        }

        return true; // File not indexed yet
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("MemoryIndex closed for agent: {}", agentId);
            }
        } catch (SQLException e) {
            log.error("Failed to close MemoryIndex", e);
        }
    }

    // Helper methods

    private MemoryChunk chunkFromResultSet(ResultSet rs) throws SQLException {
        MemoryChunk chunk = MemoryChunk.builder()
            .id(rs.getString("id"))
            .content(rs.getString("content"))
            .sourceFile(rs.getString("source_file"))
            .startLine(rs.getInt("start_line"))
            .endLine(rs.getInt("end_line"))
            .hash(rs.getString("hash"))
            .type(MemoryType.valueOf(rs.getString("memory_type")))
            .createdAt(Instant.parse(rs.getString("created_at")))
            .build();

        byte[] embeddingBytes = rs.getBytes("embedding");
        if (embeddingBytes != null) {
            chunk.setEmbedding(bytesToFloatArray(embeddingBytes));
        }

        return chunk;
    }

    private String escapeFtsQuery(String query) {
        // Simple tokenization for FTS5
        // Remove special characters and split into tokens
        return query.replaceAll("[\"'(){}\\[\\]*:]", " ")
                   .trim()
                   .replaceAll("\\s+", " ");
    }

    private String extractSnippet(String content, String query, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // Find the first occurrence of any query term
        String[] terms = query.toLowerCase().split("\\s+");
        String lowerContent = content.toLowerCase();

        int bestPos = 0;
        for (String term : terms) {
            int pos = lowerContent.indexOf(term);
            if (pos >= 0) {
                bestPos = pos;
                break;
            }
        }

        // Extract snippet around the match
        int start = Math.max(0, bestPos - maxLength / 4);
        int end = Math.min(content.length(), start + maxLength);

        String snippet = content.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < content.length()) snippet = snippet + "...";

        return snippet;
    }

    private byte[] floatArrayToBytes(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int intBits = Float.floatToIntBits(floats[i]);
            bytes[i * 4] = (byte) (intBits >> 24);
            bytes[i * 4 + 1] = (byte) (intBits >> 16);
            bytes[i * 4 + 2] = (byte) (intBits >> 8);
            bytes[i * 4 + 3] = (byte) intBits;
        }
        return bytes;
    }

    private float[] bytesToFloatArray(byte[] bytes) {
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            int intBits = ((bytes[i * 4] & 0xFF) << 24)
                        | ((bytes[i * 4 + 1] & 0xFF) << 16)
                        | ((bytes[i * 4 + 2] & 0xFF) << 8)
                        | (bytes[i * 4 + 3] & 0xFF);
            floats[i] = Float.intBitsToFloat(intBits);
        }
        return floats;
    }
}
