package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolResultChunk - 工具流式结果片段")
class ToolResultChunkTest {

    @Test
    @DisplayName("progress 事件")
    void progressChunk() {
        ToolResultChunk chunk = ToolResultChunk.progress("tool1", "50%", 50, 100);

        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
        assertEquals("tool1", chunk.getToolName());
        assertEquals("50%", chunk.getMessage());
        assertEquals(50, chunk.getProgress());
        assertEquals(100, chunk.getTotal());
        assertNull(chunk.getToolCallId());
    }

    @Test
    @DisplayName("progress 带 meta 事件")
    void progressWithMeta() {
        Map<String, Object> meta = Map.of("step", 2);
        ToolResultChunk chunk = ToolResultChunk.progress("tool1", "step2", 2, 5, meta);

        assertNotNull(chunk.getMeta());
        assertEquals(2, chunk.getMeta().get("step"));
    }

    @Test
    @DisplayName("log 事件")
    void logChunk() {
        ToolResultChunk chunk = ToolResultChunk.log("tool1", "INFO", "something happened");

        assertEquals(ToolResultChunk.ChunkType.LOG, chunk.getType());
        assertTrue(chunk.getMessage().contains("INFO"));
        assertTrue(chunk.getMessage().contains("something happened"));
    }

    @Test
    @DisplayName("log 带 logger 名")
    void logWithLogger() {
        ToolResultChunk chunk = ToolResultChunk.log("tool1", "WARN", "MyClass", "warning msg", null);

        assertTrue(chunk.getMessage().contains("MyClass"));
        assertTrue(chunk.getMessage().contains("WARN"));
    }

    @Test
    @DisplayName("complete 事件")
    void completeChunk() {
        ToolResult result = ToolResult.success("done");
        ToolResultChunk chunk = ToolResultChunk.complete("tool1", result);

        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
        assertEquals("tool1", chunk.getToolName());
        assertNotNull(chunk.getResult());
        assertEquals("done", chunk.getResult().getContent());
    }

    @Test
    @DisplayName("error 事件")
    void errorChunk() {
        ToolResultChunk chunk = ToolResultChunk.error("tool1", "boom");

        assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
        assertEquals("boom", chunk.getMessage());
        assertNotNull(chunk.getError());
    }

    @Test
    @DisplayName("withToolCallId 绑定")
    void withToolCallId() {
        ToolResultChunk chunk = ToolResultChunk.progress("tool1", "msg", 1, 2);
        ToolResultChunk bound = chunk.withToolCallId("call-1");

        assertNull(chunk.getToolCallId());
        assertEquals("call-1", bound.getToolCallId());
        assertEquals(chunk.getType(), bound.getType());
        assertEquals(chunk.getToolName(), bound.getToolName());
    }

    @Test
    @DisplayName("toString 输出")
    void toStringOutput() {
        ToolResultChunk chunk = ToolResultChunk.progress("tool1", "working", 1, 3)
            .withToolCallId("c1");

        String str = chunk.toString();
        assertTrue(str.contains("PROGRESS"));
        assertTrue(str.contains("tool1"));
        assertTrue(str.contains("c1"));
        assertTrue(str.contains("working"));
    }
}
