package com.lightweightai.web.agent;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.memory.ConversationMemory;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.memory.UserMemory;
import com.lightweightai.kernel.prompt.PromptContext;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.PromptRequest;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 心灵引导 Agent（OpenClaw 重构版）
 *
 * Prompt 组装完全委托给 PromptEngine：
 *   1. 激活 Skill（按触发词自动检测）
 *   2. 组装单条 System Message（base prompt + skill prompts + memory context）
 *   3. 追加会话历史（来自 MemoryProvider.getHistory）
 *
 * 三种构造模式：
 *   - OpenClaw 模式（推荐）：SoulComfortAgent(llmProvider, promptEngine, tools)
 *   - OpenClaw 无工具：SoulComfortAgent(llmProvider, promptEngine)
 *   - 兼容模式：SoulComfortAgent(llmProvider) / SoulComfortAgent(llmProvider, memory)
 */
public class SoulComfortAgent {

    private static final int MAX_TOOL_ITERATIONS = 5;

    private final LLMProvider llmProvider;
    private final PromptEngine promptEngine;   // OpenClaw 模式
    private final ConversationMemory memory;   // 兼容模式回退
    private final ReflectionService reflectionService;
    private final List<Tool> tools;
    private final ToolCallingLoop toolCallingLoop; // nullable, set when ToolRegistry is provided
    private AgentObserver observer;

    // 兼容模式的系统提示（PromptEngine 缺席时使用）
    private static final String FALLBACK_SYSTEM_PROMPT =
        "你是「心灵港湾」的引导者，一位温暖、富有同理心的倾听者。\n" +
        "\n" +
        "你的使命：\n" +
        "- 用心倾听每一个来访者的心声\n" +
        "- 提供温暖、包容、不评判的陪伴\n" +
        "- 帮助他们理清思路，找到内心的答案\n" +
        "- 给予希望和力量，而不是简单的建议\n" +
        "\n" +
        "你的风格：语气温和、真诚；善于提出启发性的问题；回应简洁而有力量（通常 2-4 句话）。\n" +
        "\n" +
        "重要原则：\n" +
        "1. 永远不要轻视对方的感受\n" +
        "2. 不要给出具体行动建议（除非对方明确请求）\n" +
        "3. 帮助对方自己找到答案，而不是替他们决定\n" +
        "4. 当对方情绪激动时，先接纳情绪，再探讨问题\n" +
        "5. 你拥有完整的对话记忆，请自然引用之前的对话内容 ⚓";

    // ==================== 构造器 ====================

    /**
     * OpenClaw 模式（推荐）：PromptEngine 负责 prompt 组装和记忆检索，带工具列表
     */
    public SoulComfortAgent(LLMProvider llmProvider, PromptEngine promptEngine, List<Tool> tools) {
        this(llmProvider, promptEngine, tools, null);
    }

    /**
     * OpenClaw 模式 + ToolCallingLoop（完整 Reactive 支持）
     *
     * 当提供 ToolCallingLoop 时，chatStreamReactive() 走 Reactive 管道，
     * 前端可收到完整的工具调用事件（TOOL_CALL_START / TOOL_PROGRESS / TOOL_RESULT 等）。
     */
    public SoulComfortAgent(LLMProvider llmProvider, PromptEngine promptEngine,
                            List<Tool> tools, ToolCallingLoop toolCallingLoop) {
        this.llmProvider = llmProvider;
        this.promptEngine = promptEngine;
        this.memory = null;
        this.tools = tools != null ? new ArrayList<>(tools) : new ArrayList<>();
        this.toolCallingLoop = toolCallingLoop;
        this.reflectionService = new ReflectionService(llmProvider);
    }

    /**
     * OpenClaw 模式（无工具）
     */
    public SoulComfortAgent(LLMProvider llmProvider, PromptEngine promptEngine) {
        this(llmProvider, promptEngine, List.of(), null);
    }

    /**
     * 兼容模式：仅用于测试，无文件记忆
     */
    public SoulComfortAgent(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
        this.promptEngine = null;
        this.memory = new ConversationMemory();
        this.tools = new ArrayList<>();
        this.toolCallingLoop = null;
        this.reflectionService = new ReflectionService(llmProvider);
    }

    /**
     * 兼容模式（外部传入 ConversationMemory）
     */
    public SoulComfortAgent(LLMProvider llmProvider, ConversationMemory memory) {
        this.llmProvider = llmProvider;
        this.promptEngine = null;
        this.memory = memory;
        this.tools = new ArrayList<>();
        this.toolCallingLoop = null;
        this.reflectionService = new ReflectionService(llmProvider);
    }

    // ==================== 可观测性 ====================

    public void setObserver(AgentObserver observer) { this.observer = observer; }
    public AgentObserver getObserver() { return observer; }

    // ==================== Chat ====================

    public String chat(String sessionId, String userMessage) {
        return chat(sessionId, userMessage, null);
    }

    /**
     * 同步对话（含工具调用循环）
     *
     * @param externalMemoryContext 兼容旧调用，OpenClaw 模式下忽略（PromptEngine 自行搜索）
     */
    public String chat(String sessionId, String userMessage, String externalMemoryContext) {
        if (observer != null) observer.onAgentStart(userMessage, sessionId);

        try {
            List<ConversationMessage> messages = buildMessages(sessionId, userMessage, externalMemoryContext);

            if (observer != null) observer.onLLMRequest(messages);

            List<Map<String, Object>> toolDefs = buildToolDefinitions();
            LLMOptions.Builder optBuilder = LLMOptions.builder()
                .maxTokens(500)
                .temperature(0.8);
            if (!toolDefs.isEmpty()) {
                optBuilder.toolDefinitions(toolDefs);
            }

            int iterations = 0;
            while (iterations < MAX_TOOL_ITERATIONS) {
                LLMResponse response = llmProvider.complete(messages, optBuilder.build());

                if (observer != null) observer.onLLMResponse(response);

                if (!response.hasToolCalls()) {
                    String text = response.getMessage().getTextContent();
                    saveAssistantMessage(sessionId, text);

                    if (observer != null) {
                        observer.onAgentComplete(AgentResponse.builder().text(text).build());
                    }
                    return text;
                }

                // Execute tools and append results as context for the next LLM call
                messages.add(executeTools(response.getToolCalls()));
                iterations++;
            }

            throw new RuntimeException("Max tool iterations (" + MAX_TOOL_ITERATIONS + ") exceeded");

        } catch (Exception e) {
            if (observer != null) observer.onError(e);
            throw e;
        }
    }

    public CompletableFuture<String> chatStream(String sessionId, String userMessage,
                                                 LLMProvider.StreamEventHandler handler) {
        return chatStream(sessionId, userMessage, null, handler);
    }

    /**
     * 流式对话（两阶段流式方案）
     *
     * 无工具时：直接 completeStream() — 文字立即开始流式输出。
     * 有工具时：
     *   Phase 1 — completeStream() 携带 tool defs；LLM 输出 tool_calls 时流很快结束（无文字）
     *   Phase 2 — 执行工具后再次 completeStream()，最终回答流式输出
     *
     * 与 probe-first 的区别：不做阻塞的同步 complete() 调用，流式体验更流畅。
     */
    public CompletableFuture<String> chatStream(String sessionId, String userMessage,
                                                 String externalMemoryContext,
                                                 LLMProvider.StreamEventHandler handler) {
        if (observer != null) observer.onAgentStart(userMessage, sessionId);

        List<ConversationMessage> messages = buildMessages(sessionId, userMessage, externalMemoryContext);
        if (observer != null) observer.onLLMRequest(messages);

        LLMOptions optionsFinal = LLMOptions.builder().maxTokens(500).temperature(0.8).build();

        if (tools.isEmpty()) {
            // 无工具 — 直接流式
            return llmProvider.completeStream(messages, optionsFinal, handler)
                .thenApply(r -> {
                    if (observer != null) {
                        observer.onLLMResponse(r);
                        observer.onAgentComplete(AgentResponse.builder()
                            .text(r.getMessage().getTextContent()).build());
                    }
                    String text = r.getMessage().getTextContent();
                    saveAssistantMessage(sessionId, text);
                    return text;
                });
        }

        // 有工具 — 两阶段流式
        LLMOptions optionsWithTools = LLMOptions.builder()
            .maxTokens(500).temperature(0.8)
            .toolDefinitions(buildToolDefinitions())
            .build();

        CompletableFuture<String> result = new CompletableFuture<>();

        // Phase 1: stream with tool definitions
        llmProvider.completeStream(messages, optionsWithTools, new LLMProvider.StreamEventHandler() {
            @Override
            public void onStart() {
                handler.onStart(); // 透传给外层 handler（触发一次即可）
            }

            @Override
            public void onTextDelta(String delta) {
                // LLM 直接用文字回复（未调用工具）— 正常透传
                handler.onTextDelta(delta);
            }

            @Override
            public void onComplete(LLMResponse response) {
                if (!response.hasToolCalls()) {
                    // 无工具调用，回答已完整流出
                    String text = response.getMessage().getTextContent();
                    saveAssistantMessage(sessionId, text);
                    if (observer != null) {
                        observer.onLLMResponse(response);
                        observer.onAgentComplete(AgentResponse.builder().text(text).build());
                    }
                    handler.onComplete(response);
                    result.complete(text);
                    return;
                }

                // 工具调用检测到 — 执行工具，再流式输出最终回答
                messages.add(executeTools(response.getToolCalls()));

                // Phase 2: stream final answer (no tools to prevent recursion)
                llmProvider.completeStream(messages, optionsFinal, new LLMProvider.StreamEventHandler() {
                    @Override
                    public void onStart() { /* 不重复触发 */ }

                    @Override
                    public void onTextDelta(String delta) {
                        handler.onTextDelta(delta);
                    }

                    @Override
                    public void onComplete(LLMResponse finalResponse) {
                        String text = finalResponse.getMessage().getTextContent();
                        saveAssistantMessage(sessionId, text);
                        if (observer != null) {
                            observer.onLLMResponse(finalResponse);
                            observer.onAgentComplete(AgentResponse.builder().text(text).build());
                        }
                        handler.onComplete(finalResponse);
                        result.complete(text);
                    }

                    @Override
                    public void onError(Throwable error) {
                        handler.onError(error);
                        result.completeExceptionally(error);
                    }
                }).exceptionally(e -> {
                    if (!result.isDone()) {
                        handler.onError(e);
                        result.completeExceptionally(e);
                    }
                    return null;
                });
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
                result.completeExceptionally(error);
            }
        }).exceptionally(e -> {
            if (!result.isDone()) {
                handler.onError(e);
                result.completeExceptionally(e);
            }
            return null;
        });

        return result;
    }

    // ==================== Reactive Streaming ====================

    /**
     * Reactive 流式对话 — 通过 ToolCallingLoop 实现完整的工具事件流
     *
     * 返回 Flux&lt;StreamEvent&gt; 包含：
     * - TEXT_DELTA: LLM 文本片段
     * - TOOL_CALL_START: LLM 决定调用工具
     * - TOOL_PROGRESS / TOOL_LOG: MCP 工具执行进度和日志
     * - TOOL_RESULT: 工具执行完成
     * - LLM_COMPLETE: 最终 LLM 响应
     *
     * 需要在构造时传入 ToolCallingLoop，否则回退到 callback-based 路径（仅 TEXT_DELTA）。
     */
    public Flux<StreamEvent> chatStreamReactive(String sessionId, String userMessage) {
        if (toolCallingLoop == null) {
            // 没有 ToolCallingLoop，回退到 callback 桥接（仅 TEXT_DELTA）
            return Flux.create(sink -> {
                chatStream(sessionId, userMessage, new LLMProvider.StreamEventHandler() {
                    @Override
                    public void onTextDelta(String delta) {
                        sink.next(StreamEvent.textDelta(delta));
                    }

                    @Override
                    public void onComplete(LLMResponse response) {
                        sink.next(StreamEvent.llmComplete(response));
                        sink.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        sink.error(error);
                    }
                });
            });
        }

        // 有 ToolCallingLoop — 走 Reactive 管道，包含完整工具事件
        List<ConversationMessage> messages = buildMessages(sessionId, userMessage, null);

        LLMOptions.Builder optBuilder = LLMOptions.builder()
            .maxTokens(500)
            .temperature(0.8);

        if (!tools.isEmpty()) {
            optBuilder.toolDefinitions(buildToolDefinitions());
        }

        StringBuilder fullText = new StringBuilder();

        return toolCallingLoop.executeWithToolsReactive(messages, optBuilder.build())
            .doOnNext(event -> {
                if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
                    fullText.append(event.getTextDelta());
                }
            })
            .doOnComplete(() -> {
                String text = fullText.toString();
                if (!text.isEmpty()) {
                    saveAssistantMessage(sessionId, text);
                }
            });
    }

    // ==================== Tool Helpers ====================

    /**
     * Build tool definitions in OpenAI function-calling format (used by OpenRouter).
     */
    private List<Map<String, Object>> buildToolDefinitions() {
        if (tools.isEmpty()) return List.of();
        return tools.stream().map(t -> {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", t.getName());
            function.put("description", t.getDescription());
            function.put("parameters", t.getSchema().toMap());

            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            def.put("function", function);
            return def;
        }).collect(Collectors.toList());
    }

    /**
     * Execute all tool calls, return a USER message containing all results.
     */
    private ConversationMessage executeTools(List<ToolCall> toolCalls) {
        StringBuilder sb = new StringBuilder();
        for (ToolCall tc : toolCalls) {
            Tool tool = tools.stream()
                .filter(t -> t.getName().equals(tc.getName()))
                .findFirst().orElse(null);

            String content;
            if (tool != null) {
                try {
                    content = tool.execute(tc.getArguments()).getContent();
                } catch (Exception e) {
                    content = "[Tool error: " + e.getMessage() + "]";
                }
            } else {
                content = "[Tool not found: " + tc.getName() + "]";
            }

            sb.append("## Tool result: ").append(tc.getName()).append("\n")
              .append(content).append("\n\n");
        }

        return ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(sb.toString().trim())
            .build();
    }

    // ==================== Prompt 组装 ====================

    /**
     * 统一入口：根据构造模式选择 PromptEngine 路径或兼容路径
     */
    private List<ConversationMessage> buildMessages(String sessionId, String userMessage,
                                                     String externalMemoryContext) {
        if (promptEngine != null) {
            return buildMessagesViaPromptEngine(sessionId, userMessage);
        } else {
            return buildMessagesFallback(sessionId, userMessage, externalMemoryContext);
        }
    }

    /**
     * OpenClaw 路径：委托 PromptEngine 完成 Skill 激活 + 记忆检索 + 历史加载
     *
     * 输出为标准的单条 system + 多轮对话 messages。
     */
    private List<ConversationMessage> buildMessagesViaPromptEngine(String sessionId, String userMessage) {
        MemoryProvider mp = promptEngine.getMemoryProvider();

        // 先把当前用户消息写入会话历史，PromptEngine.loadHistory() 会把它包含进来
        mp.addMessage(sessionId, Message.user(userMessage));

        PromptRequest request = PromptRequest.builder()
            .sessionId(sessionId)
            .userMessage(userMessage)
            .autoDetectSkills(true)
            .maxHistoryMessages(20)
            .maxMemoryResults(5)
            .build();

        PromptContext ctx = promptEngine.build(request);

        List<ConversationMessage> messages = new ArrayList<>();

        // 单条 System Message（base prompt + memory context）
        StringBuilder sys = new StringBuilder(ctx.getSystemPrompt());
        if (ctx.hasMemoryContext()) {
            sys.append("\n\n").append(ctx.getMemoryContext());
        }
        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent(sys.toString())
            .build());

        // 会话历史（已包含刚添加的用户消息）
        ctx.getHistoryMessages().stream()
            .filter(m -> !"system".equals(m.getRole()))
            .map(Message::toConversationMessage)
            .forEach(messages::add);

        return messages;
    }

    /**
     * 兼容路径（无 PromptEngine 时）：行为与旧版 buildContextWithMemory 相同
     */
    private List<ConversationMessage> buildMessagesFallback(String sessionId, String userMessage,
                                                              String externalMemoryContext) {
        ConversationMessage userMsg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(userMessage)
            .build();
        memory.addMessage(sessionId, userMsg);

        List<ConversationMessage> messages = new ArrayList<>();

        // System: 基础提示
        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent(FALLBACK_SYSTEM_PROMPT)
            .build());

        // System: 外部记忆上下文（来自旧版 SoulComfortChatService.formatMemoryContext）
        if (externalMemoryContext != null && !externalMemoryContext.isBlank()) {
            messages.add(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent("历史记忆中的相关信息（可参考但不必每次都提及）：\n" + externalMemoryContext)
                .build());
        }

        // System: 用户记忆（UserMemory）
        UserMemory userMemory = memory.getUserMemory(sessionId);
        userMemory.addEmotionRecord("温柔", userMessage);
        String memCtx = buildLegacyUserMemoryContext(userMemory);
        if (!memCtx.isBlank()) {
            messages.add(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent("关于这位访客的记忆：\n" + memCtx)
                .build());
        }

        // 会话历史
        memory.getRecentMessages(sessionId, 20).stream()
            .filter(m -> m.getRole() != ConversationMessage.MessageRole.SYSTEM)
            .forEach(messages::add);

        return messages;
    }

    private void saveAssistantMessage(String sessionId, String text) {
        if (promptEngine != null) {
            promptEngine.getMemoryProvider().addMessage(sessionId, Message.assistant(text));
        } else {
            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build();
            memory.addMessage(sessionId, msg);
        }
    }

    // ==================== 兼容辅助 ====================

    private String buildLegacyUserMemoryContext(UserMemory userMemory) {
        StringBuilder sb = new StringBuilder();
        String userName = userMemory.get("userName");
        if (userName != null) sb.append("- 称呼：").append(userName).append("\n");

        List<UserMemory.EmotionRecord> emotions = userMemory.getRecentEmotions(3);
        if (!emotions.isEmpty()) {
            sb.append("- 最近情绪：");
            for (var e : emotions) sb.append(e.getEmotion()).append("、");
            sb.setLength(sb.length() - 1);
            sb.append("\n");
        }

        List<String> topics = userMemory.getImportantTopics();
        if (!topics.isEmpty()) {
            sb.append("- 关注话题：").append(String.join("、", topics)).append("\n");
        }
        return sb.toString();
    }

    // ==================== 其他接口 ====================

    public String getSessionSummary(String sessionId) {
        List<ConversationMessage> history;
        if (promptEngine != null) {
            history = promptEngine.getMemoryProvider()
                .getHistory(sessionId, 10)
                .stream()
                .map(Message::toConversationMessage)
                .toList();
        } else {
            history = memory.getRecentMessages(sessionId, 10);
        }
        return history.isEmpty() ? "暂无对话历史" : reflectionService.extractKeyPoints(history);
    }

    public void clearSession(String sessionId) {
        if (promptEngine != null) {
            promptEngine.getMemoryProvider().clearSession(sessionId);
        } else {
            memory.clearHistory(sessionId);
        }
    }

    /** 兼容旧调用 */
    public UserMemory getUserMemory(String sessionId) {
        if (memory != null) return memory.getUserMemory(sessionId);
        return new UserMemory(); // 空对象，OpenClaw 模式下情绪/话题由 durable memory 管理
    }

    /** 兼容旧调用 */
    public ConversationMemory getMemory() {
        return memory != null ? memory : new ConversationMemory();
    }
}
