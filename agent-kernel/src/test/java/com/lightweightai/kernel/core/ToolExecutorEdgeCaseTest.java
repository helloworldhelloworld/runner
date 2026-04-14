package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.PluginFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolExecutor 补充测试 — Reactive 路径、Context 路径、边界条件
 *
 * 覆盖：
 * - Reactive 单工具调用（正常 + 工具不存在）
 * - Reactive 多工具并行调用
 * - executeToolCallsAndCollect 过滤 PROGRESS/LOG
 * - 带 ToolExecutionContext 的 reactive 调用
 * - Reactive null ToolCall
 * - ToolExecutor 和 PluginFunction 混合注册定义
 * - clear 方法
 * - hasFunction 跨注册表查找
 */
@DisplayName("ToolExecutor - Reactive 和 Context 补充测试")
class ToolExecutorEdgeCaseTest {

    private ToolExecutor toolExecutor;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolExecutor = new ToolExecutor(toolRegistry);

        toolRegistry.register(new SimpleTool("add", args -> {
            int a = ((Number) args.get("a")).intValue();
            int b = ((Number) args.get("b")).intValue();
            return ToolResult.success(String.valueOf(a + b));
        }));

        toolRegistry.register(new SimpleTool("concat", args ->
                ToolResult.success(args.get("s1") + "" + args.get("s2"))));
    }

    // ==================== Reactive 单工具 ====================

    @Test
    @DisplayName("Reactive 单工具调用返回 COMPLETE chunk")
    void reactiveSingleToolReturnsComplete() {
        ToolCall call = new ToolCall("c1", "add", Map.of("a", 2, "b", 3));

        StepVerifier.create(toolExecutor.executeToolCallReactive(call))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertEquals("c1", chunk.getToolCallId());
                    assertEquals("5", chunk.getResult().getContent());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Reactive 工具不存在返回 ERROR chunk")
    void reactiveToolNotFoundReturnsError() {
        ToolCall call = new ToolCall("c1", "nonexistent", Map.of());

        StepVerifier.create(toolExecutor.executeToolCallReactive(call))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertTrue(chunk.getMessage().contains("not found"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Reactive null ToolCall 返回 error Flux")
    void reactiveNullToolCallReturnsError() {
        StepVerifier.create(toolExecutor.executeToolCallReactive(null))
                .verifyError(IllegalArgumentException.class);
    }

    // ==================== Reactive 多工具并行 ====================

    @Test
    @DisplayName("Reactive 多工具并行执行返回所有结果")
    void reactiveMultipleToolsReturnAllResults() {
        List<ToolCall> calls = List.of(
                new ToolCall("c1", "add", Map.of("a", 1, "b", 2)),
                new ToolCall("c2", "concat", Map.of("s1", "hello", "s2", "world"))
        );

        List<ToolResultChunk> results = toolExecutor.executeToolCallsReactive(calls)
                .collectList().block();

        assertNotNull(results);
        assertEquals(2, results.size());

        // 都应是 COMPLETE
        assertTrue(results.stream().allMatch(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE));
    }

    @Test
    @DisplayName("Reactive 空列表返回空 Flux")
    void reactiveEmptyListReturnsEmpty() {
        StepVerifier.create(toolExecutor.executeToolCallsReactive(List.of()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Reactive null 列表返回空 Flux")
    void reactiveNullListReturnsEmpty() {
        StepVerifier.create(toolExecutor.executeToolCallsReactive(null))
                .verifyComplete();
    }

    // ==================== executeToolCallsAndCollect ====================

    @Test
    @DisplayName("executeToolCallsAndCollect 只收集 COMPLETE 和 ERROR")
    void collectFiltersToFinalResults() {
        List<ToolCall> calls = List.of(
                new ToolCall("c1", "add", Map.of("a", 5, "b", 5)),
                new ToolCall("c2", "missing_tool", Map.of())
        );

        List<ToolResult> results = toolExecutor.executeToolCallsAndCollect(calls).block();
        assertNotNull(results);
        assertEquals(2, results.size());

        // c1 should succeed
        ToolResult addResult = results.stream()
                .filter(r -> "c1".equals(r.getToolUseId()))
                .findFirst().orElseThrow();
        assertEquals("10", addResult.getContent());
        assertFalse(addResult.isError());

        // c2 should be error
        ToolResult errorResult = results.stream()
                .filter(r -> "c2".equals(r.getToolUseId()))
                .findFirst().orElseThrow();
        assertTrue(errorResult.isError());
    }

    // ==================== Context 路径 ====================

    @Test
    @DisplayName("带 serverOnly context 的 reactive 调用正常工作")
    void reactiveWithServerOnlyContext() {
        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        ToolCall call = new ToolCall("c1", "add", Map.of("a", 10, "b", 20));

        StepVerifier.create(toolExecutor.executeToolCallReactive(call, ctx))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertEquals("30", chunk.getResult().getContent());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("带 context 的批量 reactive 调用")
    void reactiveMultipleWithContext() {
        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        List<ToolCall> calls = List.of(
                new ToolCall("c1", "add", Map.of("a", 1, "b", 1)),
                new ToolCall("c2", "add", Map.of("a", 2, "b", 2))
        );

        List<ToolResultChunk> results = toolExecutor.executeToolCallsReactive(calls, ctx)
                .collectList().block();
        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("带 context 的同步批量调用")
    void syncBatchWithContext() {
        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        List<ToolCall> calls = List.of(
                new ToolCall("c1", "add", Map.of("a", 3, "b", 7)),
                new ToolCall("c2", "concat", Map.of("s1", "a", "s2", "b"))
        );

        List<ToolResult> results = toolExecutor.executeToolCalls(calls, ctx);
        assertEquals(2, results.size());
        assertFalse(results.get(0).isError());
        assertFalse(results.get(1).isError());
    }

    @Test
    @DisplayName("带 context 的异步批量调用")
    void asyncBatchWithContext() {
        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        List<ToolCall> calls = List.of(
                new ToolCall("c1", "add", Map.of("a", 4, "b", 6))
        );

        List<ToolResult> results = toolExecutor.executeToolCallsAsync(calls, ctx).join();
        assertEquals(1, results.size());
        assertEquals("10", results.get(0).getContent());
    }

    // ==================== 混合注册和查询 ====================

    @Test
    @DisplayName("hasFunction 检测 ToolRegistry 和 legacy 注册")
    void hasFunctionCrossBothRegistries() {
        // Tool 在 ToolRegistry
        assertTrue(toolExecutor.hasFunction("add"));

        // PluginFunction 在 legacy registry
        toolExecutor.registerFunction("legacy_fn", new PluginFunction() {
            public String getName() { return "legacy_fn"; }
            public String getDescription() { return "Legacy"; }
            public List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() { return List.of(); }
            public FunctionResult execute(Map<String, Object> input) { return FunctionResult.success("ok"); }
            public Map<String, Object> toJsonSchema() { return Map.of("name", "legacy_fn"); }
        });

        assertTrue(toolExecutor.hasFunction("legacy_fn"));
        assertFalse(toolExecutor.hasFunction("nonexistent"));
    }

    @Test
    @DisplayName("getToolDefinitions 合并 Tool 和 PluginFunction 定义")
    void getToolDefinitionsMergesBothRegistries() {
        toolExecutor.registerFunction("legacy", new PluginFunction() {
            public String getName() { return "legacy"; }
            public String getDescription() { return "Legacy function"; }
            public List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() { return List.of(); }
            public FunctionResult execute(Map<String, Object> input) { return FunctionResult.success("ok"); }
            public Map<String, Object> toJsonSchema() { return Map.of("name", "legacy", "type", "function"); }
        });

        List<Map<String, Object>> defs = toolExecutor.getToolDefinitions();
        assertTrue(defs.size() >= 3, "Should have at least add + concat + legacy, got: " + defs.size());
    }

    @Test
    @DisplayName("getFunctionCount 计算 enabled tools + functions")
    void getFunctionCountIsCombined() {
        int baseCount = toolExecutor.getFunctionCount();
        assertEquals(2, baseCount); // add + concat

        toolExecutor.registerFunction("fn1", new PluginFunction() {
            public String getName() { return "fn1"; }
            public String getDescription() { return ""; }
            public List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() { return List.of(); }
            public FunctionResult execute(Map<String, Object> input) { return FunctionResult.success("ok"); }
            public Map<String, Object> toJsonSchema() { return Map.of(); }
        });

        assertEquals(3, toolExecutor.getFunctionCount());
    }

    @Test
    @DisplayName("clear 清理所有注册")
    void clearRemovesAll() {
        toolExecutor.registerFunction("temp", new PluginFunction() {
            public String getName() { return "temp"; }
            public String getDescription() { return ""; }
            public List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() { return List.of(); }
            public FunctionResult execute(Map<String, Object> input) { return FunctionResult.success("ok"); }
            public Map<String, Object> toJsonSchema() { return Map.of(); }
        });

        assertTrue(toolExecutor.getFunctionCount() > 0);
        toolExecutor.clear();
        assertEquals(0, toolExecutor.getFunctionCount());
    }

    @Test
    @DisplayName("executeToolCall 异常时返回错误结果而不抛出")
    void executeToolCallCatchesExceptions() {
        toolRegistry.register(new SimpleTool("boom", args -> {
            throw new RuntimeException("tool explosion");
        }));

        ToolCall call = new ToolCall("c1", "boom", Map.of());
        ToolResult result = toolExecutor.executeToolCall(call);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Execution failed"));
    }

    @Test
    @DisplayName("null ToolCall 抛 IllegalArgumentException")
    void nullToolCallThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                toolExecutor.executeToolCall((ToolCall) null));
    }

    @Test
    @DisplayName("toString 包含工具和函数数量")
    void toStringIncludesCounts() {
        String str = toolExecutor.toString();
        assertTrue(str.contains("ToolExecutor"));
        assertTrue(str.contains("2")); // 2 tools
    }

    @Test
    @DisplayName("registerFunction 拒绝 null name 和 null function")
    void registerFunctionValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                toolExecutor.registerFunction(null, new PluginFunction() {
                    public String getName() { return "x"; }
                    public String getDescription() { return ""; }
                    public List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() { return List.of(); }
                    public FunctionResult execute(Map<String, Object> input) { return FunctionResult.success("ok"); }
                    public Map<String, Object> toJsonSchema() { return Map.of(); }
                }));

        assertThrows(IllegalArgumentException.class, () ->
                toolExecutor.registerFunction("name", null));
    }

    @Test
    @DisplayName("registerTool 拒绝 null")
    void registerToolRejectsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                toolExecutor.registerTool(null));
    }

    // ==================== Helper ====================

    private static class SimpleTool implements Tool {
        private final String name;
        private final java.util.function.Function<Map<String, Object>, ToolResult> fn;

        SimpleTool(String name, java.util.function.Function<Map<String, Object>, ToolResult> fn) {
            this.name = name;
            this.fn = fn;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return name + " tool"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) { return fn.apply(args); }
    }
}
