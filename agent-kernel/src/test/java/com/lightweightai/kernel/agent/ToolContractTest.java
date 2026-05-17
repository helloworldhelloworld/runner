package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tool interface — contract validation")
class ToolContractTest {

    @Test
    @DisplayName("default executeReactive wraps synchronous execute as single COMPLETE chunk")
    void defaultReactiveWrapsSyncExecute() {
        Tool tool = new Tool() {
            @Override public String getName() { return "sync_tool"; }
            @Override public String getDescription() { return "A sync tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("sync result: " + args.get("input"));
            }
        };

        StepVerifier.create(tool.executeReactive(Map.of("input", "test")))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertEquals("sync_tool", chunk.getToolName());
                    assertNotNull(chunk.getResult());
                    assertEquals("sync result: test", chunk.getResult().getContent());
                    assertFalse(chunk.getResult().isError());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("default executeReactive wraps exception from execute as ERROR chunk")
    void defaultReactiveWrapsException() {
        Tool tool = new Tool() {
            @Override public String getName() { return "err_tool"; }
            @Override public String getDescription() { return "throws"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                throw new RuntimeException("tool exploded");
            }
        };

        StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertEquals("err_tool", chunk.getToolName());
                    assertTrue(chunk.getMessage().contains("tool exploded"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("isAutoExecute defaults to true")
    void isAutoExecuteDefaultsTrue() {
        Tool tool = new Tool() {
            @Override public String getName() { return "auto"; }
            @Override public String getDescription() { return "auto"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };

        assertTrue(tool.isAutoExecute());
    }

    @Test
    @DisplayName("Tool implementation can override isAutoExecute to false")
    void isAutoExecuteCanBeOverridden() {
        Tool tool = new Tool() {
            @Override public String getName() { return "confirm"; }
            @Override public String getDescription() { return "needs confirm"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
            @Override public boolean isAutoExecute() { return false; }
        };

        assertFalse(tool.isAutoExecute());
    }

    @Test
    @DisplayName("Tool with required parameters validates schema structure")
    void toolSchemaWithRequiredParams() {
        Tool tool = new Tool() {
            @Override public String getName() { return "search"; }
            @Override public String getDescription() { return "Search tool"; }
            @Override public ToolSchema getSchema() {
                return ToolSchema.withRequired(
                        Map.of("query", Map.of("type", "string", "description", "search query")),
                        "query"
                );
            }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("found: " + args.get("query"));
            }
        };

        assertEquals("object", tool.getSchema().getType());
        assertTrue(tool.getSchema().getProperties().containsKey("query"));

        ToolResult result = tool.execute(Map.of("query", "hello world"));
        assertEquals("found: hello world", result.getContent());
    }
}
