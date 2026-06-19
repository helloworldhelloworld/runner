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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SharedClientToolController - 共享客户端工具 API")
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

    private SharedClientTool createTool(String key, String name, String description) {
        SharedClientTool tool = new SharedClientTool();
        tool.setKey(key);
        tool.setName(name);
        tool.setDescription(description);
        tool.setEnabled(true);
        tool.setCreatedAt(1000L);
        tool.setUpdatedAt(2000L);
        return tool;
    }

    @Nested
    @DisplayName("GET /client-tools - 列出工具")
    class ListTools {

        @Test
        @DisplayName("已认证用户列出工具并返回 userId 作为 viewer")
        void listWithAuthenticatedUser() {
            when(authSessionService.resolveUserId("tk-user1"))
                    .thenReturn(Optional.of("user-123"));
            SharedClientTool t1 = createTool("calc", "Calculator", "Calculates things");
            SharedClientTool t2 = createTool("search", "Search", "Searches the web");
            when(sharedClientToolService.list()).thenReturn(List.of(t1, t2));

            ResponseEntity<?> response = controller.list("Bearer tk-user1");

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("user-123", body.get("viewer"));

            @SuppressWarnings("unchecked")
            List<SharedClientTool> tools = (List<SharedClientTool>) body.get("tools");
            assertEquals(2, tools.size());
            assertEquals("calc", tools.get(0).getKey());
            assertEquals("search", tools.get(1).getKey());
        }

        @Test
        @DisplayName("无 Authorization 头时 viewer 为 anonymous")
        void listWithoutAuth() {
            SharedClientTool t1 = createTool("calc", "Calculator", "Calc");
            when(sharedClientToolService.list()).thenReturn(List.of(t1));

            ResponseEntity<?> response = controller.list(null);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("anonymous", body.get("viewer"));
        }

        @Test
        @DisplayName("非 Bearer 格式的 Authorization 头视为匿名")
        void listWithNonBearerAuth() {
            when(sharedClientToolService.list()).thenReturn(List.of());

            ResponseEntity<?> response = controller.list("Basic dXNlcjpwYXNz");

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("anonymous", body.get("viewer"));
        }

        @Test
        @DisplayName("token 无法解析时 viewer 为 anonymous")
        void listWithInvalidToken() {
            when(authSessionService.resolveUserId("tk-expired"))
                    .thenReturn(Optional.empty());
            when(sharedClientToolService.list()).thenReturn(List.of());

            ResponseEntity<?> response = controller.list("Bearer tk-expired");

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("anonymous", body.get("viewer"));
        }

        @Test
        @DisplayName("工具列表为空时正常返回空列表")
        void listEmpty() {
            when(sharedClientToolService.list()).thenReturn(List.of());

            ResponseEntity<?> response = controller.list(null);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            @SuppressWarnings("unchecked")
            List<SharedClientTool> tools = (List<SharedClientTool>) body.get("tools");
            assertTrue(tools.isEmpty());
        }
    }

    @Nested
    @DisplayName("PUT /client-tools/{key} - 创建或更新工具")
    class UpsertTool {

        @Test
        @DisplayName("已认证用户成功 upsert 工具")
        void upsertWithAuthenticatedUser() {
            when(authSessionService.resolveUserId("tk-user1"))
                    .thenReturn(Optional.of("user-123"));
            SharedClientTool payload = createTool("weather", "Weather", "Gets weather data");
            SharedClientTool saved = createTool("weather", "Weather", "Gets weather data");
            saved.setUpdatedBy("user-123");
            when(sharedClientToolService.upsert("weather", payload, "user-123")).thenReturn(saved);

            ResponseEntity<?> response = controller.upsert("weather", payload, "Bearer tk-user1");

            assertEquals(200, response.getStatusCode().value());
            SharedClientTool body = (SharedClientTool) response.getBody();
            assertNotNull(body);
            assertEquals("weather", body.getKey());
            assertEquals("Weather", body.getName());
            assertEquals("user-123", body.getUpdatedBy());

            verify(sharedClientToolService).upsert("weather", payload, "user-123");
        }

        @Test
        @DisplayName("匿名用户 upsert 工具使用 anonymous 作为 actorUserId")
        void upsertAnonymous() {
            SharedClientTool payload = createTool("timer", "Timer", "Set timers");
            SharedClientTool saved = createTool("timer", "Timer", "Set timers");
            saved.setUpdatedBy("anonymous");
            when(sharedClientToolService.upsert("timer", payload, "anonymous")).thenReturn(saved);

            ResponseEntity<?> response = controller.upsert("timer", payload, null);

            assertEquals(200, response.getStatusCode().value());
            verify(sharedClientToolService).upsert("timer", payload, "anonymous");
        }
    }

    @Nested
    @DisplayName("DELETE /client-tools/{key} - 删除工具")
    class RemoveTool {

        @Test
        @DisplayName("成功删除已存在的工具")
        void removeExisting() {
            when(authSessionService.resolveUserId("tk-user1"))
                    .thenReturn(Optional.of("user-123"));
            when(sharedClientToolService.remove("calc")).thenReturn(true);

            ResponseEntity<?> response = controller.remove("calc", "Bearer tk-user1");

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(true, body.get("removed"));
            assertEquals("user-123", body.get("operator"));
        }

        @Test
        @DisplayName("删除不存在的工具返回 removed=false")
        void removeNonexistent() {
            when(sharedClientToolService.remove("nope")).thenReturn(false);

            ResponseEntity<?> response = controller.remove("nope", null);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(false, body.get("removed"));
            assertEquals("anonymous", body.get("operator"));
        }

        @Test
        @DisplayName("匿名用户删除工具 operator 为 anonymous")
        void removeAnonymous() {
            when(sharedClientToolService.remove("calc")).thenReturn(true);

            ResponseEntity<?> response = controller.remove("calc", null);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("anonymous", body.get("operator"));
        }
    }
}
