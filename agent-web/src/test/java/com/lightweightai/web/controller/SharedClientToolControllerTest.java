package com.lightweightai.web.controller;

import com.lightweightai.web.model.SharedClientTool;
import com.lightweightai.web.service.AuthSessionService;
import com.lightweightai.web.service.SharedClientToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SharedClientToolController - shared client tool REST API")
class SharedClientToolControllerTest {

    @Mock private SharedClientToolService sharedClientToolService;
    @Mock private AuthSessionService authSessionService;

    private SharedClientToolController controller;

    @BeforeEach
    void setUp() {
        controller = new SharedClientToolController(sharedClientToolService, authSessionService);
    }

    // ==================== list ====================

    @Test
    @DisplayName("list returns tools with viewer from token")
    void listReturnsToolsWithViewer() {
        when(authSessionService.resolveUserId("valid-token")).thenReturn(Optional.of("user1"));
        SharedClientTool tool = new SharedClientTool();
        tool.setName("tool1");
        when(sharedClientToolService.list()).thenReturn(List.of(tool));

        ResponseEntity<?> result = controller.list("Bearer valid-token");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertEquals("user1", body.get("viewer"));
        assertNotNull(body.get("tools"));
    }

    @Test
    @DisplayName("list defaults to anonymous when no authorization")
    void listDefaultsToAnonymous() {
        when(sharedClientToolService.list()).thenReturn(List.of());

        ResponseEntity<?> result = controller.list(null);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertEquals("anonymous", body.get("viewer"));
    }

    // ==================== upsert ====================

    @Test
    @DisplayName("upsert creates/updates tool with resolved userId")
    void upsertWithResolvedUser() {
        when(authSessionService.resolveUserId("my-token")).thenReturn(Optional.of("user1"));
        SharedClientTool payload = new SharedClientTool();
        payload.setName("myTool");
        SharedClientTool saved = new SharedClientTool();
        saved.setKey("key1");
        saved.setName("myTool");
        when(sharedClientToolService.upsert("key1", payload, "user1")).thenReturn(saved);

        ResponseEntity<?> result = controller.upsert("key1", payload, "Bearer my-token");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(sharedClientToolService).upsert("key1", payload, "user1");
    }

    @Test
    @DisplayName("upsert uses anonymous when no auth header")
    void upsertUsesAnonymous() {
        SharedClientTool payload = new SharedClientTool();
        SharedClientTool saved = new SharedClientTool();
        when(sharedClientToolService.upsert("key1", payload, "anonymous")).thenReturn(saved);

        ResponseEntity<?> result = controller.upsert("key1", payload, null);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(sharedClientToolService).upsert("key1", payload, "anonymous");
    }

    // ==================== remove ====================

    @Test
    @DisplayName("remove returns removal result with operator")
    void removeReturnsResult() {
        when(authSessionService.resolveUserId("token")).thenReturn(Optional.of("user1"));
        when(sharedClientToolService.remove("key1")).thenReturn(true);

        ResponseEntity<?> result = controller.remove("key1", "Bearer token");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertEquals(true, body.get("removed"));
        assertEquals("user1", body.get("operator"));
    }

    @Test
    @DisplayName("remove returns false when tool not found")
    void removeReturnsFalseForMissing() {
        when(sharedClientToolService.remove("key1")).thenReturn(false);

        ResponseEntity<?> result = controller.remove("key1", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertEquals(false, body.get("removed"));
    }
}
