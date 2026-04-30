package com.lightweightai.web.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token session service with role information.
 */
@Service
public class AuthSessionService {

    public static class SessionInfo {
        private final String userId;
        private final String role;

        public SessionInfo(String userId, String role) {
            this.userId = userId;
            this.role = role != null ? role : "USER";
        }

        public String getUserId() { return userId; }
        public String getRole() { return role; }
    }

    private final Map<String, SessionInfo> tokenToSession = new ConcurrentHashMap<>();

    public String createSessionToken(String userId) {
        return createSessionToken(userId, "USER");
    }

    public String createSessionToken(String userId, String role) {
        String token = "tk-" + UUID.randomUUID().toString().replace("-", "");
        tokenToSession.put(token, new SessionInfo(userId, role));
        return token;
    }

    public Optional<String> resolveUserId(String token) {
        if (token == null) return Optional.empty();
        return Optional.ofNullable(tokenToSession.get(token)).map(SessionInfo::getUserId);
    }

    public Optional<SessionInfo> resolveSession(String token) {
        if (token == null) return Optional.empty();
        return Optional.ofNullable(tokenToSession.get(token));
    }

    public void invalidate(String token) {
        if (token != null) tokenToSession.remove(token);
    }
}
