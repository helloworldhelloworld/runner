package com.lightweightai.web.config;

import com.lightweightai.web.service.AuthSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthInterceptor - Bearer token authentication")
class AuthInterceptorTest {

    private AuthSessionService authService;
    private AuthInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        authService = new AuthSessionService();
        interceptor = new AuthInterceptor(authService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Nested
    @DisplayName("Whitelisted paths")
    class Whitelisted {

        @Test
        void loginPath_passesThrough() throws Exception {
            request.setRequestURI("/user/login");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void registerPath_passesThrough() throws Exception {
            request.setRequestURI("/user/register");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void staticAssets_passThrough() throws Exception {
            request.setRequestURI("/assets/style.css");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void indexHtml_passesThrough() throws Exception {
            request.setRequestURI("/index.html");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void rootPath_passesThrough() throws Exception {
            request.setRequestURI("/");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void favicon_passesThrough() throws Exception {
            request.setRequestURI("/favicon.ico");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void webSocketPath_passesThrough() throws Exception {
            request.setRequestURI("/ws/chat");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void jsFile_passesThrough() throws Exception {
            request.setRequestURI("/app.js");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void cssFile_passesThrough() throws Exception {
            request.setRequestURI("/style.css");
            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void fontFile_passesThrough() throws Exception {
            request.setRequestURI("/fonts/roboto.woff2");
            assertTrue(interceptor.preHandle(request, response, null));
        }
    }

    @Nested
    @DisplayName("OPTIONS requests")
    class CorsPreflights {

        @Test
        void optionsRequest_passesThrough() throws Exception {
            request.setRequestURI("/api/chat");
            request.setMethod("OPTIONS");
            assertTrue(interceptor.preHandle(request, response, null));
        }
    }

    @Nested
    @DisplayName("Missing or invalid authorization")
    class Unauthorized {

        @Test
        void noAuthorizationHeader_returns401() throws Exception {
            request.setRequestURI("/api/chat");
            assertFalse(interceptor.preHandle(request, response, null));
            assertEquals(401, response.getStatus());
        }

        @Test
        void nonBearerToken_returns401() throws Exception {
            request.setRequestURI("/api/chat");
            request.addHeader("Authorization", "Basic abc123");
            assertFalse(interceptor.preHandle(request, response, null));
            assertEquals(401, response.getStatus());
        }

        @Test
        void invalidBearerToken_returns401() throws Exception {
            request.setRequestURI("/api/chat");
            request.addHeader("Authorization", "Bearer invalid-token-xyz");
            assertFalse(interceptor.preHandle(request, response, null));
            assertEquals(401, response.getStatus());
        }

        @Test
        void responseBody_containsErrorMessage() throws Exception {
            request.setRequestURI("/api/chat");
            interceptor.preHandle(request, response, null);
            String body = response.getContentAsString();
            assertTrue(body.contains("error"));
        }
    }

    @Nested
    @DisplayName("Valid authentication")
    class Authorized {

        @Test
        void validToken_passesThrough() throws Exception {
            String token = authService.createSessionToken("user123");

            request.setRequestURI("/api/chat");
            request.addHeader("Authorization", "Bearer " + token);

            assertTrue(interceptor.preHandle(request, response, null));
        }

        @Test
        void validToken_setsUserIdAttribute() throws Exception {
            String token = authService.createSessionToken("user456");

            request.setRequestURI("/api/chat");
            request.addHeader("Authorization", "Bearer " + token);

            interceptor.preHandle(request, response, null);

            assertEquals("user456", request.getAttribute("userId"));
        }

        @Test
        void validToken_setsUserRoleAttribute() throws Exception {
            String token = authService.createSessionToken("admin1", "ADMIN");

            request.setRequestURI("/api/chat");
            request.addHeader("Authorization", "Bearer " + token);

            interceptor.preHandle(request, response, null);

            assertEquals("ADMIN", request.getAttribute("userRole"));
        }

        @Test
        void invalidatedToken_returns401() throws Exception {
            String token = authService.createSessionToken("user789");
            authService.invalidate(token);

            request.setRequestURI("/api/chat");
            request.addHeader("Authorization", "Bearer " + token);

            assertFalse(interceptor.preHandle(request, response, null));
            assertEquals(401, response.getStatus());
        }
    }
}
