package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tool - 统一工具接口默认方法")
class ToolInterfaceTest {

    // ==================== executeReactive Default ====================

    @Test
    @DisplayName("executeReactive 默认将同步 execute 包装为单个 COMPLETE 事件")
    void shouldWrapSyncExecuteAsReactiveComplete() {
        Tool tool = createSuccessTool("greet", "Hello!");

        Flux<ToolResultChunk> flux = tool.executeReactive(Map.of());

        StepVerifier.create(flux)
            .assertNext(chunk -> {
                assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                assertEquals("greet", chunk.getToolName());
                assertNotNull(chunk.getResult());
                assertEquals("Hello!", chunk.getResult().getContent());
                assertFalse(chunk.getResult().isError());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("executeReactive 同步异常转为 ERROR 事件")
    void shouldConvertExceptionToErrorChunk() {
        Tool tool = new Tool() {
            @Override public String getName() { return "failing"; }
            @Override public String getDescription() { return "Always fails"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                throw new RuntimeException("connection refused");
            }
        };

        Flux<ToolResultChunk> flux = tool.executeReactive(Map.of());

        StepVerifier.create(flux)
            .assertNext(chunk -> {
                assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                assertEquals("failing", chunk.getToolName());
                assertTrue(chunk.getMessage().contains("connection refused"));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("executeReactive 支持延迟执行（defer）")
    void shouldDeferExecution() {
        int[] callCount = {0};
        Tool tool = new Tool() {
            @Override public String getName() { return "counter"; }
            @Override public String getDescription() { return "Counts calls"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                callCount[0]++;
                return ToolResult.success("call " + callCount[0]);
            }
        };

        // Create flux but don't subscribe yet
        Flux<ToolResultChunk> flux = tool.executeReactive(Map.of());
        assertEquals(0, callCount[0]); // Not yet executed

        // Subscribe triggers execution
        StepVerifier.create(flux)
            .assertNext(chunk -> assertEquals("call 1", chunk.getResult().getContent()))
            .verifyComplete();
        assertEquals(1, callCount[0]);
    }

    // ==================== isAutoExecute Default ====================

    @Test
    @DisplayName("isAutoExecute 默认为 true")
    void shouldBeAutoExecuteByDefault() {
        Tool tool = createSuccessTool("auto", "ok");
        assertTrue(tool.isAutoExecute());
    }

    // ==================== Helper ====================

    private Tool createSuccessTool(String name, String result) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Test tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(result);
            }
        };
    }
}
