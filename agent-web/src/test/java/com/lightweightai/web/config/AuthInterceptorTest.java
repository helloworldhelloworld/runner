package com.lightweightai.web.config;

import com.lightweightai.web.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthInterceptor - Bearer token authentication")
class AuthInterceptorTest {

    private AuthSessionService authService;
    private AuthInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        authService = new AuthSessionService();
        interceptor = new AuthInterceptor(authService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Nested
    @DisplayName("Whitelisted paths")
    class Whitelisted {

        @Test
        void loginPath_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/user/login");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void registerPath_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/user/register");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void staticAssets_passThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/assets/style.css");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void indexHtml_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/index.html");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void rootPath_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void favicon_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/favicon.ico");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void webSocketPath_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/ws/chat");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void jsFile_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/app.js");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void cssFile_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/style.css");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void fontFile_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/fonts/roboto.woff2");
            assertTrue(interceptor.preHandle(request, response, null));
        }
    }

    @Nested
    @DisplayName("OPTIONS requests")
    class CorsPreflights {

        @Test
        void optionsRequest_passesThrough() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/chat");
            when(request.getMethod()).thenReturn("OPTIONS");
            assertTrue(interceptor.preHandle(request, response, null));
        }
    }

    @Nested
    @DisplayName("Missing or invalid authorization")
    class Unauthorized {

        @Test
        void noAuthorizationHeader_returns401() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/chat");
            when(request.getMethod()).thenReturn("POST");
            when(request.getHeader("Authorization")).thenReturn(null);

            assertFalse(interceptor.preHandle(request, response, null));
            verify(response).setStatus(401);
        }

        @Test
        void nonBearerToken_returns401() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/chat");
            when(request.getMethod()).thenReturn("POST");
            when(request.getHeader("Authorization")).thenReturn("Basic abc123");

            assertFalse(interceptor.preHandle(request, response, null));
            verify(response).setStatus(401);
        }

        @Test
        void invalidBearerToken_returns401() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/chat");
            when(request.getMethod()).thenReturn("POST");
            when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token-xyz");

            assertFalse(interceptor.preHandle(request, response, null));
            verify(response).setStatus(401);
        }

        @Test
        void responseBody_containsErrorMessage() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/chat");
            when(request.getMethod()).thenReturn("POST");
            when(request.getHeader("Authorization")).thenReturn(null);

            interceptor.preHandle(request, response, null);
            assertTrue(responseBody.toString().contains("error"));
        }
    }

    @Nested
    @DisplayName("Valid authentication")
    class Authorized {

        @Test
        void validToken_passesThrough() throws Exception {
            String token = authService.createSessionToken("user123");
            when(request.getRequestURI()).thenReturn("/api/chat");
            when(request.getMethod()).thenReturn("POST");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void validToken_setsUserIdAttribute() throws Exception {
            String token = authService.createSessionToken("user456");
            when(request.getRequestURI()).thenReturn("/api/chat");
            when(request.getMethod()).thenReturn("POST");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            interceptor.preHandle(request, response, null);
            verify(request).setAttribute("userId", "user456");
        }

        @Test
        void validToken_setsUserRoleAttribute() throws Exception {
            String token = authService.createSessionToken("admin1", "ADMIN");
            when(request.getRequestURI()).thenReturn("/api/chat");
            when(request.getMethod()).thenReturn("POST");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            interceptor.preHandle(request, response, null);
            verify(request).setAttribute("userRole", "ADMIN");
        }

        @Test
        void invalidatedToken_returns401() throws Exception {
            String token = authService.createSessionToken("user789");
            authService.invalidate(token);
            when(request.getRequestURI()).thenReturn("/api/chat");
            when(request.getMethod()).thenReturn("POST");
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            assertFalse(interceptor.preHandle(request, response, null));
            verify(response).setStatus(401);
        }
    }
}
