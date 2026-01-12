package com.lightweightai.kernel.memory;

import com.lightweightai.kernel.llm.ConversationMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆 - 管理对话上下文和长期记忆
 */
public class ConversationMemory {

    // 会话ID到消息历史的映射
    private final Map<String, List<ConversationMessage>> sessionHistory = new ConcurrentHashMap<>();

    // 会话ID到长期记忆的映射
    private final Map<String, UserMemory> longTermMemory = new ConcurrentHashMap<>();

    // 最大保留消息数量
    private final int maxHistorySize;

    public ConversationMemory() {
        this(50); // 默认保留50条消息
    }

    public ConversationMemory(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    /**
     * 添加消息到会话历史
     */
    public void addMessage(String sessionId, ConversationMessage message) {
        sessionHistory.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);

        // 限制历史大小
        List<ConversationMessage> history = sessionHistory.get(sessionId);
        if (history.size() > maxHistorySize) {
            // 保留系统消息和最近的消息
            List<ConversationMessage> systemMessages = new ArrayList<>();
            List<ConversationMessage> recentMessages = new ArrayList<>();

            for (ConversationMessage msg : history) {
                if (msg.getRole() == ConversationMessage.MessageRole.SYSTEM) {
                    systemMessages.add(msg);
                } else {
                    recentMessages.add(msg);
                }
            }

            // 保留系统消息 + 最近的N条消息
            int keepRecent = maxHistorySize - systemMessages.size();
            if (recentMessages.size() > keepRecent) {
                recentMessages = recentMessages.subList(
                    recentMessages.size() - keepRecent,
                    recentMessages.size()
                );
            }

            history.clear();
            history.addAll(systemMessages);
            history.addAll(recentMessages);
        }
    }

    /**
     * 获取会话历史
     */
    public List<ConversationMessage> getHistory(String sessionId) {
        return new ArrayList<>(sessionHistory.getOrDefault(sessionId, new ArrayList<>()));
    }

    /**
     * 获取最近N条消息
     */
    public List<ConversationMessage> getRecentMessages(String sessionId, int count) {
        List<ConversationMessage> history = sessionHistory.getOrDefault(sessionId, new ArrayList<>());
        int size = history.size();
        if (size <= count) {
            return new ArrayList<>(history);
        }
        return new ArrayList<>(history.subList(size - count, size));
    }

    /**
     * 获取或创建用户的长期记忆
     */
    public UserMemory getUserMemory(String sessionId) {
        return longTermMemory.computeIfAbsent(sessionId, k -> new UserMemory());
    }

    /**
     * 更新用户记忆
     */
    public void updateUserMemory(String sessionId, String key, String value) {
        getUserMemory(sessionId).put(key, value);
    }

    /**
     * 清空会话历史
     */
    public void clearHistory(String sessionId) {
        sessionHistory.remove(sessionId);
    }

    /**
     * 清空所有数据
     */
    public void clearAll() {
        sessionHistory.clear();
        longTermMemory.clear();
    }

    /**
     * 获取会话摘要
     */
    public String getSummary(String sessionId) {
        UserMemory memory = longTermMemory.get(sessionId);
        if (memory == null) {
            return "新用户";
        }

        StringBuilder summary = new StringBuilder();
        if (memory.get("userName") != null) {
            summary.append("用户名: ").append(memory.get("userName")).append("\n");
        }
        if (memory.get("concerns") != null) {
            summary.append("关注话题: ").append(memory.get("concerns")).append("\n");
        }
        if (memory.get("emotionalState") != null) {
            summary.append("情绪状态: ").append(memory.get("emotionalState")).append("\n");
        }

        return summary.length() > 0 ? summary.toString() : "新用户";
    }
}
