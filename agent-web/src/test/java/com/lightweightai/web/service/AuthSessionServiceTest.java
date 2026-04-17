package com.lightweightai.web.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthSessionServiceTest {

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService();
    }

    @Test
    void createSessionTokenReturnsTokenWithPrefix() {
        String token = service.createSessionToken("user-1");
        assertNotNull(token);
        assertTrue(token.startsWith("tk-"));
    }

    @Test
    void createSessionTokenDefaultsRoleToUser() {
        String token = service.createSessionToken("user-1");
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    @Test
    void createSessionTokenWithCustomRole() {
        String token = service.createSessionToken("admin-1", "ADMIN");
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("ADMIN", session.get().getRole());
    }

    @Test
    void createSessionTokenWithNullRoleDefaultsToUser() {
        String token = service.createSessionToken("user-1", null);
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    @Test
    void twoTokensForSameUserAreDifferent() {
        String token1 = service.createSessionToken("user-1");
        String token2 = service.createSessionToken("user-1");
        assertNotEquals(token1, token2);
    }

    @Test
    void resolveUserIdWithValidToken() {
        String token = service.createSessionToken("user-42");
        Optional<String> userId = service.resolveUserId(token);
        assertTrue(userId.isPresent());
        assertEquals("user-42", userId.get());
    }

    @Test
    void resolveUserIdWithInvalidTokenReturnsEmpty() {
        Optional<String> userId = service.resolveUserId("tk-nonexistent");
        assertTrue(userId.isEmpty());
    }

    @Test
    void resolveSessionWithValidToken() {
        String token = service.createSessionToken("user-1", "MODERATOR");
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("user-1", session.get().getUserId());
        assertEquals("MODERATOR", session.get().getRole());
    }

    @Test
    void resolveSessionWithInvalidTokenReturnsEmpty() {
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession("tk-bad");
        assertTrue(session.isEmpty());
    }

    @Test
    void invalidateRemovesToken() {
        String token = service.createSessionToken("user-1");
        assertTrue(service.resolveUserId(token).isPresent());

        service.invalidate(token);
        assertTrue(service.resolveUserId(token).isEmpty());
    }

    @Test
    void invalidateNonexistentTokenNoError() {
        assertDoesNotThrow(() -> service.invalidate("tk-does-not-exist"));
    }

    @Test
    void multipleUsersIsolated() {
        String tokenA = service.createSessionToken("alice");
        String tokenB = service.createSessionToken("bob");

        assertEquals("alice", service.resolveUserId(tokenA).orElseThrow());
        assertEquals("bob", service.resolveUserId(tokenB).orElseThrow());

        service.invalidate(tokenA);
        assertTrue(service.resolveUserId(tokenA).isEmpty());
        assertTrue(service.resolveUserId(tokenB).isPresent());
    }
}
