package com.lightweightai.web.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthSessionService 单元测试
 *
 * 覆盖关键路径：
 * - 创建 session token（默认 / 指定角色）
 * - 解析 userId / Session 信息
 * - 失效 token
 * - token 唯一性与隔离
 */
@DisplayName("AuthSessionService - 内存 token 会话管理")
class AuthSessionServiceTest {

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService();
    }

    @Test
    @DisplayName("createSessionToken 默认角色应为 USER 且 token 以 tk- 开头")
    void shouldCreateTokenWithDefaultRole() {
        String token = service.createSessionToken("u-1");

        assertNotNull(token);
        assertTrue(token.startsWith("tk-"), "token should start with tk- prefix");

        Optional<AuthSessionService.SessionInfo> info = service.resolveSession(token);
        assertTrue(info.isPresent());
        assertEquals("u-1", info.get().getUserId());
        assertEquals("USER", info.get().getRole());
    }

    @Test
    @DisplayName("createSessionToken 显式指定角色应被保留")
    void shouldCreateTokenWithExplicitRole() {
        String token = service.createSessionToken("admin-1", "ADMIN");

        AuthSessionService.SessionInfo info = service.resolveSession(token).orElseThrow();
        assertEquals("admin-1", info.getUserId());
        assertEquals("ADMIN", info.getRole());
    }

    @Test
    @DisplayName("createSessionToken 传入 null 角色应回退为 USER")
    void shouldFallbackToUserRoleWhenNullGiven() {
        String token = service.createSessionToken("u-2", null);
        assertEquals("USER", service.resolveSession(token).orElseThrow().getRole());
    }

    @Test
    @DisplayName("resolveUserId 应返回对应用户 ID")
    void shouldResolveUserId() {
        String token = service.createSessionToken("alice");

        Optional<String> userId = service.resolveUserId(token);

        assertTrue(userId.isPresent());
        assertEquals("alice", userId.get());
    }

    @Test
    @DisplayName("resolveUserId 未知 token 应返回空")
    void shouldReturnEmptyForUnknownToken() {
        assertTrue(service.resolveUserId("tk-does-not-exist").isEmpty());
        assertTrue(service.resolveSession("tk-does-not-exist").isEmpty());
    }

    @Test
    @DisplayName("invalidate 后该 token 不再可解析,其它 token 不受影响")
    void shouldInvalidateSpecificTokenOnly() {
        String tokenA = service.createSessionToken("user-a");
        String tokenB = service.createSessionToken("user-b");

        service.invalidate(tokenA);

        assertTrue(service.resolveUserId(tokenA).isEmpty());
        assertTrue(service.resolveUserId(tokenB).isPresent());
        assertEquals("user-b", service.resolveUserId(tokenB).get());
    }

    @Test
    @DisplayName("连续 createSessionToken 应生成不同的 token")
    void shouldGenerateUniqueTokens() {
        String t1 = service.createSessionToken("u");
        String t2 = service.createSessionToken("u");
        assertNotEquals(t1, t2);
    }

    @Test
    @DisplayName("invalidate 未知 token 不应抛出异常")
    void shouldIgnoreInvalidateOfUnknownToken() {
        assertDoesNotThrow(() -> service.invalidate("tk-unknown"));
    }
}
