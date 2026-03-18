package com.lightweightai.kernel.prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 构建请求
 *
 * 指定构建 Prompt 所需的参数。
 */
public class PromptRequest {

    private final String userMessage;
    private final String sessionId;
    private final List<String> activeSkills;
    private final boolean autoDetectSkills;
    private final int maxHistoryMessages;
    private final int maxMemoryResults;
    private final Map<String, Object> context;

    private PromptRequest(Builder builder) {
        this.userMessage = builder.userMessage;
        this.sessionId = builder.sessionId;
        this.activeSkills = new ArrayList<>(builder.activeSkills);
        this.autoDetectSkills = builder.autoDetectSkills;
        this.maxHistoryMessages = builder.maxHistoryMessages;
        this.maxMemoryResults = builder.maxMemoryResults;
        this.context = new HashMap<>(builder.context);
    }

    // ==================== Getters ====================

    public String getUserMessage() {
        return userMessage;
    }
    public String getSessionId() {
        return sessionId;
    }
    public List<String> getActiveSkills() {
        return new ArrayList<>(activeSkills);
    }
    public boolean isAutoDetectSkills() {
        return autoDetectSkills;
    }
    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }
    public int getMaxMemoryResults() {
        return maxMemoryResults;
    }
    public Map<String, Object> getContext() { return new HashMap<>(context); }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userMessage = "";
        private String sessionId = "default";
        private List<String> activeSkills = new ArrayList<>();
        private boolean autoDetectSkills = false;
        private int maxHistoryMessages = 20;
        private int maxMemoryResults = 5;
        private Map<String, Object> context = new HashMap<>();

        public Builder userMessage(String userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder activeSkills(List<String> skills) {
            this.activeSkills = new ArrayList<>(skills);
            return this;
        }

        public Builder addActiveSkill(String skill) {
            this.activeSkills.add(skill);
            return this;
        }

        public Builder autoDetectSkills(boolean autoDetect) {
            this.autoDetectSkills = autoDetect;
            return this;
        }

        public Builder maxHistoryMessages(int max) {
            this.maxHistoryMessages = max;
            return this;
        }

        public Builder maxMemoryResults(int max) {
            this.maxMemoryResults = max;
            return this;
        }

        public Builder context(String key, Object value) {
            this.context.put(key, value);
            return this;
        }

        public PromptRequest build() {
            return new PromptRequest(this);
        }
    }
}
