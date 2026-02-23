package com.lightweightai.kernel.memory;

import java.util.Objects;

/**
 * 记忆搜索结果
 *
 * 包含匹配的内容、来源和相关性分数。
 */
public class MemorySearchResult {

    private final String content;
    private final String source;
    private final double score;
    private final MemoryType type;

    public MemorySearchResult(String content, String source, double score, MemoryType type) {
        this.content = Objects.requireNonNull(content);
        this.source = source;
        this.score = score;
        this.type = type != null ? type : MemoryType.EPHEMERAL;
    }

    // ==================== 工厂方法 ====================

    public static MemorySearchResult of(String content, double score) {
        return new MemorySearchResult(content, null, score, MemoryType.EPHEMERAL);
    }

    public static MemorySearchResult ephemeral(String content, String source, double score) {
        return new MemorySearchResult(content, source, score, MemoryType.EPHEMERAL);
    }

    public static MemorySearchResult durable(String content, String source, double score) {
        return new MemorySearchResult(content, source, score, MemoryType.DURABLE);
    }

    // ==================== Getters ====================

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public double getScore() {
        return score;
    }

    public MemoryType getType() {
        return type;
    }

    /**
     * 获取内容摘要（截断长文本）
     */
    public String getSnippet(int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    @Override
    public String toString() {
        return String.format("MemorySearchResult{score=%.3f, type=%s, content='%s'}",
            score, type, getSnippet(50));
    }

    /**
     * 记忆类型
     */
    public enum MemoryType {
        EPHEMERAL,  // 每日临时记忆
        DURABLE,    // 长期持久记忆
        SESSION     // 会话记忆
    }
}
