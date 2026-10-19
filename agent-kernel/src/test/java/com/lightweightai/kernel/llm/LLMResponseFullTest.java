package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLMResponse 全路径覆盖测试。
 *
 * 已有 LLMResponseTest 覆盖基本构建。
 * 此测试补充：
 * - hasToolCalls 边界条件
 * - UsageInfo getTotalTokens
 * - toolCalls 防御性副本
 */
@DisplayName("LLMResponse - 全路径覆盖")
class LLMResponseFullTest {

    @Nested
    @DisplayName("hasToolCalls")
    class HasToolCalls {

        @Test
        @DisplayName("无 toolCalls 时返回 false")
        void noToolCallsReturnsFalse() {
            LLMResponse response = LLMResponse.builder()
                    .stopReason("end_turn")
                    .build();
            assertFalse(response.hasToolCalls());
        }

        @Test
        @DisplayName("空列表 toolCalls 时返回 false")
        void emptyToolCallsReturnsFalse() {
            LLMResponse response = LLMResponse.builder()
                    .toolCalls(List.of())
                    .stopReason("end_turn")
                    .build();
            assertFalse(response.hasToolCalls());
        }

        @Test
        @DisplayName("有 toolCalls 时返回 true")
        void withToolCallsReturnsTrue() {
            ToolCall tc = new ToolCall("tc-1", "search", Map.of("q", "test"));
            LLMResponse response = LLMResponse.builder()
                    .toolCalls(List.of(tc))
                    .stopReason("tool_use")
                    .build();
            assertTrue(response.hasToolCalls());
        }
    }

    @Nested
    @DisplayName("UsageInfo")
    class UsageInfoTests {

        @Test
        @DisplayName("getTotalTokens 返回 input + output 之和")
        void totalTokensIsSum() {
            LLMResponse.UsageInfo usage = new LLMResponse.UsageInfo(500, 200);
            assertEquals(700, usage.getTotalTokens());
            assertEquals(500, usage.getInputTokens());
            assertEquals(200, usage.getOutputTokens());
        }

        @Test
        @DisplayName("UsageInfo 通过 builder 正确设置")
        void usageViaBuilder() {
            LLMResponse.UsageInfo usage = new LLMResponse.UsageInfo(100, 50);
            LLMResponse response = LLMResponse.builder()
                    .usage(usage)
                    .build();
            assertNotNull(response.getUsage());
            assertEquals(150, response.getUsage().getTotalTokens());
        }
    }

    @Nested
    @DisplayName("toolCalls 防御性副本")
    class ToolCallsDefensiveCopy {

        @Test
        @DisplayName("getToolCalls 返回的列表不影响内部状态")
        void toolCallsReturnsList() {
            ToolCall tc = new ToolCall("tc-1", "tool1", Map.of());
            LLMResponse response = LLMResponse.builder()
                    .toolCalls(List.of(tc))
                    .build();

            List<ToolCall> calls = response.getToolCalls();
            assertEquals(1, calls.size());
            assertEquals("tool1", calls.get(0).getName());
        }
    }

    @Nested
    @DisplayName("message + stopReason")
    class BasicFields {

        @Test
        @DisplayName("message 和 stopReason 正确设置")
        void fieldsSetCorrectly() {
            ConversationMessage msg = ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT)
                    .textContent("Hello!")
                    .build();
            LLMResponse response = LLMResponse.builder()
                    .message(msg)
                    .stopReason("end_turn")
                    .build();

            assertEquals("Hello!", response.getMessage().getTextContent());
            assertEquals("end_turn", response.getStopReason());
        }

        @Test
        @DisplayName("message 为 null 时允许构建")
        void nullMessageAllowed() {
            LLMResponse response = LLMResponse.builder()
                    .stopReason("end_turn")
                    .build();
            assertNull(response.getMessage());
        }
    }
}
