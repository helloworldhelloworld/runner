package com.lightweightai.web.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.service.SoulComfortChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("GatewayService - ChatHandler + SessionManager adapter")
class GatewayServiceTest {

    private SoulComfortChatService soulComfortService;
    private GatewayService gatewayService;

    @BeforeEach
    void setUp() {
        soulComfortService = mock(SoulComfortChatService.class);
        gatewayService = new GatewayService(soulComfortService);
    }

    @Nested
    @DisplayName("chat() - synchronous")
    class SyncChat {

        @Test
        @DisplayName("successful chat returns GatewayResponse with text")
        void successfulChatReturnsResponse() {
            GatewayRequest request = GatewayRequest.builder()
                .requestId("req-1")
                .sessionId("sess-1")
                .message("hello")
                .build();

            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setResponse("Hi there!");
            chatResponse.setSkillsApplied(List.of("soul-comfort"));
            chatResponse.setMetadata(Map.of("provider", "claude"));

            when(soulComfortService.chat(any(ChatRequest.class))).thenReturn(chatResponse);

            GatewayResponse result = gatewayService.chat(request);

            assertFalse(result.isError());
            assertEquals("Hi there!", result.getText());
            assertEquals("req-1", result.getRequestId());
            assertEquals("sess-1", result.getSessionId());
        }

        @Test
        @DisplayName("chat error response propagates error")
        void chatErrorPropagatesError() {
            GatewayRequest request = GatewayRequest.builder()
                .requestId("req-2")
                .sessionId("sess-2")
                .message("hello")
                .build();

            ChatResponse errorResponse = ChatResponse.error("LLM timeout");
            when(soulComfortService.chat(any(ChatRequest.class))).thenReturn(errorResponse);

            GatewayResponse result = gatewayService.chat(request);

            assertTrue(result.isError());
        }

        @Test
        @DisplayName("exception during chat returns error response")
        void exceptionReturnsErrorResponse() {
            GatewayRequest request = GatewayRequest.builder()
                .requestId("req-3")
                .sessionId("sess-3")
                .message("hello")
                .build();

            when(soulComfortService.chat(any(ChatRequest.class)))
                .thenThrow(new RuntimeException("Connection failed"));

            GatewayResponse result = gatewayService.chat(request);

            assertTrue(result.isError());
            assertEquals("Connection failed", result.getErrorMessage());
        }

        @Test
        @DisplayName("toInternal sets soulComfortMode=true and propagates model from metadata")
        void toInternalSetsCorrectFields() {
            GatewayRequest request = GatewayRequest.builder()
                .requestId("req-4")
                .sessionId("sess-4")
                .message("test message")
                .metadata("model", "claude-3.5-sonnet")
                .build();

            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setResponse("ok");
            when(soulComfortService.chat(any(ChatRequest.class))).thenAnswer(inv -> {
                ChatRequest internal = inv.getArgument(0);
                assertEquals("test message", internal.getMessage());
                assertEquals("sess-4", internal.getSessionId());
                assertTrue(internal.isSoulComfortMode());
                assertEquals("claude-3.5-sonnet", internal.getModel());
                return chatResponse;
            });

            gatewayService.chat(request);
            verify(soulComfortService).chat(any(ChatRequest.class));
        }
    }

    @Nested
    @DisplayName("chatStreamReactive() - reactive streaming")
    class ReactiveStream {

        @Test
        @DisplayName("returns Flux from underlying service")
        void returnsFluxFromService() {
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-5")
                .message("stream me")
                .build();

            StreamEvent event = StreamEvent.textDelta("hello");
            when(soulComfortService.chatStreamReactive("stream me", "sess-5"))
                .thenReturn(Flux.just(event));

            Flux<StreamEvent> result = gatewayService.chatStreamReactive(request);
            List<StreamEvent> events = result.collectList().block();

            assertNotNull(events);
            assertEquals(1, events.size());
        }

        @Test
        @DisplayName("null sessionId defaults to 'default'")
        void nullSessionIdDefaultsToDefault() {
            GatewayRequest request = GatewayRequest.builder()
                .message("test")
                .build();

            when(soulComfortService.chatStreamReactive("test", "default"))
                .thenReturn(Flux.empty());

            gatewayService.chatStreamReactive(request);
            verify(soulComfortService).chatStreamReactive("test", "default");
        }
    }

    @Nested
    @DisplayName("SessionManager methods")
    class SessionManagerMethods {

        @Test
        @DisplayName("getSessionHistory delegates to service")
        void getSessionHistoryDelegates() {
            List<Map<String, String>> history = List.of(Map.of("role", "user", "content", "hi"));
            when(soulComfortService.getSessionHistory("sess-1")).thenReturn(history);

            List<Map<String, String>> result = gatewayService.getSessionHistory("sess-1");

            assertEquals(1, result.size());
            assertEquals("hi", result.get(0).get("content"));
        }

        @Test
        @DisplayName("getSessionSummary delegates to service")
        void getSessionSummaryDelegates() {
            Map<String, Object> summary = Map.of("messageCount", 5);
            when(soulComfortService.getSessionSummary("sess-2")).thenReturn(summary);

            Map<String, Object> result = gatewayService.getSessionSummary("sess-2");

            assertEquals(5, result.get("messageCount"));
        }

        @Test
        @DisplayName("clearSession delegates to service")
        void clearSessionDelegates() {
            gatewayService.clearSession("sess-3");
            verify(soulComfortService).clearSession("sess-3");
        }
    }
}
