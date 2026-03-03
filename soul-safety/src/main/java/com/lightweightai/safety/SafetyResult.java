package com.lightweightai.safety;

import java.util.Collections;
import java.util.List;

/**
 * Result of a safety check on user input.
 */
public class SafetyResult {

    private final Level level;
    private final List<String> matchedKeywords;
    private final List<CrisisResource> resources;

    public enum Level { SAFE, CRISIS }

    public SafetyResult(Level level, List<String> matchedKeywords, List<CrisisResource> resources) {
        this.level = level;
        this.matchedKeywords = matchedKeywords;
        this.resources = resources;
    }

    public Level getLevel() { return level; }
    public List<String> getMatchedKeywords() { return matchedKeywords; }
    public List<CrisisResource> getResources() { return resources; }

    public boolean isCrisis() { return level == Level.CRISIS; }

    public static SafetyResult safe() {
        return new SafetyResult(Level.SAFE, Collections.emptyList(), Collections.emptyList());
    }

    public static SafetyResult crisis(List<String> keywords, List<CrisisResource> resources) {
        return new SafetyResult(Level.CRISIS, keywords, resources);
    }
}
