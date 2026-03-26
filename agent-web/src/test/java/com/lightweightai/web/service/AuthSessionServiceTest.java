package com.lightweightai.web.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthSessionService - 会话令牌管理")
class AuthSessionServiceTest {

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService();
    }

    @Nested
    @DisplayName("创建会话令牌")
    class CreateSessionToken {

        @Test
        @DisplayName("默认角色应为 USER")
        void shouldCreateTokenWithDefaultRole() {
            String token = service.createSessionToken("user-123");

            assertNotNull(token);
            assertTrue(token.startsWith("tk-"));

            Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
            assertTrue(session.isPresent());
            assertEquals("user-123", session.get().getUserId());
            assertEquals("USER", session.get().getRole());
        }

        @Test
        @DisplayName("指定角色创建令牌")
        void shouldCreateTokenWithSpecifiedRole() {
            String token = service.createSessionToken("admin-1", "ADMIN");

            Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
            assertTrue(session.isPresent());
            assertEquals("admin-1", session.get().getUserId());
            assertEquals("ADMIN", session.get().getRole());
        }

        @Test
        @DisplayName("null 角色应回退为 USER")
        void shouldFallbackToUserWhenRoleIsNull() {
            String token = service.createSessionToken("user-456", null);

            Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
            assertTrue(session.isPresent());
            assertEquals("USER", session.get().getRole());
        }

        @Test
        @DisplayName("每次创建不同令牌")
        void shouldCreateUniqueTokens() {
            String token1 = service.createSessionToken("user-1");
            String token2 = service.createSessionToken("user-1");

            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("令牌格式: tk- 前缀 + 32位hex")
        void shouldGenerateCorrectTokenFormat() {
            String token = service.createSessionToken("user-1");

            assertTrue(token.startsWith("tk-"));
            // UUID without hyphens = 32 hex chars
            String hex = token.substring(3);
            assertEquals(32, hex.length());
            assertTrue(hex.matches("[0-9a-f]+"));
        }
    }

    @Nested
    @DisplayName("解析会话")
    class ResolveSession {

        @Test
        @DisplayName("有效令牌返回用户ID")
        void shouldResolveUserIdForValidToken() {
            String token = service.createSessionToken("user-789");

            Optional<String> userId = service.resolveUserId(token);
            assertTrue(userId.isPresent());
            assertEquals("user-789", userId.get());
        }

        @Test
        @DisplayName("无效令牌返回空")
        void shouldReturnEmptyForInvalidToken() {
            Optional<String> userId = service.resolveUserId("tk-invalid");
            assertFalse(userId.isPresent());
        }

        @Test
        @DisplayName("null令牌返回空")
        void shouldReturnEmptyForNullToken() {
            Optional<String> userId = service.resolveUserId(null);
            assertFalse(userId.isPresent());
        }

        @Test
        @DisplayName("resolveSession 返回完整会话信息")
        void shouldResolveFullSessionInfo() {
            String token = service.createSessionToken("user-abc", "MODERATOR");

            Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
            assertTrue(session.isPresent());
            assertEquals("user-abc", session.get().getUserId());
            assertEquals("MODERATOR", session.get().getRole());
        }
    }

    @Nested
    @DisplayName("使令牌失效")
    class Invalidate {

        @Test
        @DisplayName("失效后无法解析")
        void shouldNotResolveAfterInvalidation() {
            String token = service.createSessionToken("user-del");
            service.invalidate(token);

            assertFalse(service.resolveUserId(token).isPresent());
            assertFalse(service.resolveSession(token).isPresent());
        }

        @Test
        @DisplayName("失效不存在的令牌不抛异常")
        void shouldNotThrowWhenInvalidatingNonExistentToken() {
            assertDoesNotThrow(() -> service.invalidate("tk-nonexistent"));
        }

        @Test
        @DisplayName("失效一个令牌不影响其他")
        void shouldNotAffectOtherTokens() {
            String token1 = service.createSessionToken("user-1");
            String token2 = service.createSessionToken("user-2");

            service.invalidate(token1);

            assertFalse(service.resolveUserId(token1).isPresent());
            assertTrue(service.resolveUserId(token2).isPresent());
        }
    }
}
