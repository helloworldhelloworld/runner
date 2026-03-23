package com.lightweightai.web.model;

import java.util.Map;

public class SharedClientTool {
    private String key;
    private String namespace;
    private String name;
    private String description;
    private String owner;
    private String version;
    private Map<String, Object> inputSchema;
    private boolean enabled;
    private Map<String, Object> mockResponse;
    private String createdBy;
    private String updatedBy;
    private long createdAt;
    private long updatedAt;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Map<String, Object> getInputSchema() { return inputSchema; }
    public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, Object> getMockResponse() { return mockResponse; }
    public void setMockResponse(Map<String, Object> mockResponse) { this.mockResponse = mockResponse; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
