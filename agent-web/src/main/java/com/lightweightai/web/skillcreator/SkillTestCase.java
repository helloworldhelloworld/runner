package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Skill 测试用例
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillTestCase {

    private String id;
    private String skillId;
    private String category;
    private String description;
    private String input;
    private String memory;
    private List<String> userClarifications = new ArrayList<>();
    private List<String> assertions = new ArrayList<>();
    private Map<String, Object> mockResponses; // 自定义 Mock 响应配置

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory; }

    public List<String> getUserClarifications() { return userClarifications; }
    public void setUserClarifications(List<String> userClarifications) {
        this.userClarifications = userClarifications != null ? userClarifications : new ArrayList<>();
    }

    public List<String> getAssertions() { return assertions; }
    public void setAssertions(List<String> assertions) {
        this.assertions = assertions != null ? assertions : new ArrayList<>();
    }

    public Map<String, Object> getMockResponses() { return mockResponses; }
    public void setMockResponses(Map<String, Object> mockResponses) { this.mockResponses = mockResponses; }
}
