package com.lightweightai.kernel.memory;

import java.util.Collections;
import java.util.List;

/**
 * 不可变消息快照
 *
 * 将会话历史的可变 List<Message> 冻结为不可变视图，
 * 用于 API 请求路径（AgentLoop → ToolCallingLoop → LLMProvider），
 * 与持久化路径的可变数组解耦。
 *
 * 参考: Claude Code 泄露源码中 QueryEngine 的双层架构 ——
 * 可变 message 数组用于 UI/持久化，不可变快照用于 LLM API 请求。
 */
public final class MessageSnapshot {

    private final String sessionId;
    private final List<Message> messages;
    private final int totalCount;

    private MessageSnapshot(String sessionId, List<Message> messages) {
        this.sessionId = sessionId;
        this.messages = Collections.unmodifiableList(List.copyOf(messages));
        this.totalCount = messages.size();
    }

    /**
     * 从 MemoryProvider 当前历史创建快照
     */
    public static MessageSnapshot capture(MemoryProvider provider, String sessionId, int limit) {
        List<Message> history = provider.getHistory(sessionId, limit);
        return new MessageSnapshot(sessionId, history);
    }

    /**
     * 从已有的消息列表创建快照（防御性拷贝）
     */
    public static MessageSnapshot of(String sessionId, List<Message> messages) {
        return new MessageSnapshot(sessionId, messages);
    }

    /**
     * 空快照
     */
    public static MessageSnapshot empty(String sessionId) {
        return new MessageSnapshot(sessionId, List.of());
    }

    public String getSessionId() {
        return sessionId;
    }

    /**
     * 返回不可变消息列表
     */
    public List<Message> getMessages() {
        return messages;
    }

    public int size() {
        return totalCount;
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    @Override
    public String toString() {
        return "MessageSnapshot{sessionId='" + sessionId + "', count=" + totalCount + "}";
    }
}
