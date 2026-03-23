package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentResponse - Agent 响应结果")
class AgentResponseTest {

    @Test
    @DisplayName("简单构造器")
    void simpleConstructor() {
        AgentResponse resp = new AgentResponse("Hello");

        assertEquals("Hello", resp.getText());
        assertEquals("end_turn", resp.getStopReason());
        assertFalse(resp.hasToolCalls());
        assertEquals(0, resp.getToolCallCount());
    }

    @Test
    @DisplayName("完整构造器")
    void fullConstructor() {
        AgentResponse.ToolCallRecord record =
            new AgentResponse.ToolCallRecord("weather", "{}", "sunny");

        AgentResponse resp = new AgentResponse("result", List.of(record), "tool_use");

        assertEquals("result", resp.getText());
        assertEquals("tool_use", resp.getStopReason());
        assertTrue(resp.hasToolCalls());
        assertEquals(1, resp.getToolCallCount());
    }

    @Test
    @DisplayName("null toolCalls 安全处理")
    void nullToolCalls() {
        AgentResponse resp = new AgentResponse("text", null, "end_turn");

        assertFalse(resp.hasToolCalls());
        assertEquals(0, resp.getToolCallCount());
    }

    @Test
    @DisplayName("Builder 模式")
    void builder() {
        AgentResponse resp = AgentResponse.builder()
            .text("built")
            .stopReason("custom")
            .toolCalls(List.of())
            .build();

        assertEquals("built", resp.getText());
        assertEquals("custom", resp.getStopReason());
    }

    @Test
    @DisplayName("Builder 默认 stopReason")
    void builderDefaults() {
        AgentResponse resp = AgentResponse.builder()
            .text("test")
            .build();

        assertEquals("end_turn", resp.getStopReason());
    }

    @Test
    @DisplayName("ToolCallRecord 访问器")
    void toolCallRecord() {
        AgentResponse.ToolCallRecord record =
            new AgentResponse.ToolCallRecord("calc", "{\"x\":1}", "42");

        assertEquals("calc", record.getToolName());
        assertEquals("{\"x\":1}", record.getArguments());
        assertEquals("42", record.getResult());
    }
}
