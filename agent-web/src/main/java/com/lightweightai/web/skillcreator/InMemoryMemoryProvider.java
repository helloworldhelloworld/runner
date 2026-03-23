package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级内存 MemoryProvider - 用于 Skill Creator 会话
 *
 * 仅维护会话历史，不做持久化和搜索。
 */
public class InMemoryMemoryProvider implements MemoryProvider {

    private final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();

    @Override
    public void addMessage(String sessionId, Message message) {
        sessions.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(message);
    }

    @Override
    public List<Message> getHistory(String sessionId, int limit) {
        List<Message> history = sessions.getOrDefault(sessionId, Collections.emptyList());
        if (history.size() <= limit) {
            return new ArrayList<>(history);
        }
        return new ArrayList<>(history.subList(history.size() - limit, history.size()));
    }

    @Override
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public void writeEphemeral(String content) {
        // no-op for skill creator
    }

    @Override
    public void writeDurable(String section, String content) {
        // no-op for skill creator
    }

    @Override
    public List<MemorySearchResult> search(String query) {
        return Collections.emptyList();
    }
}
