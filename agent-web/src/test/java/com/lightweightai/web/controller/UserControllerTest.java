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
import static org.mockito.Mockito.*;

@DisplayName("UserController - 用户管理 API")
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthSessionService authSessionService;
    @Mock private HttpServletRequest adminRequest;
    @Mock private HttpServletRequest userRequest;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService, passwordEncoder, authSessionService);
        lenient().when(adminRequest.getAttribute("userRole")).thenReturn("ADMIN");
        lenient().when(userRequest.getAttribute("userRole")).thenReturn("USER");
    }

    // ==================== 匿名用户 ====================

    @Test
    @DisplayName("创建匿名用户")
    void shouldCreateAnonymousUser() {
        SoulUser anon = createUser("anon-1", null);
        when(userService.createAnonymousUser()).thenReturn(anon);

        ResponseEntity<SoulUser> response = controller.createAnonymous();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("anon-1", response.getBody().getId());
    }

    // ==================== 注册 ====================

    @Nested
    @DisplayName("用户注册")
    class Register {

        @Test
        @DisplayName("成功注册返回 token 和用户信息")
        void shouldRegisterSuccessfully() {
            SoulUser user = createUser("usr-1", "testuser");
            when(passwordEncoder.encode("password")).thenReturn("hashed");
            when(userService.register("testuser", "hashed", "Test")).thenReturn(user);
            when(authSessionService.createSessionToken("usr-1", "USER")).thenReturn("tk-xxx");

            var request = new UserController.RegisterRequest("testuser", "password", "Test");
            ResponseEntity<?> response = controller.register(request);

            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals("tk-xxx", body.get("token"));
        }

        @Test
        @DisplayName("用户名为空返回 400")
        void shouldRejectEmptyUsername() {
            var request = new UserController.RegisterRequest("", "password", "Test");
            ResponseEntity<?> response = controller.register(request);
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("密码为空返回 400")
        void shouldRejectEmptyPassword() {
            var request = new UserController.RegisterRequest("user", "", "Test");
            ResponseEntity<?> response = controller.register(request);
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("null 用户名返回 400")
        void shouldRejectNullUsername() {
            var request = new UserController.RegisterRequest(null, "password", "Test");
            ResponseEntity<?> response = controller.register(request);
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("用户名重复返回 400")
        void shouldRejectDuplicateUsername() {
            when(passwordEncoder.encode("pass")).thenReturn("hashed");
            when(userService.register("existing", "hashed", null))
                .thenThrow(new IllegalArgumentException("用户名已存在"));

            var request = new UserController.RegisterRequest("existing", "pass", null);
            ResponseEntity<?> response = controller.register(request);
            assertEquals(400, response.getStatusCode().value());
        }
    }

    // ==================== 登录 ====================

    @Nested
    @DisplayName("用户登录")
    class Login {

        @Test
        @DisplayName("成功登录返回 token")
        void shouldLoginSuccessfully() {
            SoulUser user = createUser("usr-1", "testuser");
            user.setPasswordHash("hashed");
            when(userService.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
            when(authSessionService.createSessionToken("usr-1", "USER")).thenReturn("tk-login");

            var request = new UserController.LoginRequest("testuser", "password");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("用户不存在返回 401")
        void shouldRejectUnknownUser() {
            when(userService.findByUsername("nobody")).thenReturn(Optional.empty());

            var request = new UserController.LoginRequest("nobody", "pass");
            ResponseEntity<?> response = controller.login(request);
            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("密码错误返回 401")
        void shouldRejectWrongPassword() {
            SoulUser user = createUser("usr-1", "testuser");
            user.setPasswordHash("hashed");
            when(userService.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            var request = new UserController.LoginRequest("testuser", "wrong");
            ResponseEntity<?> response = controller.login(request);
            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("账户已禁用返回 403")
        void shouldRejectDisabledUser() {
            SoulUser user = createUser("usr-1", "testuser");
            user.setPasswordHash("hashed");
            user.setEnabled(false);
            when(userService.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

            var request = new UserController.LoginRequest("testuser", "pass");
            ResponseEntity<?> response = controller.login(request);
            assertEquals(403, response.getStatusCode().value());
        }

        @Test
        @DisplayName("空用户名返回 400")
        void shouldRejectEmptyCredentials() {
            var request = new UserController.LoginRequest("", "pass");
            ResponseEntity<?> response = controller.login(request);
            assertEquals(400, response.getStatusCode().value());
        }
    }

    // ==================== /me ====================

    @Nested
    @DisplayName("获取当前用户")
    class Me {

        @Test
        @DisplayName("有效 token 返回用户信息")
        void shouldReturnUserForValidToken() {
            SoulUser user = createUser("usr-1", "testuser");
            when(authSessionService.resolveSession("tk-valid"))
                .thenReturn(Optional.of(new AuthSessionService.SessionInfo("usr-1", "USER")));
            when(userService.findById("usr-1")).thenReturn(Optional.of(user));

            ResponseEntity<?> response = controller.me("Bearer tk-valid");
            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("无 Authorization 返回 401")
        void shouldRejectMissingAuth() {
            ResponseEntity<?> response = controller.me(null);
            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("非 Bearer 格式返回 401")
        void shouldRejectNonBearerAuth() {
            ResponseEntity<?> response = controller.me("Basic xyz");
            assertEquals(401, response.getStatusCode().value());
        }

        @Test
        @DisplayName("无效 token 返回 401")
        void shouldRejectInvalidToken() {
            when(authSessionService.resolveSession("tk-bad")).thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.me("Bearer tk-bad");
            assertEquals(401, response.getStatusCode().value());
        }
    }

    // ==================== 登出 ====================

    @Test
    @DisplayName("登出清除 token")
    void shouldLogout() {
        ResponseEntity<?> response = controller.logout("Bearer tk-xxx");
        assertEquals(200, response.getStatusCode().value());
        verify(authSessionService).invalidate("tk-xxx");
    }

    @Test
    @DisplayName("无 token 登出不报错")
    void shouldHandleLogoutWithoutToken() {
        ResponseEntity<?> response = controller.logout(null);
        assertEquals(200, response.getStatusCode().value());
    }

    // ==================== 管理员操作 ====================

    @Nested
    @DisplayName("管理员操作")
    class AdminOps {

        @Test
        @DisplayName("管理员可列出用户")
        void shouldListUsersAsAdmin() {
            when(userService.listAllUsers()).thenReturn(List.of(createUser("u1", "user1")));

            ResponseEntity<?> response = controller.listUsers(adminRequest);
            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("非管理员列出用户返回 403")
        void shouldDenyListUsersForNonAdmin() {
            ResponseEntity<?> response = controller.listUsers(userRequest);
            assertEquals(403, response.getStatusCode().value());
        }

        @Test
        @DisplayName("管理员可更新角色")
        void shouldUpdateRoleAsAdmin() {
            ResponseEntity<?> response = controller.updateRole(
                "usr-1", Map.of("role", "ADMIN"), adminRequest
            );
            assertEquals(200, response.getStatusCode().value());
            verify(userService).updateUserRole("usr-1", "ADMIN");
        }

        @Test
        @DisplayName("管理员可启用/禁用用户")
        void shouldUpdateStatusAsAdmin() {
            ResponseEntity<?> response = controller.updateStatus(
                "usr-1", Map.of("enabled", false), adminRequest
            );
            assertEquals(200, response.getStatusCode().value());
            verify(userService).disableUser("usr-1");
        }

        @Test
        @DisplayName("非管理员更新角色返回 403")
        void shouldDenyUpdateRoleForNonAdmin() {
            ResponseEntity<?> response = controller.updateRole(
                "usr-1", Map.of("role", "ADMIN"), userRequest
            );
            assertEquals(403, response.getStatusCode().value());
        }
    }

    // ==================== 情绪打卡 ====================

    @Nested
    @DisplayName("情绪功能")
    class EmotionTests {

        @Test
        @DisplayName("情绪打卡成功")
        void shouldCheckinSuccessfully() {
            EmotionRecord record = new EmotionRecord("emo-1", "usr-1", "happy", "今天不错", System.currentTimeMillis());
            when(userService.checkin("usr-1", "happy", "今天不错")).thenReturn(record);

            var request = new UserController.CheckinRequest("usr-1", "happy", "今天不错");
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
        @DisplayName("获取用户统计")
        void shouldGetStats() {
            when(userService.getStats("usr-1")).thenReturn(Map.of("totalSessions", 5));

            ResponseEntity<Map<String, Object>> response = controller.getStats("usr-1");
            assertEquals(200, response.getStatusCode().value());
        }
    }

    // ==================== 辅助方法 ====================

    private static SoulUser createUser(String id, String username) {
        return new SoulUser(id, username, null, null, username != null ? username : "匿名",
            "FREE", "USER", true, System.currentTimeMillis(), System.currentTimeMillis());
    }
}
