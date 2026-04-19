package com.lightweightai.web.gateway;

import com.lightweightai.web.gateway.UnifiedChatResponse.ResponseType;
import com.lightweightai.web.gateway.UnifiedChatResponse.ToolEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnifiedChatResponse - 统一聊天响应")
class UnifiedChatResponseTest {

    @Nested
    @DisplayName("full 工厂方法")
    class FullFactoryTests {

        @Test
        @DisplayName("创建完整响应")
        void shouldCreateFullResponse() {
            UnifiedChatResponse response = UnifiedChatResponse.full("req-1", "sess-1", "Hello!");

            assertEquals("req-1", response.getRequestId());
            assertEquals("sess-1", response.getSessionId());
            assertEquals(ResponseType.FULL, response.getType());
            assertEquals("Hello!", response.getContent());
            assertFalse(response.isError());
        }
    }

    @Nested
    @DisplayName("delta 工厂方法")
    class DeltaFactoryTests {

        @Test
        @DisplayName("创建流式增量")
        void shouldCreateDelta() {
            UnifiedChatResponse response = UnifiedChatResponse.delta("req-1", "chunk");

            assertEquals("req-1", response.getRequestId());
            assertEquals(ResponseType.DELTA, response.getType());
            assertEquals("chunk", response.getContent());
            assertNull(response.getSessionId());
        }
    }

    @Nested
    @DisplayName("complete 工厂方法")
    class CompleteFactoryTests {

        @Test
        @DisplayName("创建流式完成")
        void shouldCreateComplete() {
            UnifiedChatResponse response = UnifiedChatResponse.complete("req-1", "sess-1", "full text");

            assertEquals(ResponseType.COMPLETE, response.getType());
            assertEquals("sess-1", response.getSessionId());
            assertEquals("full text", response.getContent());
        }
    }

    @Nested
    @DisplayName("error 工厂方法")
    class ErrorFactoryTests {

        @Test
        @DisplayName("创建错误响应")
        void shouldCreateError() {
            UnifiedChatResponse response = UnifiedChatResponse.error("req-1", "Something went wrong");

            assertEquals(ResponseType.ERROR, response.getType());
            assertTrue(response.isError());
            assertEquals("Something went wrong", response.getErrorMessage());
            assertEquals("req-1", response.getRequestId());
        }
    }

    @Nested
    @DisplayName("工具事件工厂方法")
    class ToolEventFactoryTests {

        @Test
        @DisplayName("toolCallStart 创建工具调用开始事件")
        void shouldCreateToolCallStart() {
            UnifiedChatResponse response = UnifiedChatResponse.toolCallStart("req-1", "weather", "call-1");

            assertEquals(ResponseType.TOOL_CALL_START, response.getType());
            assertNotNull(response.getToolEvent());
            assertEquals("weather", response.getToolEvent().getToolName());
            assertEquals("call-1", response.getToolEvent().getToolCallId());
        }

        @Test
        @DisplayName("toolProgress 创建工具进度事件")
        void shouldCreateToolProgress() {
            UnifiedChatResponse response = UnifiedChatResponse.toolProgress(
                    "req-1", "download", 50.0, 100.0, "Downloading...");

            assertEquals(ResponseType.TOOL_PROGRESS, response.getType());
            ToolEvent te = response.getToolEvent();
            assertNotNull(te);
            assertEquals("download", te.getToolName());
            assertEquals(50.0, te.getProgress());
            assertEquals(100.0, te.getTotal());
            assertEquals("Downloading...", te.getMessage());
        }

        @Test
        @DisplayName("toolLog 创建工具日志事件")
        void shouldCreateToolLog() {
            UnifiedChatResponse response = UnifiedChatResponse.toolLog("req-1", "build", "Compiling...");

            assertEquals(ResponseType.TOOL_LOG, response.getType());
            ToolEvent te = response.getToolEvent();
            assertEquals("build", te.getToolName());
            assertEquals("Compiling...", te.getMessage());
        }

        @Test
        @DisplayName("toolResult 创建工具结果事件")
        void shouldCreateToolResult() {
            UnifiedChatResponse response = UnifiedChatResponse.toolResult(
                    "req-1", "calculator", "{\"result\": 42}", false);

            assertEquals(ResponseType.TOOL_RESULT, response.getType());
            ToolEvent te = response.getToolEvent();
            assertEquals("calculator", te.getToolName());
            assertEquals("{\"result\": 42}", te.getContent());
            assertFalse(te.getIsError());
        }

        @Test
        @DisplayName("toolResult isError=true")
        void shouldCreateToolResultWithError() {
            UnifiedChatResponse response = UnifiedChatResponse.toolResult(
                    "req-1", "calculator", "Division by zero", true);

            ToolEvent te = response.getToolEvent();
            assertTrue(te.getIsError());
        }

        @Test
        @DisplayName("toolError 创建工具错误事件")
        void shouldCreateToolError() {
            UnifiedChatResponse response = UnifiedChatResponse.toolError(
                    "req-1", "api_call", "Connection timeout");

            assertEquals(ResponseType.TOOL_ERROR, response.getType());
            ToolEvent te = response.getToolEvent();
            assertEquals("api_call", te.getToolName());
            assertEquals("Connection timeout", te.getMessage());
        }
    }

    @Nested
    @DisplayName("Getter/Setter")
    class GetterSetterTests {

        @Test
        @DisplayName("设置和获取延迟")
        void shouldSetAndGetLatency() {
            UnifiedChatResponse response = UnifiedChatResponse.full("r", "s", "c");
            response.setLatencyMs(150);
            assertEquals(150, response.getLatencyMs());
        }

        @Test
        @DisplayName("设置和获取 skillsApplied")
        void shouldSetAndGetSkills() {
            UnifiedChatResponse response = UnifiedChatResponse.full("r", "s", "c");
            response.setSkillsApplied(List.of("crisis_detection", "assessment"));

            assertEquals(2, response.getSkillsApplied().size());
            assertTrue(response.getSkillsApplied().contains("crisis_detection"));
        }

        @Test
        @DisplayName("设置和获取 metadata")
        void shouldSetAndGetMetadata() {
            UnifiedChatResponse response = UnifiedChatResponse.full("r", "s", "c");
            response.setMetadata(Map.of("model", "claude-3", "tokens", 100));

            assertEquals("claude-3", response.getMetadata().get("model"));
            assertEquals(100, response.getMetadata().get("tokens"));
        }
    }

    @Nested
    @DisplayName("ResponseType 枚举完整性")
    class ResponseTypeTests {

        @Test
        @DisplayName("所有 ResponseType 值存在")
        void allResponseTypesShouldExist() {
            assertEquals(9, ResponseType.values().length);
            assertNotNull(ResponseType.FULL);
            assertNotNull(ResponseType.DELTA);
            assertNotNull(ResponseType.COMPLETE);
            assertNotNull(ResponseType.TOOL_CALL_START);
            assertNotNull(ResponseType.TOOL_PROGRESS);
            assertNotNull(ResponseType.TOOL_LOG);
            assertNotNull(ResponseType.TOOL_RESULT);
            assertNotNull(ResponseType.TOOL_ERROR);
            assertNotNull(ResponseType.ERROR);
        }
    }
}
