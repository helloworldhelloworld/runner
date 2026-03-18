package com.lightweightai.kernel.memory.model;

import java.util.Set;

/**
 * Options for memory search operations.
 */
public class SearchOptions {
    private final int topK;
    private final float vectorWeight;
    private final Set<MemoryType> memoryTypes;
    private final String sessionId;
    private final int snippetLength;

    private SearchOptions(Builder builder) {
        this.topK = builder.topK;
        this.vectorWeight = builder.vectorWeight;
        this.memoryTypes = builder.memoryTypes;
        this.sessionId = builder.sessionId;
        this.snippetLength = builder.snippetLength;
    }

    public int getTopK() {
        return topK;
    }

    public float getVectorWeight() {
        return vectorWeight;
    }

    public Set<MemoryType> getMemoryTypes() {
        return memoryTypes;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getSnippetLength() {
        return snippetLength;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SearchOptions defaults() {
        return new Builder().build();
    }

    public static class Builder {
        private int topK = 10;
        private float vectorWeight = 0.7f;
        private Set<MemoryType> memoryTypes = Set.of(MemoryType.values());
        private String sessionId = null;
        private int snippetLength = 700;

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder vectorWeight(float vectorWeight) {
            this.vectorWeight = Math.max(0f, Math.min(1f, vectorWeight));
            return this;
        }

        public Builder memoryTypes(Set<MemoryType> memoryTypes) {
            this.memoryTypes = memoryTypes;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder snippetLength(int snippetLength) {
            this.snippetLength = snippetLength;
            return this;
        }

        public SearchOptions build() {
            return new SearchOptions(this);
        }
    }
}
