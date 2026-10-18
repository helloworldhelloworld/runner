package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ExecutionModelScope;
import com.lightweightai.kernel.core.ExecutionConfigurationScope;
import com.lightweightai.kernel.prompt.PromptSource;
import com.lightweightai.kernel.prompt.PromptSnapshot;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.memory.MessageSnapshot;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.Skill;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Agent 执行循环 (OpenClaw 风格)
 *
 * 核心流程：
 * 1. 检索记忆 (Memory Search)
 * 2. 构建上下文 (System Prompt + Memory + History)
 * 3. 调用 LLM
 * 4. 处理 Tool Calls (循环)
 * 5. 写入记忆
 * 6. 返回结果
 */
public class AgentLoop {

    private final LLMProvider llmProvider;
    private final MemoryProvider memoryProvider;
    private final PromptEngine promptEngine;
    private final ToolRegistry toolRegistry;
    private final String systemPrompt;
    private final int maxToolIterations;
    private final LLMOptions llmOptions;
    private final boolean autoToolDefinitions;
    private final PromptSource promptSource;
    private final String agentId;
    private final List<AgentObserver> observers;
    private final com.lightweightai.kernel.context.ContextCompactor contextCompactor;

    private AgentLoop(Builder builder) {
        this.llmProvider = Objects.requireNonNull(builder.llmProvider, "llmProvider required");
        this.memoryProvider = Objects.requireNonNull(builder.memoryProvider, "memoryProvider required");
        this.toolRegistry = builder.toolRegistry;
        this.promptSource = builder.promptSource;
        this.agentId = builder.agentId;
        this.systemPrompt = builder.systemPrompt != null ? builder.systemPrompt : "";
        this.maxToolIterations = builder.maxToolIterations;
        // 若调用方未显式设置 toolDefinitions，则从 toolRegistry 自动注入，
        // 使 Orchestrator 等非手动装配路径也能正确把工具告诉 LLM。
        // 调用方已显式提供时保持原样（调用方优先）。
        LLMOptions baseOptions = builder.llmOptions != null ? builder.llmOptions : LLMOptions.builder().build();
        boolean callerProvidedTools = baseOptions.getToolDefinitions() != null
                && !baseOptions.getToolDefinitions().isEmpty();
        this.autoToolDefinitions = !callerProvidedTools;
        if (!callerProvidedTools && this.toolRegistry != null
                && !this.toolRegistry.getToolDefinitions().isEmpty()) {
            this.llmOptions = LLMOptions.builder()
                    .from(baseOptions)
                    .toolDefinitions(this.toolRegistry.getToolDefinitions())
                    .build();
        } else {
            this.llmOptions = baseOptions;
        }
        this.observers = new ArrayList<>(builder.observers);
        this.contextCompactor = builder.contextCompactor;

        // 初始化 PromptEngine
        this.promptEngine = PromptEngine.builder()
            .memoryProvider(memoryProvider)
            .baseSystemPrompt(systemPrompt)
            .build();

        // 注册 Skills（将 Tools 转换为 Skill）
        for (Tool tool : toolRegistry.getEnabled()) {
            Skill skill = Skill.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .addTool(tool.getName(), tool.getDescription(), tool.getSchema().toMap())
                .build();
            promptEngine.registerSkill(skill);
        }
    }

    // ==================== 同步执行 ====================

    /**
     * 执行 Agent 循环（同步）
     *
     * 记忆检索和 Prompt 组装在 AgentLoop 完成，
     * Tool 循环委托给 ToolCallingLoop 统一处理。
     */
    public AgentResponse run(String input, String sessionId) {
        return run(input, sessionId, null);
    }

    public AgentResponse run(String input, String sessionId, ToolExecutionContext executionContext) {
        // Hook: onAgentStart
        notifyObservers(o -> o.onAgentStart(input, sessionId));

        try {
            RuntimeAssembly assembly = assemble(input, sessionId, executionContext);
            ToolExecutionContext runContext = assembly.context();
            MemoryProvider memoryProvider = runContext.getMemoryProvider();
            // 1. 检索相关记忆
            List<MemorySearchResult> memoryResults = memoryProvider.search(input);
            String memoryContext = formatMemoryContext(memoryResults);

            // 2. 保存用户消息
            memoryProvider.addMessage(sessionId, Message.user(input));

            // 3. 构建消息上下文
            List<ConversationMessage> rawMessages = buildMessages(sessionId, memoryContext, assembly.systemPrompt(), memoryProvider);
            final List<ConversationMessage> messages = contextCompactor != null
                    ? contextCompactor.compact(rawMessages) : rawMessages;

            // Hook: onLLMRequest
            notifyObservers(o -> o.onLLMRequest(messages));

            // 4. 委托 ToolCallingLoop 执行 LLM + Tool 循环
            ToolExecutor toolExecutor = new ToolExecutor(assembly.tools());
            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(assembly.provider())
                .toolExecutor(toolExecutor)
                .executionContext(runContext)
                .contextCompactor(contextCompactor)
                .maxIterations(maxToolIterations)
                .build();

            LLMResponse response = loop.executeWithTools(messages, assembly.options());
            String responseText = response.getMessage().getTextContent();

            // Hook: onLLMResponse
            if (!"budget_exhausted".equals(response.getStopReason())) {
                notifyObservers(o -> o.onLLMResponse(response));
            }

            // 5. 保存助手消息到记忆
            if (!"budget_exhausted".equals(response.getStopReason())) {
                memoryProvider.addMessage(sessionId, Message.assistant(responseText));
                writeConversationToMemory(input, responseText, memoryProvider);
            }

            AgentResponse agentResponse = AgentResponse.builder()
                .text(responseText)
                .stopReason(response.getStopReason())
                .usage(runContext.getUsageScope().snapshot())
                .build();

            // Hook: onAgentComplete
            notifyObservers(o -> o.onAgentComplete(agentResponse));

            return agentResponse;
        } catch (Exception e) {
            // Hook: onError
            notifyObservers(o -> o.onError(e));
            throw e;
        }
    }

    // ==================== 流式执行 ====================

    /**
     * 执行 Agent 循环（流式）
     */
    public CompletableFuture<AgentResponse> runStream(
            String input,
            String sessionId,
            Consumer<String> onDelta) {
        return runStream(input, sessionId, onDelta, null);
    }

    public CompletableFuture<AgentResponse> runStream(String input, String sessionId,
            Consumer<String> onDelta, ToolExecutionContext executionContext) {
        ToolExecutionContext context = executionContext != null ? executionContext : ToolExecutionContext.serverOnly();
        if (context.getUsageScope() == null) {
            context = context.withUsageScope(new com.lightweightai.kernel.context.ExecutionUsageScope(
                    llmOptions.getMaxBudgetTokens() == null ? 0 : llmOptions.getMaxBudgetTokens()));
        }
        var token = context.getCancellationToken() != null ? context.getCancellationToken()
                : new com.lightweightai.kernel.core.CancellationToken();
        var usage = context.getUsageScope();
        StringBuilder text = new StringBuilder();
        var stopReason = new java.util.concurrent.atomic.AtomicReference<>("end_turn");
        return runReactive(input, sessionId, token, context)
                .doOnNext(event -> {
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
                        text.append(event.getTextDelta());
                        onDelta.accept(event.getTextDelta());
                    } else if (event.getType() == StreamEvent.EventType.BUDGET_STOPPED) {
                        stopReason.set("budget_exhausted");
                    } else if (event.getType() == StreamEvent.EventType.LLM_COMPLETE && event.getResponse() != null) {
                        stopReason.set(event.getResponse().getStopReason());
                    }
                })
                .doOnCancel(token::cancel)
                .then(reactor.core.publisher.Mono.fromSupplier(() -> AgentResponse.builder()
                        .text(text.toString()).stopReason(stopReason.get()).usage(usage.snapshot()).build()))
                .toFuture();
    }

    // ==================== Reactive 流式执行 ====================

    /**
     * 执行 Agent 循环（Reactive 流式）
     *
     * 返回 Flux&lt;StreamEvent&gt;，包含 LLM 文本片段、工具进度、工具结果等全链路事件。
     * 需要 ToolCallingLoop 支持。
     */
    public Flux<StreamEvent> runReactive(String input, String sessionId) {
        return runReactive(input, sessionId, null);
    }

    /**
     * 执行 Agent 循环（Reactive，支持外部 CancellationToken）
     *
     * CancellationToken 会传递到 ToolCallingLoop，每轮迭代前检查。
     * 用于 InterruptibleRun 的打断接续机制。
     */
    public Flux<StreamEvent> runReactive(String input, String sessionId,
                                          com.lightweightai.kernel.core.CancellationToken cancellationToken) {
        return runReactive(input, sessionId, cancellationToken, null);
    }

    public Flux<StreamEvent> runReactive(String input, String sessionId,
            com.lightweightai.kernel.core.CancellationToken cancellationToken, ToolExecutionContext executionContext) {
        ToolExecutionContext cancellationContext = cancellationToken == null ? executionContext
                : (executionContext != null ? executionContext : ToolExecutionContext.serverOnly())
                        .withCancellationToken(cancellationToken);
        // Wrap in Flux.defer + subscribeOn(boundedElastic) so that blocking operations
        // (memory search, message building) don't run on the caller's thread.
        // Without this, calling from Vert.x WebSocket handler blocks the event loop.
        return Flux.<StreamEvent>defer(() -> {
            RuntimeAssembly assembly = assemble(input, sessionId, cancellationContext);
            ToolExecutionContext runContext = assembly.context();
            MemoryProvider memoryProvider = runContext.getMemoryProvider();
            // Hook: onAgentStart
            notifyObservers(o -> o.onAgentStart(input, sessionId));

            // 1. 检索记忆（阻塞操作，现在在 boundedElastic 线程上执行）
            List<MemorySearchResult> memoryResults = memoryProvider.search(input);
            String memoryContext = formatMemoryContext(memoryResults);

            // 2. 保存用户消息
            memoryProvider.addMessage(sessionId, Message.user(input));

            // 3. 构建消息上下文 + compaction
            List<ConversationMessage> rawMessages = buildMessages(sessionId, memoryContext, assembly.systemPrompt(), memoryProvider);
            final List<ConversationMessage> messages = contextCompactor != null
                    ? contextCompactor.compact(rawMessages) : rawMessages;

            // Hook: onLLMRequest
            notifyObservers(o -> o.onLLMRequest(messages));

            // 4. 构建 ToolCallingLoop 并执行
            ToolExecutor toolExecutor = new ToolExecutor(assembly.tools());
            ToolCallingLoop.Builder loopBuilder = ToolCallingLoop.builder()
                .provider(assembly.provider())
                .toolExecutor(toolExecutor)
                .executionContext(runContext)
                .contextCompactor(contextCompactor)
                .maxIterations(maxToolIterations)
                .observers(observers);
            if (cancellationToken != null) {
                loopBuilder.cancellationToken(cancellationToken);
            }
            ToolCallingLoop loop = loopBuilder.build();

            // 累积完整响应文本，流结束后保存到记忆
            StringBuilder fullResponse = new StringBuilder();
            java.util.concurrent.atomic.AtomicReference<String> stopReason = new java.util.concurrent.atomic.AtomicReference<>("end_turn");

            return loop.executeWithToolsReactive(messages, assembly.options())
                .doOnNext(event -> {
                    if (event.getType() == StreamEvent.EventType.BUDGET_STOPPED) stopReason.set("budget_exhausted");
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA) {
                        fullResponse.append(event.getTextDelta());
                    }
                    // Hook: onLLMResponse for LLM_COMPLETE events
                    if (event.getType() == StreamEvent.EventType.LLM_COMPLETE
                            && event.getResponse() != null) {
                        stopReason.set(event.getResponse().getStopReason());
                        notifyObservers(o -> o.onLLMResponse(event.getResponse()));
                    }
                })
                .doOnComplete(() -> {
                    // 保存助手消息到记忆（包裹 try-catch 防止异常转换 complete→error 信号）
                    try {
                        String responseText = fullResponse.toString();
                        if (!responseText.isEmpty()) {
                            memoryProvider.addMessage(sessionId, Message.assistant(responseText));
                        }
                        if (!responseText.isEmpty()) writeConversationToMemory(input, responseText, memoryProvider);

                        // Hook: onAgentComplete
                        AgentResponse agentResponse = AgentResponse.builder()
                            .text(responseText)
                            .stopReason(stopReason.get())
                            .usage(runContext.getUsageScope().snapshot())
                            .build();
                        notifyObservers(o -> o.onAgentComplete(agentResponse));
                    } catch (Exception e) {
                        // 记忆保存失败不应阻断流的正常完成
                    }
                })
                .doOnError(e -> notifyObservers(o -> o.onError(e)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private record RuntimeAssembly(LLMProvider provider, ToolRegistry tools, LLMOptions options,
                                   String systemPrompt, ToolExecutionContext context) {}

    private RuntimeAssembly assemble(String input, String sessionId, ToolExecutionContext context) {
        ToolExecutionContext runContext = bindModelContext(context)
                .withMemoryProvider(getExecutionMemory(context, sessionId));
        if (runContext.getConfigurationScope() == null) {
            runContext = runContext.withConfigurationScope(new ExecutionConfigurationScope());
        }
        if (runContext.getUsageScope() == null) {
            runContext = runContext.withUsageScope(new com.lightweightai.kernel.context.ExecutionUsageScope(
                    llmOptions.getMaxBudgetTokens() == null ? 0 : llmOptions.getMaxBudgetTokens()));
        }
        var scope = runContext.getConfigurationScope();
        ToolRegistry selectedTools = scope.tools(toolRegistry, runContext.getDeviceContext());
        LLMOptions selectedOptions = autoToolDefinitions
                ? LLMOptions.builder().from(llmOptions).toolDefinitions(selectedTools.getToolDefinitions()).build().snapshot()
                : llmOptions.snapshot();
        PromptSnapshot prompts = promptSource == null ? PromptSnapshot.empty() : scope.prompts(promptSource);
        String additions = prompts.render(input);
        String selectedPrompt = systemPrompt + (additions.isBlank() ? "" : "\n\n" + additions);
        LLMProvider selectedProvider = runContext.getModelScope().resolve(llmProvider);
        ExecutionAssembly description = new ExecutionAssembly(agentId, selectedPrompt, selectedProvider, selectedOptions,
                selectedTools, runContext.getDeviceContext(), prompts, input, maxToolIterations,
                runContext.getMemoryProvider().getClass().getName());
        notifyObservers(o -> o.onExecutionAssembly(sessionId, description));
        return new RuntimeAssembly(selectedProvider, selectedTools, selectedOptions, selectedPrompt, runContext);
    }

    private ToolExecutionContext bindModelContext(ToolExecutionContext context) {
        ToolExecutionContext runContext = context != null ? context : ToolExecutionContext.serverOnly();
        if (runContext.getModelScope() == null) {
            runContext = runContext.withModelScope(new ExecutionModelScope());
        }
        runContext.getModelScope().resolve(llmProvider);
        return runContext;
    }

    // ==================== Observer 通知 ====================

    private void notifyObservers(Consumer<AgentObserver> action) {
        for (AgentObserver observer : observers) {
            try {
                action.accept(observer);
            } catch (Exception e) {
                // Observer 异常不应中断主链路
            }
        }
    }

    // ==================== 辅助方法 ====================

    private List<ConversationMessage> buildMessages(String sessionId, String memoryContext, String executionPrompt, MemoryProvider memoryProvider) {
        List<ConversationMessage> messages = new ArrayList<>();

        // System prompt
        StringBuilder systemContent = new StringBuilder(executionPrompt);
        // 确定性注入长期记忆(durable)——不靠模糊搜索/embedding(mock embedding 语义召回基本失效)，
        // 只要写进 durable 就一定在每轮上文里，跨会话可靠记得用户(名字/喜好)。
        String durable = readDurableSafe(memoryProvider);
        if (!durable.isBlank()) {
            systemContent.append("\n\n长期记忆（关于用户，回答时务必参考）:\n").append(durable);
        }
        if (!memoryContext.isEmpty()) {
            systemContent.append("\n\n相关记忆:\n").append(memoryContext);
        }

        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent(systemContent.toString())
            .build());

        // 历史消息 — 使用不可变快照，与持久化路径解耦
        MessageSnapshot snapshot = MessageSnapshot.capture(memoryProvider, sessionId, 20);
        for (Message msg : snapshot.getMessages()) {
            messages.add(msg.toConversationMessage());
        }

        return messages;
    }

    /** 读 durable 长期记忆（容错 + 截断防过长），用于每轮确定性注入系统提示。 */
    private String readDurableSafe(MemoryProvider memoryProvider) {
        try {
            String d = memoryProvider.readDurable();
            if (d == null || d.isBlank()) {
                return "";
            }
            return d.length() > 2000 ? d.substring(0, 2000) : d;
        } catch (Exception e) {
            return "";
        }
    }

    private String formatMemoryContext(List<MemorySearchResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int count = Math.min(3, results.size());
        for (int i = 0; i < count; i++) {
            sb.append("- ").append(results.get(i).getSnippet(100)).append("\n");
        }
        return sb.toString();
    }

    public MemoryProvider getExecutionMemory(ToolExecutionContext context, String sessionId) {
        return memoryProvider.forScope(context == null ? null : context.getPrincipalId(), agentId).forSession(sessionId);
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public LLMOptions getLlmOptions() {
        return llmOptions;
    }

    private void writeConversationToMemory(String userInput, String response, MemoryProvider memoryProvider) {
        String summary = String.format("用户: %s\n助手: %s",
            truncate(userInput, 100),
            truncate(response, 100));
        memoryProvider.writeEphemeral(summary);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        text = text.replace("\n", " ").trim();
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LLMProvider llmProvider;
        private MemoryProvider memoryProvider;
        private ToolRegistry toolRegistry = new ToolRegistry();
        private com.lightweightai.kernel.context.ContextCompactor contextCompactor;
        private String systemPrompt;
        private int maxToolIterations = 10;
        private LLMOptions llmOptions = LLMOptions.builder().build();
        private PromptSource promptSource;
        private String agentId = "default";
        private List<AgentObserver> observers = new ArrayList<>();

        public Builder promptSource(PromptSource source) { this.promptSource = source; return this; }
        public Builder agentId(String agentId) { this.agentId = Objects.requireNonNull(agentId); return this; }

        public Builder llmProvider(LLMProvider provider) {
            this.llmProvider = provider;
            return this;
        }

        public Builder memoryProvider(MemoryProvider provider) {
            this.memoryProvider = provider;
            return this;
        }

        public Builder toolRegistry(ToolRegistry registry) {
            this.toolRegistry = registry;
            return this;
        }

        public Builder addTool(Tool tool) {
            this.toolRegistry.register(tool);
            return this;
        }

        public Builder tools(List<Tool> tools) {
            this.toolRegistry.registerAll(tools);
            return this;
        }

        public Builder systemPrompt(String prompt) {
            this.systemPrompt = prompt;
            return this;
        }

        public Builder maxToolIterations(int max) {
            this.maxToolIterations = max;
            return this;
        }

        public Builder llmOptions(LLMOptions options) {
            this.llmOptions = options;
            return this;
        }

        public Builder addObserver(AgentObserver observer) {
            this.observers.add(observer);
            return this;
        }

        public Builder contextCompactor(com.lightweightai.kernel.context.ContextCompactor compactor) {
            this.contextCompactor = compactor;
            return this;
        }

        public AgentLoop build() {
            return new AgentLoop(this);
        }
    }
}
