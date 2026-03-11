package com.lightweightai.kernel.agent.directive;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DirectiveResultTest {

    @Test
    void toToolResult_success() {
        DirectiveResult dr = DirectiveResult.success("d-1", "photo_url");
        ToolResult tr = dr.toToolResult();

        assertFalse(tr.isError());
        assertEquals("photo_url", tr.getContent());
        assertNull(tr.getToolUseId()); // toolUseId set by ToolExecutor, not here
    }

    @Test
    void toToolResult_successWithMetadata() {
        DirectiveResult dr = DirectiveResult.success("d-2", "result",
            Map.of("device", "Pixel 8", "elapsed_ms", 1200));
        ToolResult tr = dr.toToolResult();

        assertFalse(tr.isError());
        assertEquals("result", tr.getContent());
        assertTrue(tr.hasStructuredContent());
        assertEquals("Pixel 8", tr.getStructuredContent().get("device"));
    }

    @Test
    void toToolResult_error() {
        DirectiveResult dr = DirectiveResult.error("d-3", "Camera permission denied");
        ToolResult tr = dr.toToolResult();

        assertTrue(tr.isError());
        assertEquals("Camera permission denied", tr.getContent());
    }

    @Test
    void toToolResult_errorNullContent() {
        DirectiveResult dr = new DirectiveResult("d-4", false, null, null);
        ToolResult tr = dr.toToolResult();

        assertTrue(tr.isError());
        assertEquals("Directive execution failed", tr.getContent());
    }

    @Test
    void toToolResult_successNullContent() {
        DirectiveResult dr = new DirectiveResult("d-5", true, null, null);
        ToolResult tr = dr.toToolResult();

        assertFalse(tr.isError());
        assertEquals("", tr.getContent());
    }

    @Test
    void toToolResult_successEmptyMetadata() {
        DirectiveResult dr = DirectiveResult.success("d-6", "content", Map.of());
        ToolResult tr = dr.toToolResult();

        assertFalse(tr.isError());
        assertFalse(tr.hasStructuredContent());
    }
}
