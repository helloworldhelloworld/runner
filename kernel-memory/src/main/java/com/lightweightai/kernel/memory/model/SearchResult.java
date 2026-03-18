package com.lightweightai.kernel.memory.model;

/**
 * A search result from hybrid search combining BM25 and vector scores.
 */
public class SearchResult {
    private final MemoryChunk chunk;
    private final float score;
    private final float bm25Score;
    private final float vectorScore;
    private final String snippet;

    public SearchResult(MemoryChunk chunk, float score, float bm25Score, float vectorScore, String snippet) {
        this.chunk = chunk;
        this.score = score;
        this.bm25Score = bm25Score;
        this.vectorScore = vectorScore;
        this.snippet = snippet;
    }

    public MemoryChunk getChunk() {
        return chunk;
    }

    public float getScore() {
        return score;
    }

    public float getBm25Score() {
        return bm25Score;
    }

    public float getVectorScore() {
        return vectorScore;
    }

    public String getSnippet() {
        return snippet;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemoryChunk chunk;
        private float score;
        private float bm25Score;
        private float vectorScore;
        private String snippet;

        public Builder chunk(MemoryChunk chunk) {
            this.chunk = chunk;
            return this;
        }

        public Builder score(float score) {
            this.score = score;
            return this;
        }

        public Builder bm25Score(float bm25Score) {
            this.bm25Score = bm25Score;
            return this;
        }

        public Builder vectorScore(float vectorScore) {
            this.vectorScore = vectorScore;
            return this;
        }

        public Builder snippet(String snippet) {
            this.snippet = snippet;
            return this;
        }

        public SearchResult build() {
            return new SearchResult(chunk, score, bm25Score, vectorScore, snippet);
        }
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "chunk=" + chunk.getId() +
                ", score=" + score +
                ", snippet='" + (snippet != null ? snippet.substring(0, Math.min(50, snippet.length())) + "..." : "null") + '\'' +
                '}';
    }
}
