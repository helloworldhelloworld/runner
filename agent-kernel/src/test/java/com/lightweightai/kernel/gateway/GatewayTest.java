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
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * Gateway 是业务逻辑的客户端无关入口：
 * - 支持同步/异步/流式调用
 * - 协议无关（HTTP/WebSocket/gRPC/本地调用）
 * - 通过 ChatHandler 接入任意业务逻辑
 * - 通过 AgentLoop 保持向后兼容
 * - 会话管理
 * - 可观测性
 */
class GatewayTest {

    // ==================== AgentLoop 向后兼容测试 ====================

    @Nested
    @DisplayName("AgentLoop 向后兼容")
    class AgentLoopBackwardCompatibility {

        private Gateway gateway;
        private MockLLMProvider llmProvider;
        private InMemoryProvider memoryProvider;

        @BeforeEach
        void setUp() {
            llmProvider = new MockLLMProvider();
            memoryProvider = new InMemoryProvider();

            // 旧方式：通过 AgentLoop 构建 Gateway
            gateway = Gateway.builder()
                .agentLoop(AgentLoop.builder()
                    .llmProvider(llmProvider)
                    .memoryProvider(memoryProvider)
                    .systemPrompt("你是一个助手。")
                    .build())
                .build();
        }

        @Test
        @DisplayName("同步处理请求")
        void shouldHandleRequestSync() {
            llmProvider.setNextResponse("你好！我是助手。");
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("session-1")
                .message("你好")
                .build();

            GatewayResponse response = gateway.handle(request);

            assertNotNull(response);
            assertTrue(response.getText().contains("助手"));
            assertEquals("session-1", response.getSessionId());
        }

        @Test
        @DisplayName("同步请求包含完整元数据")
        void shouldIncludeMetadataInResponse() {
            llmProvider.setNextResponse("回复");
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("session-1")
                .message("测试")
                .build();

            GatewayResponse response = gateway.handle(request);

            assertNotNull(response.getRequestId());
            assertNotNull(response.getTimestamp());
            assertTrue(response.getLatencyMs() >= 0);
        }

        @Test
        @DisplayName("异步处理请求")
        void shouldHandleRequestAsync() throws Exception {
            llmProvider.setNextResponse("异步回复");
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("session-1")
                .message("异步测试")
                .build();

            CompletableFuture<GatewayResponse> future = gateway.handleAsync(request);

            GatewayResponse response = future.get(5, TimeUnit.SECONDS);
            assertEquals("异步回复", response.getText());
        }

        @Test
        @DisplayName("流式处理请求")
        void shouldHandleRequestStream() throws Exception {
            llmProvider.setStreamResponse(List.of("你", "好", "！"));
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("session-1")
                .message("流式测试")
                .build();

            CountDownLatch completeLatch = new CountDownLatch(1);
            StringBuilder received = new StringBuilder();
            AtomicReference<GatewayResponse> finalResponse = new AtomicReference<>();

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

            assertTrue(completeLatch.await(5, TimeUnit.SECONDS));
            assertEquals("你好！", received.toString());
            assertNotNull(finalResponse.get());
        }

        @Test
        @DisplayName("会话内保持上下文")
        void shouldMaintainSessionContext() {
            llmProvider.setNextResponse("我记得你叫张三。");

            gateway.handle(GatewayRequest.builder()
                .sessionId("session-1")
                .message("我叫张三")
                .build());

            GatewayResponse response = gateway.handle(GatewayRequest.builder()
                .sessionId("session-1")
                .message("我叫什么？")
                .build());

            assertNotNull(response);
            assertTrue(memoryProvider.getHistory("session-1", 10).size() >= 2);
        }

        @Test
        @DisplayName("不同会话相互隔离")
        void shouldIsolateSessionContexts() {
            llmProvider.setNextResponse("回复");

            gateway.handle(GatewayRequest.builder()
                .sessionId("session-1")
                .message("会话1的消息")
                .build());

            gateway.handle(GatewayRequest.builder()
                .sessionId("session-2")
                .message("会话2的消息")
                .build());

            List<?> history1 = memoryProvider.getHistory("session-1", 10);
            List<?> history2 = memoryProvider.getHistory("session-2", 10);

            assertEquals(2, history1.size());
            assertEquals(2, history2.size());
        }

        @Test
        @DisplayName("处理 LLM 错误")
        void shouldHandleLLMError() {
            llmProvider.setError(new RuntimeException("API 错误"));
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("session-1")
                .message("测试")
                .build();

            GatewayResponse response = gateway.handle(request);

            assertTrue(response.isError());
            assertNotNull(response.getErrorMessage());
        }
    }

    // ==================== ChatHandler 测试 ====================

    @Nested
    @DisplayName("ChatHandler 抽象接口")
    class ChatHandlerTests {

        @Test
        @DisplayName("通过 ChatHandler 构建 Gateway")
        void shouldBuildWithChatHandler() {
            MockChatHandler handler = new MockChatHandler("测试回复");

            Gateway gw = Gateway.builder()
                .chatHandler(handler)
                .build();

            GatewayResponse response = gw.handle(GatewayRequest.builder()
                .sessionId("s1")
                .message("你好")
                .build());

            assertNotNull(response);
            assertEquals("测试回复", response.getText());
            assertFalse(response.isError());
            assertTrue(response.getLatencyMs() >= 0);
        }

        @Test
        @DisplayName("ChatHandler 错误被包装为 error response")
        void shouldHandleChatHandlerError() {
            ChatHandler failingHandler = new ChatHandler() {
                @Override
                public GatewayResponse chat(GatewayRequest request) {
                    throw new RuntimeException("业务错误");
                }

                @Override
                public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                    return CompletableFuture.failedFuture(new RuntimeException("业务错误"));
                }
            };

            Gateway gw = Gateway.builder().chatHandler(failingHandler).build();

            GatewayResponse response = gw.handle(GatewayRequest.builder()
                .sessionId("s1").message("test").build());

            assertTrue(response.isError());
            assertEquals("业务错误", response.getErrorMessage());
        }

        @Test
        @DisplayName("ChatHandler 流式调用带 metadata")
        void shouldStreamWithMetadata() throws Exception {
            MockChatHandler handler = new MockChatHandler("完整回复");

            Gateway gw = Gateway.builder().chatHandler(handler).build();

            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("流式测试").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> deltas = new ArrayList<>();
            List<Map<String, Object>> metadatas = new ArrayList<>();
            AtomicReference<GatewayResponse> finalResponse = new AtomicReference<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override
                public void onDelta(String delta) {
                    // 不会被调用（Gateway 使用带 metadata 的版本）
                    fail("Should not call onDelta without metadata");
                }

                @Override
                public void onDelta(String delta, Map<String, Object> metadata) {
                    deltas.add(delta);
                    metadatas.add(metadata);
                }

                @Override
                public void onComplete(GatewayResponse response) {
                    finalResponse.set(response);
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("完整", "回复"), deltas);
            assertEquals(2, metadatas.size());
            assertEquals("温柔", metadatas.get(0).get("emotion"));
            assertNotNull(finalResponse.get());
            assertEquals("完整回复", finalResponse.get().getText());
        }

        @Test
        @DisplayName("SessionManager 可选注入")
        void shouldSupportOptionalSessionManager() {
            MockChatHandler handler = new MockChatHandler("ok");
            MockSessionManager sm = new MockSessionManager();

            Gateway gw = Gateway.builder()
                .chatHandler(handler)
                .sessionManager(sm)
                .build();

            assertNotNull(gw.getSessionManager());
            assertEquals(0, gw.getSessionManager().getSessionHistory("any").size());

            // 无 SessionManager 的 Gateway
            Gateway gw2 = Gateway.builder()
                .chatHandler(handler)
                .build();
            assertNull(gw2.getSessionManager());
        }

        @Test
        @DisplayName("必须提供 chatHandler 或 agentLoop")
        void shouldRequireHandlerOrLoop() {
            assertThrows(IllegalArgumentException.class, () ->
                Gateway.builder().build()
            );
        }
    }

    // ==================== Mock 实现 ====================

    private static class MockChatHandler implements ChatHandler {
        private final String responseText;

        MockChatHandler(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public GatewayResponse chat(GatewayRequest request) {
            return GatewayResponse.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .text(responseText)
                .build();
        }

        @Override
        public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
            // 模拟分两块流式返回
            String[] chunks = {responseText.substring(0, responseText.length() / 2),
                               responseText.substring(responseText.length() / 2)};

            for (String chunk : chunks) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("emotion", "温柔");
                callback.onDelta(chunk, metadata);
            }

            GatewayResponse response = GatewayResponse.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .text(responseText)
                .build();
            callback.onComplete(response);

            return CompletableFuture.completedFuture(response);
        }
    }

    private static class MockSessionManager implements SessionManager {
        @Override
        public List<Map<String, String>> getSessionHistory(String sessionId) {
            return List.of();
        }

        @Override
        public Map<String, Object> getSessionSummary(String sessionId) {
            return Map.of("sessionId", sessionId);
        }

        @Override
        public void clearSession(String sessionId) {
            // no-op
        }
    }

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
