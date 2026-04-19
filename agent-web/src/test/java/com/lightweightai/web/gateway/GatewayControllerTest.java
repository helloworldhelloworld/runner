package com.lightweightai.web.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayController - Gateway REST 协议适配器")
class GatewayControllerTest {

    @Mock
    private Gateway gateway;

    private GatewayController controller;

    @BeforeEach
    void setUp() {
        controller = new GatewayController(gateway, new ObjectMapper());
    }

    private UnifiedChatRequest createRequest(String message, String sessionId) {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setMessage(message);
        request.setSessionId(sessionId);
        request.setClientType(UnifiedChatRequest.ClientType.WEB);
        return request;
    }

    @Nested
    @DisplayName("POST /gateway/chat - 同步聊天")
    class SyncChatTests {

        @Test
        @DisplayName("成功响应")
        void shouldReturnFullResponse() {
            GatewayResponse gatewayResponse = GatewayResponse.builder()
                    .requestId("req-1")
                    .sessionId("sess-1")
                    .text("Hello!")
                    .latencyMs(100)
                    .build();

            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResponse);

            UnifiedChatRequest request = createRequest("Hi", "sess-1");
            UnifiedChatResponse response = controller.chat(request);

            assertEquals(UnifiedChatResponse.ResponseType.FULL, response.getType());
            assertEquals("Hello!", response.getContent());
            assertFalse(response.isError());
            assertEquals(100, response.getLatencyMs());
        }

        @Test
        @DisplayName("Gateway 返回错误响应")
        void shouldHandleErrorResponse() {
            GatewayResponse gatewayResponse = GatewayResponse.error("req-1", "sess-1", "Service unavailable");
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResponse);

            UnifiedChatRequest request = createRequest("Hi", "sess-1");
            UnifiedChatResponse response = controller.chat(request);

            assertEquals(UnifiedChatResponse.ResponseType.ERROR, response.getType());
            assertTrue(response.isError());
            assertEquals("Service unavailable", response.getErrorMessage());
        }

        @Test
        @DisplayName("Gateway 抛出异常")
        void shouldHandleException() {
            when(gateway.handle(any(GatewayRequest.class)))
                    .thenThrow(new RuntimeException("Connection failed"));

            UnifiedChatRequest request = createRequest("Hi", "sess-1");
            UnifiedChatResponse response = controller.chat(request);

            assertEquals(UnifiedChatResponse.ResponseType.ERROR, response.getType());
            assertTrue(response.isError());
            assertEquals("Connection failed", response.getErrorMessage());
        }

        @Test
        @DisplayName("响应包含 skillsApplied 元数据")
        void shouldIncludeSkillsApplied() {
            GatewayResponse gatewayResponse = GatewayResponse.builder()
                    .requestId("req-1")
                    .sessionId("sess-1")
                    .text("I see you're stressed")
                    .latencyMs(200)
                    .metadata("skillsApplied", List.of("crisis_detection", "pss10"))
                    .build();

            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResponse);

            UnifiedChatRequest request = createRequest("I'm feeling terrible", "sess-1");
            UnifiedChatResponse response = controller.chat(request);

            assertNotNull(response.getSkillsApplied());
            assertEquals(2, response.getSkillsApplied().size());
            assertTrue(response.getSkillsApplied().contains("crisis_detection"));
        }

        @Test
        @DisplayName("latencyMs 为 0 时使用计算值")
        void shouldCalculateLatencyWhenZero() {
            GatewayResponse gatewayResponse = GatewayResponse.builder()
                    .requestId("req-1")
                    .sessionId("sess-1")
                    .text("OK")
                    .latencyMs(0)
                    .build();

            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResponse);

            UnifiedChatRequest request = createRequest("Hi", "sess-1");
            UnifiedChatResponse response = controller.chat(request);

            assertTrue(response.getLatencyMs() >= 0);
        }
    }

    @Nested
    @DisplayName("GET /gateway/health - 健康检查")
    class HealthCheckTests {

        @Test
        @DisplayName("返回 UP 状态")
        void shouldReturnHealthStatus() {
            Map<String, Object> health = controller.health();

            assertEquals("UP", health.get("status"));
            assertEquals("active", health.get("gateway"));
            assertEquals("1.0.0", health.get("version"));
            assertNotNull(health.get("supportedClients"));
        }
    }

    @Nested
    @DisplayName("会话管理端点")
    class SessionManagementTests {

        @Test
        @DisplayName("获取会话历史 - SessionManager 为 null")
        void shouldReturnEmptyHistoryWhenNoSessionManager() {
            when(gateway.getSessionManager()).thenReturn(null);

            Map<String, Object> result = controller.getSessionHistory("sess-1");

            assertEquals("sess-1", result.get("sessionId"));
            assertEquals(List.of(), result.get("history"));
        }

        @Test
        @DisplayName("获取会话历史 - 有 SessionManager")
        void shouldDelegateToSessionManager() {
            SessionManager sm = mock(SessionManager.class);
            when(sm.getSessionHistory("sess-1")).thenReturn(List.of("msg1", "msg2"));
            when(gateway.getSessionManager()).thenReturn(sm);

            Map<String, Object> result = controller.getSessionHistory("sess-1");
            assertEquals("sess-1", result.get("sessionId"));
        }

        @Test
        @DisplayName("获取会话摘要 - SessionManager 为 null")
        void shouldReturnMinimalSummaryWhenNoSessionManager() {
            when(gateway.getSessionManager()).thenReturn(null);

            Map<String, Object> result = controller.getSessionSummary("sess-1");
            assertEquals("sess-1", result.get("sessionId"));
        }

        @Test
        @DisplayName("获取会话摘要 - 有 SessionManager")
        void shouldDelegateToSessionManagerForSummary() {
            SessionManager sm = mock(SessionManager.class);
            when(sm.getSessionSummary("sess-1")).thenReturn(Map.of("sessionId", "sess-1", "turns", 5));
            when(gateway.getSessionManager()).thenReturn(sm);

            Map<String, Object> result = controller.getSessionSummary("sess-1");
            assertEquals("sess-1", result.get("sessionId"));
            assertEquals(5, result.get("turns"));
        }

        @Test
        @DisplayName("清空会话 - SessionManager 为 null 时不抛异常")
        void shouldNotThrowWhenClearingWithoutSessionManager() {
            when(gateway.getSessionManager()).thenReturn(null);

            Map<String, Object> result = controller.clearSession("sess-1");
            assertEquals("sess-1", result.get("sessionId"));
            assertEquals(true, result.get("cleared"));
        }

        @Test
        @DisplayName("清空会话 - 有 SessionManager")
        void shouldDelegateClearToSessionManager() {
            SessionManager sm = mock(SessionManager.class);
            when(gateway.getSessionManager()).thenReturn(sm);

            Map<String, Object> result = controller.clearSession("sess-1");

            verify(sm).clearSession("sess-1");
            assertEquals(true, result.get("cleared"));
        }
    }

    @Nested
    @DisplayName("请求转换")
    class RequestConversionTests {

        @Test
        @DisplayName("model 设置到 metadata")
        void shouldSetModelInMetadata() {
            GatewayResponse gatewayResponse = GatewayResponse.builder()
                    .requestId("req-1")
                    .text("OK")
                    .latencyMs(50)
                    .build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResponse);

            UnifiedChatRequest request = createRequest("Hi", "sess-1");
            request.setModel("claude-3-opus");

            controller.chat(request);

            verify(gateway).handle(argThat(gr ->
                    "claude-3-opus".equals(gr.getMetadata().get("model"))
            ));
        }

        @Test
        @DisplayName("clientType 设置到 metadata")
        void shouldSetClientTypeInMetadata() {
            GatewayResponse gatewayResponse = GatewayResponse.builder()
                    .requestId("req-1")
                    .text("OK")
                    .latencyMs(50)
                    .build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResponse);

            UnifiedChatRequest request = createRequest("Hi", "sess-1");
            request.setClientType(UnifiedChatRequest.ClientType.IOS);

            controller.chat(request);

            verify(gateway).handle(argThat(gr ->
                    "IOS".equals(gr.getMetadata().get("clientType"))
            ));
        }

        @Test
        @DisplayName("extra 参数合并到 metadata")
        void shouldMergeExtraIntoMetadata() {
            GatewayResponse gatewayResponse = GatewayResponse.builder()
                    .requestId("req-1")
                    .text("OK")
                    .latencyMs(50)
                    .build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResponse);

            UnifiedChatRequest request = createRequest("Hi", "sess-1");
            request.setExtra(Map.of("custom_key", "custom_value"));

            controller.chat(request);

            verify(gateway).handle(argThat(gr ->
                    "custom_value".equals(gr.getMetadata().get("custom_key"))
            ));
        }
    }
}
