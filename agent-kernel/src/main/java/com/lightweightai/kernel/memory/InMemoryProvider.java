package com.lightweightai.kernel.memory;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存实现的 MemoryProvider
 *
 * 用于测试和轻量级场景，不持久化到磁盘。
 * 支持 BM25 风格的关键词搜索。
 */
public class InMemoryProvider implements MemoryProvider {

    // 会话历史：sessionId -> 消息列表
    private final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();

    // 临时记忆：日期 -> 内容列表
    private final Map<LocalDate, List<String>> ephemeralMemory = new ConcurrentHashMap<>();

    // 长期记忆：section -> 内容列表
    private final Map<String, List<String>> durableMemory = new ConcurrentHashMap<>();

    // ==================== 会话记忆 ====================

    @Override
    public void addMessage(String sessionId, Message message) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    @Override
    public List<Message> getHistory(String sessionId, int limit) {
        List<Message> history = sessions.getOrDefault(sessionId, Collections.emptyList());

        if (history.size() <= limit) {
            return new ArrayList<>(history);
        }

        // 返回最近的 N 条
        return new ArrayList<>(history.subList(history.size() - limit, history.size()));
    }

    @Override
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    // ==================== 持久化记忆 ====================

    @Override
    public void writeEphemeral(String content) {
        LocalDate today = LocalDate.now();
        ephemeralMemory.computeIfAbsent(today, k -> new ArrayList<>()).add(content);
    }

    @Override
    public void writeDurable(String section, String content) {
        durableMemory.computeIfAbsent(section, k -> new ArrayList<>()).add(content);
    }

    @Override
    public String readTodayEphemeral() {
        List<String> today = ephemeralMemory.get(LocalDate.now());
        if (today == null || today.isEmpty()) {
            return "";
        }
        return String.join("\n", today);
    }

    @Override
    public String readDurable() {
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
    public List<MemorySearchResult> search(String query) {
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
    public Set<String> getAllSessionIds() {
        return new HashSet<>(sessions.keySet());
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("sessions", sessions.size());
        stats.put("ephemeralDays", ephemeralMemory.size());
        stats.put("durableSections", durableMemory.size());
        stats.put("totalMessages", sessions.values().stream().mapToInt(List::size).sum());
        return stats;
    }

    /**
     * 清空所有记忆
     */
    public void clearAll() {
        sessions.clear();
        ephemeralMemory.clear();
        durableMemory.clear();
    }
}
