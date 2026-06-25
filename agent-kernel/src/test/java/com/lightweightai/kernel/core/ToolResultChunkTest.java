package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolResultChunk - 工具流式结果片段")
class ToolResultChunkTest {

    @Nested
    @DisplayName("progress 工厂方法")
    class ProgressFactory {

        @Test
        @DisplayName("创建进度事件，类型为 PROGRESS，字段正确")
        void createsProgressWithCorrectFields() {
            ToolResultChunk chunk = ToolResultChunk.progress("myTool", "正在处理", 30, 100);

            assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
            assertEquals("myTool", chunk.getToolName());
            assertEquals("正在处理", chunk.getMessage());
            assertEquals(30, chunk.getProgress());
            assertEquals(100, chunk.getTotal());
            assertNull(chunk.getToolCallId());
            assertNull(chunk.getResult());
            assertNull(chunk.getError());
            assertNull(chunk.getMeta());
        }

        @Test
        @DisplayName("携带 meta 的进度事件保留 meta 数据")
        void progressWithMetaPreservesMeta() {
            Map<String, Object> meta = Map.of("step", 2, "detail", "downloading");
            ToolResultChunk chunk = ToolResultChunk.progress("myTool", "step2", 2, 5, meta);

            assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
            assertNotNull(chunk.getMeta());
            assertEquals(2, chunk.getMeta().get("step"));
            assertEquals("downloading", chunk.getMeta().get("detail"));
        }
    }

    @Nested
    @DisplayName("log 工厂方法")
    class LogFactory {

        @Test
        @DisplayName("带 logger 时格式为 [level] logger: message")
        void logWithLoggerFormatsCorrectly() {
            ToolResultChunk chunk = ToolResultChunk.log("myTool", "WARN", "MyClass", "something broke", null);

            assertEquals(ToolResultChunk.ChunkType.LOG, chunk.getType());
            assertEquals("myTool", chunk.getToolName());
            assertEquals("[WARN] MyClass: something broke", chunk.getMessage());
            assertNull(chunk.getToolCallId());
        }

        @Test
        @DisplayName("不带 logger 时格式为 [level] message，无 null 出现")
        void logWithoutLoggerOmitsLoggerPrefix() {
            ToolResultChunk chunk = ToolResultChunk.log("myTool", "INFO", "all good");

            assertEquals(ToolResultChunk.ChunkType.LOG, chunk.getType());
            assertEquals("[INFO] all good", chunk.getMessage());
            assertFalse(chunk.getMessage().contains("null"));
        }

        @Test
        @DisplayName("携带 meta 的日志事件保留 meta 数据")
        void logWithMetaPreservesMeta() {
            Map<String, Object> meta = Map.of("thread", "main", "line", 42);
            ToolResultChunk chunk = ToolResultChunk.log("myTool", "DEBUG", "Logger", "trace msg", meta);

            assertNotNull(chunk.getMeta());
            assertEquals("main", chunk.getMeta().get("thread"));
            assertEquals(42, chunk.getMeta().get("line"));
        }
    }

    @Nested
    @DisplayName("complete 工厂方法")
    class CompleteFactory {

        @Test
        @DisplayName("创建完成事件，类型为 COMPLETE，携带 ToolResult")
        void createsCompleteWithResult() {
            ToolResult result = ToolResult.success("执行成功");
            ToolResultChunk chunk = ToolResultChunk.complete("myTool", result);

            assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
            assertEquals("myTool", chunk.getToolName());
            assertSame(result, chunk.getResult());
            assertEquals("执行成功", chunk.getResult().getContent());
            assertNull(chunk.getMessage());
            assertNull(chunk.getError());
        }
    }

    @Nested
    @DisplayName("error 工厂方法")
    class ErrorFactory {

        @Test
        @DisplayName("创建错误事件，类型为 ERROR，message 和 error 均已设置")
        void createsErrorWithMessageAndThrowable() {
            ToolResultChunk chunk = ToolResultChunk.error("myTool", "连接超时");

            assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
            assertEquals("myTool", chunk.getToolName());
            assertEquals("连接超时", chunk.getMessage());
            assertNotNull(chunk.getError());
            assertInstanceOf(RuntimeException.class, chunk.getError());
            assertEquals("连接超时", chunk.getError().getMessage());
            assertNull(chunk.getResult());
        }
    }

    @Nested
    @DisplayName("withToolCallId 方法")
    class WithToolCallId {

        @Test
        @DisplayName("返回带 toolCallId 的新实例，原实例不变")
        void returnsNewInstanceWithIdOriginalUnchanged() {
            ToolResultChunk original = ToolResultChunk.progress("myTool", "working", 1, 3);
            ToolResultChunk bound = original.withToolCallId("call-123");

            assertNull(original.getToolCallId());
            assertEquals("call-123", bound.getToolCallId());
            assertNotSame(original, bound);
            assertEquals(original.getType(), bound.getType());
            assertEquals(original.getToolName(), bound.getToolName());
            assertEquals(original.getMessage(), bound.getMessage());
            assertEquals(original.getProgress(), bound.getProgress());
            assertEquals(original.getTotal(), bound.getTotal());
        }
    }

    @Nested
    @DisplayName("toString 方法")
    class ToStringMethod {

        @Test
        @DisplayName("输出包含 type 和 toolName")
        void includesTypeAndToolName() {
            ToolResultChunk chunk = ToolResultChunk.progress("searchTool", "searching", 1, 10);

            String str = chunk.toString();
            assertTrue(str.contains("PROGRESS"));
            assertTrue(str.contains("searchTool"));
        }

        @Test
        @DisplayName("有 toolCallId 时包含在输出中")
        void includesToolCallIdWhenPresent() {
            ToolResultChunk chunk = ToolResultChunk.log("myTool", "INFO", "msg")
                    .withToolCallId("tc-99");

            String str = chunk.toString();
            assertTrue(str.contains("tc-99"));
        }

        @Test
        @DisplayName("有 message 时包含在输出中")
        void includesMessageWhenPresent() {
            ToolResultChunk chunk = ToolResultChunk.error("myTool", "failure reason");

            String str = chunk.toString();
            assertTrue(str.contains("failure reason"));
        }
    }
}
