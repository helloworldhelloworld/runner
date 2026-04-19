package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.FunctionParameter;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.PluginFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMOptions - LLM 请求选项构建器")
class LLMOptionsTest {

    /** Minimal inline mock of PluginFunction */
    private static class StubPluginFunction implements PluginFunction {
        private final String name;

        StubPluginFunction(String name) {
            this.name = name;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return "stub"; }
        @Override public List<FunctionParameter> getParameters() { return List.of(); }
        @Override public FunctionResult execute(Map<String, Object> arguments) { return FunctionResult.success("ok"); }
        @Override public Map<String, Object> toJsonSchema() { return Map.of(); }
    }

    @Test
    @DisplayName("构建包含所有字段的 LLMOptions")
    void buildWithAllFields() {
        List<PluginFunction> tools = List.of(new StubPluginFunction("tool1"));
        List<Map<String, Object>> toolDefs = List.of(Map.of("name", "def1"));

        LLMOptions options = LLMOptions.builder()
            .temperature(0.7)
            .maxTokens(1024)
            .tools(tools)
            .toolDefinitions(toolDefs)
            .systemPrompt("You are helpful.")
            .build();

        assertEquals(0.7, options.getTemperature());
        assertEquals(1024, options.getMaxTokens());
        assertEquals(1, options.getTools().size());
        assertEquals("tool1", options.getTools().get(0).getName());
        assertEquals(1, options.getToolDefinitions().size());
        assertEquals("def1", options.getToolDefinitions().get(0).get("name"));
        assertEquals("You are helpful.", options.getSystemPrompt());
    }

    @Test
    @DisplayName("默认值：空列表和 null")
    void buildWithDefaults() {
        LLMOptions options = LLMOptions.builder().build();

        assertNull(options.getTemperature());
        assertNull(options.getMaxTokens());
        assertNull(options.getSystemPrompt());
        assertNotNull(options.getTools());
        assertTrue(options.getTools().isEmpty());
        assertNotNull(options.getToolDefinitions());
        assertTrue(options.getToolDefinitions().isEmpty());
    }

    @Test
    @DisplayName("toolDefinitions 防御性拷贝 - 修改原始列表不影响 Options")
    void toolDefinitionsDefensiveCopy() {
        List<Map<String, Object>> original = new ArrayList<>();
        original.add(Map.of("name", "def1"));

        LLMOptions options = LLMOptions.builder()
            .toolDefinitions(original)
            .build();

        original.add(Map.of("name", "def2"));

        assertEquals(1, options.getToolDefinitions().size());
    }

    @Test
    @DisplayName("tools 防御性拷贝 - 修改原始列表不影响 Options")
    void toolsDefensiveCopy() {
        List<PluginFunction> original = new ArrayList<>();
        original.add(new StubPluginFunction("t1"));

        LLMOptions options = LLMOptions.builder()
            .tools(original)
            .build();

        original.add(new StubPluginFunction("t2"));

        assertEquals(1, options.getTools().size());
    }

    @Test
    @DisplayName("Builder.from(existing) 复制全部字段")
    void builderFromCopiesAllFields() {
        List<PluginFunction> tools = List.of(new StubPluginFunction("t"));
        List<Map<String, Object>> toolDefs = List.of(Map.of("name", "d"));

        LLMOptions original = LLMOptions.builder()
            .temperature(0.5)
            .maxTokens(256)
            .maxBudgetTokens(4096)
            .tools(tools)
            .toolDefinitions(toolDefs)
            .systemPrompt("sys")
            .build();

        LLMOptions copy = LLMOptions.builder().from(original).build();

        assertEquals(0.5, copy.getTemperature());
        assertEquals(256, copy.getMaxTokens());
        assertEquals(4096, copy.getMaxBudgetTokens());
        assertEquals(1, copy.getTools().size());
        assertEquals("t", copy.getTools().get(0).getName());
        assertEquals(1, copy.getToolDefinitions().size());
        assertEquals("d", copy.getToolDefinitions().get(0).get("name"));
        assertEquals("sys", copy.getSystemPrompt());
    }

    @Test
    @DisplayName("Builder.from(existing) 后续 setter 覆盖旧值")
    void builderFromAllowsOverride() {
        LLMOptions original = LLMOptions.builder()
            .toolDefinitions(List.of(Map.of("name", "old")))
            .build();

        LLMOptions copy = LLMOptions.builder()
            .from(original)
            .toolDefinitions(List.of(Map.of("name", "new")))
            .build();

        assertEquals(1, copy.getToolDefinitions().size());
        assertEquals("new", copy.getToolDefinitions().get(0).get("name"));
    }
}
