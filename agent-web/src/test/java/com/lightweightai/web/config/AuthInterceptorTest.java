package com.lightweightai.web.config;

import com.lightweightai.web.service.AuthSessionService;
import com.lightweightai.web.service.AuthSessionService.SessionInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthInterceptor - Bearer token authentication")
class AuthInterceptorTest {

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AuthInterceptor interceptor;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new AuthInterceptor(authSessionService);
        responseBody = new StringWriter();
    }

    private void mockResponseWriter() throws Exception {
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    // --- Whitelisted paths ---

    @ParameterizedTest
    @ValueSource(strings = {
        "/user/login",
        "/user/register",
        "/assets/logo.png",
        "/assets/css/style.css",
        "/",
        "/index.html",
        "/favicon.ico",
        "/ws/chat",
        "/ws/stream",
        "/app/bundle.js",
        "/static/style.css",
        "/images/avatar.png",
        "/photos/pic.jpg",
        "/icons/icon.svg",
        "/old-favicon.ico",
        "/fonts/roboto.woff",
        "/fonts/roboto.woff2",
        "/fonts/roboto.ttf"
    })
    @DisplayName("Whitelisted paths bypass authentication")
    void whitelistedPathsBypasses(String path) throws Exception {
        when(request.getRequestURI()).thenReturn(path);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("OPTIONS request bypasses authentication (CORS preflight)")
    void optionsRequestBypasses() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("OPTIONS");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(response, never()).setStatus(anyInt());
    }

    // --- Missing / invalid authorization ---

    @Test
    @DisplayName("Missing Authorization header returns 401")
    void missingAuthorizationHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(null);
        mockResponseWriter();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).setStatus(401);
    }

    @Test
    @DisplayName("Authorization without Bearer prefix returns 401")
    void authorizationWithoutBearerPrefix() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");
        mockResponseWriter();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).setStatus(401);
    }

    @Test
    @DisplayName("Invalid token returns 401")
    void invalidTokenReturns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(authSessionService.resolveSession("invalid-token")).thenReturn(Optional.empty());
        mockResponseWriter();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).setStatus(401);
        verify(authSessionService).resolveSession("invalid-token");
    }

    // --- Valid token ---

    @Test
    @DisplayName("Valid token sets userId and userRole attributes and returns true")
    void validTokenSetsAttributesAndReturnsTrue() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/chat");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token-123");

        SessionInfo sessionInfo = new SessionInfo("user-42", "ADMIN");
        when(authSessionService.resolveSession("valid-token-123")).thenReturn(Optional.of(sessionInfo));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(request).setAttribute("userId", "user-42");
        verify(request).setAttribute("userRole", "ADMIN");
        verify(response, never()).setStatus(anyInt());
    }
}
