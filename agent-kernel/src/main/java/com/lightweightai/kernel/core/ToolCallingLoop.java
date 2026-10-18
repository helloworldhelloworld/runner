package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.TraceContext;
import com.lightweightai.kernel.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.Disposables;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handles the tool calling loop for LLM conversations.
 * When an LLM wants to use tools, this class:
 * 1. Executes the tool calls
 * 2. Sends results back to the LLM
 * 3. Repeats until the LLM provides a final response
 */
public class ToolCallingLoop {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallingLoop.class);

    private final LLMProvider provider;
    private final ToolExecutor toolExecutor;
    private final int maxIterations;
    private final ToolExecutionContext executionContext;
    private final Tracer tracer;
    private final CancellationToken cancellationToken;
    private final com.lightweightai.kernel.context.CostTracker costTracker;
    private java.util.List<com.lightweightai.kernel.agent.AgentObserver> observers;
    private com.lightweightai.kernel.context.ContextCompactor contextCompactor;

    /**
     * Create a new ToolCallingLoop
     *
     * @param provider     LLM provider to use
     * @param toolExecutor Tool executor for running tools
     * @param maxIterations Maximum number of tool calling iterations (prevents infinite loops)
     */
    public ToolCallingLoop(LLMProvider provider, ToolExecutor toolExecutor, int maxIterations) {
        this(provider, toolExecutor, maxIterations, null, null);
    }

    public ToolCallingLoop(LLMProvider provider, ToolExecutor toolExecutor, int maxIterations,
                           ToolExecutionContext executionContext) {
        this(provider, toolExecutor, maxIterations, executionContext, null);
    }

    public ToolCallingLoop(LLMProvider provider, ToolExecutor toolExecutor, int maxIterations,
                           ToolExecutionContext executionContext, Tracer tracer) {
        this(provider, toolExecutor, maxIterations, executionContext, tracer, null);
    }

    public ToolCallingLoop(LLMProvider provider, ToolExecutor toolExecutor, int maxIterations,
                           ToolExecutionContext executionContext, Tracer tracer,
                           CancellationToken cancellationToken) {
        this(provider, toolExecutor, maxIterations, executionContext, tracer, cancellationToken, null);
    }

    public ToolCallingLoop(LLMProvider provider, ToolExecutor toolExecutor, int maxIterations,
                           ToolExecutionContext executionContext, Tracer tracer,
                           CancellationToken cancellationToken,
                           com.lightweightai.kernel.context.CostTracker costTracker) {
        if (provider == null) {
            throw new IllegalArgumentException("LLM provider cannot be null");
        }
        if (toolExecutor == null) {
            throw new IllegalArgumentException("Tool executor cannot be null");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("Max iterations must be at least 1");
        }

        this.provider = provider;
        this.toolExecutor = toolExecutor;
        this.maxIterations = maxIterations;
        this.executionContext = executionContext;
        this.tracer = tracer != null ? tracer : Tracer.NOOP;
        this.cancellationToken = cancellationToken;
        this.costTracker = costTracker;
        this.observers = java.util.List.of();
    }

    /**
     * Execute a conversation with tool calling support
     *
     * @param messages     Initial conversation messages
     * @param options      LLM options (including tools definition)
     * @return Final LLM response after tool calling loop completes
     */
    public LLMResponse executeWithTools(List<ConversationMessage> messages, LLMOptions options) {
        ExecutionBinding binding = bindExecution(options);
        List<ConversationMessage> conversation = new ArrayList<>(messages);
        int iteration = 0;

        while (iteration < maxIterations) {
            // 打断检查
            if (binding.context().getCancellationToken().isCancelled()) {
                logger.debug("ToolCallingLoop cancelled at sync iteration {}", iteration);
                return LLMResponse.builder()
                        .message(ConversationMessage.builder()
                                .role(MessageRole.ASSISTANT)
                                .textContent("[cancelled]")
                                .build())
                        .stopReason("cancelled")
                        .build();
            }

            // Call LLM
            compactConversation(conversation);
            LLMResponse response;
            try { response = binding.provider().complete(conversation, options); }
            catch (com.lightweightai.kernel.context.BudgetStoppedException stopped) { return budgetResponse(); }

            // Check if LLM wants to call tools
            if (!response.hasToolCalls()) {
                // No tool calls, we're done
                return response;
            }

            // Execute all tool calls (with client-side routing if context present)
            List<ToolCall> toolCalls = response.getToolCalls();
            List<ToolResult> toolResults = toolExecutor.executeToolCalls(toolCalls, binding.context());

            // Add assistant's response (with tool calls) to conversation
            conversation.add(enrichWithToolCalls(response));

            // Add tool results as user messages to conversation
            for (ToolResult toolResult : toolResults) {
                appendToolResultMessages(conversation, toolResult);
            }

            iteration++;
        }

        throw new RuntimeException(
            "Tool calling loop exceeded maximum iterations: " + maxIterations +
            ". This may indicate an infinite loop or the LLM is stuck calling tools."
        );
    }

    /**
     * Execute a conversation with tool calling support (Async Non-blocking version)
     *
     * This method uses CompletableFuture composition to avoid blocking threads.
     * Even though tasks have dependencies (LLM -> Tool -> LLM), this uses
     * an async reactive model with thenCompose for chaining dependent operations.
     *
     * @param messages     Initial conversation messages
     * @param options      LLM options (including tools definition)
     * @return CompletableFuture of the final LLM response
     */
    public CompletableFuture<LLMResponse> executeWithToolsAsync(
        List<ConversationMessage> messages,
        LLMOptions options
    ) {
        ExecutionBinding binding = bindExecution(options);
        CompletableFuture<LLMResponse> result = executeWithToolsAsync(new ArrayList<>(messages), options, 0, binding);
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) binding.context().getCancellationToken().cancel();
        });
        return result;
    }

    /**
     * Internal async recursive method with iteration tracking
     */
    private CompletableFuture<LLMResponse> executeWithToolsAsync(
        List<ConversationMessage> conversation,
        LLMOptions options,
        int iteration,
        ExecutionBinding binding
    ) {
        // Check iteration limit
        if (iteration >= maxIterations) {
            return CompletableFuture.failedFuture(
                new RuntimeException(
                    "Tool calling loop exceeded maximum iterations: " + maxIterations +
                    ". This may indicate an infinite loop or the LLM is stuck calling tools."
                )
            );
        }

        if (binding.context().getCancellationToken().isCancelled()) {
            return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Execution cancelled"));
        }

        // Step 1: Call LLM asynchronously (non-blocking)
        compactConversation(conversation);
        return binding.provider().completeAsync(conversation, options)
            .thenCompose(response -> {
                // Step 2: Check if LLM wants to use tools
                if (!response.hasToolCalls() || response.getToolCalls().isEmpty()) {
                    // No tools needed, return final response
                    return CompletableFuture.completedFuture(response);
                }

                // Step 3: Execute tools asynchronously (with client-side routing if context present)
                List<ToolCall> toolCalls = response.getToolCalls();
                CompletableFuture<List<ToolResult>> toolFuture =
                    toolExecutor.executeToolCallsAsync(toolCalls, binding.context());
                return toolFuture
                    .thenCompose(toolResults -> {
                        // Step 4: Add assistant's response and tool results to conversation
                        conversation.add(enrichWithToolCalls(response));

                        for (ToolResult toolResult : toolResults) {
                            appendToolResultMessages(conversation, toolResult);
                        }

                        // Step 5: Recursively continue the conversation (async)
                        // This is the key: we chain dependent async operations
                        // without blocking any thread
                        return executeWithToolsAsync(conversation, options, iteration + 1, binding);
                    });
            })
            .exceptionally(error -> {
                Throwable cause = error;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof com.lightweightai.kernel.context.BudgetStoppedException) return budgetResponse();
                throw new RuntimeException("Tool calling loop failed: " + error.getMessage(), error);
            });
    }

    // ==================== Reactive 流式执行 ====================

    /**
     * Reactive 流式执行，返回统一的 Flux&lt;StreamEvent&gt;
     *
     * 整个 LLM + 工具调用循环以 Flux 形式输出：
     * - TEXT_DELTA: LLM 文本片段
     * - TOOL_CALL_START: LLM 决定调用工具
     * - TOOL_PROGRESS / TOOL_LOG: 工具执行进度（MCP ProgressNotification / LoggingNotification）
     * - TOOL_RESULT: 工具执行完成
     * - LLM_COMPLETE: LLM 最终响应（无更多工具调用）
     */
    public Flux<StreamEvent> executeWithToolsReactive(
            List<ConversationMessage> messages, LLMOptions options) {
        return Flux.defer(() -> executeReactiveLoop(new ArrayList<>(messages), options, 0, bindExecution(options)))
                .onErrorResume(com.lightweightai.kernel.context.BudgetStoppedException.class,
                        stopped -> Flux.just(StreamEvent.budgetStopped(stopped.getUsage())));
    }

    private Flux<StreamEvent> executeReactiveLoop(
            List<ConversationMessage> conversation,
            LLMOptions options,
            int iteration,
            ExecutionBinding binding) {

        // 打断检查：CancellationToken 触发后优雅退出
        if (binding.context().getCancellationToken().isCancelled()) {
            logger.debug("ToolCallingLoop cancelled at iteration {}", iteration);
            return Flux.empty();
        }

        // 预算检查：超预算时优雅退出
        if (costTracker != null && costTracker.isOverBudget()) {
            logger.warn("ToolCallingLoop over budget at iteration {} (consumed={}/{})",
                    iteration, costTracker.getTotalConsumed(), costTracker.getMaxBudgetTokens());
            return Flux.empty();
        }

        if (iteration >= maxIterations) {
            return Flux.error(new RuntimeException(
                "Tool calling loop exceeded maximum iterations: " + maxIterations));
        }

        return Flux.deferContextual(ctx -> {
            compactConversation(conversation);
            // 从 Reactor Context 读取父 span，创建迭代子 span
            SpanContext parentSpan = TraceContext.current(ctx);
            SpanContext iterationSpan = parentSpan != null
                ? parentSpan.createChild("tcl.iteration." + iteration)
                : SpanContext.createRoot("tcl.iteration." + iteration);
            iterationSpan.setAttribute("iteration", iteration);
            iterationSpan.setAttribute("messagesCount", conversation.size());

            // Step 1: LLM 流式调用 — 收集所有事件
            List<StreamEvent> accumulated = new ArrayList<>();

            // LLM 流子 span
            SpanContext llmSpan = iterationSpan.createChild("llm.stream");

            Flux<StreamEvent> providerStream = binding.provider().completeStreamReactive(conversation, options);
            if (binding.context().getResourceRegistrar() != null) {
                providerStream = providerStream.doOnSubscribe(subscription ->
                        binding.context().getResourceRegistrar().accept(
                                (Disposable) subscription::cancel));
            }
            return providerStream
                .doOnNext(accumulated::add)
                .doOnComplete(() -> {
                    llmSpan.setAttribute("eventCount", accumulated.size());
                    tracer.endSpan(llmSpan);
                })
                .doOnError(e -> tracer.endSpanWithError(llmSpan, e))
                .concatWith(Flux.defer(() -> {
                    // Step 2: 从收集的事件中找到 LLM_COMPLETE
                    StreamEvent completeEvent = accumulated.stream()
                        .filter(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE)
                        .findFirst()
                        .orElse(null);

                    boolean hasToolCalls = completeEvent != null
                        && completeEvent.getResponse() != null
                        && completeEvent.getResponse().hasToolCalls();
                    iterationSpan.setAttribute("hasToolCalls", hasToolCalls);

                    if (completeEvent == null || !hasToolCalls) {
                        tracer.endSpan(iterationSpan);
                        return Flux.empty();
                    }

                    // Step 3: LLM 要调用工具
                    LLMResponse response = completeEvent.getResponse();
                    List<com.lightweightai.kernel.llm.ToolCall> toolCalls = response.getToolCalls();
                    iterationSpan.setAttribute("toolCallCount", toolCalls.size());
                    iterationSpan.setAttribute("toolNames",
                        toolCalls.stream().map(ToolCall::getName).toList());
                    conversation.add(enrichWithToolCalls(response));

                    // 本轮 call id → 原始 args 的映射,用于把结构化参数透传到 PostToolUse
                    java.util.Map<String, Map<String, Object>> argsByCallId = new java.util.concurrent.ConcurrentHashMap<>();
                    for (ToolCall tc : toolCalls) {
                        if (tc.getId() != null) {
                            argsByCallId.put(tc.getId(),
                                    tc.getArguments() != null ? tc.getArguments() : Map.of());
                        }
                    }

                    // Step 3a: 发出 TOOL_CALL_START 事件 + PreToolUse hooks
                    Flux<StreamEvent> toolStartEvents = Flux.fromIterable(toolCalls)
                        .doOnNext(tc -> firePreToolUse(tc.getName(), tc.getArguments()))
                        .map(StreamEvent::toolCallStart);

                    // One subscription forwards events and collects final results.
                    // Cancellation must reach pending tools; cache() would retain their subscription.
                    List<ToolResult> toolResults = new ArrayList<>();
                    Flux<ToolResultChunk> toolExec = toolExecutor
                        .executeToolCallsReactive(toolCalls, binding.context())
                        .doOnNext(chunk -> {
                            // PostToolUse hook on COMPLETE/ERROR（按 callId 查回 args）
                            Map<String, Object> args = argsByCallId.getOrDefault(
                                    chunk.getToolCallId(), Map.of());
                            if (chunk.getType() == ToolResultChunk.ChunkType.COMPLETE && chunk.getResult() != null) {
                                firePostToolUse(chunk.getToolName(), args, chunk.getResult());
                                toolResults.add(chunk.getResult().withToolUseId(chunk.getToolCallId()));
                            } else if (chunk.getType() == ToolResultChunk.ChunkType.ERROR) {
                                firePostToolUse(chunk.getToolName(), args,
                                        ToolResult.error(chunk.getMessage()));
                                toolResults.add(ToolResult.error(chunk.getToolCallId(), chunk.getMessage()));
                            }
                        });

                    // 流式发出 TOOL_PROGRESS / TOOL_LOG / TOOL_RESULT 事件
                    Flux<StreamEvent> toolExecEvents = toolExec
                        .map(chunk -> switch (chunk.getType()) {
                            case PROGRESS -> StreamEvent.toolProgress(chunk);
                            case LOG -> StreamEvent.toolLog(chunk);
                            case COMPLETE -> StreamEvent.toolResult(chunk);
                            case ERROR -> StreamEvent.toolError(chunk);
                        });

                    // Step 3c: 收集工具结果，加入对话，递归下一轮
                    Flux<StreamEvent> nextRound = Flux.defer(() -> {
                        for (ToolResult result : toolResults) {
                            appendToolResultMessages(conversation, result);
                        }
                        tracer.endSpan(iterationSpan);
                        return executeReactiveLoop(conversation, options, iteration + 1, binding);
                    });

                    return Flux.concat(toolStartEvents, toolExecEvents, nextRound);
                }));
        });
    }

    private record ExecutionBinding(LLMProvider provider, ToolExecutionContext context) {}

    private void compactConversation(List<ConversationMessage> conversation) {
        if (contextCompactor == null) return;
        List<ConversationMessage> compacted = new ArrayList<>(contextCompactor.compact(conversation));
        conversation.clear();
        conversation.addAll(compacted);
    }

    private ExecutionBinding bindExecution(LLMOptions options) {
        ToolExecutionContext context = executionContext != null ? executionContext : ToolExecutionContext.serverOnly();
        if (context.getModelScope() == null) context = context.withModelScope(new ExecutionModelScope());
        if (cancellationToken != null) context = context.withCancellationToken(cancellationToken);
        if (context.getCancellationToken() == null) context = context.withCancellationToken(new CancellationToken());
        if (context.getUsageScope() == null) {
            int budget = options != null && options.getMaxBudgetTokens() != null ? options.getMaxBudgetTokens()
                    : costTracker != null ? costTracker.getMaxBudgetTokens() : 0;
            context = context.withUsageScope(new com.lightweightai.kernel.context.ExecutionUsageScope(budget, costTracker));
        }
        LLMProvider selected = context.getModelScope().resolve(provider);
        return new ExecutionBinding(com.lightweightai.kernel.llm.UsageTrackingLLMProvider.bind(selected,
                context.getUsageScope(), context.getCancellationToken()), context);
    }

    private static LLMResponse budgetResponse() {
        return LLMResponse.builder().message(ConversationMessage.builder()
                .role(MessageRole.ASSISTANT).textContent("").build()).stopReason("budget_exhausted").build();
    }

    /**
     * Enrich assistant message with tool call metadata so that providers
     * can serialize tool_calls in the conversation history.
     */
    private ConversationMessage enrichWithToolCalls(LLMResponse response) {
        ConversationMessage original = response.getMessage();
        if (!response.hasToolCalls()) {
            return original;
        }
        return ConversationMessage.builder()
            .role(original.getRole())
            .content(original.getContent())
            .metadata(original.getMetadata())
            .addMetadata("tool_calls", response.getToolCalls())
            .build();
    }

    /**
     * 把一个工具结果回灌进对话。
     *
     * 先加 {@link MessageRole#TOOL} 文本消息（既有 tool_result 线格式不变）；
     * 若结果带图像帧（视觉回灌链，ADR-010），再追加一条 {@link MessageRole#USER}
     * 多模态消息承载帧，让下一轮 LLM 请求体出现 image block。
     *
     * 为何另起 USER 消息而非塞进 TOOL 消息：OpenAI/OpenRouter 的 {@code role:"tool"}
     * 消息不支持图像（provider 走 tool 分支只发文本），独立 USER 消息是对 Claude /
     * OpenRouter 都正确的单一路径，且命中两者已验证的 buildContentBlocks 序列化。
     */
    private void appendToolResultMessages(List<ConversationMessage> conversation, ToolResult toolResult) {
        conversation.add(createToolResultMessage(toolResult));
        if (toolResult.hasImages()) {
            conversation.add(createImageFeedbackMessage(toolResult));
        }
    }

    /**
     * 构造承载工具帧的 USER 多模态消息（caption + image blocks）。
     */
    private ConversationMessage createImageFeedbackMessage(ToolResult toolResult) {
        ConversationMessage.Builder builder = ConversationMessage.builder()
            .role(MessageRole.USER)
            .textContent("(以下为上一步工具返回的图像)")
            .addMetadata("tool_use_id", toolResult.getToolUseId())
            .addMetadata("image_feedback", true);
        for (com.lightweightai.kernel.llm.ImageContent image : toolResult.getImages()) {
            builder.addContent(image);
        }
        return builder.build();
    }

    /**
     * Create a conversation message from a tool result
     *
     * @param toolResult The tool execution result
     * @return ConversationMessage with the tool result
     */
    private ConversationMessage createToolResultMessage(ToolResult toolResult) {
        // Convert tool result to a user message format
        // The exact format depends on the LLM provider's requirements
        // For now, create a simple message with the tool result content

        return ConversationMessage.builder()
            .role(MessageRole.TOOL)
            .textContent(toolResult.getContent())
            .metadata(Map.of(
                "tool_use_id", toolResult.getToolUseId(),
                "is_error", toolResult.isError()
            ))
            .build();
    }

    /**
     * Get the maximum number of iterations
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * Builder for ToolCallingLoop
     */
    public static class Builder {
        private LLMProvider provider;
        private ToolExecutor toolExecutor;
        private int maxIterations = 10;
        private ToolExecutionContext executionContext;
        private Tracer tracer;
        private CancellationToken cancellationToken;
        private com.lightweightai.kernel.context.CostTracker costTracker;
        private com.lightweightai.kernel.context.ContextCompactor contextCompactor;
        private java.util.List<com.lightweightai.kernel.agent.AgentObserver> observers = new java.util.ArrayList<>();

        public Builder provider(LLMProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder toolExecutor(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        /**
         * 设置工具执行上下文（用于客户端工具路由）
         */
        public Builder executionContext(ToolExecutionContext context) {
            this.executionContext = context;
            return this;
        }

        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        /**
         * 设置取消信号，ToolCallingLoop 每轮迭代前检查
         */
        public Builder cancellationToken(CancellationToken cancellationToken) {
            this.cancellationToken = cancellationToken;
            return this;
        }

        /**
         * 设置成本追踪器，每轮迭代前检查预算
         */
        public Builder costTracker(com.lightweightai.kernel.context.CostTracker costTracker) {
            this.costTracker = costTracker;
            return this;
        }

        public Builder observers(java.util.List<com.lightweightai.kernel.agent.AgentObserver> observers) {
            this.observers = observers != null ? observers : java.util.List.of();
            return this;
        }

        public Builder contextCompactor(com.lightweightai.kernel.context.ContextCompactor compactor) {
            this.contextCompactor = compactor;
            return this;
        }

        public ToolCallingLoop build() {
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, maxIterations,
                    executionContext, tracer, cancellationToken, costTracker);
            loop.observers = observers != null ? java.util.List.copyOf(observers) : java.util.List.of();
            loop.contextCompactor = contextCompactor;
            return loop;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private void firePreToolUse(String toolName, Map<String, Object> args) {
        // 结构化日志 —— 调用链上能看到"调了什么工具、传了什么字段"，值不入日志（PII 防护）
        if (logger.isInfoEnabled()) {
            logger.info("tool.execute.start: name={}, argKeys={}",
                    toolName, args != null ? args.keySet() : java.util.Set.of());
        }
        for (var observer : observers) {
            try { observer.onPreToolUse(toolName, args); } catch (Exception ignored) {}
        }
    }

    private void firePostToolUse(String toolName, Map<String, Object> args, com.lightweightai.kernel.llm.ToolResult result) {
        // 结构化日志 —— 调用链上能看到"工具返回了啥"，content 截断到 500 字符
        if (logger.isInfoEnabled() && result != null) {
            logger.info("tool.execute.end: name={}, argKeys={}, isError={}, result={}",
                    toolName,
                    args != null ? args.keySet() : java.util.Set.of(),
                    result.isError(),
                    com.lightweightai.kernel.util.TextTruncator.truncate(result.getContent(), 500));
        }
        for (var observer : observers) {
            try { observer.onPostToolUse(toolName, args, result); } catch (Exception ignored) {}
        }
    }

    @Override
    public String toString() {
        return "ToolCallingLoop{" +
            "provider=" + provider.getProviderName() +
            ", maxIterations=" + maxIterations +
            ", toolCount=" + toolExecutor.getFunctionCount() +
            '}';
    }
}
