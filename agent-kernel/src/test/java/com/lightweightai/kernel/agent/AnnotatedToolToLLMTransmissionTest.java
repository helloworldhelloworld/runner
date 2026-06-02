package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission-chain test: @ToolFunction annotation → AnnotatedToolScanner → ToolRegistry
 * → AgentLoop → LLMOptions.toolDefinitions → LLMProvider request body.
 *
 * This is the canonical shape for chain-payload tests per CLAUDE.md UT rule #3:
 * "Every producer–consumer pair needs ≥1 transmission test."
 *
 * The class of bugs this catches:
 * - AgentFactory built AgentLoop with default empty LLMOptions → toolDefinitions never injected
 * - ToolRegistry.getToolDefinitions() was never called on the Orchestrator path
 * - toolDefinitions present in registry but not wired to LLMOptions
 */
@DisplayName("Transmission chain: @ToolFunction → ToolRegistry → AgentLoop → LLMProvider.toolDefinitions")
class AnnotatedToolToLLMTransmissionTest {

    // ==================== Annotated tool class ====================

    static class WeatherTools {
        @ToolFunction(name = "get_weather", description = "Get current weather for a city")
        public String getWeather(
                @ToolParam(name = "city", description = "City name", required = true) String city
        ) {
            return "Sunny in " + city;
        }

        @ToolFunction(name = "get_forecast", description = "Get 5-day forecast")
        public String getForecast(
                @ToolParam(name = "city", description = "City name", required = true) String city,
                @ToolParam(name = "days", description = "Number of days") int days
        ) {
            return days + "-day forecast for " + city;
        }
    }

    // ==================== Tests ====================

    @Test
    @DisplayName("annotated tools registered via registerObject arrive as toolDefinitions in LLMProvider call")
    void annotatedToolsReachProvider() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("The weather is sunny.");
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new WeatherTools());

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new StubMemory())
                .toolRegistry(registry)
                .systemPrompt("You are a weather assistant.")
                .build();

        loop.run("What's the weather?", "session-1");

        LLMOptions capturedOptions = spy.lastOptions();
        assertNotNull(capturedOptions, "LLMOptions should have been captured");

        List<Map<String, Object>> toolDefs = capturedOptions.getToolDefinitions();
        assertNotNull(toolDefs, "toolDefinitions must not be null");
        assertEquals(2, toolDefs.size(), "both annotated tools should be present");

        List<String> toolNames = toolDefs.stream()
                .map(d -> (String) d.get("name"))
                .sorted()
                .toList();
        assertEquals(List.of("get_forecast", "get_weather"), toolNames);

        Map<String, Object> weatherDef = toolDefs.stream()
                .filter(d -> "get_weather".equals(d.get("name")))
                .findFirst().orElseThrow();
        assertEquals("Get current weather for a city", weatherDef.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) weatherDef.get("input_schema");
        assertNotNull(inputSchema, "input_schema must be present");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertTrue(properties.containsKey("city"), "city property must be in schema");
    }

    @Test
    @DisplayName("ManualToolProvider → ToolRegistry → AgentLoop → LLMProvider toolDefinitions chain")
    void manualProviderToolsReachProvider() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("Done.");
        ToolRegistry registry = new ToolRegistry();

        ManualToolProvider provider = new ManualToolProvider()
                .addAnnotated(new WeatherTools());
        provider.start(registry);

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new StubMemory())
                .toolRegistry(registry)
                .build();

        loop.run("Forecast?", "session-2");

        List<Map<String, Object>> toolDefs = spy.lastOptions().getToolDefinitions();
        assertEquals(2, toolDefs.size());
    }

    @Test
    @DisplayName("disabled tools are excluded from toolDefinitions sent to LLM")
    void disabledToolsExcluded() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("OK");
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new WeatherTools());
        registry.disable("get_forecast");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new StubMemory())
                .toolRegistry(registry)
                .build();

        loop.run("Weather?", "session-3");

        List<Map<String, Object>> toolDefs = spy.lastOptions().getToolDefinitions();
        assertEquals(1, toolDefs.size());
        assertEquals("get_weather", toolDefs.get(0).get("name"));
    }

    @Test
    @DisplayName("reactive path also carries toolDefinitions to provider")
    void reactivePathCarriesToolDefs() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("Reactive OK");
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new WeatherTools());

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new StubMemory())
                .toolRegistry(registry)
                .build();

        List<StreamEvent> events = loop.runReactive("Weather?", "session-4")
                .collectList().block();

        assertNotNull(events);

        List<Map<String, Object>> toolDefs = spy.lastOptions().getToolDefinitions();
        assertNotNull(toolDefs);
        assertEquals(2, toolDefs.size());
    }

    // ==================== Stub ====================

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
