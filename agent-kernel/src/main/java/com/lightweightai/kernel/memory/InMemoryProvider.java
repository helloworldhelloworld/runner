package com.lightweightai.kernel.memory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现的 MemoryProvider
 *
 * 用于测试和轻量级场景，不持久化到磁盘。
 * 支持 BM25 风格的关键词搜索。
 * 所有读写由实例锁保护，避免并发 Agent 修改检索正在遍历的列表。
 */
public class InMemoryProvider implements MemoryProvider {
    private final Map<List<String>, MemoryProvider> scopedMemory = new ConcurrentHashMap<>();
    private final Map<String, InMemoryProvider> sessionJournals = new ConcurrentHashMap<>();

    @Override public MemoryProvider forScope(String principalId, String agentId) {
        if (principalId == null) return this;
        return scopedMemory.computeIfAbsent(List.of(principalId, agentId), key -> new InMemoryProvider());
    }

    @Override public void clearExecutionTree(String principalId, String agentId, String sessionId) {
        if (principalId == null) {
            clearSession(sessionId);
            return;
        }
        scopedMemory.forEach((scope, memory) -> {
            if (scope.getFirst().equals(principalId)) memory.clearSession(sessionId);
        });
    }

    @Override public synchronized MemoryProvider forSession(String sessionId) {
        InMemoryProvider owner = this;
        InMemoryProvider journal = sessionJournals.computeIfAbsent(sessionId, key -> new InMemoryProvider());
        return new MemoryProvider() {
            private void live() {
                if (sessionJournals.get(sessionId) != journal) throw new IllegalStateException("Session was cleared; stale writes are rejected");
            }
            public void addMessage(String session, Message message) {
                if (!sessionId.equals(session)) throw new IllegalArgumentException("Memory view belongs to another session");
                synchronized (owner) { live(); owner.addMessage(session, message); }
            }
            public List<Message> getHistory(String session, int limit) { return owner.getHistory(session, limit); }
            public void clearSession(String session) { owner.clearSession(session); }
            public void writeEphemeral(String content) { synchronized (owner) { live(); journal.writeEphemeral(content); } }
            public void writeDurable(String section, String content) { synchronized (owner) { live(); owner.writeDurable(section, content); } }
            public List<MemorySearchResult> search(String query) { return owner.search(query); }
            public String readDurable() { return owner.readDurable(); }
            public String readTodayEphemeral() { return owner.readTodayEphemeral(); }
        };
    }

    // 会话历史：sessionId -> 消息列表
    private final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();

    // 临时记忆：日期 -> 内容列表
    private final Map<LocalDate, List<String>> ephemeralMemory = new ConcurrentHashMap<>();

    // 长期记忆：section -> 内容列表
    private final Map<String, List<String>> durableMemory = new ConcurrentHashMap<>();

    // ==================== 会话记忆 ====================

    @Override
    public synchronized void addMessage(String sessionId, Message message) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    @Override
    public synchronized List<Message> getHistory(String sessionId, int limit) {
        List<Message> history = sessions.getOrDefault(sessionId, Collections.emptyList());

        if (history.size() <= limit) {
            return new ArrayList<>(history);
        }

        // 返回最近的 N 条
        return new ArrayList<>(history.subList(history.size() - limit, history.size()));
    }

    @Override
    public synchronized void clearSession(String sessionId) {
        sessions.keySet().removeIf(key -> key.equals(sessionId) || key.startsWith(sessionId + ":subagent:"));
        sessionJournals.keySet().removeIf(key -> key.equals(sessionId) || key.startsWith(sessionId + ":subagent:"));
    }

    // ==================== 持久化记忆 ====================

    @Override
    public synchronized void writeEphemeral(String content) {
        LocalDate today = LocalDate.now();
        ephemeralMemory.computeIfAbsent(today, k -> new ArrayList<>()).add(content);
    }

    @Override
    public synchronized void writeDurable(String section, String content) {
        durableMemory.computeIfAbsent(section, k -> new ArrayList<>()).add(content);
    }

    @Override
    public synchronized String readTodayEphemeral() {
        List<String> today = new ArrayList<>(ephemeralMemory.getOrDefault(LocalDate.now(), List.of()));
        sessionJournals.values().forEach(journal -> today.add(journal.readTodayEphemeral()));
        return String.join("\n", today);
    }

    @Override
    public synchronized String readDurable() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : durableMemory.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");
            for (String content : entry.getValue()) {
                sb.append(content).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ==================== 记忆检索 ====================

    @Override
    public synchronized List<MemorySearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<MemorySearchResult> results = new ArrayList<>();
        String[] queryTerms = query.toLowerCase().split("\\s+");

        // 搜索临时记忆
        for (Map.Entry<LocalDate, List<String>> entry : ephemeralMemory.entrySet()) {
            for (String content : entry.getValue()) {
                double score = calculateBM25Score(content, queryTerms);
                if (score > 0) {
                    results.add(MemorySearchResult.ephemeral(
                        content,
                        entry.getKey().toString(),
                        score
                    ));
                }
            }
        }

        // 搜索长期记忆
        for (Map.Entry<String, List<String>> entry : durableMemory.entrySet()) {
            for (String content : entry.getValue()) {
                double score = calculateBM25Score(content, queryTerms);
                if (score > 0) {
                    results.add(MemorySearchResult.durable(
                        content,
                        entry.getKey(),
                        score
                    ));
                }
            }
        }

        sessionJournals.values().forEach(journal -> results.addAll(journal.search(query)));
        // 按相关性排序
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        return results;
    }

    /**
     * 简化的 BM25 评分
     *
     * 真正的 BM25 需要 IDF，这里用简化版本：
     * - 匹配的词越多，分数越高
     * - 内容越短（信息密度越高），分数越高
     */
    private double calculateBM25Score(String content, String[] queryTerms) {
        if (content == null || content.isEmpty()) {
            return 0.0;
        }

        String lowerContent = content.toLowerCase();
        int matchCount = 0;

        for (String term : queryTerms) {
            if (lowerContent.contains(term)) {
                matchCount++;
            }
        }

        if (matchCount == 0) {
            return 0.0;
        }

        // 简化 BM25：命中率 * 长度惩罚
        double hitRate = (double) matchCount / queryTerms.length;
        double lengthPenalty = 1.0 / (1.0 + Math.log(content.length() / 50.0 + 1));

        return hitRate * lengthPenalty;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取所有会话 ID
     */
    public synchronized Set<String> getAllSessionIds() {
        return new HashSet<>(sessions.keySet());
    }

    /**
     * 获取统计信息
     */
    public synchronized Map<String, Object> getStats() {
        return Map.of(
            "sessions", sessions.size(),
            "ephemeralDays", ephemeralMemory.size(),
            "durableSections", durableMemory.size(),
            "totalMessages", sessions.values().stream().mapToInt(List::size).sum()
        );
    }

    /**
     * 清空所有记忆
     */
    public synchronized void clearAll() {
        sessions.clear();
        sessionJournals.clear();
        scopedMemory.clear();
        ephemeralMemory.clear();
        durableMemory.clear();
    }
}
