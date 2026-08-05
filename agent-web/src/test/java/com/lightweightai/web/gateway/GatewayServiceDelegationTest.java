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

/**
 * GatewayService SessionManager 委托测试。
 *
 * 已有 GatewayServiceTest 覆盖基本功能。
 * 此测试确保 SessionManager 接口的三个方法都正确委托到 SoulComfortChatService，
 * 且返回值和参数无丢失（CLAUDE.md UT 规则 1：断言载荷）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayService - SessionManager 委托链路")
class GatewayServiceDelegationTest {

    @Mock
    private SoulComfortChatService soulComfortService;

    private GatewayService gatewayService;

    @BeforeEach
    void setUp() {
        gatewayService = new GatewayService(soulComfortService);
    }

    @Test
    @DisplayName("getSessionHistory 委托且返回值透传")
    void getSessionHistoryDelegatesAndReturnsPayload() {
        List<Map<String, String>> expected = List.of(
                Map.of("role", "user", "content", "hi"),
                Map.of("role", "assistant", "content", "hello")
        );
        when(soulComfortService.getSessionHistory("sess-1")).thenReturn(expected);

        List<Map<String, String>> result = gatewayService.getSessionHistory("sess-1");

        assertSame(expected, result, "Return value must be transparently delegated");
        verify(soulComfortService).getSessionHistory("sess-1");
    }

    @Test
    @DisplayName("getSessionSummary 委托且返回值透传")
    void getSessionSummaryDelegatesAndReturnsPayload() {
        Map<String, Object> expected = Map.of("messageCount", 10, "lastActive", "2024-01-01");
        when(soulComfortService.getSessionSummary("sess-2")).thenReturn(expected);

        Map<String, Object> result = gatewayService.getSessionSummary("sess-2");

        assertSame(expected, result);
        verify(soulComfortService).getSessionSummary("sess-2");
    }

    @Test
    @DisplayName("clearSession 委托到 soulComfortService.clearSession")
    void clearSessionDelegates() {
        gatewayService.clearSession("sess-3");

        verify(soulComfortService).clearSession("sess-3");
    }

    @Test
    @DisplayName("sessionId 参数原样传递，不做转换")
    void sessionIdPassedAsIs() {
        when(soulComfortService.getSessionHistory(anyString())).thenReturn(List.of());

        gatewayService.getSessionHistory("agent:default:main:abc-123");
        verify(soulComfortService).getSessionHistory("agent:default:main:abc-123");
    }
}
