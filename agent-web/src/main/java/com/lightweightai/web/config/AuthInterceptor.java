package com.lightweightai.web.config;

import com.lightweightai.web.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP interceptor that enforces Bearer token authentication.
 * Whitelists login/register endpoints and static resources.
 */
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthSessionService authSessionService;

    public AuthInterceptor(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // Whitelist: auth endpoints, static resources
        if (isWhitelisted(path)) {
            return true;
        }

        // OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"未登录，请先登录\"}");
            return false;
        }

        String token = authorization.substring("Bearer ".length());
        var session = authSessionService.resolveSession(token);
        if (session.isEmpty()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"登录态无效，请重新登录\"}");
            return false;
        }

        var info = session.get();
        request.setAttribute("userId", info.getUserId());
        request.setAttribute("userRole", info.getRole());
        return true;
    }

    private boolean isWhitelisted(String path) {
        return path.equals("/user/login")
            || path.equals("/user/register")
            || path.startsWith("/assets/")
            || path.equals("/")
            || path.equals("/index.html")
            || path.equals("/favicon.ico")
            || path.startsWith("/ws/")
            || path.endsWith(".js")
            || path.endsWith(".css")
            || path.endsWith(".png")
            || path.endsWith(".jpg")
            || path.endsWith(".svg")
            || path.endsWith(".ico")
            || path.endsWith(".woff")
            || path.endsWith(".woff2")
            || path.endsWith(".ttf");
    }
}
