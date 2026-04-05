package com.lightweightai.web.service;

import com.lightweightai.web.service.AuthSessionService.SessionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthSessionService - in-memory token session management")
class AuthSessionServiceTest {

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService();
    }

    @Test
    @DisplayName("createSessionToken generates tk- prefixed token")
    void createSessionTokenGeneratesPrefixedToken() {
        String token = service.createSessionToken("user-1");

        assertNotNull(token);
        assertTrue(token.startsWith("tk-"));
        assertTrue(token.length() > 10);
    }

    @Test
    @DisplayName("createSessionToken with role stores role info")
    void createSessionTokenWithRoleStoresRole() {
        String token = service.createSessionToken("admin-1", "ADMIN");

        Optional<SessionInfo> session = service.resolveSession(token);

        assertTrue(session.isPresent());
        assertEquals("admin-1", session.get().getUserId());
        assertEquals("ADMIN", session.get().getRole());
    }

    @Test
    @DisplayName("createSessionToken without role defaults to USER")
    void createSessionTokenDefaultsToUserRole() {
        String token = service.createSessionToken("user-2");

        Optional<SessionInfo> session = service.resolveSession(token);

        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    @Test
    @DisplayName("createSessionToken with null role defaults to USER")
    void createSessionTokenNullRoleDefaultsToUser() {
        String token = service.createSessionToken("user-3", null);

        Optional<SessionInfo> session = service.resolveSession(token);

        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    @Test
    @DisplayName("resolveUserId returns userId for valid token")
    void resolveUserIdReturnsForValidToken() {
        String token = service.createSessionToken("user-4");

        Optional<String> userId = service.resolveUserId(token);

        assertTrue(userId.isPresent());
        assertEquals("user-4", userId.get());
    }

    @Test
    @DisplayName("resolveUserId returns empty for unknown token")
    void resolveUserIdReturnsEmptyForUnknown() {
        Optional<String> userId = service.resolveUserId("nonexistent-token");

        assertTrue(userId.isEmpty());
    }

    @Test
    @DisplayName("resolveSession returns empty for unknown token")
    void resolveSessionReturnsEmptyForUnknown() {
        Optional<SessionInfo> session = service.resolveSession("bad-token");

        assertTrue(session.isEmpty());
    }

    @Test
    @DisplayName("invalidate removes token")
    void invalidateRemovesToken() {
        String token = service.createSessionToken("user-5");

        assertTrue(service.resolveUserId(token).isPresent());

        service.invalidate(token);

        assertTrue(service.resolveUserId(token).isEmpty());
        assertTrue(service.resolveSession(token).isEmpty());
    }

    @Test
    @DisplayName("multiple tokens for same user are independent")
    void multipleTokensAreIndependent() {
        String token1 = service.createSessionToken("user-6");
        String token2 = service.createSessionToken("user-6");

        assertNotEquals(token1, token2);

        service.invalidate(token1);

        assertTrue(service.resolveUserId(token1).isEmpty());
        assertTrue(service.resolveUserId(token2).isPresent());
    }

    @Test
    @DisplayName("each call generates unique tokens")
    void eachCallGeneratesUniqueTokens() {
        String token1 = service.createSessionToken("user-7");
        String token2 = service.createSessionToken("user-7");
        String token3 = service.createSessionToken("user-8");

        assertNotEquals(token1, token2);
        assertNotEquals(token1, token3);
        assertNotEquals(token2, token3);
    }
}
