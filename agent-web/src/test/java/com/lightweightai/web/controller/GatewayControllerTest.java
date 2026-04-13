package com.lightweightai.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.web.gateway.GatewayController;
import com.lightweightai.web.gateway.UnifiedChatRequest;
import com.lightweightai.web.gateway.UnifiedChatResponse;
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
@DisplayName("GatewayController - unified chat protocol adapter")
class GatewayControllerTest {

    @Mock private Gateway gateway;

    private GatewayController controller;

    @BeforeEach
    void setUp() {
        controller = new GatewayController(gateway, new ObjectMapper());
    }

    // ==================== chat (sync) ====================

    @Test
    @DisplayName("chat delegates to gateway and returns unified response")
    void chatDelegatesToGateway() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setMessage("hello");
        request.setSessionId("s1");
        request.setClientType(UnifiedChatRequest.ClientType.WEB);

        GatewayResponse gatewayResponse = GatewayResponse.builder()
            .text("Hi there!")
            .metadata("skillsApplied", List.of("soul-comfort"))
            .build();
        when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResponse);

        UnifiedChatResponse result = controller.chat(request);

        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals("Hi there!", result.getContent());

        ArgumentCaptor<GatewayRequest> captor = ArgumentCaptor.forClass(GatewayRequest.class);
        verify(gateway).handle(captor.capture());
        assertEquals("hello", captor.getValue().getMessage());
        assertEquals("s1", captor.getValue().getSessionId());
    }

    @Test
    @DisplayName("chat returns error response when gateway returns error")
    void chatReturnsErrorOnGatewayError() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setMessage("hello");

        GatewayResponse errorResponse = GatewayResponse.builder()
            .error(true)
            .errorMessage("Service unavailable")
            .build();
        when(gateway.handle(any(GatewayRequest.class))).thenReturn(errorResponse);

        UnifiedChatResponse result = controller.chat(request);

        assertTrue(result.isError());
        assertEquals("Service unavailable", result.getErrorMessage());
    }

    @Test
    @DisplayName("chat returns error response on exception")
    void chatReturnsErrorOnException() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setMessage("hello");

        when(gateway.handle(any(GatewayRequest.class)))
            .thenThrow(new RuntimeException("Connection refused"));

        UnifiedChatResponse result = controller.chat(request);

        assertTrue(result.isError());
        assertEquals("Connection refused", result.getErrorMessage());
    }

    // ==================== health ====================

    @Test
    @DisplayName("health returns UP status with gateway info")
    void healthReturnsUpStatus() {
        Map<String, Object> result = controller.health();

        assertEquals("UP", result.get("status"));
        assertEquals("active", result.get("gateway"));
        assertEquals("1.0.0", result.get("version"));
        assertNotNull(result.get("supportedClients"));
    }

    // ==================== session endpoints ====================

    @Test
    @DisplayName("getSessionHistory delegates to session manager")
    void getSessionHistoryDelegates() {
        SessionManager sm = mock(SessionManager.class);
        when(gateway.getSessionManager()).thenReturn(sm);
        when(sm.getSessionHistory("s1")).thenReturn(List.of(
            Map.of("role", "user", "content", "hi")
        ));

        Map<String, Object> result = controller.getSessionHistory("s1");

        assertEquals("s1", result.get("sessionId"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) result.get("history");
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("getSessionHistory returns empty when no session manager")
    void getSessionHistoryReturnsEmptyWithoutSM() {
        when(gateway.getSessionManager()).thenReturn(null);

        Map<String, Object> result = controller.getSessionHistory("s1");

        assertEquals("s1", result.get("sessionId"));
        @SuppressWarnings("unchecked")
        List<?> history = (List<?>) result.get("history");
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("getSessionSummary delegates to session manager")
    void getSessionSummaryDelegates() {
        SessionManager sm = mock(SessionManager.class);
        when(gateway.getSessionManager()).thenReturn(sm);
        when(sm.getSessionSummary("s1")).thenReturn(Map.of("count", 5));

        Map<String, Object> result = controller.getSessionSummary("s1");

        assertEquals(5, result.get("count"));
    }

    @Test
    @DisplayName("getSessionSummary returns minimal map without session manager")
    void getSessionSummaryNoSM() {
        when(gateway.getSessionManager()).thenReturn(null);

        Map<String, Object> result = controller.getSessionSummary("s1");

        assertEquals("s1", result.get("sessionId"));
    }

    @Test
    @DisplayName("clearSession delegates and returns confirmation")
    void clearSessionDelegates() {
        SessionManager sm = mock(SessionManager.class);
        when(gateway.getSessionManager()).thenReturn(sm);

        Map<String, Object> result = controller.clearSession("s1");

        assertEquals("s1", result.get("sessionId"));
        assertEquals(true, result.get("cleared"));
        verify(sm).clearSession("s1");
    }

    @Test
    @DisplayName("clearSession succeeds without session manager")
    void clearSessionNoSM() {
        when(gateway.getSessionManager()).thenReturn(null);

        Map<String, Object> result = controller.clearSession("s1");

        assertEquals(true, result.get("cleared"));
    }
}
