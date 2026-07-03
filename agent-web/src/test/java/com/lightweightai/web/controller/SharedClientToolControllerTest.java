package com.lightweightai.web.controller;

import com.lightweightai.web.model.SharedClientTool;
import com.lightweightai.web.service.AuthSessionService;
import com.lightweightai.web.service.SharedClientToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SharedClientToolController - Client tool management API")
class SharedClientToolControllerTest {

    @Mock
    private SharedClientToolService sharedClientToolService;

    @Mock
    private AuthSessionService authSessionService;

    private SharedClientToolController controller;

    @BeforeEach
    void setUp() {
        controller = new SharedClientToolController(sharedClientToolService, authSessionService);
    }

    private SharedClientTool createTool(String key, String name) {
        SharedClientTool tool = new SharedClientTool();
        tool.setKey(key);
        tool.setName(name);
        tool.setEnabled(true);
        tool.setUpdatedAt(System.currentTimeMillis());
        return tool;
    }

    @Nested
    @DisplayName("GET /client-tools - list tools")
    class ListTools {

        @Test
        @DisplayName("authenticated user sees tools with their userId as viewer")
        void authenticatedUserListTools() {
            when(authSessionService.resolveUserId("valid-token")).thenReturn(Optional.of("usr-1"));
            List<SharedClientTool> tools = List.of(createTool("k1", "tool1"), createTool("k2", "tool2"));
            when(sharedClientToolService.list()).thenReturn(tools);

            ResponseEntity<?> response = controller.list("Bearer valid-token");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("usr-1", body.get("viewer"));
            @SuppressWarnings("unchecked")
            List<SharedClientTool> returnedTools = (List<SharedClientTool>) body.get("tools");
            assertEquals(2, returnedTools.size());
        }

        @Test
        @DisplayName("anonymous user sees tools with 'anonymous' as viewer")
        void anonymousUserListTools() {
            List<SharedClientTool> tools = List.of(createTool("k1", "tool1"));
            when(sharedClientToolService.list()).thenReturn(tools);

            ResponseEntity<?> response = controller.list(null);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("anonymous", body.get("viewer"));
        }

        @Test
        @DisplayName("invalid token falls back to 'anonymous' viewer")
        void invalidTokenFallsBackToAnonymous() {
            when(authSessionService.resolveUserId("bad-token")).thenReturn(Optional.empty());
            when(sharedClientToolService.list()).thenReturn(List.of());

            ResponseEntity<?> response = controller.list("Bearer bad-token");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("anonymous", body.get("viewer"));
        }

        @Test
        @DisplayName("non-Bearer authorization falls back to 'anonymous' viewer")
        void nonBearerAuth() {
            when(sharedClientToolService.list()).thenReturn(List.of());

            ResponseEntity<?> response = controller.list("Basic user:pass");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("anonymous", body.get("viewer"));
        }
    }

    @Nested
    @DisplayName("PUT /client-tools/{key} - upsert tool")
    class UpsertTool {

        @Test
        @DisplayName("authenticated user can upsert a tool")
        void authenticatedUpsert() {
            when(authSessionService.resolveUserId("valid-token")).thenReturn(Optional.of("usr-1"));
            SharedClientTool payload = createTool("my-tool", "My Tool");
            SharedClientTool saved = createTool("my-tool", "My Tool");
            saved.setCreatedBy("usr-1");
            when(sharedClientToolService.upsert("my-tool", payload, "usr-1")).thenReturn(saved);

            ResponseEntity<?> response = controller.upsert("my-tool", payload, "Bearer valid-token");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            SharedClientTool body = (SharedClientTool) response.getBody();
            assertNotNull(body);
            assertEquals("my-tool", body.getKey());
            assertEquals("usr-1", body.getCreatedBy());
        }

        @Test
        @DisplayName("anonymous user can upsert with 'anonymous' as actor")
        void anonymousUpsert() {
            SharedClientTool payload = createTool("tool-1", "Tool 1");
            SharedClientTool saved = createTool("tool-1", "Tool 1");
            saved.setCreatedBy("anonymous");
            when(sharedClientToolService.upsert("tool-1", payload, "anonymous")).thenReturn(saved);

            ResponseEntity<?> response = controller.upsert("tool-1", payload, null);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(sharedClientToolService).upsert("tool-1", payload, "anonymous");
        }
    }

    @Nested
    @DisplayName("DELETE /client-tools/{key} - remove tool")
    class RemoveTool {

        @Test
        @DisplayName("successful removal returns removed=true with operator")
        void successfulRemoval() {
            when(authSessionService.resolveUserId("valid-token")).thenReturn(Optional.of("usr-1"));
            when(sharedClientToolService.remove("my-tool")).thenReturn(true);

            ResponseEntity<?> response = controller.remove("my-tool", "Bearer valid-token");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(true, body.get("removed"));
            assertEquals("usr-1", body.get("operator"));
        }

        @Test
        @DisplayName("removal of non-existent tool returns removed=false")
        void nonExistentRemoval() {
            when(sharedClientToolService.remove("no-such-tool")).thenReturn(false);

            ResponseEntity<?> response = controller.remove("no-such-tool", null);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(false, body.get("removed"));
            assertEquals("anonymous", body.get("operator"));
        }
    }
}
