package com.lightweightai.kernel.memory;

import java.util.List;

/**
 * 统一的记忆提供者接口
 *
 * 合并三套 Memory 实现：
 * 1. 会话记忆 - 存储对话历史
 * 2. 持久化记忆 - Ephemeral (每日) + Durable (长期)
 * 3. 记忆检索 - BM25/Vector 搜索
 *
 * 设计原则：
 * - 接口定义在 kernel-core，实现可插拔
 * - 支持 InMemoryProvider (测试/轻量) 和 FileMemoryProvider (生产)
 * - 兼容 OpenClaw 三层记忆架构
 */
public interface MemoryProvider {

    // ==================== 会话记忆 ====================

    /**
     * 添加消息到会话历史
     *
     * @param sessionId 会话ID
     * @param message 消息
     */
    void addMessage(String sessionId, Message message);

    /**
     * 获取会话历史
     *
     * @param sessionId 会话ID
     * @param limit 最大返回数量（返回最近的N条）
     * @return 消息列表（按时间顺序）
     */
    List<Message> getHistory(String sessionId, int limit);

    /**
     * 清空会话历史
     *
     * @param sessionId 会话ID
     */
    void clearSession(String sessionId);

    // ==================== 持久化记忆 (OpenClaw风格) ====================

    /**
     * 写入临时记忆（Ephemeral）
     *
     * 每日日志，自动按日期分组存储。
     * 适合存储：对话摘要、情绪记录、临时笔记
     *
     * @param content 内容
     */
    void writeEphemeral(String content);

    /**
     * 写入长期记忆（Durable）
     *
     * 持久化知识，按 section 分组存储。
     * 适合存储：用户信息、重要话题、学到的知识
     *
     * @param section 分组名称（如 "用户信息"、"重要话题"）
     * @param content 内容
     */
    void writeDurable(String section, String content);

    // ==================== 记忆检索 ====================

    /**
     * 搜索记忆
     *
     * 默认使用混合搜索（BM25 + Vector），返回相关性排序的结果。
     *
     * @param query 搜索查询
     * @return 搜索结果列表（按相关性排序）
     */
    List<MemorySearchResult> search(String query);

    /**
     * 搜索记忆（带选项）
     *
     * @param query 搜索查询
     * @param options 搜索选项
     * @return 搜索结果列表
     */
    default List<MemorySearchResult> search(String query, SearchOptions options) {
        return search(query);
    }

    // ==================== 可选方法 ====================

    /**
     * 读取今日临时记忆
     */
    default String readTodayEphemeral() {
        return "";
    }

    /**
     * 读取长期记忆
     */
    default String readDurable() {
        return "";
    }

    /**
     * 关闭资源
     */
    default void close() {
        // 默认空实现
    }

    // ==================== 搜索选项 ====================

    /**
     * 搜索选项
     */
    class SearchOptions {
        private int topK = 5;
        private double vectorWeight = 0.7;
        private boolean includeEphemeral = true;
        private boolean includeDurable = true;

        public static SearchOptions defaults() {
            return new SearchOptions();
        }

        public SearchOptions topK(int topK) {
            this.topK = topK;
            return this;
        }

        public SearchOptions vectorWeight(double weight) {
            this.vectorWeight = weight;
            return this;
        }

        public SearchOptions ephemeralOnly() {
            this.includeDurable = false;
            return this;
        }

        public SearchOptions durableOnly() {
            this.includeEphemeral = false;
            return this;
        }

        public int getTopK() { return topK; }
        public double getVectorWeight() { return vectorWeight; }
        public boolean isIncludeEphemeral() { return includeEphemeral; }
        public boolean isIncludeDurable() { return includeDurable; }
    }
}
