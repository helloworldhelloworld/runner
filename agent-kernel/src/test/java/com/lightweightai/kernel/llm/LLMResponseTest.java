package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMResponse - LLM 响应包装")
class LLMResponseTest {

    @Test
    @DisplayName("构建基本响应")
    void buildBasicResponse() {
        ConversationMessage msg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .textContent("Hello")
            .build();

        LLMResponse response = LLMResponse.builder()
            .message(msg)
            .stopReason("end_turn")
            .build();

        assertEquals(msg, response.getMessage());
        assertEquals("end_turn", response.getStopReason());
        assertFalse(response.hasToolCalls());
        assertNotNull(response.getToolCalls());
        assertTrue(response.getToolCalls().isEmpty());
    }

    @Test
    @DisplayName("包含 tool calls")
    void responseWithToolCalls() {
        ToolCall tc = new ToolCall("id1", "weather", Map.of("city", "Beijing"));

        LLMResponse response = LLMResponse.builder()
            .toolCalls(List.of(tc))
            .stopReason("tool_use")
            .build();

        assertTrue(response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("weather", response.getToolCalls().get(0).getName());
    }

    @Test
    @DisplayName("null toolCalls 处理为空列表")
    void nullToolCalls() {
        LLMResponse response = LLMResponse.builder()
            .toolCalls(null)
            .build();

        assertFalse(response.hasToolCalls());
        assertTrue(response.getToolCalls().isEmpty());
    }

    @Test
    @DisplayName("UsageInfo 令牌计数")
    void usageInfo() {
        LLMResponse.UsageInfo usage = new LLMResponse.UsageInfo(100, 50);

        assertEquals(100, usage.getInputTokens());
        assertEquals(50, usage.getOutputTokens());
        assertEquals(150, usage.getTotalTokens());
    }

    @Test
    @DisplayName("响应包含 usage 信息")
    void responseWithUsage() {
        LLMResponse.UsageInfo usage = new LLMResponse.UsageInfo(200, 100);

        LLMResponse response = LLMResponse.builder()
            .usage(usage)
            .build();

        assertNotNull(response.getUsage());
        assertEquals(300, response.getUsage().getTotalTokens());
    }
}
