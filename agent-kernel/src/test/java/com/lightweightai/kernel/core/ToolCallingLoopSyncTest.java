package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.PluginFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCallingLoop 同步执行路径测试
 *
 * 覆盖关键路径：
 * - 无工具调用直接返回
 * - 单轮工具调用
 * - 多轮工具调用
 * - 迭代上限保护
 * - 构造器参数校验
 * - Builder 模式
 */
@DisplayName("ToolCallingLoop - 同步执行路径")
class ToolCallingLoopSyncTest {

    private MockLLMProvider mockProvider;
    private ToolExecutor toolExecutor;
    private AtomicInteger callCounter;

    @BeforeEach
    void setUp() {
        mockProvider = new MockLLMProvider();
        toolExecutor = new ToolExecutor();
        callCounter = new AtomicInteger(0);

        toolExecutor.registerFunction("add", new PluginFunction() {
            public String getName() { return "add"; }
            public String getDescription() { return "Add two numbers"; }
            public List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() { return List.of(); }
            public FunctionResult execute(Map<String, Object> input) {
                int a = ((Number) input.get("a")).intValue();
                int b = ((Number) input.get("b")).intValue();
                return FunctionResult.success(String.valueOf(a + b));
            }
            public Map<String, Object> toJsonSchema() { return Map.of("name", "add"); }
        });
    }

    @Test
    @DisplayName("无工具调用时直接返回 LLM 响应")
    void shouldReturnDirectlyWhenNoToolCalls() {
        mockProvider.addResponse(createTextResponse("Hello!"));

        ToolCallingLoop loop = new ToolCallingLoop(mockProvider, toolExecutor, 10);
        LLMResponse result = loop.executeWithTools(createUserMessages("Hi"), LLMOptions.builder().build());

        assertEquals("Hello!", result.getMessage().getTextContent());
        assertEquals(1, mockProvider.getCallCount());
    }

    @Test
    @DisplayName("单轮工具调用后返回最终响应")
    void shouldHandleSingleToolCallRound() {
        mockProvider.addResponse(createToolCallResponse("add", Map.of("a", 3, "b", 4)));
        mockProvider.addResponse(createTextResponse("The sum is 7"));

        ToolCallingLoop loop = new ToolCallingLoop(mockProvider, toolExecutor, 10);
        LLMResponse result = loop.executeWithTools(createUserMessages("3+4?"), LLMOptions.builder().build());

        assertEquals("The sum is 7", result.getMessage().getTextContent());
        assertEquals(2, mockProvider.getCallCount());
    }

    @Test
    @DisplayName("多轮工具调用后返回最终响应")
    void shouldHandleMultipleToolCallRounds() {
        mockProvider.addResponse(createToolCallResponse("add", Map.of("a", 1, "b", 2)));
        mockProvider.addResponse(createToolCallResponse("add", Map.of("a", 3, "b", 4)));
        mockProvider.addResponse(createTextResponse("Done: 3 and 7"));

        ToolCallingLoop loop = new ToolCallingLoop(mockProvider, toolExecutor, 10);
        LLMResponse result = loop.executeWithTools(createUserMessages("calc"), LLMOptions.builder().build());

        assertEquals("Done: 3 and 7", result.getMessage().getTextContent());
        assertEquals(3, mockProvider.getCallCount());
    }

    @Test
    @DisplayName("超过最大迭代次数抛出 RuntimeException")
    void shouldThrowOnMaxIterationsExceeded() {
        // 每次都返回工具调用，永不返回文本
        for (int i = 0; i < 5; i++) {
            mockProvider.addResponse(createToolCallResponse("add", Map.of("a", 1, "b", 1)));
        }

        ToolCallingLoop loop = new ToolCallingLoop(mockProvider, toolExecutor, 3);
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            loop.executeWithTools(createUserMessages("loop"), LLMOptions.builder().build()));

        assertTrue(ex.getMessage().contains("maximum iterations"));
        assertTrue(ex.getMessage().contains("3"));
    }

    @Test
    @DisplayName("构造器 - provider 为 null 抛异常")
    void shouldRejectNullProvider() {
        assertThrows(IllegalArgumentException.class, () ->
            new ToolCallingLoop(null, toolExecutor, 10));
    }

    @Test
    @DisplayName("构造器 - toolExecutor 为 null 抛异常")
    void shouldRejectNullToolExecutor() {
        assertThrows(IllegalArgumentException.class, () ->
            new ToolCallingLoop(mockProvider, null, 10));
    }

    @Test
    @DisplayName("构造器 - maxIterations < 1 抛异常")
    void shouldRejectInvalidMaxIterations() {
        assertThrows(IllegalArgumentException.class, () ->
            new ToolCallingLoop(mockProvider, toolExecutor, 0));
        assertThrows(IllegalArgumentException.class, () ->
            new ToolCallingLoop(mockProvider, toolExecutor, -1));
    }

    @Test
    @DisplayName("Builder 模式创建实例")
    void shouldBuildViaBuilder() {
        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .build();

        assertEquals(5, loop.getMaxIterations());
    }

    @Test
    @DisplayName("Builder 默认 maxIterations 为 10")
    void shouldDefaultMaxIterationsTo10() {
        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockProvider)
            .toolExecutor(toolExecutor)
            .build();

        assertEquals(10, loop.getMaxIterations());
    }

    @Test
    @DisplayName("工具不存在时返回错误结果并继续循环")
    void shouldHandleToolNotFound() {
        mockProvider.addResponse(createToolCallResponse("nonexistent", Map.of()));
        mockProvider.addResponse(createTextResponse("Tool was not found"));

        ToolCallingLoop loop = new ToolCallingLoop(mockProvider, toolExecutor, 10);
        LLMResponse result = loop.executeWithTools(createUserMessages("call missing"), LLMOptions.builder().build());

        assertEquals("Tool was not found", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("带 executionContext 构造")
    void shouldConstructWithExecutionContext() {
        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        ToolCallingLoop loop = new ToolCallingLoop(mockProvider, toolExecutor, 10, ctx);

        mockProvider.addResponse(createTextResponse("ok"));
        LLMResponse result = loop.executeWithTools(createUserMessages("hi"), LLMOptions.builder().build());
        assertEquals("ok", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringShouldIncludeInfo() {
        ToolCallingLoop loop = new ToolCallingLoop(mockProvider, toolExecutor, 10);
        String str = loop.toString();
        assertTrue(str.contains("ToolCallingLoop"));
        assertTrue(str.contains("mock"));
        assertTrue(str.contains("10"));
    }

    // ==================== 辅助方法 ====================

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

    private static class MockLLMProvider implements LLMProvider {
        private final Queue<LLMResponse> responses = new LinkedList<>();
        private int callCount = 0;

        void addResponse(LLMResponse response) { responses.add(response); }
        int getCallCount() { return callCount; }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            callCount++;
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
        public String getProviderName() { return "mock"; }
    }
}
