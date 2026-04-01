package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentResponse - Agent 响应结果")
class AgentResponseExtendedTest {

    @Test
    @DisplayName("单参数构造 - 默认无工具调用")
    void singleArgConstructor() {
        AgentResponse resp = new AgentResponse("Hello");

        assertEquals("Hello", resp.getText());
        assertFalse(resp.hasToolCalls());
        assertEquals(0, resp.getToolCallCount());
        assertEquals("end_turn", resp.getStopReason());
    }

    @Test
    @DisplayName("完整构造 - 含工具调用")
    void fullConstructor() {
        var record = new AgentResponse.ToolCallRecord("weather", "{\"city\":\"BJ\"}", "sunny");
        AgentResponse resp = new AgentResponse("result", List.of(record), "tool_use");

        assertTrue(resp.hasToolCalls());
        assertEquals(1, resp.getToolCallCount());
        assertEquals("tool_use", resp.getStopReason());
        assertEquals("weather", resp.getToolCalls().get(0).getToolName());
    }

    @Test
    @DisplayName("null toolCalls 视为空列表")
    void nullToolCalls() {
        AgentResponse resp = new AgentResponse("text", null, "end_turn");
        assertFalse(resp.hasToolCalls());
        assertEquals(0, resp.getToolCallCount());
    }

    @Test
    @DisplayName("Builder 构建")
    void builder() {
        var record = new AgentResponse.ToolCallRecord("search", "{}", "found");
        AgentResponse resp = AgentResponse.builder()
                .text("Answer")
                .toolCalls(List.of(record))
                .stopReason("max_tokens")
                .build();

        assertEquals("Answer", resp.getText());
        assertEquals(1, resp.getToolCallCount());
        assertEquals("max_tokens", resp.getStopReason());
    }

    @Test
    @DisplayName("Builder 默认 stopReason 为 end_turn")
    void builderDefaultStopReason() {
        AgentResponse resp = AgentResponse.builder()
                .text("hi")
                .build();
        assertEquals("end_turn", resp.getStopReason());
    }

    @Test
    @DisplayName("ToolCallRecord 属性正确")
    void toolCallRecord() {
        var record = new AgentResponse.ToolCallRecord("calc", "{\"a\":1}", "42");
        assertEquals("calc", record.getToolName());
        assertEquals("{\"a\":1}", record.getArguments());
        assertEquals("42", record.getResult());
    }
}
