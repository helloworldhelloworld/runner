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
@DisplayName("ChatController — REST chat API")
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

    @Nested
    @DisplayName("POST /api/chat — sync chat")
    class SyncChatTests {

        @Test
        @DisplayName("successful chat returns response text")
        void successfulChat() {
            GatewayResponse gatewayResp = GatewayResponse.builder()
                    .requestId("r1").sessionId("s1").text("Hello!").latencyMs(50).build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResp);

            ChatRequest request = createRequest("Hi", "s1");
            ChatResponse response = controller.chat(request);

            assertEquals("Hello!", response.getResponse());
            assertNull(response.getError());
        }

        @Test
        @DisplayName("error response sets error field")
        void errorResponse() {
            GatewayResponse gatewayResp = GatewayResponse.error("r1", "s1", "Service down");
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResp);

            ChatRequest request = createRequest("Hi", "s1");
            ChatResponse response = controller.chat(request);

            assertEquals("Service down", response.getError());
        }

        @Test
        @DisplayName("model from request is passed as metadata to Gateway")
        void modelPassedAsMetadata() {
            GatewayResponse gatewayResp = GatewayResponse.builder()
                    .requestId("r1").text("OK").latencyMs(10).build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResp);

            ChatRequest request = createRequest("Hi", "s1");
            request.setModel("claude-3-opus");
            controller.chat(request);

            ArgumentCaptor<GatewayRequest> captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(gateway).handle(captor.capture());

            GatewayRequest captured = captor.getValue();
            assertEquals("claude-3-opus", captured.getMetadata().get("model"));
            assertEquals("s1", captured.getSessionId());
            assertEquals("Hi", captured.getMessage());
        }

        @Test
        @DisplayName("response includes skillsApplied from metadata")
        void skillsAppliedExtracted() {
            GatewayResponse gatewayResp = GatewayResponse.builder()
                    .requestId("r1").sessionId("s1").text("I see")
                    .latencyMs(100)
                    .metadata("skillsApplied", List.of("crisis_detection", "pss10"))
                    .build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResp);

            ChatRequest request = createRequest("I feel bad", "s1");
            ChatResponse response = controller.chat(request);

            assertNotNull(response.getSkillsApplied());
            assertEquals(2, response.getSkillsApplied().size());
            assertTrue(response.getSkillsApplied().contains("crisis_detection"));
        }

        @Test
        @DisplayName("response metadata is passed through")
        void metadataPassedThrough() {
            GatewayResponse gatewayResp = GatewayResponse.builder()
                    .requestId("r1").text("OK").latencyMs(10)
                    .metadata("custom_key", "custom_value")
                    .build();
            when(gateway.handle(any(GatewayRequest.class))).thenReturn(gatewayResp);

            ChatRequest request = createRequest("Hi", "s1");
            ChatResponse response = controller.chat(request);

            assertNotNull(response.getMetadata());
            assertEquals("custom_value", response.getMetadata().get("custom_key"));
        }
    }

    @Nested
    @DisplayName("GET /api/skills — list skills")
    class SkillsTests {

        @Test
        @DisplayName("returns skills from ChatService")
        void returnsSkills() {
            List<Map<String, String>> skills = List.of(
                    Map.of("name", "crisis_detection"),
                    Map.of("name", "pss10")
            );
            when(chatService.getAvailableSkills()).thenReturn(skills);

            List<Map<String, String>> result = controller.getSkills();

            assertEquals(2, result.size());
            assertEquals("crisis_detection", result.get(0).get("name"));
        }
    }

    @Nested
    @DisplayName("GET /api/tools — list tools")
    class ToolsTests {

        @Test
        @DisplayName("returns tools from ChatService")
        void returnsTools() {
            List<Map<String, Object>> tools = List.of(
                    Map.of("name", "search", "description", "Web search")
            );
            when(chatService.getAvailableTools()).thenReturn(tools);

            List<Map<String, Object>> result = controller.getTools();

            assertEquals(1, result.size());
            assertEquals("search", result.get(0).get("name"));
        }
    }

    @Nested
    @DisplayName("GET /api/health — health check")
    class HealthTests {

        @Test
        @DisplayName("returns UP status with LLM info")
        void healthReturnsUp() {
            when(chatService.getAvailableSkills()).thenReturn(List.of(Map.of("name", "s1")));
            when(chatService.getAvailableTools()).thenReturn(List.of());
            when(llmProvider.getProviderName()).thenReturn("openrouter");

            Map<String, Object> health = controller.health();

            assertEquals("UP", health.get("status"));
            assertEquals(1, health.get("skills"));
            assertEquals(0, health.get("tools"));
            @SuppressWarnings("unchecked")
            Map<String, Object> llmHealth = (Map<String, Object>) health.get("llm");
            assertNotNull(llmHealth);
            assertEquals("openrouter", llmHealth.get("provider"));
        }
    }

    @Nested
    @DisplayName("Session management endpoints")
    class SessionTests {

        @Test
        @DisplayName("GET /session/{id}/history — returns empty when no SessionManager")
        void historyWithoutSessionManager() {
            when(gateway.getSessionManager()).thenReturn(null);

            List<Map<String, String>> result = controller.getSessionHistory("s1");

            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("GET /session/{id}/history — delegates to SessionManager")
        void historyWithSessionManager() {
            SessionManager sm = mock(SessionManager.class);
            when(sm.getSessionHistory("s1")).thenReturn(
                    List.of(Map.of("role", "user", "content", "hi")));
            when(gateway.getSessionManager()).thenReturn(sm);

            List<Map<String, String>> result = controller.getSessionHistory("s1");

            assertEquals(1, result.size());
            assertEquals("hi", result.get(0).get("content"));
        }

        @Test
        @DisplayName("GET /session/{id}/summary — returns minimal without SessionManager")
        void summaryWithoutSessionManager() {
            when(gateway.getSessionManager()).thenReturn(null);

            Map<String, Object> result = controller.getSessionSummary("s1");

            assertEquals("s1", result.get("sessionId"));
        }

        @Test
        @DisplayName("DELETE /session/{id} — clears session")
        void clearSession() {
            SessionManager sm = mock(SessionManager.class);
            when(gateway.getSessionManager()).thenReturn(sm);

            Map<String, Object> result = controller.clearSession("s1");

            verify(sm).clearSession("s1");
            assertEquals("cleared", result.get("status"));
        }

        @Test
        @DisplayName("DELETE /session/{id} — no-op without SessionManager")
        void clearSessionWithoutManager() {
            when(gateway.getSessionManager()).thenReturn(null);

            Map<String, Object> result = controller.clearSession("s1");

            assertEquals("cleared", result.get("status"));
        }
    }
}
