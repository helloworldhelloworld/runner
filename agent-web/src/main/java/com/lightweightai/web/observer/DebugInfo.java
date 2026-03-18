package com.lightweightai.web.observer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 可观测调试信息 - 用于前端展示 LLM 调用详情
 */
public class DebugInfo {

    private long startTime;
    private long endTime;
    private String sessionId;
    private String userInput;

    // LLM 请求信息
    private List<MessageInfo> requestMessages = new ArrayList<>();
    private int totalRequestTokens;

    // LLM 响应信息
    private String responseText;
    private String stopReason;
    private int responseTokens;

    // 工具调用信息
    private List<ToolCallInfo> toolCalls = new ArrayList<>();

    // Skills 信息
    private List<String> activeSkills = new ArrayList<>();
    private String systemPromptPreview;

    // 错误信息
    private String error;

    // ==================== Nested Classes ====================

    public static class MessageInfo {
        private String role;
        private String content;
        private int index;

        public MessageInfo(int index, String role, String content) {
            this.index = index;
            this.role = role;
            this.content = truncate(content, 500);
        }

        public String getRole() {
            return role;
        }
        public String getContent() {
            return content;
        }
        public int getIndex() {
            return index;
        }

        private static String truncate(String text, int maxLen) {
            if (text == null) {
                return "(null)";
            }
            return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...[truncated]";
        }
    }

    public static class ToolCallInfo {
        private String toolName;
        private Map<String, Object> arguments;
        private String result;
        private long duration;

        public ToolCallInfo(String toolName, Map<String, Object> arguments, String result, long duration) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = truncate(result, 300);
            this.duration = duration;
        }

        public String getToolName() {
            return toolName;
        }
        public Map<String, Object> getArguments() {
            return arguments;
        }
        public String getResult() {
            return result;
        }
        public long getDuration() {
            return duration;
        }

        private static String truncate(String text, int maxLen) {
            if (text == null) {
                return "(null)";
            }
            return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...[truncated]";
        }
    }

    // ==================== Getters & Setters ====================

    public long getStartTime() {
        return startTime;
    }
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDuration() {
        return endTime - startTime;
    }

    public String getSessionId() {
        return sessionId;
    }
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserInput() {
        return userInput;
    }
    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    public List<MessageInfo> getRequestMessages() {
        return requestMessages;
    }
    public void setRequestMessages(List<MessageInfo> requestMessages) {
        this.requestMessages = requestMessages;
    }

    public int getTotalRequestTokens() {
        return totalRequestTokens;
    }
    public void setTotalRequestTokens(int totalRequestTokens) {
        this.totalRequestTokens = totalRequestTokens;
    }

    public String getResponseText() {
        return responseText;
    }
    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public String getStopReason() {
        return stopReason;
    }
    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public int getResponseTokens() {
        return responseTokens;
    }
    public void setResponseTokens(int responseTokens) {
        this.responseTokens = responseTokens;
    }

    public List<ToolCallInfo> getToolCalls() {
        return toolCalls;
    }
    public void setToolCalls(List<ToolCallInfo> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public List<String> getActiveSkills() {
        return activeSkills;
    }
    public void setActiveSkills(List<String> activeSkills) {
        this.activeSkills = activeSkills;
    }

    public String getSystemPromptPreview() {
        return systemPromptPreview;
    }
    public void setSystemPromptPreview(String systemPromptPreview) {
        this.systemPromptPreview = systemPromptPreview;
    }

    public String getError() {
        return error;
    }
    public void setError(String error) {
        this.error = error;
    }

    // ==================== Builder Methods ====================

    public void addRequestMessage(int index, String role, String content) {
        this.requestMessages.add(new MessageInfo(index, role, content));
    }

    public void addToolCall(String toolName, Map<String, Object> args, String result, long duration) {
        this.toolCalls.add(new ToolCallInfo(toolName, args, result, duration));
    }
}
