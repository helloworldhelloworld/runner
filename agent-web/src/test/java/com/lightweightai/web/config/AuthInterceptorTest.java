package com.lightweightai.web.config;

import com.lightweightai.web.service.AuthSessionService;
import com.lightweightai.web.service.AuthSessionService.SessionInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthInterceptor - Bearer token authentication")
class AuthInterceptorTest {

    private AuthSessionService authSessionService;
    private AuthInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        authSessionService = mock(AuthSessionService.class);
        interceptor = new AuthInterceptor(authSessionService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Nested
    @DisplayName("Whitelisted paths")
    class WhitelistedPaths {

        @Test
        @DisplayName("/user/login bypasses auth")
        void loginEndpointBypasses() throws Exception {
            when(request.getRequestURI()).thenReturn("/user/login");
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("/user/register bypasses auth")
        void registerEndpointBypasses() throws Exception {
            when(request.getRequestURI()).thenReturn("/user/register");
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("static .js files bypass auth")
        void jsFilesBypasses() throws Exception {
            when(request.getRequestURI()).thenReturn("/app/main.js");
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("static .css files bypass auth")
        void cssFilesBypasses() throws Exception {
            when(request.getRequestURI()).thenReturn("/styles/app.css");
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("/assets/* paths bypass auth")
        void assetsPathBypasses() throws Exception {
            when(request.getRequestURI()).thenReturn("/assets/logo.png");
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("/ws/* paths bypass auth")
        void webSocketPathBypasses() throws Exception {
            when(request.getRequestURI()).thenReturn("/ws/chat");
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("root / bypasses auth")
        void rootPathBypasses() throws Exception {
            when(request.getRequestURI()).thenReturn("/");
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("/index.html bypasses auth")
        void indexHtmlBypasses() throws Exception {
            when(request.getRequestURI()).thenReturn("/index.html");
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("font files (.woff, .woff2, .ttf) bypass auth")
        void fontFilesBypasses() throws Exception {
            for (String ext : new String[]{".woff", ".woff2", ".ttf"}) {
                when(request.getRequestURI()).thenReturn("/fonts/roboto" + ext);
                assertTrue(interceptor.preHandle(request, response, new Object()),
                    "Expected " + ext + " to bypass auth");
            }
        }
    }

    @Test
    @DisplayName("OPTIONS (CORS preflight) bypasses auth")
    void optionsRequestBypasses() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("OPTIONS");
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    @DisplayName("missing Authorization header returns 401")
    void missingAuthHeaderReturns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(null);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
        assertTrue(responseBody.toString().contains("error"));
    }

    @Test
    @DisplayName("non-Bearer token returns 401")
    void nonBearerTokenReturns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
    }

    @Test
    @DisplayName("invalid Bearer token returns 401")
    void invalidBearerTokenReturns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(authSessionService.resolveSession("invalid-token")).thenReturn(Optional.empty());

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
    }

    @Test
    @DisplayName("valid Bearer token sets userId and userRole attributes")
    void validTokenSetsAttributes() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token-123");
        SessionInfo session = new SessionInfo("user-42", "ADMIN");
        when(authSessionService.resolveSession("valid-token-123")).thenReturn(Optional.of(session));

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(request).setAttribute("userId", "user-42");
        verify(request).setAttribute("userRole", "ADMIN");
    }
}
