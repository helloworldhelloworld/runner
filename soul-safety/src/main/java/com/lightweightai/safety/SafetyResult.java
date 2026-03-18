package com.lightweightai.safety;

import java.util.List;

/**
 * Result of a safety check on user input.
 */
public record SafetyResult(
    Level level,
    List<String> matchedKeywords,
    List<CrisisResource> resources
) {
    public enum Level { SAFE, CRISIS }

    public boolean isCrisis() {
        return level == Level.CRISIS;
    }

    public static SafetyResult safe() {
        return new SafetyResult(Level.SAFE, List.of(), List.of());
    }

    public static SafetyResult crisis(List<String> keywords, List<CrisisResource> resources) {
        return new SafetyResult(Level.CRISIS, keywords, resources);
    }
}
