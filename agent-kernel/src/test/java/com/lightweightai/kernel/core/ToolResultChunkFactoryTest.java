package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolResultChunk factory methods and field access.
 * Existing ToolResultChunkTest may cover basics; this adds factory coverage.
 */
@DisplayName("ToolResultChunk - Factory methods")
class ToolResultChunkFactoryTest {

    @Nested
    @DisplayName("progress chunk")
    class ProgressTests {

        @Test
        @DisplayName("preserves progress values")
        void preservesProgress() {
            ToolResultChunk chunk = ToolResultChunk.progress("search", "50%", 50.0, 100.0);
            assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
            assertEquals("search", chunk.getToolName());
            assertEquals("50%", chunk.getMessage());
            assertEquals(50.0, chunk.getProgress(), 0.001);
            assertEquals(100.0, chunk.getTotal(), 0.001);
        }

        @Test
        @DisplayName("progress with meta preserves metadata")
        void progressWithMeta() {
            Map<String, Object> meta = Map.of("token", "abc");
            ToolResultChunk chunk = ToolResultChunk.progress("tool", "msg", 1.0, 2.0, meta);
            assertNotNull(chunk.getMeta());
            assertEquals("abc", chunk.getMeta().get("token"));
        }
    }

    @Nested
    @DisplayName("log chunk")
    class LogTests {

        @Test
        @DisplayName("formats log message with level, logger, and meta")
        void formatsWithLevelAndLogger() {
            ToolResultChunk chunk = ToolResultChunk.log("tool", "INFO", "my.logger", "started", null);
            assertEquals(ToolResultChunk.ChunkType.LOG, chunk.getType());
            assertTrue(chunk.getMessage().contains("INFO"));
            assertTrue(chunk.getMessage().contains("my.logger"));
            assertTrue(chunk.getMessage().contains("started"));
        }

        @Test
        @DisplayName("log without logger omits logger prefix")
        void logWithoutLogger() {
            ToolResultChunk chunk = ToolResultChunk.log("tool", "WARN", "caution");
            assertEquals(ToolResultChunk.ChunkType.LOG, chunk.getType());
            assertTrue(chunk.getMessage().contains("WARN"));
            assertTrue(chunk.getMessage().contains("caution"));
        }
    }

    @Nested
    @DisplayName("complete chunk")
    class CompleteTests {

        @Test
        @DisplayName("preserves tool result")
        void preservesResult() {
            ToolResult result = ToolResult.success("done");
            ToolResultChunk chunk = ToolResultChunk.complete("weather", result);

            assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
            assertEquals("weather", chunk.getToolName());
            assertSame(result, chunk.getResult());
        }
    }

    @Nested
    @DisplayName("error chunk")
    class ErrorTests {

        @Test
        @DisplayName("preserves error message and creates error throwable")
        void preservesError() {
            ToolResultChunk chunk = ToolResultChunk.error("tool", "timeout");
            assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
            assertEquals("tool", chunk.getToolName());
            assertEquals("timeout", chunk.getMessage());
            assertNotNull(chunk.getError());
            assertEquals("timeout", chunk.getError().getMessage());
        }
    }

    @Nested
    @DisplayName("withToolCallId")
    class WithToolCallIdTests {

        @Test
        @DisplayName("creates new chunk with toolCallId")
        void createsWithId() {
            ToolResultChunk original = ToolResultChunk.progress("tool", "msg", 1.0, 2.0);
            assertNull(original.getToolCallId());

            ToolResultChunk bound = original.withToolCallId("call_abc");
            assertEquals("call_abc", bound.getToolCallId());
            assertEquals("tool", bound.getToolName());
            assertEquals(ToolResultChunk.ChunkType.PROGRESS, bound.getType());
        }
    }

    @Test
    @DisplayName("toString contains type and toolName")
    void toStringContainsFields() {
        ToolResultChunk chunk = ToolResultChunk.error("my_tool", "boom");
        String str = chunk.toString();
        assertTrue(str.contains("ERROR"));
        assertTrue(str.contains("my_tool"));
        assertTrue(str.contains("boom"));
    }
}
