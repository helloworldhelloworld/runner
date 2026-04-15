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
        List<ConversationMessage> conversation = new ArrayList<>(messages);
        int iteration = 0;

        while (iteration < maxIterations) {
            // 打断检查
            if (cancellationToken != null && cancellationToken.isCancelled()) {
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
            LLMResponse response = provider.complete(conversation, options);

            // Check if LLM wants to call tools
            if (!response.hasToolCalls()) {
                // No tool calls, we're done
                return response;
            }

            // Execute all tool calls (with client-side routing if context present)
            List<ToolCall> toolCalls = response.getToolCalls();
            List<ToolResult> toolResults = executionContext != null
                ? toolExecutor.executeToolCalls(toolCalls, executionContext)
                : toolExecutor.executeToolCalls(toolCalls);

            // Add assistant's response (with tool calls) to conversation
            conversation.add(enrichWithToolCalls(response));

            // Add tool results as user messages to conversation
            for (ToolResult toolResult : toolResults) {
                ConversationMessage toolResultMessage = createToolResultMessage(toolResult);
                conversation.add(toolResultMessage);
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
        return executeWithToolsAsync(new ArrayList<>(messages), options, 0);
    }

    /**
     * Internal async recursive method with iteration tracking
     */
    private CompletableFuture<LLMResponse> executeWithToolsAsync(
        List<ConversationMessage> conversation,
        LLMOptions options,
        int iteration
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

        // Step 1: Call LLM asynchronously (non-blocking)
        return provider.completeAsync(conversation, options)
            .thenCompose(response -> {
                // Step 2: Check if LLM wants to use tools
                if (!response.hasToolCalls() || response.getToolCalls().isEmpty()) {
                    // No tools needed, return final response
                    return CompletableFuture.completedFuture(response);
                }

                // Step 3: Execute tools asynchronously (with client-side routing if context present)
                List<ToolCall> toolCalls = response.getToolCalls();
                CompletableFuture<List<ToolResult>> toolFuture = executionContext != null
                    ? toolExecutor.executeToolCallsAsync(toolCalls, executionContext)
                    : toolExecutor.executeToolCallsAsync(toolCalls);
                return toolFuture
                    .thenCompose(toolResults -> {
                        // Step 4: Add assistant's response and tool results to conversation
                        conversation.add(enrichWithToolCalls(response));

                        for (ToolResult toolResult : toolResults) {
                            ConversationMessage toolResultMessage =
                                createToolResultMessage(toolResult);
                            conversation.add(toolResultMessage);
                        }

                        // Step 5: Recursively continue the conversation (async)
                        // This is the key: we chain dependent async operations
                        // without blocking any thread
                        return executeWithToolsAsync(conversation, options, iteration + 1);
                    });
            })
            .exceptionally(error -> {
                // Handle any errors in the chain
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
        return executeReactiveLoop(new ArrayList<>(messages), options, 0);
    }

    private Flux<StreamEvent> executeReactiveLoop(
            List<ConversationMessage> conversation,
            LLMOptions options,
            int iteration) {

        // 打断检查：CancellationToken 触发后优雅退出
        if (cancellationToken != null && cancellationToken.isCancelled()) {
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

            return provider.completeStreamReactive(conversation, options)
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

                    // Step 3a: 发出 TOOL_CALL_START 事件 + PreToolUse hooks
                    Flux<StreamEvent> toolStartEvents = Flux.fromIterable(toolCalls)
                        .doOnNext(tc -> firePreToolUse(tc.getName(), tc.getArguments()))
                        .map(StreamEvent::toolCallStart);

                    // Step 3b: 并行执行工具，使用 cache() 缓存并共享同一执行
                    Flux<ToolResultChunk> sharedToolExec = toolExecutor
                        .executeToolCallsReactive(toolCalls, executionContext)
                        .doOnNext(chunk -> {
                            // PostToolUse hook on COMPLETE/ERROR
                            if (chunk.getType() == ToolResultChunk.ChunkType.COMPLETE && chunk.getResult() != null) {
                                firePostToolUse(chunk.getToolName(), Map.of(), chunk.getResult());
                            } else if (chunk.getType() == ToolResultChunk.ChunkType.ERROR) {
                                firePostToolUse(chunk.getToolName(), Map.of(),
                                        ToolResult.error(chunk.getMessage()));
                            }
                        })
                        .cache();

                    // 流式发出 TOOL_PROGRESS / TOOL_LOG / TOOL_RESULT 事件
                    Flux<StreamEvent> toolExecEvents = sharedToolExec
                        .map(chunk -> switch (chunk.getType()) {
                            case PROGRESS -> StreamEvent.toolProgress(chunk);
                            case LOG -> StreamEvent.toolLog(chunk);
                            case COMPLETE -> StreamEvent.toolResult(chunk);
                            case ERROR -> StreamEvent.toolError(chunk);
                        });

                    // Step 3c: 收集工具结果，加入对话，递归下一轮
                    Flux<StreamEvent> nextRound = sharedToolExec
                        .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE
                                  || c.getType() == ToolResultChunk.ChunkType.ERROR)
                        .map(c -> {
                            if (c.getType() == ToolResultChunk.ChunkType.COMPLETE) {
                                return c.getResult().withToolUseId(c.getToolCallId());
                            }
                            return ToolResult.error(c.getToolCallId(), c.getMessage());
                        })
                        .collectList()
                        .flatMapMany(results -> {
                            for (ToolResult result : results) {
                                conversation.add(createToolResultMessage(result));
                            }
                            tracer.endSpan(iterationSpan);
                            return executeReactiveLoop(conversation, options, iteration + 1);
                        });

                    return Flux.concat(toolStartEvents, toolExecEvents, nextRound);
                }));
        });
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

        public ToolCallingLoop build() {
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, maxIterations,
                    executionContext, tracer, cancellationToken, costTracker);
            loop.observers = observers != null ? java.util.List.copyOf(observers) : java.util.List.of();
            return loop;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private void firePreToolUse(String toolName, Map<String, Object> args) {
        for (var observer : observers) {
            try { observer.onPreToolUse(toolName, args); } catch (Exception ignored) {}
        }
    }

    private void firePostToolUse(String toolName, Map<String, Object> args, com.lightweightai.kernel.llm.ToolResult result) {
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
