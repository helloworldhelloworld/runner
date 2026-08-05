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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController - 用户管理与认证 API")
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private HttpServletRequest httpRequest;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService, passwordEncoder, authSessionService);
    }

    private SoulUser createTestUser(String id, String username, String nickname,
                                     String role, boolean enabled) {
        SoulUser user = new SoulUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setMemberLevel("FREE");
        user.setCreatedAt(1000L);
        user.setLastActive(2000L);
        return user;
    }

    @Nested
    @DisplayName("POST /user/anonymous - 创建匿名用户")
    class CreateAnonymous {

        @Test
        @DisplayName("成功创建匿名用户并返回用户对象")
        void createsAnonymousUser() {
            SoulUser anon = createTestUser("anon-1", null, "匿名用户", "USER", true);
            when(userService.createAnonymousUser()).thenReturn(anon);

            ResponseEntity<SoulUser> response = controller.createAnonymous();

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals("anon-1", response.getBody().getId());
        }
    }

    @Nested
    @DisplayName("POST /user/register - 用户注册")
    class Register {

        @Test
        @DisplayName("注册成功返回 token 和用户信息")
        void registerSuccess() {
            SoulUser user = createTestUser("u1", "alice", "Alice", "USER", true);
            when(passwordEncoder.encode("pass123")).thenReturn("hashed-pass");
            when(userService.register("alice", "hashed-pass", "Alice")).thenReturn(user);
            when(authSessionService.createSessionToken("u1", "USER")).thenReturn("tk-abc");

            UserController.RegisterRequest request =
                    new UserController.RegisterRequest("alice", "pass123", "Alice");
            ResponseEntity<?> response = controller.register(request);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("tk-abc", body.get("token"));

            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = (Map<String, Object>) body.get("user");
            assertEquals("u1", userMap.get("id"));
            assertEquals("alice", userMap.get("username"));
            assertEquals("Alice", userMap.get("nickname"));
            assertEquals("USER", userMap.get("role"));
            assertEquals(true, userMap.get("enabled"));
        }

        @Test
        @DisplayName("用户名为空返回 400")
        void registerBlankUsername() {
            UserController.RegisterRequest request =
                    new UserController.RegisterRequest("  ", "pass123", "Alice");
            ResponseEntity<?> response = controller.register(request);

            assertEquals(400, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("error"));
        }

        @Test
        @DisplayName("密码为 null 返回 400")
        void registerNullPassword() {
            UserController.RegisterRequest request =
                    new UserController.RegisterRequest("alice", null, "Alice");
            ResponseEntity<?> response = controller.register(request);

            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("用户名已存在返回 400 (UserService 抛 IllegalArgumentException)")
        void registerDuplicateUsername() {
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userService.register(eq("alice"), anyString(), anyString()))
                    .thenThrow(new IllegalArgumentException("用户名已存在"));

            UserController.RegisterRequest request =
                    new UserController.RegisterRequest("alice", "pass", "Alice");
            ResponseEntity<?> response = controller.register(request);

            assertEquals(400, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("用户名已存在", body.get("error"));
        }
    }

    @Nested
    @DisplayName("POST /user/login - 用户登录")
    class Login {

        @Test
        @DisplayName("登录成功返回 token 和用户信息")
        void loginSuccess() {
            SoulUser user = createTestUser("u1", "bob", "Bob", "USER", true);
            user.setPasswordHash("hashed-pw");
            when(userService.findByUsername("bob")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("secret", "hashed-pw")).thenReturn(true);
            when(authSessionService.createSessionToken("u1", "USER")).thenReturn("tk-login");

            UserController.LoginRequest request = new UserController.LoginRequest("bob", "secret");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("tk-login", body.get("token"));

            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = (Map<String, Object>) body.get("user");
            assertEquals("u1", userMap.get("id"));
            assertEquals("bob", userMap.get("username"));
        }

        @Test
        @DisplayName("密码错误返回 401")
        void loginWrongPassword() {
            SoulUser user = createTestUser("u1", "bob", "Bob", "USER", true);
            user.setPasswordHash("hashed-pw");
            when(userService.findByUsername("bob")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hashed-pw")).thenReturn(false);

            UserController.LoginRequest request = new UserController.LoginRequest("bob", "wrong");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(401, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("error"));
        }

        @Test
        @DisplayName("用户不存在返回 401")
        void loginUserNotFound() {
            when(userService.findByUsername("nobody")).thenReturn(Optional.empty());

            UserController.LoginRequest request = new UserController.LoginRequest("nobody", "pass");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("账户被禁用返回 403")
        void loginDisabledUser() {
            SoulUser user = createTestUser("u1", "bob", "Bob", "USER", false);
            user.setPasswordHash("hashed-pw");
            when(userService.findByUsername("bob")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("secret", "hashed-pw")).thenReturn(true);

            UserController.LoginRequest request = new UserController.LoginRequest("bob", "secret");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(403, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("error"));
        }

        @Test
        @DisplayName("用户名和密码为空返回 400")
        void loginBlankCredentials() {
            UserController.LoginRequest request = new UserController.LoginRequest("", "");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(400, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("GET /user/me - 获取当前用户信息")
    class Me {

        @Test
        @DisplayName("有效 token 返回用户信息")
        void meWithValidToken() {
            AuthSessionService.SessionInfo session =
                    new AuthSessionService.SessionInfo("u1", "USER");
            SoulUser user = createTestUser("u1", "alice", "Alice", "USER", true);

            when(authSessionService.resolveSession("tk-valid"))
                    .thenReturn(Optional.of(session));
            when(userService.findById("u1")).thenReturn(Optional.of(user));

            ResponseEntity<?> response = controller.me("Bearer tk-valid");

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("u1", body.get("id"));
            assertEquals("alice", body.get("username"));
            assertEquals("Alice", body.get("nickname"));
            assertEquals("USER", body.get("role"));
            assertEquals(true, body.get("enabled"));
            assertEquals("FREE", body.get("memberLevel"));
        }

        @Test
        @DisplayName("缺少 Authorization 头返回 401")
        void meWithoutAuthHeader() {
            ResponseEntity<?> response = controller.me(null);

            assertEquals(401, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("error"));
        }

        @Test
        @DisplayName("非 Bearer 格式返回 401")
        void meWithNonBearerAuth() {
            ResponseEntity<?> response = controller.me("Basic dXNlcjpwYXNz");

            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("无效 token 返回 401")
        void meWithInvalidToken() {
            when(authSessionService.resolveSession("tk-expired"))
                    .thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.me("Bearer tk-expired");

            assertEquals(401, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("POST /user/logout - 退出登录")
    class Logout {

        @Test
        @DisplayName("退出登录成功")
        void logoutWithToken() {
            ResponseEntity<?> response = controller.logout("Bearer tk-abc");

            verify(authSessionService).invalidate("tk-abc");
            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("已退出登录", body.get("message"));
        }

        @Test
        @DisplayName("没有 Authorization 头也能正常退出")
        void logoutWithoutToken() {
            ResponseEntity<?> response = controller.logout(null);

            verify(authSessionService, never()).invalidate(anyString());
            assertEquals(200, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("GET /user/list - 管理员获取用户列表")
    class ListUsers {

        @Test
        @DisplayName("管理员成功获取用户列表")
        void adminListsUsers() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            SoulUser u1 = createTestUser("u1", "alice", "Alice", "USER", true);
            SoulUser u2 = createTestUser("u2", "bob", "Bob", "ADMIN", true);
            when(userService.listAllUsers()).thenReturn(List.of(u1, u2));

            ResponseEntity<?> response = controller.listUsers(httpRequest);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(2, body.get("total"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> users = (List<Map<String, Object>>) body.get("users");
            assertEquals(2, users.size());
            assertEquals("alice", users.get(0).get("username"));
            assertEquals("bob", users.get(1).get("username"));
        }

        @Test
        @DisplayName("非管理员返回 403")
        void nonAdminDenied() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            ResponseEntity<?> response = controller.listUsers(httpRequest);

            assertEquals(403, response.getStatusCode().value());
            verify(userService, never()).listAllUsers();
        }

        @Test
        @DisplayName("userRole 属性为 null 返回 403")
        void nullRoleDenied() {
            when(httpRequest.getAttribute("userRole")).thenReturn(null);

            ResponseEntity<?> response = controller.listUsers(httpRequest);

            assertEquals(403, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("PUT /user/{id}/role - 管理员更新角色")
    class UpdateRole {

        @Test
        @DisplayName("管理员成功更新用户角色")
        void adminUpdatesRole() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            ResponseEntity<?> response = controller.updateRole(
                    "u1", Map.of("role", "ADMIN"), httpRequest);

            assertEquals(200, response.getStatusCode().value());
            verify(userService).updateUserRole("u1", "ADMIN");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("角色已更新", body.get("message"));
        }

        @Test
        @DisplayName("非管理员更新角色返回 403")
        void nonAdminDenied() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            ResponseEntity<?> response = controller.updateRole(
                    "u1", Map.of("role", "ADMIN"), httpRequest);

            assertEquals(403, response.getStatusCode().value());
            verify(userService, never()).updateUserRole(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("PUT /user/{id}/status - 管理员启用/禁用用户")
    class UpdateStatus {

        @Test
        @DisplayName("管理员禁用用户")
        void adminDisablesUser() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            ResponseEntity<?> response = controller.updateStatus(
                    "u1", Map.of("enabled", false), httpRequest);

            assertEquals(200, response.getStatusCode().value());
            verify(userService).disableUser("u1");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("已禁用", body.get("message"));
        }

        @Test
        @DisplayName("管理员启用用户")
        void adminEnablesUser() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            ResponseEntity<?> response = controller.updateStatus(
                    "u1", Map.of("enabled", true), httpRequest);

            assertEquals(200, response.getStatusCode().value());
            verify(userService).enableUser("u1");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("已启用", body.get("message"));
        }

        @Test
        @DisplayName("非管理员返回 403")
        void nonAdminDenied() {
            when(httpRequest.getAttribute("userRole")).thenReturn(null);

            ResponseEntity<?> response = controller.updateStatus(
                    "u1", Map.of("enabled", true), httpRequest);

            assertEquals(403, response.getStatusCode().value());
            verify(userService, never()).enableUser(anyString());
            verify(userService, never()).disableUser(anyString());
        }
    }

    @Nested
    @DisplayName("POST /user/checkin - 情绪签到")
    class Checkin {

        @Test
        @DisplayName("签到成功返回 EmotionRecord")
        void checkinSuccess() {
            EmotionRecord record = new EmotionRecord("e1", "u1", "happy", "今天不错", 3000L);
            when(userService.checkin("u1", "happy", "今天不错")).thenReturn(record);

            UserController.CheckinRequest request =
                    new UserController.CheckinRequest("u1", "happy", "今天不错");
            ResponseEntity<?> response = controller.checkin(request);

            assertEquals(200, response.getStatusCode().value());
            EmotionRecord body = (EmotionRecord) response.getBody();
            assertNotNull(body);
            assertEquals("e1", body.getId());
            assertEquals("u1", body.getUserId());
            assertEquals("happy", body.getEmotion());
            assertEquals("今天不错", body.getNote());
        }

        @Test
        @DisplayName("签到异常返回 500")
        void checkinError() {
            when(userService.checkin(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("DB error"));

            UserController.CheckinRequest request =
                    new UserController.CheckinRequest("u1", "sad", null);
            ResponseEntity<?> response = controller.checkin(request);

            assertEquals(500, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("GET /user/{userId}/emotions - 获取情绪记录")
    class GetEmotions {

        @Test
        @DisplayName("返回指定天数的情绪记录列表")
        void getEmotionsForUser() {
            EmotionRecord r1 = new EmotionRecord("e1", "u1", "happy", "good", 1000L);
            EmotionRecord r2 = new EmotionRecord("e2", "u1", "calm", "ok", 2000L);
            when(userService.getEmotions("u1", 7)).thenReturn(List.of(r1, r2));

            ResponseEntity<List<EmotionRecord>> response = controller.getEmotions("u1", 7);

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().size());
            assertEquals("happy", response.getBody().get(0).getEmotion());
            assertEquals("calm", response.getBody().get(1).getEmotion());
        }
    }

    @Nested
    @DisplayName("GET /user/{userId}/stats - 获取用户统计")
    class GetStats {

        @Test
        @DisplayName("返回用户统计数据")
        void getStatsForUser() {
            Map<String, Object> stats = Map.of("totalCheckins", 10, "streak", 3);
            when(userService.getStats("u1")).thenReturn(stats);

            ResponseEntity<Map<String, Object>> response = controller.getStats("u1");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(10, response.getBody().get("totalCheckins"));
            assertEquals(3, response.getBody().get("streak"));
        }
    }
}
