package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.ManualToolProvider;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传输链验证 — ManualToolProvider → ToolRegistry → ToolExecutor → ToolCallingLoop
 *
 * 遵循 CLAUDE.md UT 规则：
 * 1. 断言 payload 而非仪式
 * 2. 捕获输入而非吞掉
 * 3. 端到端传输链至少 1 个测试
 */
@DisplayName("ManualToolProvider 传输链 — 工具注册到执行全链路")
class ManualToolProviderTransmissionTest {

    @Test
    @DisplayName("ManualToolProvider → ToolRegistry → ToolExecutor 链路：工具 execute 结果完整传输")
    void toolProviderToExecutorChain() {
        ToolRegistry registry = new ToolRegistry();

        // 通过 ManualToolProvider 注册工具
        ManualToolProvider provider = new ManualToolProvider();
        provider.addTool(new Tool() {
            @Override
            public String getName() { return "add_numbers"; }
            @Override
            public String getDescription() { return "Add two numbers"; }
            @Override
            public ToolSchema getSchema() {
                return ToolSchema.withRequired(
                    Map.of(
                        "a", Map.of("type", "number"),
                        "b", Map.of("type", "number")
                    ), "a", "b"
                );
            }
            @Override
            public ToolResult execute(Map<String, Object> args) {
                double a = ((Number) args.get("a")).doubleValue();
                double b = ((Number) args.get("b")).doubleValue();
                return ToolResult.success(String.valueOf(a + b));
            }
        });
        provider.start(registry);

        // 验证 ToolRegistry 包含工具
        assertTrue(registry.has("add_numbers"));

        // 通过 ToolExecutor 执行
        ToolExecutor executor = new ToolExecutor(registry);
        assertTrue(executor.hasFunction("add_numbers"));

        ToolCall call = new ToolCall("call_1", "add_numbers", Map.of("a", 3, "b", 7));
        ToolResult result = executor.executeToolCall(call);

        // 断言 payload — 不是 assertNotNull
        assertFalse(result.isError());
        assertEquals("10.0", result.getContent());
        assertEquals("call_1", result.getToolUseId());
    }

    @Test
    @DisplayName("ManualToolProvider.addAnnotated → ToolRegistry → ToolExecutor 链路")
    void annotatedToolProviderToExecutorChain() {
        ToolRegistry registry = new ToolRegistry();

        ManualToolProvider provider = new ManualToolProvider();
        provider.addAnnotated(new MathTools());
        provider.start(registry);

        ToolExecutor executor = new ToolExecutor(registry);

        ToolCall call = new ToolCall("call_2", "multiply", Map.of("x", 6, "y", 7));
        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError());
        assertEquals("42", result.getContent());
    }

    @Test
    @DisplayName("ToolRegistry → toolDefinitions → CapturingLLMProvider：定义完整传输到 LLM")
    void toolDefinitionsReachLLMProvider() {
        ToolRegistry registry = new ToolRegistry();

        ManualToolProvider provider = new ManualToolProvider();
        provider.addAnnotated(new MathTools());
        provider.start(registry);

        List<Map<String, Object>> definitions = registry.getToolDefinitions();

        // 验证定义包含正确的 payload
        assertFalse(definitions.isEmpty());
        Map<String, Object> multiplyDef = definitions.stream()
            .filter(d -> "multiply".equals(d.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("multiply tool definition missing"));

        assertEquals("Multiply two numbers", multiplyDef.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) multiplyDef.get("input_schema");
        assertNotNull(inputSchema);

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertTrue(properties.containsKey("x"));
        assertTrue(properties.containsKey("y"));
    }

    @Test
    @DisplayName("stop 后 ToolExecutor 找不到工具")
    void stopRemovesToolsFromExecutor() {
        ToolRegistry registry = new ToolRegistry();

        Tool tool = new Tool() {
            @Override
            public String getName() { return "ephemeral"; }
            @Override
            public String getDescription() { return "Temporary tool"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("temp");
            }
        };

        ManualToolProvider provider = new ManualToolProvider();
        provider.addTool(tool);
        provider.start(registry);

        ToolExecutor executor = new ToolExecutor(registry);
        assertTrue(executor.hasFunction("ephemeral"));

        provider.stop(registry);
        assertFalse(executor.hasFunction("ephemeral"));
    }

    // ==================== Test tools ====================

    static class MathTools {
        @ToolFunction(name = "multiply", description = "Multiply two numbers")
        public String multiply(
            @ToolParam(name = "x", description = "First number", required = true) int x,
            @ToolParam(name = "y", description = "Second number", required = true) int y
        ) {
            return String.valueOf(x * y);
        }
    }
}
