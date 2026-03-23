package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 候选工具 Schema 条目 - 从 Excel 导入的工具/方法定义
 *
 * 存储在数据库中，在 Skill Creator 中作为可选工具展示。
 * 创建 Skill 时可以将这些工具关联到 Skill。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolSchemaEntry {

    private String id;
    private String name;           // 工具名（如 GeoInformation.GetPosition）
    private String description;    // 中文描述
    private String category;       // 分类（如 地理信息、导航、天气）
    private String inputSchemaJson;  // JSON Schema 字符串
    private String outputSchemaJson; // 输出 Schema（可选）
    private String status;         // enabled | disabled
    private String createdAt;
    private String updatedAt;

    public ToolSchemaEntry() {}

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getInputSchemaJson() { return inputSchemaJson; }
    public void setInputSchemaJson(String inputSchemaJson) { this.inputSchemaJson = inputSchemaJson; }

    public String getOutputSchemaJson() { return outputSchemaJson; }
    public void setOutputSchemaJson(String outputSchemaJson) { this.outputSchemaJson = outputSchemaJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (id != null) map.put("id", id);
        if (name != null) map.put("name", name);
        if (description != null) map.put("description", description);
        if (category != null) map.put("category", category);
        if (inputSchemaJson != null) map.put("inputSchema", inputSchemaJson);
        if (outputSchemaJson != null) map.put("outputSchema", outputSchemaJson);
        map.put("status", status != null ? status : "enabled");
        return map;
    }
}
