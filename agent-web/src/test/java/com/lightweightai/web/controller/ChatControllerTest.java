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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ChatController} — REST API surface for chat operations.
 *
 * Covers: sync chat, health endpoint, session management, event conversion.
 * Streaming endpoints (SSE) are harder to unit test; focus on request/response logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController - REST API")
class ChatControllerTest {

    @Mock private ChatService chatService;
    @Mock private Gateway gateway;
    @Mock private LLMProvider llmProvider;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService, gateway, llmProvider, new ObjectMapper());
    }

    private ChatRequest createRequest(String message, String sessionId) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setSessionId(sessionId);
        return request;
    }

    // ==================== POST /api/chat ====================

    @Nested
    @DisplayName("POST /api/chat - 同步聊天")
    class SyncChatTests {

        @Test
        @DisplayName("成功响应 — 返回文本和技能信息")
        void chat_success_returnsTextAndMetadata() {
            GatewayResponse gwResp = GatewayResponse.builder()
                .requestId("req-1")
                .sessionId("sess-1")
                .text("Hello there!")
                .latencyMs(100)
                .metadata("skillsApplied", List.of("soul-comfort"))
                .build();

            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gwResp);

            ChatResponse response = controller.chat(createRequest("Hi", "sess-1"));

            assertEquals("Hello there!", response.getResponse());
            assertNull(response.getError());
            assertNotNull(response.getSkillsApplied());
            assertEquals(1, response.getSkillsApplied().size());
        }

        @Test
        @DisplayName("Gateway 返回错误 — 返回 error response")
        void chat_gatewayError_returnsError() {
            GatewayResponse gwResp = GatewayResponse.error("req-1", "sess-1", "LLM unavailable");
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gwResp);

            ChatResponse response = controller.chat(createRequest("Hi", "sess-1"));

            assertNotNull(response.getError());
        }

        @Test
        @DisplayName("元数据传递 — model 设置到 GatewayRequest")
        void chat_passesModelToGateway() {
            GatewayResponse gwResp = GatewayResponse.builder()
                .requestId("req-1").text("OK").latencyMs(50).build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gwResp);

            ChatRequest request = createRequest("Hi", "sess-1");
            request.setModel("claude-3-opus");

            controller.chat(request);

            verify(gateway).handle(argThat(gr ->
                "claude-3-opus".equals(gr.getMetadata().get("model"))
            ));
        }
    }

    // ==================== GET /api/skills / /api/tools ====================

    @Nested
    @DisplayName("Skills and Tools endpoints")
    class SkillsToolsTests {

        @Test
        @DisplayName("GET /api/skills — 代理到 ChatService")
        void getSkills_delegatesToChatService() {
            List<Map<String, String>> mockSkills = List.of(
                Map.of("name", "soul-comfort", "description", "心灵引导")
            );
            when(chatService.getAvailableSkills()).thenReturn(mockSkills);

            List<Map<String, String>> result = controller.getSkills();

            assertEquals(1, result.size());
            assertEquals("soul-comfort", result.get(0).get("name"));
        }

        @Test
        @DisplayName("GET /api/tools — 代理到 ChatService")
        void getTools_delegatesToChatService() {
            List<Map<String, Object>> mockTools = List.of(
                Map.of("name", "weather", "description", "查天气")
            );
            when(chatService.getAvailableTools()).thenReturn(mockTools);

            List<Map<String, Object>> result = controller.getTools();
            assertEquals(1, result.size());
        }
    }

    // ==================== GET /api/health ====================

    @Nested
    @DisplayName("GET /api/health - 健康检查")
    class HealthTests {

        @Test
        @DisplayName("基本 LLM provider — 返回 NOT_MONITORED 状态")
        void health_basicProvider_returnsNotMonitored() {
            when(llmProvider.getProviderName()).thenReturn("claude");
            when(chatService.getAvailableSkills()).thenReturn(List.of());
            when(chatService.getAvailableTools()).thenReturn(List.of());

            Map<String, Object> health = controller.health();

            assertEquals("UP", health.get("status"));
            assertEquals("enabled", health.get("soulComfortMode"));

            @SuppressWarnings("unchecked")
            Map<String, Object> llmHealth = (Map<String, Object>) health.get("llm");
            assertEquals("NOT_MONITORED", llmHealth.get("status"));
            assertEquals("claude", llmHealth.get("provider"));
        }
    }

    // ==================== Session management ====================

    @Nested
    @DisplayName("Session 管理端点")
    class SessionTests {

        @Test
        @DisplayName("GET /session/{id}/history — SessionManager 为 null 返回空")
        void getHistory_noSessionManager_returnsEmpty() {
            when(gateway.getSessionManager()).thenReturn(null);

            List<Map<String, String>> result = controller.getSessionHistory("sess-1");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("GET /session/{id}/history — 代理到 SessionManager")
        void getHistory_delegatesSessionManager() {
            SessionManager sm = mock(SessionManager.class);
            when(sm.getSessionHistory("sess-1")).thenReturn(List.of(
                Map.of("role", "user", "content", "Hello")
            ));
            when(gateway.getSessionManager()).thenReturn(sm);

            List<Map<String, String>> result = controller.getSessionHistory("sess-1");
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("GET /session/{id}/summary — SessionManager 为 null 返回 minimal")
        void getSummary_noSessionManager_returnsMinimal() {
            when(gateway.getSessionManager()).thenReturn(null);

            Map<String, Object> result = controller.getSessionSummary("sess-1");
            assertEquals("sess-1", result.get("sessionId"));
        }

        @Test
        @DisplayName("DELETE /session/{id} — SessionManager 为 null 不抛异常")
        void clearSession_noSessionManager_doesNotThrow() {
            when(gateway.getSessionManager()).thenReturn(null);

            Map<String, Object> result = controller.clearSession("sess-1");
            assertEquals("cleared", result.get("status"));
        }

        @Test
        @DisplayName("DELETE /session/{id} — 代理到 SessionManager.clearSession")
        void clearSession_delegatesSessionManager() {
            SessionManager sm = mock(SessionManager.class);
            when(gateway.getSessionManager()).thenReturn(sm);

            controller.clearSession("sess-1");

            verify(sm).clearSession("sess-1");
        }
    }
}
