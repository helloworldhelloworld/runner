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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayService - adapts SoulComfortChatService to ChatHandler + SessionManager")
class GatewayServiceTest {

    @Mock
    private SoulComfortChatService soulComfortService;

    private GatewayService gatewayService;

    @BeforeEach
    void setUp() {
        gatewayService = new GatewayService(soulComfortService);
    }

    // ==================== chat() ====================

    @Test
    @DisplayName("chat() converts GatewayRequest to ChatRequest and delegates to service")
    void chatDelegatesToService() {
        GatewayRequest request = GatewayRequest.builder()
            .message("hello")
            .sessionId("s1")
            .requestId("r1")
            .build();

        ChatResponse chatResponse = ChatResponse.success("Hi there!");
        chatResponse.setSkillsApplied(List.of("soul-comfort"));
        when(soulComfortService.chat(any(ChatRequest.class))).thenReturn(chatResponse);

        GatewayResponse result = gatewayService.chat(request);

        assertNotNull(result);
        assertEquals("r1", result.getRequestId());
        assertEquals("s1", result.getSessionId());
        assertEquals("Hi there!", result.getText());
        assertFalse(result.isError());

        // Verify the ChatRequest was built correctly
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(soulComfortService).chat(captor.capture());
        ChatRequest internal = captor.getValue();
        assertEquals("hello", internal.getMessage());
        assertEquals("s1", internal.getSessionId());
        assertTrue(internal.isSoulComfortMode());
    }

    @Test
    @DisplayName("chat() handles exception and returns error GatewayResponse")
    void chatHandlesException() {
        GatewayRequest request = GatewayRequest.builder()
            .message("hello")
            .sessionId("s1")
            .requestId("r1")
            .build();

        when(soulComfortService.chat(any(ChatRequest.class)))
            .thenThrow(new RuntimeException("LLM timeout"));

        GatewayResponse result = gatewayService.chat(request);

        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals("LLM timeout", result.getErrorMessage());
        assertEquals("r1", result.getRequestId());
        assertEquals("s1", result.getSessionId());
    }

    // ==================== chatStreamReactive() ====================

    @Test
    @DisplayName("chatStreamReactive() delegates to soulComfortService.chatStreamReactive()")
    void chatStreamReactiveDelegates() {
        GatewayRequest request = GatewayRequest.builder()
            .message("hi")
            .sessionId("s1")
            .requestId("r1")
            .build();

        Flux<StreamEvent> expectedFlux = Flux.just(
            StreamEvent.textDelta("Hello "),
            StreamEvent.textDelta("world!")
        );
        when(soulComfortService.chatStreamReactive("hi", "s1")).thenReturn(expectedFlux);

        Flux<StreamEvent> result = gatewayService.chatStreamReactive(request);

        List<StreamEvent> events = result.collectList().block();
        assertNotNull(events);
        assertEquals(2, events.size());

        verify(soulComfortService).chatStreamReactive("hi", "s1");
    }

    // ==================== SessionManager methods ====================

    @Test
    @DisplayName("getSessionHistory() delegates to service")
    void getSessionHistoryDelegates() {
        List<Map<String, String>> history = List.of(
            Map.of("role", "user", "content", "hello"),
            Map.of("role", "assistant", "content", "hi")
        );
        when(soulComfortService.getSessionHistory("s1")).thenReturn(history);

        List<Map<String, String>> result = gatewayService.getSessionHistory("s1");

        assertEquals(2, result.size());
        assertEquals("hello", result.get(0).get("content"));
        verify(soulComfortService).getSessionHistory("s1");
    }

    @Test
    @DisplayName("getSessionSummary() delegates to service")
    void getSessionSummaryDelegates() {
        Map<String, Object> summary = Map.of("messageCount", 5, "sessionId", "s1");
        when(soulComfortService.getSessionSummary("s1")).thenReturn(summary);

        Map<String, Object> result = gatewayService.getSessionSummary("s1");

        assertEquals(5, result.get("messageCount"));
        verify(soulComfortService).getSessionSummary("s1");
    }

    @Test
    @DisplayName("clearSession() delegates to service")
    void clearSessionDelegates() {
        gatewayService.clearSession("s1");

        verify(soulComfortService).clearSession("s1");
    }
}
