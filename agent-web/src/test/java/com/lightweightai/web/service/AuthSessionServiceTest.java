package com.lightweightai.web.service;

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

    // ==================== createSessionToken ====================

    @Test
    @DisplayName("createSessionToken generates token with tk- prefix")
    void createSessionTokenGeneratesTkPrefix() {
        String token = service.createSessionToken("user1");
        assertNotNull(token);
        assertTrue(token.startsWith("tk-"));
    }

    @Test
    @DisplayName("createSessionToken generates unique tokens")
    void createSessionTokenGeneratesUniqueTokens() {
        String token1 = service.createSessionToken("user1");
        String token2 = service.createSessionToken("user1");
        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("createSessionToken with default role uses USER")
    void createSessionTokenDefaultRole() {
        String token = service.createSessionToken("user1");
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    @Test
    @DisplayName("createSessionToken with explicit role stores it")
    void createSessionTokenExplicitRole() {
        String token = service.createSessionToken("admin1", "ADMIN");
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("ADMIN", session.get().getRole());
    }

    @Test
    @DisplayName("createSessionToken with null role defaults to USER")
    void createSessionTokenNullRole() {
        String token = service.createSessionToken("user1", null);
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("USER", session.get().getRole());
    }

    // ==================== resolveUserId ====================

    @Test
    @DisplayName("resolveUserId returns userId for valid token")
    void resolveUserIdReturnsUserForValidToken() {
        String token = service.createSessionToken("user1");
        Optional<String> userId = service.resolveUserId(token);
        assertTrue(userId.isPresent());
        assertEquals("user1", userId.get());
    }

    @Test
    @DisplayName("resolveUserId returns empty for unknown token")
    void resolveUserIdReturnsEmptyForUnknownToken() {
        Optional<String> userId = service.resolveUserId("tk-unknown");
        assertTrue(userId.isEmpty());
    }

    // ==================== resolveSession ====================

    @Test
    @DisplayName("resolveSession returns full session info")
    void resolveSessionReturnsFullInfo() {
        String token = service.createSessionToken("user1", "ADMIN");
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession(token);
        assertTrue(session.isPresent());
        assertEquals("user1", session.get().getUserId());
        assertEquals("ADMIN", session.get().getRole());
    }

    @Test
    @DisplayName("resolveSession returns empty for unknown token")
    void resolveSessionReturnsEmptyForUnknown() {
        Optional<AuthSessionService.SessionInfo> session = service.resolveSession("invalid");
        assertTrue(session.isEmpty());
    }

    // ==================== invalidate ====================

    @Test
    @DisplayName("invalidate removes token so it cannot be resolved")
    void invalidateRemovesToken() {
        String token = service.createSessionToken("user1");
        assertTrue(service.resolveUserId(token).isPresent());

        service.invalidate(token);

        assertTrue(service.resolveUserId(token).isEmpty());
        assertTrue(service.resolveSession(token).isEmpty());
    }

    @Test
    @DisplayName("invalidate with unknown token does not throw")
    void invalidateUnknownTokenNoOp() {
        assertDoesNotThrow(() -> service.invalidate("nonexistent"));
    }
}
