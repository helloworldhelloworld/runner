package com.lightweightai.kernel.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Internal keys. Caller-controlled text is always one encoded segment in authenticated namespaces. */
public final class SessionKeys {
    private SessionKeys() {}
    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8));
    }
    public static String slot(String principalId, String sessionId) {
        return principalId == null ? sessionId : "principal:" + encode(principalId) + ":session:" + encode(sessionId);
    }
    public static String main(String principalId, String agentId, String sessionId) {
        return principalId == null ? "agent:" + agentId + ":main:" + sessionId
                : "principal:" + encode(principalId) + ":agent:" + encode(agentId) + ":main:" + encode(sessionId);
    }
}
