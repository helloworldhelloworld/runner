package com.lightweightai.agent.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 简单的内存对话记忆实现
 *
 * 消息按时间顺序存储为字符串列表
 */
public class SimpleConversationMemory implements ConversationMemory {

    private final List<String> messages = new ArrayList<>();

    @Override
    public void addMessage(String role, String content) {
        // 简单实现：直接存储内容，忽略角色
        // 实际使用时按用户、助手交替存储
        messages.add(content);
    }

    @Override
    public List<String> getHistory() {
        return Collections.unmodifiableList(messages);
    }

    @Override
    public int getMessageCount() {
        return messages.size();
    }

    @Override
    public void clear() {
        messages.clear();
    }
}
