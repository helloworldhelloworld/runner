package com.lightweightai.web.gateway;

import com.lightweightai.web.service.SoulComfortChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayService - SessionManager 实现")
class GatewayServiceTest {

    @Mock
    private SoulComfortChatService soulComfortService;

    private GatewayService gatewayService;

    @BeforeEach
    void setUp() {
        gatewayService = new GatewayService(soulComfortService);
    }

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
