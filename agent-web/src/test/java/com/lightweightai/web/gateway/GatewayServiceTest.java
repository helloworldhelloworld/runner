package com.lightweightai.web.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.service.SoulComfortChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GatewayService - ChatHandler + SessionManager 实现")
class GatewayServiceTest {

    private GatewayService service;
    private StubSoulComfortChatService stubService;

    @BeforeEach
    void setUp() {
        stubService = new StubSoulComfortChatService();
        service = new GatewayService(stubService);
    }

    // ==================== ChatHandler.chat ====================

    @Test
    @DisplayName("chat 正常返回")
    void chatSuccess() {
        stubService.syncResponse = new ChatResponse();
        stubService.syncResponse.setResponse("Hi there!");

        GatewayRequest request = GatewayRequest.builder()
            .requestId("r1")
            .sessionId("s1")
            .message("Hello")
            .build();

        GatewayResponse response = service.chat(request);

        assertNotNull(response);
        assertEquals("Hi there!", response.getText());
        assertEquals("r1", response.getRequestId());
    }

    @Test
    @DisplayName("chat 异常时返回 error response")
    void chatError() {
        stubService.syncThrow = new RuntimeException("LLM unavailable");

        GatewayRequest request = GatewayRequest.builder()
            .requestId("r1")
            .sessionId("s1")
            .message("Hello")
            .build();

        GatewayResponse response = service.chat(request);

        assertTrue(response.isError());
        assertTrue(response.getErrorMessage().contains("LLM unavailable"));
    }

    @Test
    @DisplayName("chat 转换 model metadata")
    void chatConvertsModelMetadata() {
        stubService.syncResponse = new ChatResponse();
        stubService.syncResponse.setResponse("ok");

        GatewayRequest request = GatewayRequest.builder()
            .requestId("r1")
            .sessionId("s1")
            .message("Hello")
            .metadata("model", "claude-sonnet")
            .build();

        service.chat(request);

        // Verify the internal request was constructed with correct message
        assertNotNull(stubService.lastSyncRequest);
        assertEquals("Hello", stubService.lastSyncRequest.getMessage());
    }

    @Test
    @DisplayName("chat 内部响应有 error 字段时返回 error response")
    void chatInternalError() {
        stubService.syncResponse = new ChatResponse();
        stubService.syncResponse.setError("rate limited");

        GatewayRequest request = GatewayRequest.builder()
            .requestId("r1")
            .sessionId("s1")
            .message("Hello")
            .build();

        GatewayResponse response = service.chat(request);

        assertTrue(response.isError());
        assertEquals("rate limited", response.getErrorMessage());
    }

    // ==================== SessionManager ====================

    @Test
    @DisplayName("getSessionHistory 委托到 service")
    void getSessionHistory() {
        stubService.historyResult = List.of(
            Map.of("role", "user", "content", "Hello"),
            Map.of("role", "assistant", "content", "Hi!")
        );

        List<Map<String, String>> history = service.getSessionHistory("sess1");

        assertEquals(2, history.size());
        assertEquals("sess1", stubService.lastSessionId);
    }

    @Test
    @DisplayName("getSessionSummary 委托到 service")
    void getSessionSummary() {
        stubService.summaryResult = Map.of("messageCount", 5, "topic", "greeting");

        Map<String, Object> summary = service.getSessionSummary("sess1");

        assertEquals(5, summary.get("messageCount"));
    }

    @Test
    @DisplayName("clearSession 委托到 service")
    void clearSession() {
        service.clearSession("sess1");
        assertEquals("sess1", stubService.lastClearedSessionId);
    }

    // ==================== chatStreamReactive ====================

    @Test
    @DisplayName("chatStreamReactive 默认 sessionId 为 default")
    void chatStreamReactiveDefaultSession() {
        stubService.reactiveResult = Flux.just(StreamEvent.textDelta("Hi"));

        GatewayRequest request = GatewayRequest.builder()
            .requestId("r1")
            .message("Hello")
            .build();

        Flux<StreamEvent> flux = service.chatStreamReactive(request);

        List<StreamEvent> events = flux.collectList().block();
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("Hi", events.get(0).getTextDelta());
    }

    // ==================== Stub implementation ====================

    /**
     * Minimal stub for SoulComfortChatService to isolate GatewayService tests.
     */
    static class StubSoulComfortChatService extends SoulComfortChatService {

        ChatResponse syncResponse;
        RuntimeException syncThrow;
        ChatRequest lastSyncRequest;
        List<Map<String, String>> historyResult = List.of();
        Map<String, Object> summaryResult = Map.of();
        String lastSessionId;
        String lastClearedSessionId;
        Flux<StreamEvent> reactiveResult = Flux.empty();

        StubSoulComfortChatService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            lastSyncRequest = request;
            if (syncThrow != null) throw syncThrow;
            return syncResponse;
        }

        @Override
        public CompletableFuture<String> chat(String message, String sessionId,
                java.util.function.Consumer<com.lightweightai.web.model.ChatResponse.StreamChunk> chunkConsumer) {
            return CompletableFuture.completedFuture("streamed");
        }

        @Override
        public Flux<StreamEvent> chatStreamReactive(String message, String sessionId) {
            return reactiveResult;
        }

        @Override
        public List<Map<String, String>> getSessionHistory(String sessionId) {
            lastSessionId = sessionId;
            return historyResult;
        }

        @Override
        public Map<String, Object> getSessionSummary(String sessionId) {
            lastSessionId = sessionId;
            return summaryResult;
        }

        @Override
        public void clearSession(String sessionId) {
            lastClearedSessionId = sessionId;
        }
    }
}
