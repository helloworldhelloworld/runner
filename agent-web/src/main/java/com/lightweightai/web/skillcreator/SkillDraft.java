package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 草稿 - 在多轮对话过程中由 LLM 逐步填充
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillDraft {

    private String id;
    private String name;
    private String description;

    @JsonProperty("systemPrompt")
    private String systemPrompt;

    @JsonProperty("toolNames")
    private List<String> toolNames = new ArrayList<>();

    private List<String> triggers = new ArrayList<>();
    private int priority = 10;
    private Map<String, Object> metadata = new HashMap<>();
    private String status = "draft"; // draft | active | disabled

    public SkillDraft() {}

    public SkillDraft(String id) {
        this.id = id;
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public List<String> getToolNames() { return toolNames; }
    public void setToolNames(List<String> toolNames) { this.toolNames = toolNames != null ? toolNames : new ArrayList<>(); }

    public List<String> getTriggers() { return triggers; }
    public void setTriggers(List<String> triggers) { this.triggers = triggers != null ? triggers : new ArrayList<>(); }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata != null ? metadata : new HashMap<>(); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /**
     * 检查草稿是否有足够信息可以保存
     */
    public boolean isValid() {
        return name != null && !name.isBlank()
            && description != null && !description.isBlank()
            && systemPrompt != null && !systemPrompt.isBlank();
    }

    /**
     * 转为简洁的 Map 用于 JSON 序列化
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (id != null) map.put("id", id);
        if (name != null) map.put("name", name);
        if (description != null) map.put("description", description);
        if (systemPrompt != null) map.put("systemPrompt", systemPrompt);
        if (!toolNames.isEmpty()) map.put("toolNames", toolNames);
        if (!triggers.isEmpty()) map.put("triggers", triggers);
        map.put("priority", priority);
        if (!metadata.isEmpty()) map.put("metadata", metadata);
        map.put("status", status);
        return map;
    }
}
