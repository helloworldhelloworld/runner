package com.lightweightai.kernel.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.Map;

/**
 * 测试用 Mock 工具注册表
 *
 * 从 Map<工具名, mock响应> 创建一组 Mock Tool 并注册到 ToolRegistry。
 * 用于 Skill 测试执行时替代真实工具，确保测试确定性。
 */
public class MockToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ToolRegistry registry;

    /**
     * @param mockResponses 工具名 → mock 返回值（Map/String/Object，将 JSON 序列化为 content）
     */
    public MockToolRegistry(Map<String, Object> mockResponses) {
        this.registry = new ToolRegistry();
        if (mockResponses != null) {
            mockResponses.forEach((toolName, response) ->
                registry.register(createMockTool(toolName, response)));
        }
    }

    public ToolRegistry getRegistry() {
        return registry;
    }

    private static Tool createMockTool(String name, Object response) {
        return new Tool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "Mock tool: " + name;
            }

            @Override
            public ToolSchema getSchema() {
                return ToolSchema.empty();
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                try {
                    String content = response instanceof String
                        ? (String) response
                        : MAPPER.writeValueAsString(response);
                    return ToolResult.success(content);
                } catch (Exception e) {
                    return ToolResult.error("Mock serialization failed: " + e.getMessage());
                }
            }
        };
    }
}
