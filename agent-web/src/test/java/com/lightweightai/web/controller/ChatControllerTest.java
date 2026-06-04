package com.lightweightai.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("ChatController - Chat API 控制器")
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private Gateway gateway;

    @Mock
    private LLMProvider llmProvider;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService, gateway, llmProvider, new ObjectMapper());
    }

    @Nested
    @DisplayName("POST /api/chat - 同步聊天")
    class SyncChat {

        @Test
        @DisplayName("成功响应携带文本和元数据")
        void successfulChatResponse() {
            ChatRequest request = new ChatRequest();
            request.setMessage("你好");
            request.setSessionId("s1");

            GatewayResponse gwResp = GatewayResponse.builder()
                    .text("你好！有什么可以帮你的？")
                    .metadata("stopReason", "end_turn")
                    .build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gwResp);

            ChatResponse response = controller.chat(request);

            assertEquals("你好！有什么可以帮你的？", response.getResponse());
        }

        @Test
        @DisplayName("Gateway 错误响应返回 ChatResponse.error")
        void errorResponse() {
            ChatRequest request = new ChatRequest();
            request.setMessage("test");

            GatewayResponse gwResp = GatewayResponse.error("req-1", "s1", "Provider unavailable");
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gwResp);

            ChatResponse response = controller.chat(request);

            assertNotNull(response);
        }

        @Test
        @DisplayName("请求中的 sessionId 和 message 正确传递给 Gateway")
        void requestFieldsPassedThrough() {
            ChatRequest request = new ChatRequest();
            request.setMessage("分析数据");
            request.setSessionId("my-session");
            request.setModel("claude-3-opus");

            GatewayResponse gwResp = GatewayResponse.builder().text("ok").build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gwResp);

            controller.chat(request);

            ArgumentCaptor<GatewayRequest> captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(gateway).handle(captor.capture());
            GatewayRequest captured = captor.getValue();

            assertEquals("分析数据", captured.getMessage());
            assertEquals("my-session", captured.getSessionId());
            assertEquals("claude-3-opus", captured.getMetadata().get("model"));
        }

        @Test
        @DisplayName("metadata 中的 skillsApplied 正确传递到响应")
        void skillsAppliedInMetadata() {
            ChatRequest request = new ChatRequest();
            request.setMessage("test");

            GatewayResponse gwResp = GatewayResponse.builder()
                    .text("done")
                    .metadata("skillsApplied", List.of("search", "memory"))
                    .build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gwResp);

            ChatResponse response = controller.chat(request);

            assertEquals("done", response.getResponse());
            assertNotNull(response.getSkillsApplied());
            assertEquals(2, response.getSkillsApplied().size());
            assertTrue(response.getSkillsApplied().contains("search"));
        }
    }

    @Nested
    @DisplayName("GET /api/skills 和 /api/tools")
    class SkillsAndTools {

        @Test
        @DisplayName("返回可用 skills 列表")
        void getSkills() {
            when(chatService.getAvailableSkills()).thenReturn(
                    List.of(Map.of("name", "search", "description", "搜索")));

            List<Map<String, String>> skills = controller.getSkills();

            assertEquals(1, skills.size());
            assertEquals("search", skills.get(0).get("name"));
        }

        @Test
        @DisplayName("返回可用 tools 列表")
        void getTools() {
            when(chatService.getAvailableTools()).thenReturn(
                    List.of(Map.of("name", "calculator", "type", "builtin")));

            List<Map<String, Object>> tools = controller.getTools();

            assertEquals(1, tools.size());
            assertEquals("calculator", tools.get(0).get("name"));
        }
    }

    @Nested
    @DisplayName("GET /api/health")
    class Health {

        @Test
        @DisplayName("返回健康状态包含基本信息")
        void healthEndpoint() {
            when(chatService.getAvailableSkills()).thenReturn(List.of());
            when(chatService.getAvailableTools()).thenReturn(List.of());
            when(llmProvider.getProviderName()).thenReturn("claude");

            Map<String, Object> health = controller.health();

            assertEquals("UP", health.get("status"));
            assertEquals("enabled", health.get("soulComfortMode"));
            assertNotNull(health.get("llm"));

            @SuppressWarnings("unchecked")
            Map<String, Object> llmHealth = (Map<String, Object>) health.get("llm");
            assertEquals("claude", llmHealth.get("provider"));
            assertEquals("NOT_MONITORED", llmHealth.get("status"));
        }
    }

    @Nested
    @DisplayName("Session 管理端点")
    class SessionEndpoints {

        @Test
        @DisplayName("getSessionHistory - 有 SessionManager 时返回历史")
        void sessionHistoryWithManager() {
            SessionManager sm = mock(SessionManager.class);
            when(gateway.getSessionManager()).thenReturn(sm);
            when(sm.getSessionHistory("s1")).thenReturn(
                    List.of(Map.of("role", "user", "content", "hello")));

            List<Map<String, String>> history = controller.getSessionHistory("s1");

            assertEquals(1, history.size());
            assertEquals("hello", history.get(0).get("content"));
        }

        @Test
        @DisplayName("getSessionHistory - 无 SessionManager 时返回空列表")
        void sessionHistoryWithoutManager() {
            when(gateway.getSessionManager()).thenReturn(null);

            List<Map<String, String>> history = controller.getSessionHistory("s1");

            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("getSessionSummary - 有 SessionManager 时返回摘要")
        void sessionSummaryWithManager() {
            SessionManager sm = mock(SessionManager.class);
            when(gateway.getSessionManager()).thenReturn(sm);
            when(sm.getSessionSummary("s1")).thenReturn(
                    Map.of("messageCount", 5, "sessionId", "s1"));

            Map<String, Object> summary = controller.getSessionSummary("s1");

            assertEquals(5, summary.get("messageCount"));
        }

        @Test
        @DisplayName("getSessionSummary - 无 SessionManager 时返回仅含 sessionId")
        void sessionSummaryWithoutManager() {
            when(gateway.getSessionManager()).thenReturn(null);

            Map<String, Object> summary = controller.getSessionSummary("s1");

            assertEquals("s1", summary.get("sessionId"));
        }

        @Test
        @DisplayName("clearSession - 有 SessionManager 时清除会话")
        void clearSessionWithManager() {
            SessionManager sm = mock(SessionManager.class);
            when(gateway.getSessionManager()).thenReturn(sm);

            Map<String, Object> result = controller.clearSession("s1");

            verify(sm).clearSession("s1");
            assertEquals("cleared", result.get("status"));
            assertEquals("s1", result.get("sessionId"));
        }

        @Test
        @DisplayName("clearSession - 无 SessionManager 时不报错")
        void clearSessionWithoutManager() {
            when(gateway.getSessionManager()).thenReturn(null);

            Map<String, Object> result = controller.clearSession("s1");

            assertEquals("cleared", result.get("status"));
        }
    }
}
