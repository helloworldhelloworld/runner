package com.lightweightai.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController - REST API for chat, skills, tools, sessions")
class ChatControllerTest {

    @Mock private ChatService chatService;
    @Mock private Gateway gateway;
    @Mock private LLMProvider llmProvider;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService, gateway, llmProvider, new ObjectMapper());
    }

    // ==================== chat() ====================

    @Test
    @DisplayName("chat delegates to gateway and returns response")
    void chatDelegatesToGateway() {
        ChatRequest request = new ChatRequest();
        request.setMessage("hello");
        request.setSessionId("s1");
        request.setModel("claude-3");

        GatewayResponse gatewayResponse = GatewayResponse.builder()
            .text("Hi there!")
            .metadata("skillsApplied", List.of("soul-comfort"))
            .build();
        when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResponse);

        ChatResponse result = controller.chat(request);

        assertNotNull(result);
        assertEquals("Hi there!", result.getResponse());
        assertNull(result.getError());
        assertEquals(List.of("soul-comfort"), result.getSkillsApplied());

        ArgumentCaptor<GatewayRequest> captor = ArgumentCaptor.forClass(GatewayRequest.class);
        verify(gateway).handle(captor.capture());
        assertEquals("hello", captor.getValue().getMessage());
        assertEquals("s1", captor.getValue().getSessionId());
    }

    @Test
    @DisplayName("chat returns error response when gateway returns error")
    void chatReturnsErrorOnGatewayError() {
        ChatRequest request = new ChatRequest();
        request.setMessage("hello");

        GatewayResponse errorResponse = GatewayResponse.builder()
            .error(true)
            .errorMessage("LLM timeout")
            .build();
        when(gateway.handle(any(GatewayRequest.class))).thenReturn(errorResponse);

        ChatResponse result = controller.chat(request);

        assertNotNull(result);
        assertEquals("LLM timeout", result.getError());
    }

    @Test
    @DisplayName("chat handles response without metadata")
    void chatHandlesNoMetadata() {
        ChatRequest request = new ChatRequest();
        request.setMessage("hello");

        GatewayResponse response = GatewayResponse.builder()
            .text("response text")
            .build();
        when(gateway.handle(any(GatewayRequest.class))).thenReturn(response);

        ChatResponse result = controller.chat(request);

        assertEquals("response text", result.getResponse());
    }

    // ==================== getSkills() ====================

    @Test
    @DisplayName("getSkills delegates to chatService")
    void getSkillsDelegatesToChatService() {
        List<Map<String, String>> skills = List.of(
            Map.of("name", "soul-comfort", "description", "Comfort mode")
        );
        when(chatService.getAvailableSkills()).thenReturn(skills);

        List<Map<String, String>> result = controller.getSkills();

        assertEquals(1, result.size());
        assertEquals("soul-comfort", result.get(0).get("name"));
        verify(chatService).getAvailableSkills();
    }

    // ==================== getTools() ====================

    @Test
    @DisplayName("getTools delegates to chatService")
    void getToolsDelegatesToChatService() {
        List<Map<String, Object>> tools = List.of(
            Map.of("name", "math_add", "description", "Add two numbers")
        );
        when(chatService.getAvailableTools()).thenReturn(tools);

        List<Map<String, Object>> result = controller.getTools();

        assertEquals(1, result.size());
        assertEquals("math_add", result.get(0).get("name"));
        verify(chatService).getAvailableTools();
    }

    // ==================== health() ====================

    @Test
    @DisplayName("health returns UP status with LLM provider info")
    void healthReturnsUpStatusWithLLMInfo() {
        when(chatService.getAvailableSkills()).thenReturn(List.of(Map.of("name", "s1")));
        when(chatService.getAvailableTools()).thenReturn(List.of());
        when(llmProvider.getProviderName()).thenReturn("TestProvider");

        Map<String, Object> result = controller.health();

        assertEquals("UP", result.get("status"));
        assertEquals(1, result.get("skills"));
        assertEquals(0, result.get("tools"));
        assertEquals("enabled", result.get("soulComfortMode"));

        @SuppressWarnings("unchecked")
        Map<String, Object> llm = (Map<String, Object>) result.get("llm");
        assertEquals("TestProvider", llm.get("provider"));
        assertEquals("NOT_MONITORED", llm.get("status"));
    }

    // ==================== session endpoints ====================

    @Test
    @DisplayName("getSessionHistory delegates to session manager")
    void getSessionHistoryDelegates() {
        SessionManager sm = mock(SessionManager.class);
        when(gateway.getSessionManager()).thenReturn(sm);
        List<Map<String, String>> history = List.of(Map.of("role", "user", "content", "hi"));
        when(sm.getSessionHistory("s1")).thenReturn(history);

        List<Map<String, String>> result = controller.getSessionHistory("s1");

        assertEquals(1, result.size());
        assertEquals("hi", result.get(0).get("content"));
    }

    @Test
    @DisplayName("getSessionHistory returns empty when no session manager")
    void getSessionHistoryReturnsEmptyWhenNoSM() {
        when(gateway.getSessionManager()).thenReturn(null);

        List<Map<String, String>> result = controller.getSessionHistory("s1");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getSessionSummary delegates to session manager")
    void getSessionSummaryDelegates() {
        SessionManager sm = mock(SessionManager.class);
        when(gateway.getSessionManager()).thenReturn(sm);
        Map<String, Object> summary = Map.of("messageCount", 5);
        when(sm.getSessionSummary("s1")).thenReturn(summary);

        Map<String, Object> result = controller.getSessionSummary("s1");

        assertEquals(5, result.get("messageCount"));
    }

    @Test
    @DisplayName("getSessionSummary returns minimal map when no session manager")
    void getSessionSummaryNoSM() {
        when(gateway.getSessionManager()).thenReturn(null);

        Map<String, Object> result = controller.getSessionSummary("s1");

        assertEquals("s1", result.get("sessionId"));
    }

    @Test
    @DisplayName("clearSession delegates to session manager")
    void clearSessionDelegates() {
        SessionManager sm = mock(SessionManager.class);
        when(gateway.getSessionManager()).thenReturn(sm);

        Map<String, Object> result = controller.clearSession("s1");

        assertEquals("cleared", result.get("status"));
        assertEquals("s1", result.get("sessionId"));
        verify(sm).clearSession("s1");
    }

    @Test
    @DisplayName("clearSession succeeds when no session manager")
    void clearSessionNoSM() {
        when(gateway.getSessionManager()).thenReturn(null);

        Map<String, Object> result = controller.clearSession("s1");

        assertEquals("cleared", result.get("status"));
    }

    // ==================== toEventMap (via chatStreamReactive) ====================

    @Test
    @DisplayName("toEventMap converts TEXT_DELTA event correctly")
    void toEventMapConvertsDelta() throws Exception {
        // Access private method via reflection
        var method = ChatController.class.getDeclaredMethod("toEventMap", StreamEvent.class);
        method.setAccessible(true);

        StreamEvent event = StreamEvent.textDelta("hello ");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) method.invoke(controller, event);

        assertEquals("text_delta", map.get("type"));
        assertEquals("hello ", map.get("delta"));
    }

    @Test
    @DisplayName("toEventMap converts LLM_COMPLETE event correctly")
    void toEventMapConvertsComplete() throws Exception {
        var method = ChatController.class.getDeclaredMethod("toEventMap", StreamEvent.class);
        method.setAccessible(true);

        StreamEvent event = StreamEvent.llmComplete(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) method.invoke(controller, event);

        assertEquals("llm_complete", map.get("type"));
        assertEquals(true, map.get("complete"));
    }
}
