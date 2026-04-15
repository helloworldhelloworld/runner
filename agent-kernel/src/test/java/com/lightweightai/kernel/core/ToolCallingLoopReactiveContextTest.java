package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCallingLoop - Reactive 路径补充测试
 *
 * 覆盖已有 ToolCallingLoopReactiveTest 未覆盖的关键路径：
 * - 带 ToolExecutionContext 的 reactive 执行
 * - enrichWithToolCalls / createToolResultMessage 间接验证（通过对话历史）
 * - Builder 带 tracer 和 executionContext
 * - 同步路径的对话历史正确性（验证 enrichWithToolCalls + createToolResultMessage）
 * - async 路径 max iterations 错误
 */
@DisplayName("ToolCallingLoop - Reactive 路径补充测试")
class ToolCallingLoopReactiveContextTest {

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

    // ==================== enrichWithToolCalls 间接验证 ====================

    @Nested
    @DisplayName("对话历史正确性（验证 enrichWithToolCalls + createToolResultMessage）")
    class ConversationHistoryTests {

        @Test
        @DisplayName("同步路径 - tool call 后对话包含 tool_calls 元数据和 TOOL 角色消息")
        void syncPathShouldBuildCorrectConversationHistory() {
            // 使用同步 mock provider 捕获对话历史
            CapturingSyncProvider syncProvider = new CapturingSyncProvider();

            // Round 1: LLM returns tool call
            syncProvider.addResponse(createToolCallResponse("echo", Map.of("input", "hello")));
            // Round 2: LLM returns final text — 此时我们可以检查发给 LLM 的对话历史
            syncProvider.addResponse(createTextResponse("Done"));

            ToolCallingLoop loop = new ToolCallingLoop(syncProvider, toolExecutor, 10);
            LLMResponse result = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());

            assertEquals("Done", result.getMessage().getTextContent());

            // 验证第二次调用 LLM 时的对话历史
            List<ConversationMessage> secondCallMessages = syncProvider.getCapturedMessages(1);
            assertNotNull(secondCallMessages);

            // 应包含: [user] + [assistant with tool_calls] + [tool result]
            assertTrue(secondCallMessages.size() >= 3,
                "Should have at least 3 messages: user + assistant(tool_calls) + tool_result");

            // 验证 assistant 消息包含 tool_calls 元数据 (enrichWithToolCalls 的产物)
            ConversationMessage assistantMsg = secondCallMessages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.ASSISTANT)
                .findFirst().orElse(null);
            assertNotNull(assistantMsg, "Should have assistant message");
            assertTrue(assistantMsg.getMetadata().containsKey("tool_calls"),
                "Assistant message should contain tool_calls metadata");

            // 验证 TOOL 角色消息 (createToolResultMessage 的产物)
            ConversationMessage toolMsg = secondCallMessages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                .findFirst().orElse(null);
            assertNotNull(toolMsg, "Should have TOOL role message");
            assertNotNull(toolMsg.getMetadata().get("tool_use_id"),
                "TOOL message should have tool_use_id");
            assertEquals(false, toolMsg.getMetadata().get("is_error"),
                "Successful tool should have is_error=false");
        }

        @Test
        @DisplayName("同步路径 - 工具执行失败时 TOOL 消息标记 is_error=true")
        void syncPathShouldMarkErrorToolResult() {
            Tool failingTool = new Tool() {
                public String getName() { return "fail_tool"; }
                public String getDescription() { return "Always fails"; }
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                public ToolResult execute(Map<String, Object> args) {
                    throw new RuntimeException("intentional failure");
                }
            };
            toolRegistry.register(failingTool);

            CapturingSyncProvider syncProvider = new CapturingSyncProvider();
            syncProvider.addResponse(createToolCallResponse("fail_tool", Map.of()));
            syncProvider.addResponse(createTextResponse("Handled error"));

            ToolCallingLoop loop = new ToolCallingLoop(syncProvider, toolExecutor, 10);
            LLMResponse result = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());

            assertEquals("Handled error", result.getMessage().getTextContent());

            // 验证 TOOL 消息标记了 is_error
            List<ConversationMessage> secondCallMessages = syncProvider.getCapturedMessages(1);
            ConversationMessage toolMsg = secondCallMessages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                .findFirst().orElse(null);
            assertNotNull(toolMsg);
            assertEquals(true, toolMsg.getMetadata().get("is_error"),
                "Failed tool should have is_error=true");
        }

        @Test
        @DisplayName("同步路径 - 不存在的工具也生成正确的 TOOL 消息")
        void syncPathShouldHandleMissingToolInHistory() {
            CapturingSyncProvider syncProvider = new CapturingSyncProvider();
            syncProvider.addResponse(createToolCallResponse("nonexistent", Map.of()));
            syncProvider.addResponse(createTextResponse("Tool missing"));

            ToolCallingLoop loop = new ToolCallingLoop(syncProvider, toolExecutor, 10);
            LLMResponse result = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());

            assertEquals("Tool missing", result.getMessage().getTextContent());

            List<ConversationMessage> secondCallMessages = syncProvider.getCapturedMessages(1);
            ConversationMessage toolMsg = secondCallMessages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                .findFirst().orElse(null);
            assertNotNull(toolMsg);
            assertEquals(true, toolMsg.getMetadata().get("is_error"));
            assertTrue(toolMsg.getTextContent().contains("Tool not found"));
        }
    }

    // ==================== 带 ExecutionContext 的 Reactive 测试 ====================

    @Nested
    @DisplayName("带 ToolExecutionContext 的 reactive 执行")
    class ReactiveWithContextTests {

        @Test
        @DisplayName("serverOnly context 走正常 reactive 执行")
        void shouldExecuteReactiveWithServerOnlyContext() {
            mockProvider.addStreamResponse(List.of(
                StreamEvent.textDelta("Hello"),
                StreamEvent.llmComplete(createTextResponse("Hello"))
            ));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(mockProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(5)
                .executionContext(ToolExecutionContext.serverOnly())
                .build();

            StepVerifier.create(loop.executeWithToolsReactive(
                    createUserMessages("Hi"), LLMOptions.builder().build()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
        }

        @Test
        @DisplayName("带 context 的工具调用 reactive 执行")
        void shouldExecuteToolCallsWithContextReactive() {
            LLMResponse toolCallResponse = createToolCallResponse("echo", Map.of("input", "ctx"));
            mockProvider.addStreamResponse(List.of(
                StreamEvent.llmComplete(toolCallResponse)
            ));
            mockProvider.addStreamResponse(List.of(
                StreamEvent.textDelta("OK"),
                StreamEvent.llmComplete(createTextResponse("OK"))
            ));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(mockProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(5)
                .executionContext(ToolExecutionContext.serverOnly())
                .build();

            List<StreamEvent> events = new ArrayList<>();
            StepVerifier.create(loop.executeWithToolsReactive(
                    createUserMessages("test"), LLMOptions.builder().build()))
                .recordWith(() -> events)
                .thenConsumeWhile(e -> true)
                .verifyComplete();

            assertTrue(events.stream().anyMatch(
                e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START));
            assertTrue(events.stream().anyMatch(
                e -> e.getType() == StreamEvent.EventType.TOOL_RESULT));
        }
    }

    // ==================== Builder 高级配置 ====================

    @Nested
    @DisplayName("Builder 高级配置")
    class BuilderAdvancedTests {

        @Test
        @DisplayName("Builder 设置 tracer")
        void shouldBuildWithTracer() {
            mockProvider.addStreamResponse(List.of(
                StreamEvent.textDelta("traced"),
                StreamEvent.llmComplete(createTextResponse("traced"))
            ));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(mockProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(5)
                .tracer(Tracer.NOOP)
                .build();

            StepVerifier.create(loop.executeWithToolsReactive(
                    createUserMessages("trace test"), LLMOptions.builder().build()))
                .assertNext(e -> assertEquals("traced", e.getTextDelta()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
        }

        @Test
        @DisplayName("Builder 同时设置 tracer 和 executionContext")
        void shouldBuildWithTracerAndContext() {
            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(mockProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(3)
                .executionContext(ToolExecutionContext.serverOnly())
                .tracer(Tracer.NOOP)
                .build();

            assertEquals(3, loop.getMaxIterations());
        }

        @Test
        @DisplayName("5 参数构造器 - tracer 为 null 使用 NOOP")
        void shouldUseNoopTracerWhenNull() {
            // 不应抛异常
            ToolCallingLoop loop = new ToolCallingLoop(
                mockProvider, toolExecutor, 5, null, null);
            assertEquals(5, loop.getMaxIterations());
        }
    }

    // ==================== Async 路径补充 ====================

    @Nested
    @DisplayName("Async 路径补充")
    class AsyncSupplementTests {

        @Test
        @DisplayName("async 路径达到 max iterations 返回 failed future")
        void asyncShouldFailOnMaxIterations() {
            MockAsyncProvider asyncProvider = new MockAsyncProvider();
            for (int i = 0; i < 5; i++) {
                asyncProvider.addResponse(createToolCallResponse("echo", Map.of("input", "loop")));
            }

            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(asyncProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(2)
                .build();

            CompletableFuture<LLMResponse> future = loop.executeWithToolsAsync(
                createUserMessages("loop"), LLMOptions.builder().build());

            try {
                future.get();
                fail("Should have thrown");
            } catch (Exception e) {
                assertTrue(e.getCause().getMessage().contains("maximum iterations")
                    || e.getCause().getCause().getMessage().contains("maximum iterations"));
            }
        }

        @Test
        @DisplayName("async 带 executionContext 执行")
        void asyncShouldWorkWithExecutionContext() throws Exception {
            MockAsyncProvider asyncProvider = new MockAsyncProvider();
            asyncProvider.addResponse(createToolCallResponse("echo", Map.of("input", "hi")));
            asyncProvider.addResponse(createTextResponse("Done"));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(asyncProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .executionContext(ToolExecutionContext.serverOnly())
                .build();

            LLMResponse result = loop.executeWithToolsAsync(
                createUserMessages("test"), LLMOptions.builder().build()
            ).get();

            assertEquals("Done", result.getMessage().getTextContent());
        }
    }

    // ==================== Reactive 事件类型映射完整性 ====================

    @Nested
    @DisplayName("Reactive 事件类型映射")
    class ReactiveEventMappingTests {

        @Test
        @DisplayName("工具返回 COMPLETE chunk 映射为 TOOL_RESULT StreamEvent")
        void completedToolShouldMapToToolResult() {
            LLMResponse toolCallResponse = createToolCallResponse("echo", Map.of("input", "map"));
            mockProvider.addStreamResponse(List.of(
                StreamEvent.llmComplete(toolCallResponse)
            ));
            mockProvider.addStreamResponse(List.of(
                StreamEvent.textDelta("mapped"),
                StreamEvent.llmComplete(createTextResponse("mapped"))
            ));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(mockProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(5)
                .build();

            List<StreamEvent> events = new ArrayList<>();
            StepVerifier.create(loop.executeWithToolsReactive(
                    createUserMessages("test"), LLMOptions.builder().build()))
                .recordWith(() -> events)
                .thenConsumeWhile(e -> true)
                .verifyComplete();

            // Verify TOOL_RESULT event has a chunk with the result
            StreamEvent toolResultEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TOOL_RESULT)
                .findFirst().orElse(null);
            assertNotNull(toolResultEvent, "Should emit TOOL_RESULT event");
            assertNotNull(toolResultEvent.getChunk(), "TOOL_RESULT should have chunk");
            assertEquals(ToolResultChunk.ChunkType.COMPLETE, toolResultEvent.getChunk().getType());
        }

        @Test
        @DisplayName("LLM_COMPLETE 无 response 时安全终止")
        void shouldHandleNullResponseInLlmComplete() {
            // LLM_COMPLETE with null response
            mockProvider.addStreamResponse(List.of(
                StreamEvent.textDelta("text"),
                StreamEvent.llmComplete(null)
            ));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(mockProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(5)
                .build();

            StepVerifier.create(loop.executeWithToolsReactive(
                    createUserMessages("test"), LLMOptions.builder().build()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
        }

        @Test
        @DisplayName("LLM_COMPLETE response 无 tool calls 时安全终止")
        void shouldHandleResponseWithoutToolCalls() {
            mockProvider.addStreamResponse(List.of(
                StreamEvent.llmComplete(createTextResponse("no tools"))
            ));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(mockProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(5)
                .build();

            StepVerifier.create(loop.executeWithToolsReactive(
                    createUserMessages("test"), LLMOptions.builder().build()))
                .assertNext(e -> {
                    assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType());
                    assertFalse(e.getResponse().hasToolCalls());
                })
                .verifyComplete();
        }
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

    // ==================== Mock Providers ====================

    /**
     * 同步 LLM Provider，捕获每次调用的对话历史
     */
    private static class CapturingSyncProvider implements LLMProvider {
        private final Queue<LLMResponse> responses = new LinkedList<>();
        private final List<List<ConversationMessage>> capturedMessages = new ArrayList<>();

        void addResponse(LLMResponse response) { responses.add(response); }

        List<ConversationMessage> getCapturedMessages(int callIndex) {
            return callIndex < capturedMessages.size() ? capturedMessages.get(callIndex) : null;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            capturedMessages.add(new ArrayList<>(messages));
            LLMResponse response = responses.poll();
            if (response == null) throw new RuntimeException("No more mock responses");
            return response;
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "capturing-mock"; }
    }

    /**
     * Reactive mock provider
     */
    private static class ReactiveMockProvider implements LLMProvider {
        private final Queue<List<StreamEvent>> streamResponses = new LinkedList<>();

        void addStreamResponse(List<StreamEvent> events) { streamResponses.add(events); }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            List<StreamEvent> events = streamResponses.poll();
            if (events == null) return Flux.error(new RuntimeException("No more mock stream responses"));
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

    /**
     * Async mock provider
     */
    private static class MockAsyncProvider implements LLMProvider {
        private final Queue<LLMResponse> responses = new LinkedList<>();

        void addResponse(LLMResponse response) { responses.add(response); }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            LLMResponse response = responses.poll();
            if (response == null) throw new RuntimeException("No more mock responses");
            return response;
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.supplyAsync(() -> complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "async-mock"; }
    }
}
