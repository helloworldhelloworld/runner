package com.lightweightai.kernel.memory;

import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.file.SessionTranscript;
import com.lightweightai.kernel.memory.model.SearchResult;
import com.lightweightai.kernel.memory.model.TranscriptEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文件存储实现的 MemoryProvider
 *
 * 适配 FileMemoryManager 到统一的 MemoryProvider 接口。
 * 支持：
 * - 会话历史持久化（JSONL）
 * - 临时记忆（每日 Markdown）
 * - 长期记忆（MEMORY.md）
 * - 混合搜索（BM25 + Vector）
 */
public class FileMemoryProvider implements MemoryProvider {

    private final FileMemoryManager fileManager;
    private final Map<String, SessionTranscript> activeSessions = new ConcurrentHashMap<>();

    public FileMemoryProvider(FileMemoryManager fileManager) {
        this.fileManager = fileManager;
    }

    // ==================== 会话记忆 ====================

    @Override
    public void addMessage(String sessionId, Message message) {
        SessionTranscript transcript = getOrCreateSession(sessionId);

        TranscriptEntry entry = "user".equals(message.getRole())
            ? TranscriptEntry.userMessage(message.getContent())
            : TranscriptEntry.assistantMessage(message.getContent());

        transcript.append(entry);
    }

    @Override
    public List<Message> getHistory(String sessionId, int limit) {
        SessionTranscript transcript = fileManager.getSession(sessionId);
        List<TranscriptEntry> entries = transcript.readAll();

        // 过滤出 user/assistant 消息
        List<Message> messages = entries.stream()
            .filter(e -> "user".equals(e.getRole()) || "assistant".equals(e.getRole()))
            .map(e -> Message.of(e.getRole(), e.getContent()))
            .collect(Collectors.toList());

        // 返回最近的 N 条
        if (messages.size() <= limit) {
            return messages;
        }
        return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
    }

    @Override
    public void clearSession(String sessionId) {
        activeSessions.remove(sessionId);
        // 注意：不删除磁盘上的 JSONL 文件，只清除内存缓存
    }

    // ==================== 持久化记忆 ====================

    @Override
    public void writeEphemeral(String content) {
        fileManager.appendEphemeral(content);
    }

    @Override
    public void writeDurable(String section, String content) {
        fileManager.appendDurable(section, content);
    }

    @Override
    public String readTodayEphemeral() {
        return fileManager.readTodayEphemeral();
    }

    @Override
    public String readDurable() {
        return fileManager.readDurable();
    }

    // ==================== 记忆检索 ====================

    @Override
    public List<MemorySearchResult> search(String query) {
        List<SearchResult> results = fileManager.search(query);

        return results.stream()
            .map(r -> new MemorySearchResult(
                r.getChunk().getContent(),
                r.getChunk().getSourceFile(),
                r.getScore(),
                convertType(r.getChunk().getType())
            ))
            .collect(Collectors.toList());
    }

    @Override
    public List<MemorySearchResult> search(String query, SearchOptions options) {
        var searchOptions = com.lightweightai.kernel.memory.model.SearchOptions.builder()
            .topK(options.getTopK())
            .vectorWeight((float) options.getVectorWeight())
            .build();

        List<SearchResult> results = fileManager.search(query, searchOptions);

        return results.stream()
            .map(r -> new MemorySearchResult(
                r.getChunk().getContent(),
                r.getChunk().getSourceFile(),
                r.getScore(),
                convertType(r.getChunk().getType())
            ))
            .collect(Collectors.toList());
    }

    // ==================== 生命周期 ====================

    @Override
    public void close() {
        fileManager.close();
    }

    // ==================== 辅助方法 ====================

    private SessionTranscript getOrCreateSession(String sessionId) {
        return activeSessions.computeIfAbsent(sessionId, id -> fileManager.getSession(id));
    }

    private MemorySearchResult.MemoryType convertType(com.lightweightai.kernel.memory.model.MemoryType type) {
        return switch (type) {
            case EPHEMERAL -> MemorySearchResult.MemoryType.EPHEMERAL;
            case DURABLE -> MemorySearchResult.MemoryType.DURABLE;
            case SESSION -> MemorySearchResult.MemoryType.SESSION;
        };
    }

    /**
     * 获取底层的 FileMemoryManager（用于高级操作）
     */
    public FileMemoryManager getFileManager() {
        return fileManager;
    }
}
