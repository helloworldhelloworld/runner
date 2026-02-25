package com.lightweightai.web.service;

import com.lightweightai.kernel.agent.SoulComfortAgent;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import com.lightweightai.web.tools.WebSearchTool;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.memory.UserMemory;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.file.SessionTranscript;
import com.lightweightai.kernel.memory.model.SearchResult;
import com.lightweightai.kernel.memory.model.TranscriptEntry;
import com.lightweightai.kernel.memory.queue.LaneQueueManager;
import com.lightweightai.kernel.memory.tools.MemoryToolkit;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.safety.ContentFilter;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.safety.SafetyResult;
import com.lightweightai.web.config.FileMemoryAdapter;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.observer.DebugAgentObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 心灵引导聊天服务 — OpenClaw 架构
 *
 * Prompt 组装和记忆检索完全委托给 PromptEngine：
 *   - Layer 1（Durable）和 Layer 2（BM25/Vector 搜索）由 PromptEngine.loadMemoryContext() 注入
 *   - Service 层只负责：Lane Queue 串行化、安全前置检查、响应后索引写入
 *
 * 三层记忆：
 *   Session  → FileMemoryAdapter.ConversationMemory（PromptEngine 维护会话历史）
 *   Ephemeral → FileMemoryManager（每轮对话写入，供 BM25 检索）
 *   Durable   → FileMemoryManager（用户信息持久化）
 */
@Service
public class SoulComfortChatService {

    private static final Logger logger = LoggerFactory.getLogger(SoulComfortChatService.class);

    private final SoulComfortAgent defaultAgent;
    private final Map<String, SoulComfortAgent> modelAgents;
    private final List<Tool> agentTools;

    // OpenClaw 核心组件
    private final PromptEngine promptEngine;
    private final FileMemoryManager memoryManager;
    private final LaneQueueManager laneManager;
    private final MemoryToolkit memoryToolkit;
    private final Map<String, SessionTranscript> activeSessions = new ConcurrentHashMap<>();

    // Safety layer (optional — injected if beans are present)
    @Autowired(required = false)
    private CrisisDetector crisisDetector;

    @Autowired(required = false)
    private ContentFilter contentFilter;

    @Value("${app.openrouter.api-key:}")
    private String openRouterApiKey;

    @Value("${app.soul.crisis-detection:true}")
    private boolean crisisDetectionEnabled;

    public SoulComfortChatService(
            LLMProvider llmProvider,
            PromptEngine promptEngine,
            FileMemoryManager memoryManager,
            LaneQueueManager laneManager,
            MemoryToolkit memoryToolkit,
            @org.springframework.lang.Nullable WebSearchTool webSearchTool
    ) {
        this.promptEngine = promptEngine;
        this.agentTools = webSearchTool != null ? List.of(webSearchTool) : List.of();
        this.defaultAgent = new SoulComfortAgent(llmProvider, promptEngine, this.agentTools);
        this.modelAgents = new ConcurrentHashMap<>();
        this.memoryManager = memoryManager;
        this.laneManager = laneManager;
        this.memoryToolkit = memoryToolkit;

        logger.info("SoulComfortChatService initialized with OpenClaw PromptEngine, tools={}",
            agentTools.stream().map(Tool::getName).collect(java.util.stream.Collectors.joining(", ")));
    }

    /**
     * Callback for streaming chat responses
     */
    public interface StreamCallback {
        void onChunk(StreamChunk chunk);
    }

    public static class StreamChunk {
        private final String delta;
        private final String emotion;

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

        return laneManager.submit(sessionId, () -> processChatInternal(request, sessionId))
            .join();
    }

    private ChatResponse processChatInternal(ChatRequest request, String sessionId) {
        DebugAgentObserver debugObserver = new DebugAgentObserver();
        boolean debugMode = request.isDebug();

        try {
            String userMessage = request.getMessage();
            String model = request.getModel();

            logger.info("Processing chat for session: {} with model: {}, debug: {}", sessionId, model, debugMode);

            // 0. 安全检查：危机检测（前置，直接返回，不进入 LLM）
            if (crisisDetectionEnabled && crisisDetector != null) {
                SafetyResult safetyResult = crisisDetector.check(userMessage);
                if (safetyResult.isCrisis()) {
                    logger.warn("Crisis detected in session: {}", sessionId);
                    String resourcesText = safetyResult.resources().stream()
                        .map(r -> r.name() + ": " + r.phone())
                        .collect(Collectors.joining("\n"));
                    ChatResponse crisisResponse = new ChatResponse();
                    crisisResponse.setResponse(
                        "我注意到你可能正在经历一些非常困难的情绪。你的生命很宝贵，有专业的人可以帮助你。\n\n" +
                        "请联系以下危机热线：\n" + resourcesText
                    );
                    crisisResponse.setSkillsApplied(List.of("crisis-safety"));
                    return crisisResponse;
                }
            }

            // 1. 获取或创建会话转录（用于持久化 JSONL，与 PromptEngine 的 ConversationMemory 并行）
            SessionTranscript transcript = getOrCreateSession(sessionId);
            transcript.append(TranscriptEntry.userMessage(userMessage));

            // 2. 获取对应模型的 Agent，设置观察者
            SoulComfortAgent agent = getAgentForModel(model);
            agent.setObserver(debugObserver);

            // 3. 调用 Agent（PromptEngine 在内部完成 Skill 激活 + 记忆检索 + Prompt 组装）
            String response = agent.chat(sessionId, userMessage);

            // 3.5 内容过滤：去除 AI 响应中的诊断性内容
            if (contentFilter != null) {
                response = contentFilter.filter(response);
            }

            // 4. 保存助手响应到会话转录（用于 JSONL 持久化）
            transcript.append(TranscriptEntry.assistantMessage(response));

            // 5. 将对话写入可检索的 Ephemeral 记忆（BM25 索引）
            indexConversationToMemory(sessionId, userMessage, response);

            // 6. 构建响应
            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setResponse(response);
            chatResponse.setSkillsApplied(List.of("soul-comfort", "openclaw-memory"));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("mode", "soul-comfort");
            metadata.put("hasMemory", true);
            metadata.put("memorySystem", "openclaw-promptengine");
            if (model != null && !model.trim().isEmpty()) {
                metadata.put("model", model);
            }
            chatResponse.setMetadata(metadata);

            if (debugMode) {
                chatResponse.setDebug(debugObserver.getDebugInfo());
            }

            logger.info("Chat completed - session: {}", sessionId);
            return chatResponse;

        } catch (Exception e) {
            logger.error("Chat failed", e);
            ChatResponse errorResponse = ChatResponse.error(e.getMessage());
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
        return chat(message, sessionId, callback, null);
    }

    /**
     * 处理聊天请求（流式 + 调试模式，通过 Lane Queue）
     *
     * 传入 debugObserver 时，streaming 结束后 observer 已收集好请求/响应信息，
     * 调用方可直接读取 debugObserver.getDebugInfo()。
     */
    public CompletableFuture<String> chat(String message, String sessionId,
                                           StreamCallback callback, DebugAgentObserver debugObserver) {
        String actualSessionId = sessionId != null ? sessionId : "default";

        return laneManager.submitAsync(actualSessionId, () -> {
            if (debugObserver != null) {
                defaultAgent.setObserver(debugObserver);
            }
            return processChatStreamInternal(message, actualSessionId, callback);
        });
    }

    private CompletableFuture<String> processChatStreamInternal(String message, String sessionId, StreamCallback callback) {
        logger.info("Processing streaming chat for session: {}", sessionId);

        SessionTranscript transcript = getOrCreateSession(sessionId);
        transcript.append(TranscriptEntry.userMessage(message));

        SoulComfortAgent agent = defaultAgent;
        StringBuilder fullResponse = new StringBuilder();

        // Agent 内部通过 PromptEngine 完成 Skill 激活 + 记忆检索 + Prompt 组装
        return agent.chatStream(sessionId, message, new LLMProvider.StreamEventHandler() {
            @Override
            public void onTextDelta(String delta) {
                fullResponse.append(delta);
                callback.onChunk(new StreamChunk(delta, "温柔"));
            }

            @Override
            public void onComplete(com.lightweightai.kernel.llm.LLMResponse response) {
                String responseText = fullResponse.toString();

                // 内容过滤
                String filtered = contentFilter != null ? contentFilter.filter(responseText) : responseText;

                transcript.append(TranscriptEntry.assistantMessage(filtered));
                indexConversationToMemory(sessionId, message, filtered);

                // 不再用空 delta 发结束信号，由调用方通过 CompletableFuture 处理
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
     * 搜索记忆（供 API 调用）
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
     * 获取会话摘要
     */
    public Map<String, Object> getSessionSummary(String sessionId) {
        Map<String, Object> summary = new HashMap<>();

        try {
            summary.put("summary", defaultAgent.getSessionSummary(sessionId));

            var stats = memoryManager.getIndexStats();
            summary.put("memoryStats", Map.of(
                "bm25Chunks", stats.bm25ChunkCount(),
                "vectorChunks", stats.vectorChunkCount(),
                "embeddingDimensions", stats.embeddingDimensions()
            ));

            SessionTranscript transcript = activeSessions.get(sessionId);
            if (transcript != null) {
                summary.put("transcriptEntries", transcript.getEntryCount());
                summary.put("transcriptFile", transcript.getSessionFile().toString());
            }

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

            // 从 JSONL 转录恢复历史到 PromptEngine 的 MemoryProvider
            restoreConversationHistory(sessionId, transcript);

            return transcript;
        });
    }

    /**
     * 从 JSONL 转录恢复对话历史到 PromptEngine 的 MemoryProvider（ConversationMemory）
     */
    private void restoreConversationHistory(String sessionId, SessionTranscript transcript) {
        List<TranscriptEntry> history = transcript.readAll();
        if (history.isEmpty()) {
            return;
        }

        logger.info("Restoring {} messages for session: {}", history.size(), sessionId);

        for (TranscriptEntry entry : history) {
            if ("user".equals(entry.getRole())) {
                promptEngine.getMemoryProvider().addMessage(sessionId, Message.user(entry.getContent()));
            } else if ("assistant".equals(entry.getRole())) {
                promptEngine.getMemoryProvider().addMessage(sessionId, Message.assistant(entry.getContent()));
            }
        }
    }

    /**
     * 获取对应模型的 Agent（所有 agent 共享同一 PromptEngine）
     */
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
            return new SoulComfortAgent(provider, promptEngine, agentTools);
        });
    }

    /**
     * 将对话写入 Ephemeral 记忆（FileMemoryManager，供 BM25 检索）
     *
     * 每轮对话写入摘要，用于下次会话的相关性搜索（Layer 2）。
     * 提取用户姓名写入 Durable 记忆（Layer 1，由 PromptEngine 始终注入）。
     */
    private void indexConversationToMemory(String sessionId, String userMessage, String response) {
        try {
            // 写入 Ephemeral（BM25 可检索）
            String conversationEntry = String.format(
                "对话记录 [%s]:\n用户: %s\n助手: %s",
                sessionId.substring(0, Math.min(12, sessionId.length())),
                truncate(userMessage, 200),
                truncate(response, 200)
            );
            memoryManager.appendEphemeral(conversationEntry);

            // 提取用户姓名写入 Durable（仅首次发现）
            String userName = extractUserName(userMessage);
            if (userName != null) {
                memoryManager.appendDurable("用户信息", "- 用户姓名: " + userName + "\n");
                logger.info("Saved user name to durable memory: {}", userName);
            }

        } catch (Exception e) {
            logger.warn("Failed to index conversation to memory", e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        text = text.replace("\n", " ").trim();
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String extractUserName(String message) {
        String[] patterns = { "我叫", "我是", "我的名字是", "我名字叫", "叫我", "称呼我", "我姓" };

        for (String pattern : patterns) {
            int idx = message.indexOf(pattern);
            if (idx >= 0) {
                String afterPattern = message.substring(idx + pattern.length()).trim();
                String[] parts = afterPattern.split("[，,。.！!？?\\s]+");
                if (parts.length > 0 && parts[0].length() >= 1 && parts[0].length() <= 10) {
                    return parts[0];
                }
            }
        }
        return null;
    }
}
