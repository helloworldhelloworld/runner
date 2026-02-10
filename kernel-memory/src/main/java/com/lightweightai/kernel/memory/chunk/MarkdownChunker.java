package com.lightweightai.kernel.memory.chunk;

import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Splits Markdown content into overlapping chunks for indexing.
 * Uses a sliding window approach with configurable size and overlap.
 *
 * Inspired by OpenClaw's memory chunking system:
 * - Target chunk size: ~400 tokens (~1,600 characters)
 * - Overlap: ~80 tokens (~320 characters)
 * - Line-aware processing preserves document structure
 * - SHA-256 hash for deduplication
 */
public class MarkdownChunker {

    private static final int DEFAULT_CHUNK_SIZE = 1600;  // ~400 tokens
    private static final int DEFAULT_OVERLAP = 320;      // ~80 tokens
    private static final int MIN_CHUNK_SIZE = 100;

    private final int chunkSize;
    private final int overlap;

    public MarkdownChunker() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public MarkdownChunker(int chunkSize, int overlap) {
        this.chunkSize = Math.max(MIN_CHUNK_SIZE, chunkSize);
        this.overlap = Math.min(overlap, chunkSize / 2);
    }

    /**
     * Chunk markdown content into overlapping segments.
     */
    public List<MemoryChunk> chunk(String content, String sourceFile, MemoryType type) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<MemoryChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        int currentStart = 0;
        int currentLineStart = 0;
        StringBuilder currentChunk = new StringBuilder();

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            // Check if adding this line would exceed chunk size
            if (currentChunk.length() + line.length() + 1 > chunkSize && currentChunk.length() > MIN_CHUNK_SIZE) {
                // Create chunk from accumulated content
                chunks.add(createChunk(
                    currentChunk.toString(),
                    sourceFile,
                    currentLineStart + 1,  // 1-indexed
                    lineNum,               // exclusive end
                    type
                ));

                // Calculate overlap: go back to include overlap content
                currentChunk = new StringBuilder();
                currentLineStart = findOverlapStart(lines, lineNum, overlap);

                // Re-add overlap lines
                for (int i = currentLineStart; i < lineNum; i++) {
                    if (currentChunk.length() > 0) {
                        currentChunk.append("\n");
                    }
                    currentChunk.append(lines[i]);
                }
            }

            // Add current line
            if (currentChunk.length() > 0) {
                currentChunk.append("\n");
            }
            currentChunk.append(line);
        }

        // Don't forget the last chunk
        if (currentChunk.length() > 0) {
            chunks.add(createChunk(
                currentChunk.toString(),
                sourceFile,
                currentLineStart + 1,
                lines.length,
                type
            ));
        }

        return chunks;
    }

    /**
     * Chunk with default memory type.
     */
    public List<MemoryChunk> chunk(String content, String sourceFile) {
        return chunk(content, sourceFile, MemoryType.DURABLE);
    }

    private int findOverlapStart(String[] lines, int currentLine, int overlapChars) {
        int chars = 0;
        int startLine = currentLine;

        while (startLine > 0 && chars < overlapChars) {
            startLine--;
            chars += lines[startLine].length() + 1;
        }

        return startLine;
    }

    private MemoryChunk createChunk(String content, String sourceFile, int startLine, int endLine, MemoryType type) {
        String hash = computeHash(content);
        String id = UUID.randomUUID().toString().substring(0, 8) + "-" + hash.substring(0, 8);

        return MemoryChunk.builder()
            .id(id)
            .content(content)
            .sourceFile(sourceFile)
            .startLine(startLine)
            .endLine(endLine)
            .hash(hash)
            .createdAt(Instant.now())
            .type(type)
            .build();
    }

    /**
     * Compute SHA-256 hash of content for deduplication.
     */
    public static String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Estimate token count (rough approximation: 1 token ≈ 4 chars).
     */
    public static int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 4;
    }
}
