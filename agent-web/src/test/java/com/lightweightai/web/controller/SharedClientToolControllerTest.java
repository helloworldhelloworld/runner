package com.lightweightai.web.controller;

import com.lightweightai.web.model.SharedClientTool;
import com.lightweightai.web.service.AuthSessionService;
import com.lightweightai.web.service.SharedClientToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SharedClientToolController - 共享客户端工具管理")
class SharedClientToolControllerTest {

    private SharedClientToolService service;
    private AuthSessionService authSessionService;
    private SharedClientToolController controller;

    @BeforeEach
    void setUp() {
        service = mock(SharedClientToolService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new SharedClientToolController(service, authSessionService);
    }

    // ==================== GET /client-tools ====================

    @Nested
    @DisplayName("列出工具")
    class ListTools {

        @Test
        @DisplayName("无认证时 viewer 为 anonymous")
        void shouldUseAnonymousWithoutAuth() {
            when(service.list()).thenReturn(List.of());

            ResponseEntity<?> response = controller.list(null);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals("anonymous", body.get("viewer"));
        }

        @Test
        @DisplayName("有认证时解析真实 userId")
        void shouldResolveUserId() {
            when(authSessionService.resolveUserId("tk-valid")).thenReturn(Optional.of("usr-1"));
            when(service.list()).thenReturn(List.of());

            ResponseEntity<?> response = controller.list("Bearer tk-valid");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals("usr-1", body.get("viewer"));
        }

        @Test
        @DisplayName("返回工具列表")
        void shouldReturnTools() {
            SharedClientTool tool = new SharedClientTool();
            tool.setKey("camera");
            tool.setName("Camera Tool");
            when(service.list()).thenReturn(List.of(tool));

            ResponseEntity<?> response = controller.list(null);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            List<SharedClientTool> tools = (List<SharedClientTool>) body.get("tools");
            assertEquals(1, tools.size());
        }
    }

    // ==================== PUT /client-tools/{key} ====================

    @Test
    @DisplayName("创建/更新工具")
    void shouldUpsertTool() {
        SharedClientTool payload = new SharedClientTool();
        payload.setName("Camera");
        SharedClientTool saved = new SharedClientTool();
        saved.setKey("camera");
        saved.setName("Camera");
        when(service.upsert("camera", payload, "anonymous")).thenReturn(saved);

        ResponseEntity<?> response = controller.upsert("camera", payload, null);

        assertEquals(200, response.getStatusCode().value());
        verify(service).upsert("camera", payload, "anonymous");
    }

    // ==================== DELETE /client-tools/{key} ====================

    @Nested
    @DisplayName("删除工具")
    class Remove {

        @Test
        @DisplayName("删除成功返回 removed=true")
        void shouldRemoveTool() {
            when(service.remove("camera")).thenReturn(true);

            ResponseEntity<?> response = controller.remove("camera", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals(true, body.get("removed"));
        }

        @Test
        @DisplayName("删除不存在的工具返回 removed=false")
        void shouldReturnFalseForMissingTool() {
            when(service.remove("nonexistent")).thenReturn(false);

            ResponseEntity<?> response = controller.remove("nonexistent", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals(false, body.get("removed"));
        }
    }
}
