package com.lightweightai.kernel.memory.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A chunk of memory content with metadata for indexing and retrieval.
 * Inspired by OpenClaw's memory chunking system.
 */
public class MemoryChunk {
    private final String id;
    private final String content;
    private final String sourceFile;
    private final int startLine;
    private final int endLine;
    private final String hash;
    private final Instant createdAt;
    private final MemoryType type;

    private float[] embedding;

    public MemoryChunk(String id, String content, String sourceFile,
                       int startLine, int endLine, String hash,
                       Instant createdAt, MemoryType type) {
        this.id = Objects.requireNonNull(id);
        this.content = Objects.requireNonNull(content);
        this.sourceFile = sourceFile;
        this.startLine = startLine;
        this.endLine = endLine;
        this.hash = Objects.requireNonNull(hash);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.type = type != null ? type : MemoryType.DURABLE;
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public String getSourceFile() { return sourceFile; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }
    public String getHash() { return hash; }
    public Instant getCreatedAt() { return createdAt; }
    public MemoryType getType() { return type; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public boolean hasEmbedding() { return embedding != null && embedding.length > 0; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String content;
        private String sourceFile;
        private int startLine;
        private int endLine;
        private String hash;
        private Instant createdAt;
        private MemoryType type;

        public Builder id(String id) { this.id = id; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder sourceFile(String sourceFile) { this.sourceFile = sourceFile; return this; }
        public Builder startLine(int startLine) { this.startLine = startLine; return this; }
        public Builder endLine(int endLine) { this.endLine = endLine; return this; }
        public Builder hash(String hash) { this.hash = hash; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder type(MemoryType type) { this.type = type; return this; }

        public MemoryChunk build() {
            return new MemoryChunk(id, content, sourceFile, startLine, endLine, hash, createdAt, type);
        }
    }

    @Override
    public String toString() {
        return "MemoryChunk{" +
                "id='" + id + '\'' +
                ", sourceFile='" + sourceFile + '\'' +
                ", lines=" + startLine + "-" + endLine +
                ", type=" + type +
                '}';
    }
}
