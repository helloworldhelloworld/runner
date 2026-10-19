package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolResultChunk 全路径覆盖测试。
 *
 * 已有 ToolResultChunkTest 覆盖基本工厂方法。
 * 此测试补充：
 * - withToolCallId 不可变性
 * - 每个 factory 方法的载荷正确性（CLAUDE.md UT 规则 1）
 * - log 带 logger/meta 的变体
 * - progress 带 meta 的变体
 * - error 内部异常
 */
@DisplayName("ToolResultChunk - 全路径覆盖")
class ToolResultChunkFullTest {

    @Nested
    @DisplayName("progress 工厂方法")
    class ProgressFactory {

        @Test
        @DisplayName("progress 携带所有字段")
        void progressCarriesAllFields() {
            ToolResultChunk chunk = ToolResultChunk.progress(
                    "download", "Downloading...", 0.75, 1.0);

            assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
            assertEquals("download", chunk.getToolName());
            assertEquals("Downloading...", chunk.getMessage());
            assertEquals(0.75, chunk.getProgress(), 0.001);
            assertEquals(1.0, chunk.getTotal(), 0.001);
            assertNull(chunk.getToolCallId());
            assertNull(chunk.getResult());
            assertNull(chunk.getError());
            assertNull(chunk.getMeta());
        }

        @Test
        @DisplayName("progress 带 meta")
        void progressWithMeta() {
            Map<String, Object> meta = Map.of("speed", "10MB/s");
            ToolResultChunk chunk = ToolResultChunk.progress(
                    "download", "Downloading...", 0.5, 1.0, meta);

            assertNotNull(chunk.getMeta());
            assertEquals("10MB/s", chunk.getMeta().get("speed"));
        }
    }

    @Nested
    @DisplayName("log 工厂方法")
    class LogFactory {

        @Test
        @DisplayName("log 格式化消息：[level] logger: message")
        void logFormatsMessage() {
            ToolResultChunk chunk = ToolResultChunk.log(
                    "mcp-fetch", "INFO", "HttpClient", "GET https://example.com", null);

            assertEquals(ToolResultChunk.ChunkType.LOG, chunk.getType());
            assertEquals("mcp-fetch", chunk.getToolName());
            assertEquals("[INFO] HttpClient: GET https://example.com", chunk.getMessage());
        }

        @Test
        @DisplayName("log 无 logger 时不含 logger 前缀")
        void logWithoutLogger() {
            ToolResultChunk chunk = ToolResultChunk.log(
                    "search", "DEBUG", "query started");

            assertEquals("[DEBUG] query started", chunk.getMessage());
        }

        @Test
        @DisplayName("log 带 meta")
        void logWithMeta() {
            Map<String, Object> meta = Map.of("requestId", "req-123");
            ToolResultChunk chunk = ToolResultChunk.log(
                    "api", "INFO", null, "call started", meta);

            assertNotNull(chunk.getMeta());
            assertEquals("req-123", chunk.getMeta().get("requestId"));
        }
    }

    @Nested
    @DisplayName("complete 工厂方法")
    class CompleteFactory {

        @Test
        @DisplayName("complete 携带 ToolResult")
        void completeCarriesResult() {
            ToolResult result = ToolResult.success("42");
            ToolResultChunk chunk = ToolResultChunk.complete("calculator", result);

            assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
            assertEquals("calculator", chunk.getToolName());
            assertNotNull(chunk.getResult());
            assertEquals("42", chunk.getResult().getContent());
            assertFalse(chunk.getResult().isError());
        }
    }

    @Nested
    @DisplayName("error 工厂方法")
    class ErrorFactory {

        @Test
        @DisplayName("error 携带消息和内部异常")
        void errorCarriesMessageAndException() {
            ToolResultChunk chunk = ToolResultChunk.error("broken", "connection refused");

            assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
            assertEquals("broken", chunk.getToolName());
            assertEquals("connection refused", chunk.getMessage());
            assertNotNull(chunk.getError());
            assertEquals("connection refused", chunk.getError().getMessage());
        }
    }

    @Nested
    @DisplayName("withToolCallId")
    class WithToolCallId {

        @Test
        @DisplayName("返回新实例，原实例不变")
        void returnsNewInstanceOriginalUnchanged() {
            ToolResultChunk original = ToolResultChunk.progress("tool", "msg", 0.5, 1.0);
            ToolResultChunk bound = original.withToolCallId("call-42");

            assertNull(original.getToolCallId(), "Original must not be mutated");
            assertEquals("call-42", bound.getToolCallId());
            assertEquals("tool", bound.getToolName());
            assertEquals(0.5, bound.getProgress(), 0.001);
        }

        @Test
        @DisplayName("complete chunk withToolCallId 保留 result")
        void completeChunkPreservesResult() {
            ToolResult result = ToolResult.success("data");
            ToolResultChunk original = ToolResultChunk.complete("tool", result);
            ToolResultChunk bound = original.withToolCallId("call-99");

            assertEquals("call-99", bound.getToolCallId());
            assertNotNull(bound.getResult());
            assertEquals("data", bound.getResult().getContent());
        }

        @Test
        @DisplayName("error chunk withToolCallId 保留 error 信息")
        void errorChunkPreservesError() {
            ToolResultChunk original = ToolResultChunk.error("bad", "timeout");
            ToolResultChunk bound = original.withToolCallId("call-err");

            assertEquals("call-err", bound.getToolCallId());
            assertEquals("timeout", bound.getMessage());
            assertNotNull(bound.getError());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString 包含 type 和 toolName")
        void toStringContainsTypeAndName() {
            ToolResultChunk chunk = ToolResultChunk.progress("search", "searching", 0.5, 1.0);
            String str = chunk.toString();
            assertTrue(str.contains("PROGRESS"));
            assertTrue(str.contains("search"));
        }

        @Test
        @DisplayName("toString 有 toolCallId 时包含")
        void toStringWithToolCallId() {
            ToolResultChunk chunk = ToolResultChunk.complete("tool", ToolResult.success("ok"))
                    .withToolCallId("call-1");
            String str = chunk.toString();
            assertTrue(str.contains("call-1"));
        }
    }
}
