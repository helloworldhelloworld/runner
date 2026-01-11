package com.lightweightai.web.model;

import java.util.List;
import java.util.Map;

/**
 * Chat request model
 */
public class ChatRequest {
    private String message;
    private List<String> activeSkills;
    private boolean useToolCalling;
    private Map<String, Object> options;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getActiveSkills() {
        return activeSkills;
    }

    public void setActiveSkills(List<String> activeSkills) {
        this.activeSkills = activeSkills;
    }

    public boolean isUseToolCalling() {
        return useToolCalling;
    }

    public void setUseToolCalling(boolean useToolCalling) {
        this.useToolCalling = useToolCalling;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }
}
