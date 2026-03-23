package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.PluginFunction;
import com.lightweightai.kernel.plugin.FunctionParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolExecutor - 核心工具执行引擎")
class ToolExecutorTest {

    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ToolExecutor();
    }

    // ==================== 构造器测试 ====================

    @Test
    @DisplayName("默认构造器创建空执行器")
    void defaultConstructor() {
        assertEquals(0, executor.getFunctionCount());
        assertNotNull(executor.getToolRegistry());
    }

    @Test
    @DisplayName("使用已有 ToolRegistry 构造")
    void constructWithRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(createTool("t1"));
        ToolExecutor exec = new ToolExecutor(registry);
        assertTrue(exec.hasFunction("t1"));
    }

    @Test
    @DisplayName("传入 null ToolRegistry 时自动创建新实例")
    void constructWithNullRegistry() {
        ToolExecutor exec = new ToolExecutor(null);
        assertNotNull(exec.getToolRegistry());
    }

    // ==================== 注册测试 ====================

    @Test
    @DisplayName("注册 Tool 对象")
    void registerTool() {
        executor.registerTool(createTool("weather"));
        assertTrue(executor.hasFunction("weather"));
        assertEquals(1, executor.getFunctionCount());
    }

    @Test
    @DisplayName("注册 null Tool 抛出异常")
    void registerNullTool() {
        assertThrows(IllegalArgumentException.class, () -> executor.registerTool(null));
    }

    @Test
    @DisplayName("批量注册 Tool")
    void registerTools() {
        executor.registerTools(List.of(createTool("a"), createTool("b")));
        assertEquals(2, executor.getFunctionCount());
    }

    @Test
    @DisplayName("批量注册 null 列表不报错")
    void registerNullTools() {
        assertDoesNotThrow(() -> executor.registerTools(null));
    }

    @Test
    @DisplayName("注册 legacy PluginFunction")
    void registerLegacyFunction() {
        executor.registerFunction("calc", createPluginFunction("calc"));
        assertTrue(executor.hasFunction("calc"));
        assertEquals(1, executor.getFunctionCount());
    }

    @Test
    @DisplayName("注册 null 函数名抛异常")
    void registerNullFunctionName() {
        assertThrows(IllegalArgumentException.class,
            () -> executor.registerFunction(null, createPluginFunction("x")));
    }

    @Test
    @DisplayName("注册空函数名抛异常")
    void registerEmptyFunctionName() {
        assertThrows(IllegalArgumentException.class,
            () -> executor.registerFunction("  ", createPluginFunction("x")));
    }

    @Test
    @DisplayName("注册 null PluginFunction 抛异常")
    void registerNullPluginFunction() {
        assertThrows(IllegalArgumentException.class,
            () -> executor.registerFunction("fn", null));
    }

    @Test
    @DisplayName("批量注册 legacy 函数")
    void registerFunctionsMap() {
        executor.registerFunctions(Map.of("a", createPluginFunction("a"), "b", createPluginFunction("b")));
        assertEquals(2, executor.getFunctionCount());
    }

    @Test
    @DisplayName("批量注册 null 函数 map 不报错")
    void registerNullFunctionsMap() {
        assertDoesNotThrow(() -> executor.registerFunctions(null));
    }

    // ==================== 执行测试 - 关键路径 ====================

    @Test
    @DisplayName("通过 ToolRegistry 执行 Tool")
    void executeViaToolRegistry() {
        executor.registerTool(createTool("echo"));
        ToolCall call = new ToolCall("tc1", "echo", Map.of("input", "hello"));

        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError());
        assertEquals("tc1", result.getToolUseId());
        assertTrue(result.getContent().contains("hello"));
    }

    @Test
    @DisplayName("Tool 不存在时 fallback 到 legacy 函数")
    void fallbackToLegacyFunction() {
        executor.registerFunction("legacy_fn", createPluginFunction("legacy_fn"));
        ToolCall call = new ToolCall("tc2", "legacy_fn", Map.of());

        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError());
        assertEquals("tc2", result.getToolUseId());
    }

    @Test
    @DisplayName("工具不存在时返回错误")
    void toolNotFound() {
        ToolCall call = new ToolCall("tc3", "nonexistent", Map.of());

        ToolResult result = executor.executeToolCall(call);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Tool not found"));
    }

    @Test
    @DisplayName("执行 null ToolCall 抛异常")
    void executeNullToolCall() {
        assertThrows(IllegalArgumentException.class, () -> executor.executeToolCall((ToolCall) null));
    }

    @Test
    @DisplayName("Tool 执行异常时返回错误结果")
    void toolExecutionException() {
        Tool failingTool = new Tool() {
            public String getName() { return "fail"; }
            public String getDescription() { return "fails"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                throw new RuntimeException("boom");
            }
        };
        executor.registerTool(failingTool);
        ToolCall call = new ToolCall("tc4", "fail", Map.of());

        ToolResult result = executor.executeToolCall(call);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("boom"));
    }

    @Test
    @DisplayName("legacy 函数返回错误")
    void legacyFunctionError() {
        PluginFunction fn = new PluginFunction() {
            public String getName() { return "err_fn"; }
            public String getDescription() { return ""; }
            public List<FunctionParameter> getParameters() { return List.of(); }
            public FunctionResult execute(Map<String, Object> args) {
                return FunctionResult.error("something wrong");
            }
            public Map<String, Object> toJsonSchema() { return Map.of(); }
        };
        executor.registerFunction("err_fn", fn);
        ToolCall call = new ToolCall("tc5", "err_fn", Map.of());

        ToolResult result = executor.executeToolCall(call);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("something wrong"));
    }

    // ==================== 批量执行 ====================

    @Test
    @DisplayName("批量顺序执行")
    void executeMultipleToolCalls() {
        executor.registerTool(createTool("t1"));
        executor.registerTool(createTool("t2"));

        List<ToolResult> results = executor.executeToolCalls(List.of(
            new ToolCall("a", "t1", Map.of("input", "x")),
            new ToolCall("b", "t2", Map.of("input", "y"))
        ));

        assertEquals(2, results.size());
        assertFalse(results.get(0).isError());
        assertFalse(results.get(1).isError());
    }

    @Test
    @DisplayName("空列表返回空结果")
    void executeEmptyList() {
        assertEquals(List.of(), executor.executeToolCalls(List.of()));
        assertEquals(List.of(), executor.executeToolCalls((List<ToolCall>) null));
    }

    @Test
    @DisplayName("异步批量执行")
    void executeToolCallsAsync() throws Exception {
        executor.registerTool(createTool("t1"));
        executor.registerTool(createTool("t2"));

        List<ToolResult> results = executor.executeToolCallsAsync(List.of(
            new ToolCall("a", "t1", Map.of("input", "x")),
            new ToolCall("b", "t2", Map.of("input", "y"))
        )).get();

        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("异步空列表返回空结果")
    void executeAsyncEmptyList() throws Exception {
        assertEquals(List.of(), executor.executeToolCallsAsync((List<ToolCall>) null).get());
    }

    // ==================== 带 Context 的执行 ====================

    @Test
    @DisplayName("context 为 null 时走普通执行")
    void executeWithNullContext() {
        executor.registerTool(createTool("t1"));
        ToolCall call = new ToolCall("tc1", "t1", Map.of("input", "hi"));

        ToolResult result = executor.executeToolCall(call, null);

        assertFalse(result.isError());
    }

    @Test
    @DisplayName("serverOnly context 走普通执行")
    void executeWithServerOnlyContext() {
        executor.registerTool(createTool("t1"));
        ToolCall call = new ToolCall("tc1", "t1", Map.of("input", "hi"));

        ToolResult result = executor.executeToolCall(call, ToolExecutionContext.serverOnly());

        assertFalse(result.isError());
    }

    @Test
    @DisplayName("带 context 的批量执行")
    void executeToolCallsWithContext() {
        executor.registerTool(createTool("t1"));
        List<ToolResult> results = executor.executeToolCalls(
            List.of(new ToolCall("a", "t1", Map.of("input", "x"))),
            ToolExecutionContext.serverOnly()
        );
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("带 context 的空列表")
    void executeEmptyListWithContext() {
        assertEquals(List.of(), executor.executeToolCalls(null, ToolExecutionContext.serverOnly()));
    }

    @Test
    @DisplayName("带 context 的异步批量执行")
    void executeToolCallsAsyncWithContext() throws Exception {
        executor.registerTool(createTool("t1"));
        List<ToolResult> results = executor.executeToolCallsAsync(
            List.of(new ToolCall("a", "t1", Map.of("input", "x"))),
            ToolExecutionContext.serverOnly()
        ).get();
        assertEquals(1, results.size());
    }

    // ==================== Reactive 执行 ====================

    @Test
    @DisplayName("reactive 单工具执行")
    void executeToolCallReactive() {
        executor.registerTool(createTool("t1"));
        ToolCall call = new ToolCall("tc1", "t1", Map.of("input", "hello"));

        StepVerifier.create(executor.executeToolCallReactive(call))
            .assertNext(chunk -> {
                assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                assertEquals("tc1", chunk.getToolCallId());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("reactive 执行未注册工具返回错误")
    void executeReactiveToolNotFound() {
        ToolCall call = new ToolCall("tc1", "missing", Map.of());

        StepVerifier.create(executor.executeToolCallReactive(call))
            .assertNext(chunk -> {
                assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                assertTrue(chunk.getMessage().contains("Tool not found"));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("reactive null ToolCall 返回错误流")
    void executeReactiveNullToolCall() {
        StepVerifier.create(executor.executeToolCallReactive(null))
            .verifyError(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reactive 多工具并行执行")
    void executeToolCallsReactive() {
        executor.registerTool(createTool("t1"));
        executor.registerTool(createTool("t2"));

        StepVerifier.create(executor.executeToolCallsReactive(List.of(
                new ToolCall("a", "t1", Map.of("input", "x")),
                new ToolCall("b", "t2", Map.of("input", "y"))
            )))
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    @DisplayName("reactive 空列表返回空流")
    void executeReactiveEmptyList() {
        StepVerifier.create(executor.executeToolCallsReactive(null))
            .verifyComplete();
    }

    @Test
    @DisplayName("收集所有最终结果")
    void executeToolCallsAndCollect() {
        executor.registerTool(createTool("t1"));
        executor.registerTool(createTool("t2"));

        StepVerifier.create(executor.executeToolCallsAndCollect(List.of(
                new ToolCall("a", "t1", Map.of("input", "x")),
                new ToolCall("b", "t2", Map.of("input", "y"))
            )))
            .assertNext(results -> assertEquals(2, results.size()))
            .verifyComplete();
    }

    // ==================== 查询与清理 ====================

    @Test
    @DisplayName("hasFunction 查询两个注册表")
    void hasFunction() {
        executor.registerTool(createTool("tool1"));
        executor.registerFunction("fn1", createPluginFunction("fn1"));

        assertTrue(executor.hasFunction("tool1"));
        assertTrue(executor.hasFunction("fn1"));
        assertFalse(executor.hasFunction("missing"));
    }

    @Test
    @DisplayName("getFunctionCount 合并计数")
    void getFunctionCount() {
        executor.registerTool(createTool("t1"));
        executor.registerFunction("fn1", createPluginFunction("fn1"));
        assertEquals(2, executor.getFunctionCount());
    }

    @Test
    @DisplayName("getToolDefinitions 合并两个注册表")
    void getToolDefinitions() {
        executor.registerTool(createTool("t1"));
        executor.registerFunction("fn1", createPluginFunction("fn1"));

        List<Map<String, Object>> defs = executor.getToolDefinitions();
        assertTrue(defs.size() >= 2);
    }

    @Test
    @DisplayName("clear 清空所有注册")
    void clear() {
        executor.registerTool(createTool("t1"));
        executor.registerFunction("fn1", createPluginFunction("fn1"));

        executor.clear();
        assertEquals(0, executor.getFunctionCount());
    }

    @Test
    @DisplayName("toString 输出")
    void toStringOutput() {
        String str = executor.toString();
        assertTrue(str.contains("ToolExecutor"));
    }

    // ==================== 辅助方法 ====================

    private Tool createTool(String name) {
        return new Tool() {
            public String getName() { return name; }
            public String getDescription() { return "Tool " + name; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("Result from " + name + ": " + args);
            }
        };
    }

    private PluginFunction createPluginFunction(String name) {
        return new PluginFunction() {
            public String getName() { return name; }
            public String getDescription() { return "Function " + name; }
            public List<FunctionParameter> getParameters() { return List.of(); }
            public FunctionResult execute(Map<String, Object> args) {
                return FunctionResult.success("OK");
            }
            public Map<String, Object> toJsonSchema() {
                return Map.of("name", name, "description", "Function " + name,
                    "parameters", Map.of("type", "object", "properties", Map.of()));
            }
        };
    }
}
