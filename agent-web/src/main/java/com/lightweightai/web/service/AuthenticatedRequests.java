package com.lightweightai.web.service;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Capture before switching threads. Identity comes from AuthInterceptor, never from JSON. */
public final class AuthenticatedRequests {
    private AuthenticatedRequests() {}
    public static String currentAgent() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getParameter("agentId") : null;
    }
    public static String currentPrincipal() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        Object principal = attributes.getRequest().getAttribute("userId");
        if (!(principal instanceof String id) || id.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return id;
    }
}
