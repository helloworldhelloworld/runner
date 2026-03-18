package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.memory.Message;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 上下文（可观测的完整上下文）
 *
 * 包含构建好的完整 Prompt 信息，支持调试和观测。
 */
public class PromptContext {

    private final String systemPrompt;
    private final String userMessage;
    private final List<Message> historyMessages;
    private final String memoryContext;
    private final List<Skill.ToolDefinition> tools;
    private final List<String> activeSkillNames;
    private final List<String> buildLog;
    private final Instant buildTime;
    private final Map<String, Object> metadata;

    private PromptContext(Builder builder) {
        this.systemPrompt = builder.systemPrompt;
        this.userMessage = builder.userMessage;
        this.historyMessages = new ArrayList<>(builder.historyMessages);
        this.memoryContext = builder.memoryContext;
        this.tools = new ArrayList<>(builder.tools);
        this.activeSkillNames = new ArrayList<>(builder.activeSkillNames);
        this.buildLog = new ArrayList<>(builder.buildLog);
        this.buildTime = Instant.now();
        this.metadata = new HashMap<>(builder.metadata);
    }

    // ==================== Getters ====================

    public String getSystemPrompt() {
        return systemPrompt;
    }
    public String getUserMessage() {
        return userMessage;
    }
    public List<Message> getHistoryMessages() {
        return new ArrayList<>(historyMessages);
    }
    public String getMemoryContext() {
        return memoryContext;
    }
    public List<Skill.ToolDefinition> getTools() {
        return new ArrayList<>(tools);
    }
    public List<String> getActiveSkillNames() {
        return new ArrayList<>(activeSkillNames);
    }
    public List<String> getBuildLog() {
        return new ArrayList<>(buildLog);
    }
    public Instant getBuildTime() {
        return buildTime;
    }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    public boolean hasTools() {
        return !tools.isEmpty();
    }

    public boolean hasMemoryContext() {
        return memoryContext != null && !memoryContext.isEmpty();
    }

    // ==================== 可观测性 ====================

    /**
     * 输出调试字符串（完整可观测）
     */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(60)).append("\n");
        sb.append("PromptContext @ ").append(buildTime).append("\n");
        sb.append("=".repeat(60)).append("\n\n");

        // Active Skills
        sb.append("【Active Skills】\n");
        if (activeSkillNames.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            activeSkillNames.forEach(s -> sb.append("  - ").append(s).append("\n"));
        }
        sb.append("\n");

        // Tools
        sb.append("【Tools】(").append(tools.size()).append(")\n");
        tools.forEach(t -> sb.append("  - ").append(t.getName())
            .append(": ").append(t.getDescription()).append("\n"));
        sb.append("\n");

        // System Prompt
        sb.append("【System Prompt】\n");
        sb.append(systemPrompt.length() > 500
            ? systemPrompt.substring(0, 500) + "...(truncated)"
            : systemPrompt);
        sb.append("\n\n");

        // Memory Context
        if (hasMemoryContext()) {
            sb.append("【Memory Context】\n");
            sb.append(memoryContext.length() > 300
                ? memoryContext.substring(0, 300) + "...(truncated)"
                : memoryContext);
            sb.append("\n\n");
        }

        // History
        sb.append("【History】(").append(historyMessages.size()).append(" messages)\n");
        int showCount = Math.min(3, historyMessages.size());
        for (int i = 0; i < showCount; i++) {
            Message msg = historyMessages.get(i);
            String content = msg.getContent();
            sb.append("  [").append(msg.getRole()).append("] ")
              .append(content.length() > 50 ? content.substring(0, 50) + "..." : content)
              .append("\n");
        }
        if (historyMessages.size() > showCount) {
            sb.append("  ... and ").append(historyMessages.size() - showCount).append(" more\n");
        }
        sb.append("\n");

        // User Message
        sb.append("【User Message】\n");
        sb.append(userMessage).append("\n\n");

        // Build Log
        sb.append("【Build Log】\n");
        buildLog.forEach(log -> sb.append("  ").append(log).append("\n"));

        sb.append("=".repeat(60)).append("\n");
        return sb.toString();
    }

    /**
     * 转换为 JSON 格式（用于 API 响应）
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "systemPrompt", systemPrompt,
            "userMessage", userMessage,
            "historyCount", historyMessages.size(),
            "toolCount", tools.size(),
            "activeSkills", activeSkillNames,
            "hasMemory", hasMemoryContext(),
            "buildTime", buildTime.toString()
        );
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String systemPrompt = "";
        private String userMessage = "";
        private List<Message> historyMessages = new ArrayList<>();
        private String memoryContext = "";
        private List<Skill.ToolDefinition> tools = new ArrayList<>();
        private List<String> activeSkillNames = new ArrayList<>();
        private List<String> buildLog = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder systemPrompt(String prompt) {
            this.systemPrompt = prompt;
            return this;
        }

        public Builder userMessage(String message) {
            this.userMessage = message;
            return this;
        }

        public Builder historyMessages(List<Message> messages) {
            this.historyMessages = new ArrayList<>(messages);
            return this;
        }

        public Builder memoryContext(String context) {
            this.memoryContext = context;
            return this;
        }

        public Builder tools(List<Skill.ToolDefinition> tools) {
            this.tools = new ArrayList<>(tools);
            return this;
        }

        public Builder addTool(Skill.ToolDefinition tool) {
            this.tools.add(tool);
            return this;
        }

        public Builder activeSkillNames(List<String> names) {
            this.activeSkillNames = new ArrayList<>(names);
            return this;
        }

        public Builder addActiveSkill(String name) {
            this.activeSkillNames.add(name);
            return this;
        }

        public Builder addBuildLog(String log) {
            this.buildLog.add(log);
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public PromptContext build() {
            return new PromptContext(this);
        }
    }
}
