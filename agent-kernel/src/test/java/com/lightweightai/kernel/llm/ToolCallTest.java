package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolCall - LLM 工具调用请求")
class ToolCallTest {

    @Test
    @DisplayName("创建 ToolCall")
    void createToolCall() {
        ToolCall call = new ToolCall("id1", "weather", Map.of("city", "Beijing"));

        assertEquals("id1", call.getId());
        assertEquals("weather", call.getName());
        assertEquals("Beijing", call.getArguments().get("city"));
    }

    @Test
    @DisplayName("getArguments 返回防御性拷贝")
    void argumentsDefensiveCopy() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("key", "value");
        ToolCall call = new ToolCall("id1", "tool", args);

        // 修改返回的 map 不影响原始对象
        call.getArguments().put("extra", "x");
        assertFalse(call.getArguments().containsKey("extra"));

        // 修改构造时的 map 不影响对象
        args.put("added", "y");
        assertFalse(call.getArguments().containsKey("added"));
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringOutput() {
        ToolCall call = new ToolCall("id1", "weather", Map.of("city", "Beijing"));
        String str = call.toString();

        assertTrue(str.contains("id1"));
        assertTrue(str.contains("weather"));
        assertTrue(str.contains("city"));
    }
}
