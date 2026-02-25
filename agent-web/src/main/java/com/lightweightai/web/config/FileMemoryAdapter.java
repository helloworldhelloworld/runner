package com.lightweightai.web.config;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.memory.ConversationMemory;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 桥接层：将 FileMemoryManager（持久化搜索）和 ConversationMemory（会话历史）
 * 统一暴露为 MemoryProvider 接口，供 PromptEngine 使用。
 *
 * OpenClaw 三层架构通过此类打通：
 *   Session 历史  → ConversationMemory
 *   Ephemeral     → FileMemoryManager.appendEphemeral()
 *   Durable       → FileMemoryManager.appendDurable() / readDurable()
 *   搜索           → FileMemoryManager.search() (BM25+Vector)
 */
@Component
public class FileMemoryAdapter implements MemoryProvider {

    private static final Logger logger = LoggerFactory.getLogger(FileMemoryAdapter.class);

    private final FileMemoryManager fileMemoryManager;
    private final ConversationMemory conversationMemory;

    public FileMemoryAdapter(FileMemoryManager fileMemoryManager) {
        this.fileMemoryManager = fileMemoryManager;
        this.conversationMemory = new ConversationMemory();
    }

    // ==================== 会话历史 ====================

    @Override
    public void addMessage(String sessionId, Message message) {
        conversationMemory.addMessage(sessionId, message.toConversationMessage());
    }

    @Override
    public List<Message> getHistory(String sessionId, int limit) {
        return conversationMemory.getRecentMessages(sessionId, limit)
            .stream()
            .map(Message::fromConversationMessage)
            .collect(Collectors.toList());
    }

    @Override
    public void clearSession(String sessionId) {
        conversationMemory.clearHistory(sessionId);
    }

    // ==================== 持久化记忆 ====================

    @Override
    public void writeEphemeral(String content) {
        fileMemoryManager.appendEphemeral(content);
    }

    @Override
    public void writeDurable(String section, String content) {
        fileMemoryManager.appendDurable(section, content);
    }

    @Override
    public String readDurable() {
        return fileMemoryManager.readDurable();
    }

    @Override
    public String readTodayEphemeral() {
        // FileMemoryManager 没有 readToday，返回空（由 readDurable 覆盖主要场景）
        return "";
    }

    // ==================== 混合搜索 ====================

    @Override
    public List<MemorySearchResult> search(String query) {
        List<SearchResult> results = fileMemoryManager.search(query);
        return results.stream()
            .map(r -> {
                String snippet = r.getSnippet() != null
                    ? r.getSnippet()
                    : r.getChunk().getContent();
                String source = r.getChunk().getSourceFile();
                return MemorySearchResult.ephemeral(snippet, source, r.getScore());
            })
            .collect(Collectors.toList());
    }

    // ==================== 直接访问（供 Service 层使用）====================

    /**
     * 暴露底层 ConversationMemory，供 SoulComfortChatService 恢复历史使用。
     */
    public ConversationMemory getConversationMemory() {
        return conversationMemory;
    }
}
