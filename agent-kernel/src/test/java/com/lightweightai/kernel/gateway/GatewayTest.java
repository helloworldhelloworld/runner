package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: Gateway 抽象测试
 *
 * Gateway 是 AgentLoop 的客户端无关入口：
 * - 支持同步/异步/流式调用
 * - 协议无关（HTTP/WebSocket/gRPC/本地调用）
 * - 会话管理
 * - 可观测性
 */
@DisplayName("Gateway - 客户端无关入口")
class GatewayTest {

    private Gateway gateway;
    private MockLLMProvider llmProvider;
    private InMemoryProvider memoryProvider;

    @BeforeEach
    void setUp() {
        llmProvider = new MockLLMProvider();
        memoryProvider = new InMemoryProvider();

        // 构建 Gateway
        gateway = Gateway.builder()
            .agentLoop(AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memoryProvider)
                .systemPrompt("你是一个助手。")
                .build())
            .build();
    }

    // ==================== 同步调用测试 ====================

    @Test
    @DisplayName("同步处理请求")
    void shouldHandleRequestSync() {
        // Given
        llmProvider.setNextResponse("你好！我是助手。");
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("session-1")
            .message("你好")
            .build();

        // When
        GatewayResponse response = gateway.handle(request);

        // Then
        assertNotNull(response);
        assertTrue(response.getText().contains("助手"));
        assertEquals("session-1", response.getSessionId());
    }

    @Test
    @DisplayName("同步请求包含完整元数据")
    void shouldIncludeMetadataInResponse() {
        // Given
        llmProvider.setNextResponse("回复");
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("session-1")
            .message("测试")
            .build();

        // When
        GatewayResponse response = gateway.handle(request);

        // Then
        assertNotNull(response.getRequestId());
        assertNotNull(response.getTimestamp());
        assertTrue(response.getLatencyMs() >= 0);
    }

    // ==================== 异步调用测试 ====================

    @Test
    @DisplayName("异步处理请求")
    void shouldHandleRequestAsync() throws Exception {
        // Given
        llmProvider.setNextResponse("异步回复");
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("session-1")
            .message("异步测试")
            .build();

        // When
        CompletableFuture<GatewayResponse> future = gateway.handleAsync(request);

        // Then
        GatewayResponse response = future.get(5, TimeUnit.SECONDS);
        assertEquals("异步回复", response.getText());
    }

    // ==================== 流式调用测试 ====================

    @Test
    @DisplayName("流式处理请求")
    void shouldHandleRequestStream() throws Exception {
        // Given
        llmProvider.setStreamResponse(List.of("你", "好", "！"));
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("session-1")
            .message("流式测试")
            .build();

        CountDownLatch completeLatch = new CountDownLatch(1);
        StringBuilder received = new StringBuilder();
        AtomicReference<GatewayResponse> finalResponse = new AtomicReference<>();

        // When
        gateway.handleStream(request, new GatewayStreamHandler() {
            @Override
            public void onDelta(String delta) {
                received.append(delta);
            }

            @Override
            public void onComplete(GatewayResponse response) {
                finalResponse.set(response);
                completeLatch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                completeLatch.countDown();
            }
        });

        // Then
        assertTrue(completeLatch.await(5, TimeUnit.SECONDS));
        assertEquals("你好！", received.toString());
        assertNotNull(finalResponse.get());
    }

    // ==================== 会话管理测试 ====================

    @Test
    @DisplayName("会话内保持上下文")
    void shouldMaintainSessionContext() {
        // Given
        llmProvider.setNextResponse("我记得你叫张三。");

        // When - 第一次对话
        gateway.handle(GatewayRequest.builder()
            .sessionId("session-1")
            .message("我叫张三")
            .build());

        // When - 第二次对话（同一会话）
        GatewayResponse response = gateway.handle(GatewayRequest.builder()
            .sessionId("session-1")
            .message("我叫什么？")
            .build());

        // Then
        assertNotNull(response);
        // 验证记忆中有上下文
        assertTrue(memoryProvider.getHistory("session-1", 10).size() >= 2);
    }

    @Test
    @DisplayName("不同会话相互隔离")
    void shouldIsolateSessionContexts() {
        // Given
        llmProvider.setNextResponse("回复");

        // When
        gateway.handle(GatewayRequest.builder()
            .sessionId("session-1")
            .message("会话1的消息")
            .build());

        gateway.handle(GatewayRequest.builder()
            .sessionId("session-2")
            .message("会话2的消息")
            .build());

        // Then
        List<?> history1 = memoryProvider.getHistory("session-1", 10);
        List<?> history2 = memoryProvider.getHistory("session-2", 10);

        assertEquals(2, history1.size()); // user + assistant
        assertEquals(2, history2.size());
    }

    // ==================== 错误处理测试 ====================

    @Test
    @DisplayName("处理 LLM 错误")
    void shouldHandleLLMError() {
        // Given
        llmProvider.setError(new RuntimeException("API 错误"));
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("session-1")
            .message("测试")
            .build();

        // When
        GatewayResponse response = gateway.handle(request);

        // Then
        assertTrue(response.isError());
        assertNotNull(response.getErrorMessage());
    }

    // ==================== 辅助类 ====================

    private static class MockLLMProvider implements LLMProvider {
        private String nextResponse = "Default response";
        private List<String> streamChunks = List.of();
        private RuntimeException error = null;

        void setNextResponse(String response) {
            this.nextResponse = response;
            this.error = null;
        }

        void setStreamResponse(List<String> chunks) {
            this.streamChunks = chunks;
            this.nextResponse = String.join("", chunks);
            this.error = null;
        }

        void setError(RuntimeException error) {
            this.error = error;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            if (error != null) {
                throw error;
            }
            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(nextResponse)
                .build();
            return LLMResponse.builder().message(msg).stopReason("stop").build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(
                List<ConversationMessage> messages,
                LLMOptions options) {
            return CompletableFuture.supplyAsync(() -> complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages,
                LLMOptions options,
                StreamEventHandler handler) {

            if (error != null) {
                handler.onError(error);
                return CompletableFuture.failedFuture(error);
            }

            // 模拟流式响应
            for (String chunk : streamChunks) {
                handler.onTextDelta(chunk);
            }

            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(nextResponse)
                .build();
            LLMResponse response = LLMResponse.builder().message(msg).stopReason("stop").build();
            handler.onComplete(response);

            return CompletableFuture.completedFuture(response);
        }

        @Override
        public ModelCapability getModelCapability() {
            return new ModelCapability() {
                @Override public String getModelId() { return "mock-model"; }
                @Override public int getMaxContextTokens() { return 4096; }
                @Override public int getMaxOutputTokens() { return 4096; }
                @Override public MessageFormatter getMessageFormatter() { return null; }
                @Override public TokenCounter getTokenCounter() { return null; }
                @Override public java.util.Set<ModelFeature> getSupportedFeatures() {
                    return java.util.Set.of(ModelFeature.TOOL_CALLING, ModelFeature.STREAMING);
                }
            };
        }

        @Override
        public String getProviderName() { return "mock"; }
    }
}
