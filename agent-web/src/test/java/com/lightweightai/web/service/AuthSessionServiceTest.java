package com.lightweightai.web.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthSessionService - 认证会话服务")
class AuthSessionServiceTest {

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService();
    }

    // ==================== 创建会话 ====================

    @Nested
    @DisplayName("创建会话")
    class CreateSession {

        @Test
        @DisplayName("创建 token 返回非空字符串")
        void shouldCreateNonEmptyToken() {
            String token = service.createSessionToken("user-1");
            assertNotNull(token);
            assertFalse(token.isEmpty());
        }

        @Test
        @DisplayName("token 以 tk- 开头")
        void shouldStartWithPrefix() {
            String token = service.createSessionToken("user-1");
            assertTrue(token.startsWith("tk-"));
        }

        @Test
        @DisplayName("每次创建的 token 不同")
        void shouldCreateUniqueTokens() {
            String token1 = service.createSessionToken("user-1");
            String token2 = service.createSessionToken("user-1");
            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("默认角色为 USER")
        void shouldDefaultToUserRole() {
            String token = service.createSessionToken("user-1");
            Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
            assertTrue(session.isPresent());
            assertEquals("USER", session.get().getRole());
        }

        @Test
        @DisplayName("可指定角色")
        void shouldAcceptCustomRole() {
            String token = service.createSessionToken("admin-1", "ADMIN");
            Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
            assertTrue(session.isPresent());
            assertEquals("ADMIN", session.get().getRole());
        }

        @Test
        @DisplayName("null 角色默认为 USER")
        void shouldDefaultNullRoleToUser() {
            String token = service.createSessionToken("user-1", null);
            Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
            assertTrue(session.isPresent());
            assertEquals("USER", session.get().getRole());
        }
    }

    // ==================== 解析会话 ====================

    @Nested
    @DisplayName("解析会话")
    class ResolveSession {

        @Test
        @DisplayName("有效 token 能解析出 userId")
        void shouldResolveUserId() {
            String token = service.createSessionToken("user-42");
            Optional<String> userId = service.resolveUserId(token);
            assertTrue(userId.isPresent());
            assertEquals("user-42", userId.get());
        }

        @Test
        @DisplayName("无效 token 返回 empty")
        void shouldReturnEmptyForInvalidToken() {
            Optional<String> userId = service.resolveUserId("invalid-token");
            assertFalse(userId.isPresent());
        }

        @Test
        @DisplayName("resolveSession 返回完整会话信息")
        void shouldResolveFullSessionInfo() {
            String token = service.createSessionToken("user-1", "ADMIN");
            Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);

            assertTrue(session.isPresent());
            assertEquals("user-1", session.get().getUserId());
            assertEquals("ADMIN", session.get().getRole());
        }

        @Test
        @DisplayName("resolveSession 无效 token 返回 empty")
        void shouldReturnEmptySessionForInvalidToken() {
            Optional<AuthSessionService.SessionInfo> session = service.resolveSession("bad-token");
            assertFalse(session.isPresent());
        }
    }

    // ==================== 注销 ====================

    @Nested
    @DisplayName("注销会话")
    class Invalidate {

        @Test
        @DisplayName("注销后 token 失效")
        void shouldInvalidateToken() {
            String token = service.createSessionToken("user-1");
            assertTrue(service.resolveUserId(token).isPresent());

            service.invalidate(token);
            assertFalse(service.resolveUserId(token).isPresent());
        }

        @Test
        @DisplayName("注销不存在的 token 不抛异常")
        void shouldNotThrowOnInvalidToken() {
            assertDoesNotThrow(() -> service.invalidate("non-existent-token"));
        }

        @Test
        @DisplayName("注销某个 token 不影响其他 token")
        void shouldNotAffectOtherTokens() {
            String token1 = service.createSessionToken("user-1");
            String token2 = service.createSessionToken("user-2");

            service.invalidate(token1);

            assertFalse(service.resolveUserId(token1).isPresent());
            assertTrue(service.resolveUserId(token2).isPresent());
        }
    }
}
