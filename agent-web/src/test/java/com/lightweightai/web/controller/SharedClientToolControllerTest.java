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
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SharedClientToolController — CRUD for shared client tools")
class SharedClientToolControllerTest {

    @Mock
    private SharedClientToolService toolService;

    @Mock
    private AuthSessionService authSessionService;

    private SharedClientToolController controller;

    @BeforeEach
    void setUp() {
        controller = new SharedClientToolController(toolService, authSessionService);
    }

    @Nested
    @DisplayName("GET /client-tools — list")
    class ListTools {

        @Test
        void returnsToolListWithViewer() {
            SharedClientTool tool = new SharedClientTool();
            tool.setKey("t1");
            tool.setName("Test Tool");
            when(toolService.list()).thenReturn(List.of(tool));

            ResponseEntity<?> response = controller.list(null);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("anonymous", body.get("viewer"));
            @SuppressWarnings("unchecked")
            List<SharedClientTool> tools = (List<SharedClientTool>) body.get("tools");
            assertEquals(1, tools.size());
        }

        @Test
        void authenticatedUserAppearsAsViewer() {
            when(authSessionService.resolveUserId("valid-token")).thenReturn(Optional.of("user-1"));
            when(toolService.list()).thenReturn(List.of());

            ResponseEntity<?> response = controller.list("Bearer valid-token");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("user-1", body.get("viewer"));
        }
    }

    @Nested
    @DisplayName("PUT /client-tools/{key} — upsert")
    class Upsert {

        @Test
        void savesToolWithAnonymousWhenNoAuth() {
            SharedClientTool payload = new SharedClientTool();
            payload.setName("New Tool");

            SharedClientTool saved = new SharedClientTool();
            saved.setKey("k1");
            saved.setName("New Tool");
            when(toolService.upsert(eq("k1"), any(), eq("anonymous"))).thenReturn(saved);

            ResponseEntity<?> response = controller.upsert("k1", payload, null);

            assertEquals(200, response.getStatusCode().value());
            assertEquals(saved, response.getBody());
            verify(toolService).upsert("k1", payload, "anonymous");
        }

        @Test
        void savesToolWithAuthenticatedUser() {
            when(authSessionService.resolveUserId("tk-abc")).thenReturn(Optional.of("user-2"));

            SharedClientTool payload = new SharedClientTool();
            SharedClientTool saved = new SharedClientTool();
            when(toolService.upsert(eq("k1"), any(), eq("user-2"))).thenReturn(saved);

            controller.upsert("k1", payload, "Bearer tk-abc");

            verify(toolService).upsert("k1", payload, "user-2");
        }
    }

    @Nested
    @DisplayName("DELETE /client-tools/{key} — remove")
    class Remove {

        @Test
        void returnsRemovedTrueWhenExists() {
            when(toolService.remove("k1")).thenReturn(true);

            ResponseEntity<?> response = controller.remove("k1", null);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(true, body.get("removed"));
            assertEquals("anonymous", body.get("operator"));
        }

        @Test
        void returnsRemovedFalseWhenNotExists() {
            when(toolService.remove("missing")).thenReturn(false);

            ResponseEntity<?> response = controller.remove("missing", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(false, body.get("removed"));
        }
    }

    @Nested
    @DisplayName("Auth resolution")
    class AuthResolution {

        @Test
        void noAuthHeaderFallsToAnonymous() {
            when(toolService.list()).thenReturn(List.of());
            ResponseEntity<?> response = controller.list(null);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals("anonymous", body.get("viewer"));
        }

        @Test
        void invalidBearerTokenFallsToAnonymous() {
            when(authSessionService.resolveUserId("bad-token")).thenReturn(Optional.empty());
            when(toolService.list()).thenReturn(List.of());

            ResponseEntity<?> response = controller.list("Bearer bad-token");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals("anonymous", body.get("viewer"));
        }

        @Test
        void nonBearerPrefixFallsToAnonymous() {
            when(toolService.list()).thenReturn(List.of());
            ResponseEntity<?> response = controller.list("Basic abc123");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals("anonymous", body.get("viewer"));
        }
    }
}
