package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCallingLoop Reactive 流式执行路径测试
 *
 * 覆盖关键路径：
 * - 无工具调用的流式输出
 * - 单轮工具调用的完整事件序列
 * - 多轮工具调用的递归流
 * - 迭代上限保护
 * - 事件类型映射（PROGRESS/LOG/COMPLETE/ERROR → StreamEvent）
 */
@DisplayName("ToolCallingLoop - Reactive 流式执行路径")
class ToolCallingLoopReactiveTest {

    private ReactiveMockProvider mockProvider;
    private ToolExecutor toolExecutor;
    private ToolRegistry toolRegistry;
    private AtomicInteger callCounter;

    @BeforeEach
    void setUp() {
        mockProvider = new ReactiveMockProvider();
        toolRegistry = new ToolRegistry();
        toolExecutor = new ToolExecutor(toolRegistry);
        callCounter = new AtomicInteger(0);

        toolRegistry.register(createEchoTool("echo"));
    }

    @Test
    @DisplayName("无工具调用 - 直接输出 TEXT_DELTA + LLM_COMPLETE")
    void shouldStreamTextDeltasAndComplete() {
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("Hello "),
            StreamEvent.textDelta("World"),
            StreamEvent.llmComplete(createTextResponse("Hello World"))
        ));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .build();

        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("Hi"), LLMOptions.builder().build()))
            .assertNext(e -> {
                assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType());
                assertEquals("Hello ", e.getTextDelta());
            })
            .assertNext(e -> {
                assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType());
                assertEquals("World", e.getTextDelta());
            })
            .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("单轮工具调用 - 输出完整事件序列")
    void shouldHandleSingleToolCallRound() {
        // Round 1: LLM wants to call echo tool
        LLMResponse toolCallResponse = createToolCallResponse("echo", Map.of("input", "test"));
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta(""),
            StreamEvent.llmComplete(toolCallResponse)
        ));

        // Round 2: LLM returns final text
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("Echo result"),
            StreamEvent.llmComplete(createTextResponse("Echo result"))
        ));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("echo test"), LLMOptions.builder().build()))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        // Verify event sequence contains expected types
        List<StreamEvent.EventType> types = events.stream().map(StreamEvent::getType).toList();
        assertTrue(types.contains(StreamEvent.EventType.LLM_COMPLETE), "Should have LLM_COMPLETE");
        assertTrue(types.contains(StreamEvent.EventType.TOOL_CALL_START), "Should have TOOL_CALL_START");
        assertTrue(types.contains(StreamEvent.EventType.TOOL_RESULT), "Should have TOOL_RESULT");
    }

    @Test
    @DisplayName("超过最大迭代次数 - 流发出错误")
    void shouldEmitErrorOnMaxIterations() {
        // Always return tool calls
        for (int i = 0; i < 5; i++) {
            LLMResponse toolCallResponse = createToolCallResponse("echo", Map.of("input", "loop"));
            mockProvider.addStreamResponse(List.of(
                StreamEvent.llmComplete(toolCallResponse)
            ));
        }

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(2)
            .build();

        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("loop"), LLMOptions.builder().build()))
            .thenConsumeWhile(e -> true) // consume all events until error
            .verifyErrorMatches(err -> err.getMessage().contains("maximum iterations"));
    }

    @Test
    @DisplayName("工具不存在时发出 TOOL_ERROR 事件")
    void shouldEmitToolErrorForMissingTool() {
        LLMResponse toolCallResponse = createToolCallResponse("nonexistent", Map.of());
        mockProvider.addStreamResponse(List.of(
            StreamEvent.llmComplete(toolCallResponse)
        ));
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("handled"),
            StreamEvent.llmComplete(createTextResponse("handled"))
        ));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("missing"), LLMOptions.builder().build()))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        // Should contain a TOOL_ERROR event for the missing tool
        boolean hasToolError = events.stream()
            .anyMatch(e -> e.getType() == StreamEvent.EventType.TOOL_ERROR);
        assertTrue(hasToolError, "Should have TOOL_ERROR event for missing tool");
    }

    @Test
    @DisplayName("多轮工具调用的递归流")
    void shouldHandleMultipleToolCallRounds() {
        // Round 1: call echo
        mockProvider.addStreamResponse(List.of(
            StreamEvent.llmComplete(createToolCallResponse("echo", Map.of("input", "r1")))
        ));
        // Round 2: call echo again
        mockProvider.addStreamResponse(List.of(
            StreamEvent.llmComplete(createToolCallResponse("echo", Map.of("input", "r2")))
        ));
        // Round 3: final
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("Done"),
            StreamEvent.llmComplete(createTextResponse("Done"))
        ));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(10)
            .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("multi"), LLMOptions.builder().build()))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        // Should have 2 TOOL_CALL_START events
        long toolCallStarts = events.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START)
            .count();
        assertEquals(2, toolCallStarts, "Should have 2 tool call rounds");
    }

    @Test
    @DisplayName("无 LLM_COMPLETE 事件时安全终止")
    void shouldHandleMissingLlmComplete() {
        // Stream with only text deltas, no LLM_COMPLETE
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("partial")
        ));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .build();

        // Should complete without errors (no tool calls detected)
        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("test"), LLMOptions.builder().build()))
            .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
            .verifyComplete();
    }

    // ==================== 辅助方法 ====================

    private Tool createEchoTool(String name) {
        return new Tool() {
            public String getName() { return name; }
            public String getDescription() { return "Echo tool"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echo: " + args);
            }
        };
    }

    private List<ConversationMessage> createUserMessages(String text) {
        return List.of(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(text)
            .build());
    }

    private LLMResponse createTextResponse(String text) {
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build())
            .build();
    }

    private LLMResponse createToolCallResponse(String toolName, Map<String, Object> args) {
        ToolCall toolCall = new ToolCall("call_" + callCounter.incrementAndGet(), toolName, args);
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("")
                .build())
            .toolCalls(List.of(toolCall))
            .build();
    }

    /**
     * Mock LLM Provider that returns pre-configured reactive streams
     */
    private static class ReactiveMockProvider implements LLMProvider {
        private final Queue<List<StreamEvent>> streamResponses = new LinkedList<>();

        void addStreamResponse(List<StreamEvent> events) {
            streamResponses.add(events);
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            List<StreamEvent> events = streamResponses.poll();
            if (events == null) {
                return Flux.error(new RuntimeException("No more mock stream responses"));
            }
            return Flux.fromIterable(events);
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException("Use reactive path");
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException("Use reactive path");
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException("Use reactive path");
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "reactive-mock"; }
    }
}
