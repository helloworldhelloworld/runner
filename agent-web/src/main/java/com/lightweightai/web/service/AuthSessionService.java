package com.lightweightai.web.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory token session service.
 */
@Service
public class AuthSessionService {

    private final Map<String, String> tokenToUserId = new ConcurrentHashMap<>();

    public String createSessionToken(String userId) {
        String token = "tk-" + UUID.randomUUID().toString().replace("-", "");
        tokenToUserId.put(token, userId);
        return token;
    }

    public Optional<String> resolveUserId(String token) {
        return Optional.ofNullable(tokenToUserId.get(token));
    }

    public void invalidate(String token) {
        tokenToUserId.remove(token);
    }
}
