package com.lightweightai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolScanner;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.tools.math.MathTools;
import com.lightweightai.tools.time.TimeTools;
import com.lightweightai.tools.web.WebTools;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test: ToolRegistry -> ToolExecutor end-to-end flow.
 *
 * Verifies that annotated Tool objects registered in ToolRegistry are actually
 * found and executed by ToolExecutor when processing LLM tool calls.
 */
@DisplayName("ToolRegistry + ToolExecutor Integration")
class ToolRegistryIntegrationTest {

    private ToolRegistry registry;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.registerObject(new MathTools());
        registry.registerObject(new TimeTools());

        executor = new ToolExecutor(registry);
    }

    @Test
    @DisplayName("ToolExecutor uses ToolRegistry to find and execute add tool")
    void shouldExecuteAddToolViaRegistry() {
        ToolCall call = new ToolCall("call_1", "add", Map.of("a", 10, "b", 20));
        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError(), "Should succeed: " + result.getContent());
        assertEquals("30", result.getContent());
    }

    @Test
    @DisplayName("ToolExecutor uses ToolRegistry to find and execute multiply tool")
    void shouldExecuteMultiplyToolViaRegistry() {
        ToolCall call = new ToolCall("call_2", "multiply", Map.of("a", 6, "b", 7));
        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError(), "Should succeed: " + result.getContent());
        assertEquals("42", result.getContent());
    }

    @Test
    @DisplayName("ToolExecutor uses ToolRegistry to find and execute get_time tool")
    void shouldExecuteGetTimeToolViaRegistry() {
        ToolCall call = new ToolCall("call_3", "get_time", Map.of());
        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError(), "Should succeed: " + result.getContent());
        assertNotNull(result.getContent());
        assertFalse(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("ToolExecutor returns error for unregistered tool")
    void shouldReturnErrorForUnknownTool() {
        ToolCall call = new ToolCall("call_4", "nonexistent_tool", Map.of());
        ToolResult result = executor.executeToolCall(call);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Tool not found"));
    }

    @Test
    @DisplayName("Disabled tools are not executed")
    void shouldNotExecuteDisabledTool() {
        registry.disable("add");
        ToolCall call = new ToolCall("call_5", "add", Map.of("a", 1, "b", 2));
        ToolResult result = executor.executeToolCall(call);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Tool not found"));
    }

    @Test
    @DisplayName("Re-enabled tools can be executed again")
    void shouldExecuteReenabledTool() {
        registry.disable("add");
        registry.enable("add");

        ToolCall call = new ToolCall("call_6", "add", Map.of("a", 5, "b", 5));
        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError());
        assertEquals("10", result.getContent());
    }

    @Test
    @DisplayName("ToolExecutor merges definitions from ToolRegistry")
    void shouldMergeToolDefinitions() {
        List<Map<String, Object>> definitions = executor.getToolDefinitions();

        // MathTools: add, multiply, divide + TimeTools: get_time = 4
        assertEquals(4, definitions.size());

        List<String> names = definitions.stream()
            .map(d -> (String) d.get("name"))
            .toList();
        assertTrue(names.contains("add"));
        assertTrue(names.contains("multiply"));
        assertTrue(names.contains("get_time"));
    }

    @Test
    @DisplayName("ToolExecutor reports correct total count from registry")
    void shouldReportCorrectFunctionCount() {
        assertEquals(4, executor.getFunctionCount());

        registry.disable("add");
        assertEquals(3, executor.getFunctionCount());
    }

    @Test
    @DisplayName("ToolExecutor.hasFunction checks ToolRegistry")
    void shouldCheckRegistryForHasFunction() {
        assertTrue(executor.hasFunction("add"));
        assertTrue(executor.hasFunction("multiply"));
        assertTrue(executor.hasFunction("get_time"));
        assertFalse(executor.hasFunction("nonexistent"));
    }

    @Test
    @DisplayName("ToolRegistry getByCategory works with annotated tools")
    void shouldGetToolsByCategory() {
        List<Tool> mathTools = registry.getByCategory("math");
        assertEquals(3, mathTools.size()); // add, multiply, divide

        List<Tool> utilityTools = registry.getByCategory("utility");
        assertEquals(1, utilityTools.size());
        assertEquals("get_time", utilityTools.get(0).getName());
    }

    @Test
    @DisplayName("ToolRegistry getByTag works with annotated tools")
    void shouldGetToolsByTag() {
        List<Tool> calcTools = registry.getByTag("calculation");
        assertEquals(3, calcTools.size()); // add, multiply, divide

        List<Tool> timeTools = registry.getByTag("time");
        assertEquals(1, timeTools.size());
    }

    @Test
    @DisplayName("Execute multiple tool calls sequentially via registry")
    void shouldExecuteMultipleToolCallsSequentially() {
        List<ToolCall> calls = List.of(
            new ToolCall("call_a", "add", Map.of("a", 1, "b", 2)),
            new ToolCall("call_b", "multiply", Map.of("a", 3, "b", 4))
        );

        List<ToolResult> results = executor.executeToolCalls(calls);

        assertEquals(2, results.size());
        assertFalse(results.get(0).isError());
        assertEquals("3", results.get(0).getContent());
        assertFalse(results.get(1).isError());
        assertEquals("12", results.get(1).getContent());
    }

    @Test
    @DisplayName("Dynamically register annotated tool object and execute")
    void shouldRegisterAnnotatedObjectAndExecute() {
        executor.registerObject(new WebTools());

        assertTrue(executor.hasFunction("web_search"));

        ToolCall call = new ToolCall("call_ws", "web_search",
            Map.of("query", "test query", "maxResults", 3));
        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("test query"));
    }

    @Test
    @DisplayName("WebTools executes in mock mode")
    void shouldExecuteWebToolsInMockMode() {
        registry.registerObject(new WebTools());

        ToolCall call = new ToolCall("call_ws", "web_search",
            Map.of("query", "test query", "maxResults", 3));
        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("test query"));
    }

    // ==================== SPI 自动扫描测试 ====================

    @Test
    @DisplayName("ToolScanner discovers all tools from agent-tools via ToolSource SPI")
    void shouldDiscoverAllToolsViaSPI() {
        List<Tool> tools = ToolScanner.scan();

        assertFalse(tools.isEmpty(), "Should discover tools via SPI");

        List<String> names = tools.stream().map(Tool::getName).toList();
        assertTrue(names.contains("add"), "Should discover add tool");
        assertTrue(names.contains("multiply"), "Should discover multiply tool");
        assertTrue(names.contains("get_time"), "Should discover get_time tool");
        assertTrue(names.contains("web_search"), "Should discover web_search tool");
    }

    @Test
    @DisplayName("ToolRegistry.scanAndRegister() auto-discovers and registers tools")
    void shouldAutoScanAndRegister() {
        ToolRegistry autoRegistry = new ToolRegistry();
        int count = autoRegistry.scanAndRegister();

        assertTrue(count >= 4, "Should register at least 4 tools: " + count);
        assertTrue(autoRegistry.has("add"));
        assertTrue(autoRegistry.has("multiply"));
        assertTrue(autoRegistry.has("get_time"));
        assertTrue(autoRegistry.has("web_search"));
    }

    @Test
    @DisplayName("ToolScanner with filter excludes specific tools")
    void shouldScanWithFilter() {
        ToolRegistry filteredRegistry = new ToolRegistry();
        int count = filteredRegistry.scanAndRegister(
            tool -> !tool.getName().equals("web_search")
        );

        assertTrue(count >= 3);
        assertTrue(filteredRegistry.has("add"));
        assertTrue(filteredRegistry.has("multiply"));
        assertTrue(filteredRegistry.has("get_time"));
        assertFalse(filteredRegistry.has("web_search"), "web_search should be filtered out");
    }

    @Test
    @DisplayName("ToolScanner.scanByCategory finds math tools from SPI")
    void shouldScanByCategoryViaSPI() {
        List<Tool> mathTools = ToolScanner.scanByCategory("math");

        assertTrue(mathTools.size() >= 2, "Should find at least 2 math tools");
        assertTrue(mathTools.stream().anyMatch(t -> "add".equals(t.getName())));
        assertTrue(mathTools.stream().anyMatch(t -> "multiply".equals(t.getName())));
    }

    @Test
    @DisplayName("ToolScanner.scanByTag finds tools by tag from SPI")
    void shouldScanByTagViaSPI() {
        List<Tool> calcTools = ToolScanner.scanByTag("calculation");

        assertTrue(calcTools.size() >= 2, "Should find at least 2 calculation tools");
    }

    @Test
    @DisplayName("Auto-scanned tools are fully executable via ToolExecutor")
    void shouldExecuteAutoScannedToolsViaExecutor() {
        ToolRegistry autoRegistry = new ToolRegistry();
        autoRegistry.scanAndRegister();
        ToolExecutor autoExecutor = new ToolExecutor(autoRegistry);

        // Execute add
        ToolCall addCall = new ToolCall("spi_1", "add", Map.of("a", 100, "b", 200));
        ToolResult addResult = autoExecutor.executeToolCall(addCall);
        assertFalse(addResult.isError());
        assertEquals("300", addResult.getContent());

        // Execute multiply
        ToolCall mulCall = new ToolCall("spi_2", "multiply", Map.of("a", 8, "b", 9));
        ToolResult mulResult = autoExecutor.executeToolCall(mulCall);
        assertFalse(mulResult.isError());
        assertEquals("72", mulResult.getContent());

        // Execute get_time
        ToolCall timeCall = new ToolCall("spi_3", "get_time", Map.of());
        ToolResult timeResult = autoExecutor.executeToolCall(timeCall);
        assertFalse(timeResult.isError());
        assertFalse(timeResult.getContent().isEmpty());
    }
}
