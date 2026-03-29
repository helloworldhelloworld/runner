package com.lightweightai.kernel.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Skill 单用例测试上下文
 *
 * 封装执行一个测试用例所需的全部信息：
 * - Skill 的 systemPrompt 和工具定义
 * - 测试输入和记忆上下文
 * - Mock 工具响应
 */
public class SkillTestContext {

    private final String testCaseId;
    private final String systemPrompt;
    private final List<Map<String, Object>> toolDefinitions;
    private final String userInput;
    private final String memoryContext;
    private final Map<String, Object> mockResponses;

    private SkillTestContext(Builder builder) {
        this.testCaseId = builder.testCaseId;
        this.systemPrompt = builder.systemPrompt;
        this.toolDefinitions = builder.toolDefinitions != null
            ? Collections.unmodifiableList(builder.toolDefinitions)
            : Collections.emptyList();
        this.userInput = builder.userInput;
        this.memoryContext = builder.memoryContext;
        this.mockResponses = builder.mockResponses != null
            ? Collections.unmodifiableMap(builder.mockResponses)
            : Collections.emptyMap();
    }

    public String getTestCaseId() { return testCaseId; }
    public String getSystemPrompt() { return systemPrompt; }
    public List<Map<String, Object>> getToolDefinitions() { return toolDefinitions; }
    public String getUserInput() { return userInput; }
    public String getMemoryContext() { return memoryContext; }
    public Map<String, Object> getMockResponses() { return mockResponses; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String testCaseId;
        private String systemPrompt;
        private List<Map<String, Object>> toolDefinitions;
        private String userInput;
        private String memoryContext;
        private Map<String, Object> mockResponses;

        public Builder testCaseId(String testCaseId) { this.testCaseId = testCaseId; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder toolDefinitions(List<Map<String, Object>> toolDefinitions) { this.toolDefinitions = toolDefinitions; return this; }
        public Builder userInput(String userInput) { this.userInput = userInput; return this; }
        public Builder memoryContext(String memoryContext) { this.memoryContext = memoryContext; return this; }
        public Builder mockResponses(Map<String, Object> mockResponses) { this.mockResponses = mockResponses; return this; }

        public SkillTestContext build() {
            if (userInput == null || userInput.isBlank()) {
                throw new IllegalArgumentException("userInput is required");
            }
            return new SkillTestContext(this);
        }
    }
}
