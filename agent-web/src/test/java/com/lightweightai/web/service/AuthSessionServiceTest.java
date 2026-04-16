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
    @DisplayName("创建 token 后可以解析 userId")
    void shouldCreateAndResolveToken() {
        String token = service.createSessionToken("user-123");

        assertNotNull(token);
        assertTrue(token.startsWith("tk-"));
        assertEquals("user-123", service.resolveUserId(token).orElse(null));
    }

    @Test
    @DisplayName("默认角色为 USER")
    void shouldDefaultToUserRole() {
        String token = service.createSessionToken("user-1");

        AuthSessionService.SessionInfo session = service.resolveSession(token).orElse(null);
        assertNotNull(session);
        assertEquals("USER", session.getRole());
    }

    @Test
    @DisplayName("可以指定角色")
    void shouldSetCustomRole() {
        String token = service.createSessionToken("admin-1", "ADMIN");

        AuthSessionService.SessionInfo session = service.resolveSession(token).orElse(null);
        assertNotNull(session);
        assertEquals("ADMIN", session.getRole());
        assertEquals("admin-1", session.getUserId());
    }

    @Test
    @DisplayName("null 角色默认为 USER")
    void shouldDefaultNullRole() {
        String token = service.createSessionToken("user-1", null);

        AuthSessionService.SessionInfo session = service.resolveSession(token).orElse(null);
        assertNotNull(session);
        assertEquals("USER", session.getRole());
    }

    @Test
    @DisplayName("不存在的 token 返回 empty")
    void shouldReturnEmptyForInvalidToken() {
        assertEquals(Optional.empty(), service.resolveUserId("invalid-token"));
        assertEquals(Optional.empty(), service.resolveSession("invalid-token"));
    }

    @Test
    @DisplayName("invalidate 后 token 不再有效")
    void shouldInvalidateToken() {
        String token = service.createSessionToken("user-1");
        assertNotNull(service.resolveUserId(token).orElse(null));

        service.invalidate(token);
        assertEquals(Optional.empty(), service.resolveUserId(token));
    }

    @Test
    @DisplayName("每次创建的 token 唯一")
    void shouldGenerateUniqueTokens() {
        String token1 = service.createSessionToken("user-1");
        String token2 = service.createSessionToken("user-1");

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("不同用户的 token 互不影响")
    void shouldIsolateUsers() {
        String tokenA = service.createSessionToken("user-a");
        String tokenB = service.createSessionToken("user-b");

        assertEquals("user-a", service.resolveUserId(tokenA).orElse(null));
        assertEquals("user-b", service.resolveUserId(tokenB).orElse(null));

        service.invalidate(tokenA);
        assertEquals(Optional.empty(), service.resolveUserId(tokenA));
        assertEquals("user-b", service.resolveUserId(tokenB).orElse(null));
    }
}
