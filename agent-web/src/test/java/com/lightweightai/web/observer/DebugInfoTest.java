package com.lightweightai.web.observer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugInfo - LLM 调试信息收集")
class DebugInfoTest {

    @Nested
    @DisplayName("时间和时长")
    class TimingTests {

        @Test
        @DisplayName("getDuration 返回 endTime - startTime")
        void durationCalculation() {
            DebugInfo info = new DebugInfo();
            info.setStartTime(1000);
            info.setEndTime(3500);
            assertEquals(2500, info.getDuration());
        }
    }

    @Nested
    @DisplayName("MessageInfo 消息截断")
    class MessageInfoTests {

        @Test
        @DisplayName("正常消息不截断")
        void shortMessageNotTruncated() {
            DebugInfo.MessageInfo msg = new DebugInfo.MessageInfo(0, "user", "hello");
            assertEquals("hello", msg.getContent());
            assertEquals("user", msg.getRole());
            assertEquals(0, msg.getIndex());
        }

        @Test
        @DisplayName("超长消息被截断到 500 字符")
        void longMessageTruncated() {
            String longContent = "a".repeat(600);
            DebugInfo.MessageInfo msg = new DebugInfo.MessageInfo(0, "user", longContent);
            assertTrue(msg.getContent().length() < 600);
            assertTrue(msg.getContent().endsWith("[truncated]"));
        }

        @Test
        @DisplayName("null 消息显示 (null)")
        void nullMessageHandled() {
            DebugInfo.MessageInfo msg = new DebugInfo.MessageInfo(0, "user", null);
            assertEquals("(null)", msg.getContent());
        }
    }

    @Nested
    @DisplayName("ToolCallInfo 工具调用信息")
    class ToolCallInfoTests {

        @Test
        @DisplayName("记录工具名、参数、结果和耗时")
        void capturesAllFields() {
            Map<String, Object> args = Map.of("query", "test");
            DebugInfo.ToolCallInfo tc = new DebugInfo.ToolCallInfo(
                "search", args, "found results", 150);

            assertEquals("search", tc.getToolName());
            assertEquals("test", tc.getArguments().get("query"));
            assertEquals("found results", tc.getResult());
            assertEquals(150, tc.getDuration());
        }

        @Test
        @DisplayName("超长结果被截断到 300 字符")
        void longResultTruncated() {
            String longResult = "b".repeat(400);
            DebugInfo.ToolCallInfo tc = new DebugInfo.ToolCallInfo(
                "tool", Map.of(), longResult, 100);

            assertTrue(tc.getResult().length() < 400);
            assertTrue(tc.getResult().endsWith("[truncated]"));
        }
    }

    @Nested
    @DisplayName("Builder 方法")
    class BuilderMethods {

        @Test
        @DisplayName("addRequestMessage 添加消息到列表")
        void addRequestMessage() {
            DebugInfo info = new DebugInfo();
            info.addRequestMessage(0, "user", "hello");
            info.addRequestMessage(1, "assistant", "hi");

            assertEquals(2, info.getRequestMessages().size());
            assertEquals("user", info.getRequestMessages().get(0).getRole());
        }

        @Test
        @DisplayName("addToolCall 添加工具调用到列表")
        void addToolCall() {
            DebugInfo info = new DebugInfo();
            info.addToolCall("search", Map.of("q", "test"), "result", 50);
            info.addToolCall("calc", Map.of("expr", "1+1"), "2", 10);

            assertEquals(2, info.getToolCalls().size());
            assertEquals("search", info.getToolCalls().get(0).getToolName());
        }
    }

    @Test
    @DisplayName("所有 getter/setter 正常工作")
    void gettersAndSetters() {
        DebugInfo info = new DebugInfo();
        info.setSessionId("sess-1");
        info.setUserInput("hello");
        info.setResponseText("response");
        info.setStopReason("end_turn");
        info.setResponseTokens(100);
        info.setTotalRequestTokens(50);
        info.setSystemPromptPreview("system...");
        info.setError("some error");

        assertEquals("sess-1", info.getSessionId());
        assertEquals("hello", info.getUserInput());
        assertEquals("response", info.getResponseText());
        assertEquals("end_turn", info.getStopReason());
        assertEquals(100, info.getResponseTokens());
        assertEquals(50, info.getTotalRequestTokens());
        assertEquals("system...", info.getSystemPromptPreview());
        assertEquals("some error", info.getError());
    }
}
