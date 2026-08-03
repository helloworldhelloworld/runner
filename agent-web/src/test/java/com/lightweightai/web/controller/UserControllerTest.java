package com.lightweightai.web.controller;

import com.lightweightai.user.UserService;
import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import com.lightweightai.web.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("UserController - User management REST API")
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthSessionService authSessionService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService, passwordEncoder, authSessionService);
    }

    private SoulUser createUser(String id, String username, String nickname, String role, boolean enabled) {
        return new SoulUser(id, username, "hashed", null, nickname, "FREE", role, enabled, 1000L, 2000L);
    }

    private HttpServletRequest adminRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userRole")).thenReturn("ADMIN");
        return request;
    }

    private HttpServletRequest nonAdminRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userRole")).thenReturn("USER");
        return request;
    }

    @Nested
    @DisplayName("POST /user/anonymous - create anonymous user")
    class CreateAnonymous {

        @Test
        @DisplayName("returns anonymous user with 200 OK")
        void createsAnonymousUser() {
            SoulUser anonUser = createUser("anon-123", null, "Anonymous", "USER", true);
            when(userService.createAnonymousUser()).thenReturn(anonUser);

            ResponseEntity<SoulUser> response = controller.createAnonymous();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("anon-123", response.getBody().getId());
        }
    }

    @Nested
    @DisplayName("POST /user/register - user registration")
    class Register {

        @Test
        @DisplayName("successful registration returns token and user info")
        void successfulRegistration() {
            SoulUser user = createUser("usr-1", "alice", "Alice", "USER", true);
            when(passwordEncoder.encode("pass123")).thenReturn("encoded-pass");
            when(userService.register("alice", "encoded-pass", "Alice")).thenReturn(user);
            when(authSessionService.createSessionToken("usr-1", "USER")).thenReturn("tk-abc123");

            UserController.RegisterRequest request = new UserController.RegisterRequest("alice", "pass123", "Alice");
            ResponseEntity<?> response = controller.register(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("tk-abc123", body.get("token"));
            assertNotNull(body.get("user"));
        }

        @Test
        @DisplayName("blank username returns 400")
        void blankUsername() {
            UserController.RegisterRequest request = new UserController.RegisterRequest("", "pass123", null);
            ResponseEntity<?> response = controller.register(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("blank password returns 400")
        void blankPassword() {
            UserController.RegisterRequest request = new UserController.RegisterRequest("alice", "", null);
            ResponseEntity<?> response = controller.register(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("null username returns 400")
        void nullUsername() {
            UserController.RegisterRequest request = new UserController.RegisterRequest(null, "pass123", null);
            ResponseEntity<?> response = controller.register(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("duplicate username returns 400 from service")
        void duplicateUsername() {
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userService.register(eq("alice"), anyString(), any()))
                    .thenThrow(new IllegalArgumentException("Username exists"));

            UserController.RegisterRequest request = new UserController.RegisterRequest("alice", "pass123", null);
            ResponseEntity<?> response = controller.register(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, String> body = (Map<String, String>) response.getBody();
            assertNotNull(body);
            assertEquals("Username exists", body.get("error"));
        }

        @Test
        @DisplayName("unexpected exception returns 500")
        void unexpectedException() {
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userService.register(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("DB down"));

            UserController.RegisterRequest request = new UserController.RegisterRequest("alice", "pass123", null);
            ResponseEntity<?> response = controller.register(request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("POST /user/login - user login")
    class Login {

        @Test
        @DisplayName("successful login returns token and user info")
        void successfulLogin() {
            SoulUser user = createUser("usr-1", "bob", "Bob", "USER", true);
            when(userService.findByUsername("bob")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);
            when(authSessionService.createSessionToken("usr-1", "USER")).thenReturn("tk-xyz");

            UserController.LoginRequest request = new UserController.LoginRequest("bob", "pass123");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("tk-xyz", body.get("token"));
        }

        @Test
        @DisplayName("wrong password returns 401")
        void wrongPassword() {
            SoulUser user = createUser("usr-1", "bob", "Bob", "USER", true);
            when(userService.findByUsername("bob")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            UserController.LoginRequest request = new UserController.LoginRequest("bob", "wrong");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        @DisplayName("non-existent user returns 401")
        void nonExistentUser() {
            when(userService.findByUsername("nobody")).thenReturn(Optional.empty());

            UserController.LoginRequest request = new UserController.LoginRequest("nobody", "pass");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        @DisplayName("disabled user returns 403")
        void disabledUser() {
            SoulUser user = createUser("usr-1", "bob", "Bob", "USER", false);
            when(userService.findByUsername("bob")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);

            UserController.LoginRequest request = new UserController.LoginRequest("bob", "pass123");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        @DisplayName("blank credentials returns 400")
        void blankCredentials() {
            UserController.LoginRequest request = new UserController.LoginRequest("", "");
            ResponseEntity<?> response = controller.login(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("null credentials returns 400")
        void nullCredentials() {
            UserController.LoginRequest request = new UserController.LoginRequest(null, null);
            ResponseEntity<?> response = controller.login(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("GET /user/me - current user info")
    class Me {

        @Test
        @DisplayName("valid token returns user info")
        void validToken() {
            AuthSessionService.SessionInfo session = new AuthSessionService.SessionInfo("usr-1", "USER");
            when(authSessionService.resolveSession("valid-token")).thenReturn(Optional.of(session));
            SoulUser user = createUser("usr-1", "alice", "Alice", "USER", true);
            when(userService.findById("usr-1")).thenReturn(Optional.of(user));

            ResponseEntity<?> response = controller.me("Bearer valid-token");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("usr-1", body.get("id"));
            assertEquals("alice", body.get("username"));
        }

        @Test
        @DisplayName("missing Authorization header returns 401")
        void missingAuth() {
            ResponseEntity<?> response = controller.me(null);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        @DisplayName("invalid token returns 401")
        void invalidToken() {
            when(authSessionService.resolveSession("bad-token")).thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.me("Bearer bad-token");

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        @DisplayName("non-Bearer authorization returns 401")
        void nonBearerAuth() {
            ResponseEntity<?> response = controller.me("Basic user:pass");

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("POST /user/logout - logout")
    class Logout {

        @Test
        @DisplayName("logout with valid token invalidates session")
        void logoutWithToken() {
            ResponseEntity<?> response = controller.logout("Bearer some-token");

            verify(authSessionService).invalidate("some-token");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("logout without token returns OK without error")
        void logoutWithoutToken() {
            ResponseEntity<?> response = controller.logout(null);

            verify(authSessionService, never()).invalidate(anyString());
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Admin endpoints")
    class AdminEndpoints {

        @Test
        @DisplayName("GET /user/list - admin can list users")
        void adminListUsers() {
            List<SoulUser> users = List.of(
                    createUser("usr-1", "alice", "Alice", "USER", true),
                    createUser("usr-2", "bob", "Bob", "ADMIN", true));
            when(userService.listAllUsers()).thenReturn(users);

            ResponseEntity<?> response = controller.listUsers(adminRequest());

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(2, body.get("total"));
        }

        @Test
        @DisplayName("GET /user/list - non-admin returns 403")
        void nonAdminListUsers() {
            ResponseEntity<?> response = controller.listUsers(nonAdminRequest());

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        @DisplayName("PUT /user/{id}/role - admin can update role")
        void adminUpdateRole() {
            ResponseEntity<?> response = controller.updateRole(
                    "usr-1", Map.of("role", "ADMIN"), adminRequest());

            verify(userService).updateUserRole("usr-1", "ADMIN");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("PUT /user/{id}/role - non-admin returns 403")
        void nonAdminUpdateRole() {
            ResponseEntity<?> response = controller.updateRole(
                    "usr-1", Map.of("role", "ADMIN"), nonAdminRequest());

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(userService, never()).updateUserRole(anyString(), anyString());
        }

        @Test
        @DisplayName("PUT /user/{id}/role - invalid role returns 400")
        void invalidRole() {
            doThrow(new IllegalArgumentException("Invalid role"))
                    .when(userService).updateUserRole("usr-1", "SUPERUSER");

            ResponseEntity<?> response = controller.updateRole(
                    "usr-1", Map.of("role", "SUPERUSER"), adminRequest());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("PUT /user/{id}/status - admin can enable user")
        void adminEnableUser() {
            ResponseEntity<?> response = controller.updateStatus(
                    "usr-1", Map.of("enabled", true), adminRequest());

            verify(userService).enableUser("usr-1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("PUT /user/{id}/status - admin can disable user")
        void adminDisableUser() {
            ResponseEntity<?> response = controller.updateStatus(
                    "usr-1", Map.of("enabled", false), adminRequest());

            verify(userService).disableUser("usr-1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("PUT /user/{id}/status - non-admin returns 403")
        void nonAdminUpdateStatus() {
            ResponseEntity<?> response = controller.updateStatus(
                    "usr-1", Map.of("enabled", true), nonAdminRequest());

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Emotion endpoints")
    class EmotionEndpoints {

        @Test
        @DisplayName("POST /user/checkin - successful emotion checkin")
        void successfulCheckin() {
            EmotionRecord record = new EmotionRecord("er-1", "user1", "happy", "Good day", 3000L);
            when(userService.checkin("user1", "happy", "Good day")).thenReturn(record);

            UserController.CheckinRequest request = new UserController.CheckinRequest("user1", "happy", "Good day");
            ResponseEntity<?> response = controller.checkin(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            EmotionRecord body = (EmotionRecord) response.getBody();
            assertNotNull(body);
            assertEquals("er-1", body.getId());
            assertEquals("happy", body.getEmotion());
        }

        @Test
        @DisplayName("POST /user/checkin - service failure returns 500")
        void checkinFailure() {
            when(userService.checkin(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("DB error"));

            UserController.CheckinRequest request = new UserController.CheckinRequest("user1", "sad", null);
            ResponseEntity<?> response = controller.checkin(request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }

        @Test
        @DisplayName("GET /user/{userId}/emotions - returns emotion records")
        void getEmotions() {
            List<EmotionRecord> records = List.of(
                    new EmotionRecord("er-1", "user1", "happy", null, 1000L),
                    new EmotionRecord("er-2", "user1", "calm", "meditation", 2000L));
            when(userService.getEmotions("user1", 7)).thenReturn(records);

            ResponseEntity<List<EmotionRecord>> response = controller.getEmotions("user1", 7);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().size());
        }

        @Test
        @DisplayName("GET /user/{userId}/stats - returns emotion stats")
        void getStats() {
            Map<String, Object> stats = Map.of(
                    "streak", 5,
                    "checkedInToday", true,
                    "totalDays", 10);
            when(userService.getStats("user1")).thenReturn(stats);

            ResponseEntity<Map<String, Object>> response = controller.getStats("user1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(5, response.getBody().get("streak"));
            assertEquals(true, response.getBody().get("checkedInToday"));
        }
    }
}
