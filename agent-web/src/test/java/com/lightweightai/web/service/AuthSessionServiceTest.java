package com.lightweightai.web.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthSessionService - Token会话管理")
class AuthSessionServiceTest {

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService();
    }

    // ==================== Token 创建 ====================

    @Test
    @DisplayName("创建 token 以 tk- 开头")
    void shouldCreateTokenWithPrefix() {
        String token = service.createSessionToken("user1");
        assertTrue(token.startsWith("tk-"));
    }

    @Test
    @DisplayName("每次创建 token 都唯一")
    void shouldCreateUniqueTokens() {
        String token1 = service.createSessionToken("user1");
        String token2 = service.createSessionToken("user1");
        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("默认 role 为 USER")
    void shouldDefaultToUserRole() {
        String token = service.createSessionToken("user1");
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);

        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    @Test
    @DisplayName("可指定 role")
    void shouldAcceptCustomRole() {
        String token = service.createSessionToken("admin1", "ADMIN");
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);

        assertTrue(session.isPresent());
        assertEquals("ADMIN", session.get().getRole());
    }

    @Test
    @DisplayName("null role 默认为 USER")
    void shouldDefaultNullRole() {
        String token = service.createSessionToken("user1", null);
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);

        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    // ==================== Token 解析 ====================

    @Test
    @DisplayName("resolveUserId 返回正确 userId")
    void shouldResolveUserId() {
        String token = service.createSessionToken("user42");
        Optional<String> userId = service.resolveUserId(token);

        assertTrue(userId.isPresent());
        assertEquals("user42", userId.get());
    }

    @Test
    @DisplayName("无效 token 返回 empty")
    void shouldReturnEmptyForInvalidToken() {
        Optional<String> userId = service.resolveUserId("invalid-token");
        assertTrue(userId.isEmpty());
    }

    @Test
    @DisplayName("resolveSession 返回完整信息")
    void shouldResolveFullSession() {
        String token = service.createSessionToken("user1", "ADMIN");
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);

        assertTrue(session.isPresent());
        assertEquals("user1", session.get().getUserId());
        assertEquals("ADMIN", session.get().getRole());
    }

    // ==================== Token 失效 ====================

    @Test
    @DisplayName("invalidate 使 token 失效")
    void shouldInvalidateToken() {
        String token = service.createSessionToken("user1");
        service.invalidate(token);

        assertTrue(service.resolveUserId(token).isEmpty());
        assertTrue(service.resolveSession(token).isEmpty());
    }

    @Test
    @DisplayName("invalidate 不存在的 token 不抛异常")
    void shouldNotThrowOnInvalidateNonexistent() {
        assertDoesNotThrow(() -> service.invalidate("nonexistent"));
    }

    // ==================== 并发安全 ====================

    @Test
    @DisplayName("多 token 独立管理")
    void shouldManageMultipleTokensIndependently() {
        String token1 = service.createSessionToken("user1");
        String token2 = service.createSessionToken("user2");

        service.invalidate(token1);

        assertTrue(service.resolveUserId(token1).isEmpty());
        assertEquals("user2", service.resolveUserId(token2).orElse(null));
    }
}
