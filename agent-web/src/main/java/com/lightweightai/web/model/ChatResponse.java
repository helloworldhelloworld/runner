package com.lightweightai.web.model;

import com.lightweightai.web.observer.DebugInfo;
import java.util.List;
import java.util.Map;

/**
 * Chat response model
 */
public class ChatResponse {
    private String response;
    private List<String> toolCallsUsed;
    private List<String> skillsApplied;
    private Map<String, Object> metadata;
    private String error;

    // LLM 可观测调试信息（仅在调试模式下返回）
    private DebugInfo debug;

    public static ChatResponse success(String response) {
        ChatResponse resp = new ChatResponse();
        resp.setResponse(response);
        return resp;
    }

    public static ChatResponse error(String error) {
        ChatResponse resp = new ChatResponse();
        resp.setError(error);
        return resp;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public List<String> getToolCallsUsed() {
        return toolCallsUsed;
    }

    public void setToolCallsUsed(List<String> toolCallsUsed) {
        this.toolCallsUsed = toolCallsUsed;
    }

    public List<String> getSkillsApplied() {
        return skillsApplied;
    }

    public void setSkillsApplied(List<String> skillsApplied) {
        this.skillsApplied = skillsApplied;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public DebugInfo getDebug() {
        return debug;
    }

    public void setDebug(DebugInfo debug) {
        this.debug = debug;
    }
}
