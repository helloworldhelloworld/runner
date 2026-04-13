package com.lightweightai.web.controller;

import com.lightweightai.user.UserService;
import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import com.lightweightai.web.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController - user management, auth, emotion check-ins")
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthSessionService authSessionService;
    @Mock private HttpServletRequest httpRequest;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService, passwordEncoder, authSessionService);
    }

    // ==================== createAnonymous ====================

    @Test
    @DisplayName("createAnonymous delegates to userService")
    void createAnonymousDelegates() {
        SoulUser user = createUser("anon-1", null, "Anonymous");
        when(userService.createAnonymousUser()).thenReturn(user);

        ResponseEntity<SoulUser> result = controller.createAnonymous();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("anon-1", result.getBody().getId());
    }

    // ==================== register ====================

    @Test
    @DisplayName("register succeeds with valid credentials")
    void registerSucceeds() {
        SoulUser user = createUser("u1", "alice", "Alice");
        user.setRole("USER");
        when(passwordEncoder.encode("pass123")).thenReturn("$hash$");
        when(userService.register("alice", "$hash$", "Alice")).thenReturn(user);
        when(authSessionService.createSessionToken("u1", "USER")).thenReturn("tk-abc");

        UserController.RegisterRequest request = new UserController.RegisterRequest("alice", "pass123", "Alice");
        ResponseEntity<?> result = controller.register(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertEquals("tk-abc", body.get("token"));
    }

    @Test
    @DisplayName("register returns bad request for blank username")
    void registerRejectsBlanks() {
        UserController.RegisterRequest request = new UserController.RegisterRequest("", "pass", null);
        ResponseEntity<?> result = controller.register(request);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    @DisplayName("register returns bad request for null password")
    void registerRejectsNullPassword() {
        UserController.RegisterRequest request = new UserController.RegisterRequest("alice", null, null);
        ResponseEntity<?> result = controller.register(request);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    @DisplayName("register handles duplicate username")
    void registerHandlesDuplicate() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userService.register(anyString(), anyString(), any()))
            .thenThrow(new IllegalArgumentException("用户名已存在"));

        UserController.RegisterRequest request = new UserController.RegisterRequest("alice", "pass", null);
        ResponseEntity<?> result = controller.register(request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    // ==================== login ====================

    @Test
    @DisplayName("login succeeds with correct credentials")
    void loginSucceeds() {
        SoulUser user = createUser("u1", "alice", "Alice");
        user.setPasswordHash("$hash$");
        user.setRole("USER");
        user.setEnabled(true);

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "$hash$")).thenReturn(true);
        when(authSessionService.createSessionToken("u1", "USER")).thenReturn("tk-xyz");

        UserController.LoginRequest request = new UserController.LoginRequest("alice", "pass123");
        ResponseEntity<?> result = controller.login(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertEquals("tk-xyz", body.get("token"));
    }

    @Test
    @DisplayName("login returns 401 for wrong password")
    void loginRejectsWrongPassword() {
        SoulUser user = createUser("u1", "alice", "Alice");
        user.setPasswordHash("$hash$");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$hash$")).thenReturn(false);

        UserController.LoginRequest request = new UserController.LoginRequest("alice", "wrong");
        ResponseEntity<?> result = controller.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    @DisplayName("login returns 403 for disabled account")
    void loginRejectsDisabledAccount() {
        SoulUser user = createUser("u1", "alice", "Alice");
        user.setPasswordHash("$hash$");
        user.setEnabled(false);

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "$hash$")).thenReturn(true);

        UserController.LoginRequest request = new UserController.LoginRequest("alice", "pass");
        ResponseEntity<?> result = controller.login(request);

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    @DisplayName("login rejects blank credentials")
    void loginRejectsBlanks() {
        UserController.LoginRequest request = new UserController.LoginRequest("", "pass");
        ResponseEntity<?> result = controller.login(request);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    // ==================== me ====================

    @Test
    @DisplayName("me returns user info for valid token")
    void meReturnsUserForValidToken() {
        SoulUser user = createUser("u1", "alice", "Alice");
        AuthSessionService.SessionInfo session = new AuthSessionService.SessionInfo("u1", "USER");
        when(authSessionService.resolveSession("valid-token")).thenReturn(Optional.of(session));
        when(userService.findById("u1")).thenReturn(Optional.of(user));

        ResponseEntity<?> result = controller.me("Bearer valid-token");

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    @DisplayName("me returns 401 without authorization header")
    void meReturns401WithoutAuth() {
        ResponseEntity<?> result = controller.me(null);
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    @DisplayName("me returns 401 for invalid token")
    void meReturns401ForInvalidToken() {
        when(authSessionService.resolveSession("bad-token")).thenReturn(Optional.empty());

        ResponseEntity<?> result = controller.me("Bearer bad-token");
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    // ==================== logout ====================

    @Test
    @DisplayName("logout invalidates token")
    void logoutInvalidatesToken() {
        ResponseEntity<?> result = controller.logout("Bearer my-token");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(authSessionService).invalidate("my-token");
    }

    @Test
    @DisplayName("logout without authorization header does not throw")
    void logoutWithoutAuthNoOp() {
        ResponseEntity<?> result = controller.logout(null);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(authSessionService, never()).invalidate(anyString());
    }

    // ==================== admin: listUsers ====================

    @Test
    @DisplayName("listUsers returns 403 for non-admin")
    void listUsersRejectsNonAdmin() {
        when(httpRequest.getAttribute("userRole")).thenReturn("USER");

        ResponseEntity<?> result = controller.listUsers(httpRequest);

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    @DisplayName("listUsers returns all users for admin")
    void listUsersReturnsForAdmin() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
        when(userService.listAllUsers()).thenReturn(List.of(createUser("u1", "alice", "Alice")));

        ResponseEntity<?> result = controller.listUsers(httpRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    // ==================== updateRole ====================

    @Test
    @DisplayName("updateRole rejects non-admin")
    void updateRoleRejectsNonAdmin() {
        when(httpRequest.getAttribute("userRole")).thenReturn("USER");

        ResponseEntity<?> result = controller.updateRole("u1", Map.of("role", "ADMIN"), httpRequest);

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    @DisplayName("updateRole succeeds for admin")
    void updateRoleSucceeds() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

        ResponseEntity<?> result = controller.updateRole("u1", Map.of("role", "ADMIN"), httpRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(userService).updateUserRole("u1", "ADMIN");
    }

    // ==================== updateStatus ====================

    @Test
    @DisplayName("updateStatus enables user for admin")
    void updateStatusEnablesUser() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

        ResponseEntity<?> result = controller.updateStatus("u1", Map.of("enabled", true), httpRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(userService).enableUser("u1");
    }

    @Test
    @DisplayName("updateStatus disables user for admin")
    void updateStatusDisablesUser() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

        ResponseEntity<?> result = controller.updateStatus("u1", Map.of("enabled", false), httpRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(userService).disableUser("u1");
    }

    // ==================== checkin ====================

    @Test
    @DisplayName("checkin creates emotion record")
    void checkinCreatesRecord() {
        EmotionRecord record = new EmotionRecord();
        when(userService.checkin("u1", "happy", "Great day")).thenReturn(record);

        UserController.CheckinRequest request = new UserController.CheckinRequest("u1", "happy", "Great day");
        ResponseEntity<?> result = controller.checkin(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(userService).checkin("u1", "happy", "Great day");
    }

    // ==================== getEmotions ====================

    @Test
    @DisplayName("getEmotions returns emotion history")
    void getEmotionsReturnsHistory() {
        when(userService.getEmotions("u1", 7)).thenReturn(List.of());

        ResponseEntity<List<EmotionRecord>> result = controller.getEmotions("u1", 7);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(userService).getEmotions("u1", 7);
    }

    // ==================== getStats ====================

    @Test
    @DisplayName("getStats returns emotion statistics")
    void getStatsReturnsStatistics() {
        when(userService.getStats("u1")).thenReturn(Map.of("totalCheckins", 10));

        ResponseEntity<Map<String, Object>> result = controller.getStats("u1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(10, result.getBody().get("totalCheckins"));
    }

    // ==================== helpers ====================

    private SoulUser createUser(String id, String username, String nickname) {
        SoulUser user = new SoulUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setEnabled(true);
        user.setRole("USER");
        return user;
    }
}
