package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import reactor.core.publisher.Flux;

/**
 * Handles the tool calling loop for LLM conversations.
 * When an LLM wants to use tools, this class:
 * 1. Executes the tool calls
 * 2. Sends results back to the LLM
 * 3. Repeats until the LLM provides a final response
 */
public class ToolCallingLoop {

    private final LLMProvider provider;
    private final ToolExecutor toolExecutor;
    private final int maxIterations;
    private final ToolExecutionContext executionContext;

    /**
     * Create a new ToolCallingLoop
     *
     * @param provider     LLM provider to use
     * @param toolExecutor Tool executor for running tools
     * @param maxIterations Maximum number of tool calling iterations (prevents infinite loops)
     */
    public ToolCallingLoop(LLMProvider provider, ToolExecutor toolExecutor, int maxIterations) {
        this(provider, toolExecutor, maxIterations, null);
    }

    public ToolCallingLoop(LLMProvider provider, ToolExecutor toolExecutor, int maxIterations,
                           ToolExecutionContext executionContext) {
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
            conversation.add(response.getMessage());

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
                        conversation.add(response.getMessage());

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

        if (iteration >= maxIterations) {
            return Flux.error(new RuntimeException(
                "Tool calling loop exceeded maximum iterations: " + maxIterations));
        }

        // Step 1: LLM 流式调用 — 收集所有事件
        List<StreamEvent> accumulated = new ArrayList<>();

        return provider.completeStreamReactive(conversation, options)
            .doOnNext(accumulated::add)
            .concatWith(Flux.defer(() -> {
                // Step 2: 从收集的事件中找到 LLM_COMPLETE
                StreamEvent completeEvent = accumulated.stream()
                    .filter(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE)
                    .findFirst()
                    .orElse(null);

                if (completeEvent == null || !completeEvent.getResponse().hasToolCalls()) {
                    return Flux.empty();  // 无工具调用，结束
                }

                // Step 3: LLM 要调用工具
                LLMResponse response = completeEvent.getResponse();
                List<com.lightweightai.kernel.llm.ToolCall> toolCalls = response.getToolCalls();
                conversation.add(response.getMessage());

                // Step 3a: 发出 TOOL_CALL_START 事件
                Flux<StreamEvent> toolStartEvents = Flux.fromIterable(toolCalls)
                    .map(StreamEvent::toolCallStart);

                // Step 3b: 并行执行工具，使用 cache() 缓存并共享同一执行
                // 注意：不能用 share()，因为 concat() 是顺序订阅，
                // nextRound 订阅时 share() 的上游已经完成，会丢失所有事件。
                // cache() 会缓存所有事件并重播给后续订阅者（nextRound）。
                Flux<ToolResultChunk> sharedToolExec = toolExecutor
                    .executeToolCallsReactive(toolCalls)
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
                        return executeReactiveLoop(conversation, options, iteration + 1);
                    });

                return Flux.concat(toolStartEvents, toolExecEvents, nextRound);
            }));
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

        public ToolCallingLoop build() {
            return new ToolCallingLoop(provider, toolExecutor, maxIterations, executionContext);
        }
    }

    public static Builder builder() {
        return new Builder();
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
