package com.lightweightai.web.service;

import com.lightweightai.kernel.agent.SoulComfortAgent;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import com.lightweightai.kernel.memory.UserMemory;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.file.SessionTranscript;
import com.lightweightai.kernel.memory.model.SearchResult;
import com.lightweightai.kernel.memory.model.TranscriptEntry;
import com.lightweightai.kernel.memory.queue.LaneQueueManager;
import com.lightweightai.kernel.memory.tools.MemoryToolkit;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.observer.DebugAgentObserver;
import com.lightweightai.web.observer.DebugInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 心灵引导聊天服务 - 使用SoulComfortAgent + OpenClaw记忆系统
 *
 * 特性：
 * - 三层记忆：Ephemeral(每日) + Durable(长期) + Session(会话转录)
 * - 混合搜索：BM25(关键词) + Vector(语义)
 * - Lane Queue：每个会话串行执行，防止竞争
 * - 记忆工具：memory_search / write_memory
 */
@Service
public class SoulComfortChatService {

    private static final Logger logger = LoggerFactory.getLogger(SoulComfortChatService.class);

    private final SoulComfortAgent defaultAgent;
    private final Map<String, SoulComfortAgent> modelAgents;

    // OpenClaw-style memory system
    private final FileMemoryManager memoryManager;
    private final LaneQueueManager laneManager;
    private final MemoryToolkit memoryToolkit;
    private final Map<String, SessionTranscript> activeSessions = new ConcurrentHashMap<>();

    @Value("${app.openrouter.api-key:}")
    private String openRouterApiKey;

    public SoulComfortChatService(
            LLMProvider llmProvider,
            FileMemoryManager memoryManager,
            LaneQueueManager laneManager,
            MemoryToolkit memoryToolkit
    ) {
        this.defaultAgent = new SoulComfortAgent(llmProvider);
        this.modelAgents = new ConcurrentHashMap<>();
        this.memoryManager = memoryManager;
        this.laneManager = laneManager;
        this.memoryToolkit = memoryToolkit;

        logger.info("SoulComfortChatService initialized with OpenClaw-style memory system");
    }

    /**
     * Callback for streaming chat responses
     */
    public interface StreamCallback {
        void onChunk(StreamChunk chunk);
    }

    public static class StreamChunk {
        private String delta;
        private String emotion;

        public StreamChunk(String delta, String emotion) {
            this.delta = delta;
            this.emotion = emotion;
        }

        public String getDelta() { return delta; }
        public String getEmotion() { return emotion; }
    }

    /**
     * 处理聊天请求（通过 Lane Queue 串行执行）
     */
    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";

        // 使用 Lane Queue 保证同一会话的消息串行处理
        return laneManager.submit(sessionId, () -> processChatInternal(request, sessionId))
            .join();
    }

    private ChatResponse processChatInternal(ChatRequest request, String sessionId) {
        // 创建调试观察者
        DebugAgentObserver debugObserver = new DebugAgentObserver();
        boolean debugMode = request.isDebug(); // 是否开启调试模式（声明在 try 外以便 catch 访问）

        try {
            String userMessage = request.getMessage();
            String model = request.getModel();

            logger.info("Processing chat for session: {} with model: {}, debug: {}", sessionId, model, debugMode);

            // 1. 获取或创建会话转录
            SessionTranscript transcript = getOrCreateSession(sessionId);
            transcript.append(TranscriptEntry.userMessage(userMessage));

            // 2. 搜索相关记忆（提供上下文）
            List<SearchResult> memoryContext = memoryManager.search(userMessage);
            String contextInfo = formatMemoryContext(memoryContext);

            // 3. 获取对应模型的Agent，并设置观察者
            logger.info("DEBUG: Getting agent for model: {}", model);
            SoulComfortAgent agent = getAgentForModel(model);
            logger.info("DEBUG: Got agent, setting observer");
            agent.setObserver(debugObserver);
            System.out.println("[SERVICE-DEBUG] Observer set, about to call chat()");
            System.out.flush();

            // 4. 使用心灵引导Agent处理消息（注入记忆上下文）
            logger.info("DEBUG: Calling agent.chat() with contextInfo length: {}", contextInfo != null ? contextInfo.length() : 0);
            System.out.println("[SERVICE-DEBUG] Calling agent.chat() now...");
            System.out.flush();
            String response = agent.chat(sessionId, userMessage, contextInfo);
            System.out.println("[SERVICE-DEBUG] Got response from agent");
            logger.info("DEBUG: Got response from agent");

            // 5. 保存助手响应到转录
            transcript.append(TranscriptEntry.assistantMessage(response));

            // 6. 将对话内容写入可检索的记忆（OpenClaw风格）
            UserMemory userMemory = agent.getUserMemory(sessionId);
            indexConversationToMemory(sessionId, userMessage, response, userMemory);

            // 7. 构建响应
            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setResponse(response);
            chatResponse.setSkillsApplied(List.of("soul-comfort", "openclaw-memory"));

            // 8. 添加元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("mode", "soul-comfort");
            metadata.put("hasMemory", true);
            metadata.put("hasReflection", true);
            metadata.put("memorySystem", "openclaw");

            if (model != null && !model.trim().isEmpty()) {
                metadata.put("model", model);
            }

            // 记忆上下文信息
            if (!memoryContext.isEmpty()) {
                metadata.put("memoryContextCount", memoryContext.size());
                metadata.put("memoryContextSources", memoryContext.stream()
                    .map(r -> r.getChunk().getSourceFile())
                    .distinct()
                    .collect(Collectors.toList()));
            }

            // 情绪和话题
            List<UserMemory.EmotionRecord> recentEmotions = userMemory.getRecentEmotions(5);
            if (!recentEmotions.isEmpty()) {
                metadata.put("recentEmotions", recentEmotions.stream()
                    .map(UserMemory.EmotionRecord::getEmotion)
                    .collect(Collectors.toList()));
            }

            List<String> topics = userMemory.getImportantTopics();
            if (!topics.isEmpty()) {
                metadata.put("importantTopics", topics);
            }

            chatResponse.setMetadata(metadata);

            // 添加调试信息（如果开启调试模式）
            if (debugMode) {
                chatResponse.setDebug(debugObserver.getDebugInfo());
            }

            logger.info("Chat completed - session: {}, memory context: {} items", sessionId, memoryContext.size());
            return chatResponse;

        } catch (Exception e) {
            logger.error("Chat failed", e);
            ChatResponse errorResponse = ChatResponse.error(e.getMessage());
            // 即使出错也返回调试信息
            if (debugMode) {
                debugObserver.onError(e);
                errorResponse.setDebug(debugObserver.getDebugInfo());
            }
            return errorResponse;
        }
    }

    /**
     * 处理聊天请求（流式，通过 Lane Queue）
     */
    public CompletableFuture<String> chat(String message, String sessionId, StreamCallback callback) {
        String actualSessionId = sessionId != null ? sessionId : "default";

        return laneManager.submitAsync(actualSessionId, () ->
            processChatStreamInternal(message, actualSessionId, callback)
        );
    }

    private CompletableFuture<String> processChatStreamInternal(String message, String sessionId, StreamCallback callback) {
        logger.info("Processing streaming chat for session: {}", sessionId);

        // 获取或创建会话转录
        SessionTranscript transcript = getOrCreateSession(sessionId);
        transcript.append(TranscriptEntry.userMessage(message));

        // 搜索相关记忆
        List<SearchResult> memoryContext = memoryManager.search(message);
        String contextInfo = formatMemoryContext(memoryContext);
        if (!memoryContext.isEmpty()) {
            logger.debug("Found {} memory items for context", memoryContext.size());
        }

        SoulComfortAgent agent = defaultAgent;
        UserMemory userMemory = agent.getUserMemory(sessionId);
        List<UserMemory.EmotionRecord> recentEmotions = userMemory.getRecentEmotions(1);
        String currentEmotion = recentEmotions.isEmpty() ? "温柔" : recentEmotions.get(0).getEmotion();

        StringBuilder fullResponse = new StringBuilder();

        // 注入记忆上下文到流式对话
        return agent.chatStream(sessionId, message, contextInfo, new LLMProvider.StreamEventHandler() {
            @Override
            public void onTextDelta(String delta) {
                fullResponse.append(delta);
                callback.onChunk(new StreamChunk(delta, currentEmotion));
            }

            @Override
            public void onComplete(com.lightweightai.kernel.llm.LLMResponse response) {
                String responseText = fullResponse.toString();

                // 保存响应到转录
                transcript.append(TranscriptEntry.assistantMessage(responseText));

                // 将对话内容索引到可检索记忆（OpenClaw风格）
                indexConversationToMemory(sessionId, message, responseText, userMemory);

                // 获取最终情绪
                List<UserMemory.EmotionRecord> emotions = agent.getUserMemory(sessionId).getRecentEmotions(1);
                String finalEmotion = emotions.isEmpty() ? currentEmotion : emotions.get(0).getEmotion();

                callback.onChunk(new StreamChunk("", finalEmotion));
                logger.info("Streaming chat completed - Session: {}", sessionId);
            }

            @Override
            public void onError(Throwable error) {
                logger.error("Streaming chat error - Session: " + sessionId, error);
            }
        });
    }

    /**
     * 获取会话历史（用于前端恢复显示）
     */
    public List<Map<String, String>> getSessionHistory(String sessionId) {
        SessionTranscript transcript = memoryManager.getSession(sessionId);
        List<TranscriptEntry> entries = transcript.readAll();

        return entries.stream()
            .filter(e -> "user".equals(e.getRole()) || "assistant".equals(e.getRole()))
            .map(e -> Map.of(
                "role", e.getRole(),
                "content", e.getContent() != null ? e.getContent() : ""
            ))
            .collect(Collectors.toList());
    }

    /**
     * 搜索记忆
     */
    public List<Map<String, Object>> searchMemory(String query, int topK) {
        List<SearchResult> results = memoryManager.search(query);
        return results.stream()
            .limit(topK)
            .map(r -> Map.<String, Object>of(
                "content", r.getChunk().getContent(),
                "source", r.getChunk().getSourceFile(),
                "score", r.getScore(),
                "snippet", r.getSnippet() != null ? r.getSnippet() : ""
            ))
            .collect(Collectors.toList());
    }

    /**
     * 保存到长期记忆
     */
    public void rememberDurable(String section, String content) {
        memoryManager.appendDurable(section, content);
        logger.info("Saved to durable memory - section: {}", section);
    }

    /**
     * 获取会话摘要（增强版）
     */
    public Map<String, Object> getSessionSummary(String sessionId) {
        Map<String, Object> summary = new HashMap<>();

        try {
            // 原有摘要
            String conversationSummary = defaultAgent.getSessionSummary(sessionId);
            summary.put("summary", conversationSummary);

            // 获取用户记忆
            UserMemory userMemory = defaultAgent.getUserMemory(sessionId);
            List<UserMemory.EmotionRecord> emotions = userMemory.getRecentEmotions(5);
            summary.put("recentEmotions", emotions.stream()
                .map(e -> Map.of(
                    "emotion", e.getEmotion(),
                    "context", e.getContext(),
                    "timestamp", e.getTimestamp().toString()
                ))
                .collect(Collectors.toList()));

            summary.put("importantTopics", userMemory.getImportantTopics());
            summary.put("basicInfo", userMemory.getAllBasicInfo());
            summary.put("firstMeet", userMemory.getFirstMeetTime().toString());
            summary.put("lastInteraction", userMemory.getLastInteractionTime().toString());

            // OpenClaw 记忆系统信息
            var stats = memoryManager.getIndexStats();
            summary.put("memoryStats", Map.of(
                "bm25Chunks", stats.bm25ChunkCount(),
                "vectorChunks", stats.vectorChunkCount(),
                "embeddingDimensions", stats.embeddingDimensions()
            ));

            // 会话转录信息
            SessionTranscript transcript = activeSessions.get(sessionId);
            if (transcript != null) {
                summary.put("transcriptEntries", transcript.getEntryCount());
                summary.put("transcriptFile", transcript.getSessionFile().toString());
            }

            // 所有历史会话
            summary.put("allSessions", memoryManager.listSessions());

        } catch (Exception e) {
            logger.error("Failed to get session summary", e);
            summary.put("error", e.getMessage());
        }

        return summary;
    }

    /**
     * 清空会话
     */
    public void clearSession(String sessionId) {
        defaultAgent.clearSession(sessionId);
        modelAgents.values().forEach(agent -> agent.clearSession(sessionId));
        activeSessions.remove(sessionId);
        logger.info("Cleared session: {}", sessionId);
    }

    /**
     * 获取记忆工具包（供 API 使用）
     */
    public MemoryToolkit getMemoryToolkit() {
        return memoryToolkit;
    }

    /**
     * 获取 Agent 实例
     */
    public SoulComfortAgent getAgent() {
        return defaultAgent;
    }

    // ==================== Private Helpers ====================

    private SessionTranscript getOrCreateSession(String sessionId) {
        return activeSessions.computeIfAbsent(sessionId, id -> {
            logger.debug("Loading/creating session transcript for: {}", id);
            SessionTranscript transcript = memoryManager.getSession(id);

            // 恢复历史对话到Agent内存（OpenClaw风格：刷新后保持对话上下文）
            restoreConversationHistory(sessionId, transcript);

            return transcript;
        });
    }

    /**
     * 从JSONL转录恢复对话历史到Agent内存
     */
    private void restoreConversationHistory(String sessionId, SessionTranscript transcript) {
        List<TranscriptEntry> history = transcript.readAll();
        if (history.isEmpty()) {
            return;
        }

        logger.info("Restoring {} messages for session: {}", history.size(), sessionId);

        var memory = defaultAgent.getMemory();
        for (TranscriptEntry entry : history) {
            if ("user".equals(entry.getRole())) {
                memory.addMessage(sessionId,
                    com.lightweightai.kernel.llm.ConversationMessage.builder()
                        .role(com.lightweightai.kernel.llm.ConversationMessage.MessageRole.USER)
                        .textContent(entry.getContent())
                        .build());
            } else if ("assistant".equals(entry.getRole())) {
                memory.addMessage(sessionId,
                    com.lightweightai.kernel.llm.ConversationMessage.builder()
                        .role(com.lightweightai.kernel.llm.ConversationMessage.MessageRole.ASSISTANT)
                        .textContent(entry.getContent())
                        .build());
            }
        }
    }

    private SoulComfortAgent getAgentForModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            return defaultAgent;
        }

        return modelAgents.computeIfAbsent(model, m -> {
            logger.info("Creating new agent for model: {}", m);

            if (openRouterApiKey == null || openRouterApiKey.trim().isEmpty()) {
                logger.warn("No OpenRouter API key configured, using default agent");
                return defaultAgent;
            }

            LLMProvider provider = new OpenRouterProvider(openRouterApiKey, m);
            return new SoulComfortAgent(provider);
        });
    }

    private String formatMemoryContext(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("相关记忆:\n");
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            SearchResult r = results.get(i);
            sb.append("- ").append(summarize(r.getSnippet(), 100)).append("\n");
        }
        return sb.toString();
    }

    private String summarize(String text, int maxLen) {
        if (text == null) return "";
        text = text.replace("\n", " ").trim();
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /**
     * 将对话内容索引到可检索记忆（OpenClaw风格）
     *
     * 策略：
     * 1. 每轮对话都写入每日记忆（Ephemeral），供BM25检索
     * 2. 提取重要信息（如姓名、关注点）写入长期记忆（Durable）
     */
    private void indexConversationToMemory(String sessionId, String userMessage, String response, UserMemory userMemory) {
        try {
            // 1. 将对话摘要写入每日记忆（Ephemeral）- 会被BM25索引
            String conversationEntry = String.format(
                "对话记录 [%s]:\n用户: %s\n店长: %s",
                sessionId.substring(0, Math.min(12, sessionId.length())),
                summarize(userMessage, 200),
                summarize(response, 200)
            );
            memoryManager.appendEphemeral(conversationEntry);
            logger.debug("Indexed conversation to ephemeral memory");

            // 2. 检测并提取用户姓名到长期记忆（Durable）
            String userName = extractUserName(userMessage);
            if (userName != null && userMemory.get("userName") == null) {
                userMemory.put("userName", userName);
                memoryManager.appendDurable("用户信息", "- 用户姓名: " + userName + "\n");
                logger.info("Saved user name to durable memory: {}", userName);
            }

            // 3. 记录情绪变化到每日记忆
            List<UserMemory.EmotionRecord> recentEmotions = userMemory.getRecentEmotions(1);
            if (!recentEmotions.isEmpty()) {
                String emotion = recentEmotions.get(0).getEmotion();
                memoryManager.appendEphemeral(String.format(
                    "情绪记录: 用户表现出 [%s] 情绪，上下文: %s",
                    emotion, summarize(userMessage, 50)
                ));
            }

            // 4. 提取重要话题到长期记忆
            List<String> topics = userMemory.getImportantTopics();
            if (!topics.isEmpty() && topics.size() <= 3) {
                String topicsStr = String.join(", ", topics);
                memoryManager.appendDurable("关注话题", "- " + topicsStr + "\n");
            }

        } catch (Exception e) {
            logger.warn("Failed to index conversation to memory", e);
        }
    }

    /**
     * 从用户消息中提取姓名
     */
    private String extractUserName(String message) {
        // 常见的自我介绍模式
        String[] patterns = {
            "我叫", "我是", "我的名字是", "我名字叫", "叫我", "称呼我", "我姓"
        };

        for (String pattern : patterns) {
            int idx = message.indexOf(pattern);
            if (idx >= 0) {
                String afterPattern = message.substring(idx + pattern.length()).trim();
                // 取第一个词（可能是名字）
                String[] parts = afterPattern.split("[，,。.！!？?\\s]+");
                if (parts.length > 0 && parts[0].length() >= 1 && parts[0].length() <= 10) {
                    return parts[0];
                }
            }
        }
        return null;
    }
}
