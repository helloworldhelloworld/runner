package com.lightweightai.web.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthSessionService - Token 会话管理")
class AuthSessionServiceTest {

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService();
    }

    @Test
    @DisplayName("创建 token 格式正确")
    void tokenFormat() {
        String token = service.createSessionToken("user1");

        assertNotNull(token);
        assertTrue(token.startsWith("tk-"), "Token should start with 'tk-'");
        assertEquals(35, token.length(), "tk- (3) + UUID without dashes (32) = 35");
        // Should not contain dashes after the prefix
        assertFalse(token.substring(3).contains("-"));
    }

    @Test
    @DisplayName("每次创建的 token 唯一")
    void tokensAreUnique() {
        String t1 = service.createSessionToken("user1");
        String t2 = service.createSessionToken("user1");

        assertNotEquals(t1, t2);
    }

    @Test
    @DisplayName("默认 role 为 USER")
    void defaultRoleIsUser() {
        String token = service.createSessionToken("user1");

        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    @Test
    @DisplayName("自定义 role")
    void customRole() {
        String token = service.createSessionToken("admin1", "ADMIN");

        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("ADMIN", session.get().getRole());
        assertEquals("admin1", session.get().getUserId());
    }

    @Test
    @DisplayName("null role 默认为 USER")
    void nullRoleDefaultsToUser() {
        String token = service.createSessionToken("user1", null);

        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    @Test
    @DisplayName("resolveUserId 正常解析")
    void resolveUserId() {
        String token = service.createSessionToken("user42");

        Optional<String> userId = service.resolveUserId(token);
        assertTrue(userId.isPresent());
        assertEquals("user42", userId.get());
    }

    @Test
    @DisplayName("resolveUserId 不存在的 token 返回 empty")
    void resolveUserIdNotFound() {
        Optional<String> userId = service.resolveUserId("tk-nonexistent");
        assertTrue(userId.isEmpty());
    }

    @Test
    @DisplayName("resolveSession 不存在的 token 返回 empty")
    void resolveSessionNotFound() {
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession("tk-fake");
        assertTrue(session.isEmpty());
    }

    @Test
    @DisplayName("invalidate 移除会话")
    void invalidateRemovesSession() {
        String token = service.createSessionToken("user1");

        // Before invalidation
        assertTrue(service.resolveUserId(token).isPresent());

        // After invalidation
        service.invalidate(token);
        assertTrue(service.resolveUserId(token).isEmpty());
        assertTrue(service.resolveSession(token).isEmpty());
    }

    @Test
    @DisplayName("invalidate 不存在的 token 不报错")
    void invalidateNonExistentTokenIsNoOp() {
        assertDoesNotThrow(() -> service.invalidate("tk-nonexistent"));
    }

    @Test
    @DisplayName("多用户独立会话")
    void multipleUsersIndependentSessions() {
        String t1 = service.createSessionToken("user1", "USER");
        String t2 = service.createSessionToken("user2", "ADMIN");

        assertEquals("user1", service.resolveUserId(t1).orElse(null));
        assertEquals("user2", service.resolveUserId(t2).orElse(null));
        assertEquals("USER", service.resolveSession(t1).get().getRole());
        assertEquals("ADMIN", service.resolveSession(t2).get().getRole());

        // Invalidating one doesn't affect the other
        service.invalidate(t1);
        assertTrue(service.resolveUserId(t1).isEmpty());
        assertTrue(service.resolveUserId(t2).isPresent());
    }
}
