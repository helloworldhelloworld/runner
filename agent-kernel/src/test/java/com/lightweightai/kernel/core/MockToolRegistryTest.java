package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockToolRegistry - 测试用 Mock 工具注册表")
class MockToolRegistryTest {

    @Test
    @DisplayName("从 mock 响应 Map 创建后，工具已注册")
    void createWithMockResponses_toolsRegistered() {
        Map<String, Object> mockResponses = Map.of(
            "GetPoi", Map.of("status", "ok", "name", "天安门"),
            "Navigate", Map.of("status", "ok", "route", "已开始导航")
        );

        MockToolRegistry mockRegistry = new MockToolRegistry(mockResponses);
        ToolRegistry registry = mockRegistry.getRegistry();

        assertTrue(registry.has("GetPoi"));
        assertTrue(registry.has("Navigate"));
        assertFalse(registry.has("UnknownTool"));
        assertEquals(2, registry.enabledCount());
    }

    @Test
    @DisplayName("执行 mock 工具返回预配置响应")
    void executeMockTool_returnsConfiguredResponse() {
        Map<String, Object> poiResult = Map.of("status", "ok", "name", "故宫");
        MockToolRegistry mockRegistry = new MockToolRegistry(Map.of("GetPoi", poiResult));

        Tool tool = mockRegistry.getRegistry().get("GetPoi").orElseThrow();
        ToolResult result = tool.execute(Map.of("keyword", "故宫"));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("故宫"));
    }

    @Test
    @DisplayName("通过 ToolExecutor 执行 mock 工具")
    void executeThroughToolExecutor_returnsConfiguredResponse() {
        Map<String, Object> navResult = Map.of("status", "ok", "message", "导航已启动");
        MockToolRegistry mockRegistry = new MockToolRegistry(Map.of("Navigate", navResult));

        ToolExecutor executor = new ToolExecutor(mockRegistry.getRegistry());
        ToolCall call = new ToolCall("call-1", "Navigate", Map.of("destination", "北京"));
        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("导航已启动"));
    }

    @Test
    @DisplayName("ToolExecutor 执行未注册工具返回错误")
    void executeUnknownTool_returnsError() {
        MockToolRegistry mockRegistry = new MockToolRegistry(Map.of(
            "GetPoi", Map.of("status", "ok")
        ));

        ToolExecutor executor = new ToolExecutor(mockRegistry.getRegistry());
        ToolCall call = new ToolCall("call-2", "UnknownTool", Map.of());
        ToolResult result = executor.executeToolCall(call);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("not found"));
    }
}
