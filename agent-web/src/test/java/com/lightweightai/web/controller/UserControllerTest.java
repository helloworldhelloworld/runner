package com.lightweightai.web.controller;

import com.lightweightai.user.UserService;
import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import com.lightweightai.web.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController - 用户管理API")
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthSessionService authSessionService;
    @Mock private HttpServletRequest httpRequest;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService, passwordEncoder, authSessionService);
    }

    private SoulUser makeUser(String id, String username, String nickname) {
        return new SoulUser(id, username, "hash", null, nickname, "FREE", "USER", true,
            System.currentTimeMillis(), System.currentTimeMillis());
    }

    @Nested
    @DisplayName("POST /user/anonymous")
    class CreateAnonymous {
        @Test
        @DisplayName("成功创建匿名用户")
        void shouldCreateAnonymousUser() {
            SoulUser anon = makeUser("anon-123", null, "匿名用户");
            when(userService.createAnonymousUser()).thenReturn(anon);

            ResponseEntity<SoulUser> response = controller.createAnonymous();

            assertEquals(200, response.getStatusCode().value());
            assertEquals("anon-123", response.getBody().getId());
        }
    }

    @Nested
    @DisplayName("POST /user/register")
    class Register {
        @Test
        @DisplayName("成功注册用户")
        void shouldRegisterUser() {
            SoulUser user = makeUser("usr-1", "alice", "Alice");
            when(passwordEncoder.encode("pass123")).thenReturn("encoded");
            when(userService.register("alice", "encoded", "Alice")).thenReturn(user);
            when(authSessionService.createSessionToken("usr-1", "USER")).thenReturn("tk-abc");

            var request = new UserController.RegisterRequest("alice", "pass123", "Alice");
            ResponseEntity<?> response = controller.register(request);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals("tk-abc", body.get("token"));
        }

        @Test
        @DisplayName("空用户名返回400")
        void shouldReject400ForBlankUsername() {
            var request = new UserController.RegisterRequest("", "pass", "Nick");
            ResponseEntity<?> response = controller.register(request);
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("空密码返回400")
        void shouldReject400ForBlankPassword() {
            var request = new UserController.RegisterRequest("alice", "", "Nick");
            ResponseEntity<?> response = controller.register(request);
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("null用户名返回400")
        void shouldReject400ForNullUsername() {
            var request = new UserController.RegisterRequest(null, "pass", "Nick");
            ResponseEntity<?> response = controller.register(request);
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("重复用户名返回400")
        void shouldReturn400ForDuplicateUsername() {
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(userService.register(eq("dup"), any(), any()))
                .thenThrow(new IllegalArgumentException("用户名已存在"));

            var request = new UserController.RegisterRequest("dup", "pass", "Nick");
            ResponseEntity<?> response = controller.register(request);
            assertEquals(400, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("POST /user/login")
    class Login {
        @Test
        @DisplayName("登录成功")
        void shouldLoginSuccessfully() {
            SoulUser user = makeUser("usr-1", "alice", "Alice");
            when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass123", "hash")).thenReturn(true);
            when(authSessionService.createSessionToken("usr-1", "USER")).thenReturn("tk-login");

            var request = new UserController.LoginRequest("alice", "pass123");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals("tk-login", body.get("token"));
        }

        @Test
        @DisplayName("密码错误返回401")
        void shouldReturn401ForWrongPassword() {
            SoulUser user = makeUser("usr-1", "alice", "Alice");
            when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

            var request = new UserController.LoginRequest("alice", "wrong");
            ResponseEntity<?> response = controller.login(request);
            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("用户不存在返回401")
        void shouldReturn401ForMissingUser() {
            when(userService.findByUsername("nobody")).thenReturn(Optional.empty());

            var request = new UserController.LoginRequest("nobody", "pass");
            ResponseEntity<?> response = controller.login(request);
            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("已禁用账户返回403")
        void shouldReturn403ForDisabledAccount() {
            SoulUser user = new SoulUser("usr-1", "alice", "hash", null, "Alice", "FREE", "USER", false,
                System.currentTimeMillis(), System.currentTimeMillis());
            when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass", "hash")).thenReturn(true);

            var request = new UserController.LoginRequest("alice", "pass");
            ResponseEntity<?> response = controller.login(request);
            assertEquals(403, response.getStatusCode().value());
        }

        @Test
        @DisplayName("空用户名返回400")
        void shouldRejectBlankUsername() {
            var request = new UserController.LoginRequest("", "pass");
            ResponseEntity<?> response = controller.login(request);
            assertEquals(400, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("GET /user/me")
    class Me {
        @Test
        @DisplayName("有效token返回用户信息")
        void shouldReturnUserForValidToken() {
            SoulUser user = makeUser("usr-1", "alice", "Alice");
            when(authSessionService.resolveSession("valid-token"))
                .thenReturn(Optional.of(new AuthSessionService.SessionInfo("usr-1", "USER")));
            when(userService.findById("usr-1")).thenReturn(Optional.of(user));

            ResponseEntity<?> response = controller.me("Bearer valid-token");
            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("无Authorization头返回401")
        void shouldReturn401ForMissingAuth() {
            ResponseEntity<?> response = controller.me(null);
            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("非Bearer格式返回401")
        void shouldReturn401ForNonBearerAuth() {
            ResponseEntity<?> response = controller.me("Basic abc123");
            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("无效token返回401")
        void shouldReturn401ForInvalidToken() {
            when(authSessionService.resolveSession("invalid-token")).thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.me("Bearer invalid-token");
            assertEquals(401, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("POST /user/logout")
    class Logout {
        @Test
        @DisplayName("成功退出")
        void shouldLogoutSuccessfully() {
            ResponseEntity<?> response = controller.logout("Bearer some-token");
            assertEquals(200, response.getStatusCode().value());
            verify(authSessionService).invalidate("some-token");
        }

        @Test
        @DisplayName("无token也返回200")
        void shouldReturn200WithoutToken() {
            ResponseEntity<?> response = controller.logout(null);
            assertEquals(200, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("管理员接口 - 权限检查")
    class AdminEndpoints {

        @Test
        @DisplayName("非管理员访问用户列表返回403")
        void shouldReturn403ForNonAdminListUsers() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            ResponseEntity<?> response = controller.listUsers(httpRequest);
            assertEquals(403, response.getStatusCode().value());
        }

        @Test
        @DisplayName("管理员访问用户列表返回200")
        void shouldReturn200ForAdminListUsers() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            when(userService.listAllUsers()).thenReturn(List.of());

            ResponseEntity<?> response = controller.listUsers(httpRequest);
            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("非管理员更新角色返回403")
        void shouldReturn403ForNonAdminUpdateRole() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            ResponseEntity<?> response = controller.updateRole("usr-1", Map.of("role", "ADMIN"), httpRequest);
            assertEquals(403, response.getStatusCode().value());
        }

        @Test
        @DisplayName("管理员更新角色成功")
        void shouldUpdateRoleForAdmin() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            ResponseEntity<?> response = controller.updateRole("usr-1", Map.of("role", "ADMIN"), httpRequest);
            assertEquals(200, response.getStatusCode().value());
            verify(userService).updateUserRole("usr-1", "ADMIN");
        }

        @Test
        @DisplayName("非管理员更新状态返回403")
        void shouldReturn403ForNonAdminUpdateStatus() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            ResponseEntity<?> response = controller.updateStatus("usr-1", Map.of("enabled", true), httpRequest);
            assertEquals(403, response.getStatusCode().value());
        }

        @Test
        @DisplayName("管理员禁用用户")
        void shouldDisableUserForAdmin() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            ResponseEntity<?> response = controller.updateStatus("usr-1", Map.of("enabled", false), httpRequest);
            assertEquals(200, response.getStatusCode().value());
            verify(userService).disableUser("usr-1");
        }

        @Test
        @DisplayName("管理员启用用户")
        void shouldEnableUserForAdmin() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            ResponseEntity<?> response = controller.updateStatus("usr-1", Map.of("enabled", true), httpRequest);
            assertEquals(200, response.getStatusCode().value());
            verify(userService).enableUser("usr-1");
        }
    }

    @Nested
    @DisplayName("情绪签到和查询")
    class EmotionEndpoints {

        @Test
        @DisplayName("签到成功")
        void shouldCheckinSuccessfully() {
            EmotionRecord record = new EmotionRecord("er-1", "usr-1", "😊", "happy", System.currentTimeMillis());
            when(userService.checkin("usr-1", "😊", "happy")).thenReturn(record);

            var request = new UserController.CheckinRequest("usr-1", "😊", "happy");
            ResponseEntity<?> response = controller.checkin(request);
            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("获取情绪记录")
        void shouldGetEmotions() {
            when(userService.getEmotions("usr-1", 7)).thenReturn(List.of());

            ResponseEntity<List<EmotionRecord>> response = controller.getEmotions("usr-1", 7);
            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("获取统计信息")
        void shouldGetStats() {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("streak", 3);
            stats.put("distribution", Map.of("😊", 5L));
            stats.put("checkedInToday", true);
            stats.put("totalDays", 3);
            when(userService.getStats("usr-1")).thenReturn(stats);

            ResponseEntity<Map<String, Object>> response = controller.getStats("usr-1");
            assertEquals(200, response.getStatusCode().value());
            assertEquals(3, response.getBody().get("streak"));
        }
    }
}
