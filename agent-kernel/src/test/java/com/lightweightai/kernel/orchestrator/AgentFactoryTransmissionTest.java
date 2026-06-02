package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission-chain test: AgentProfile → AgentFactory → AgentLoop → LLMProvider.
 *
 * Verifies that tools registered in the global ToolRegistry, when filtered through
 * ScopedToolRegistry (via AgentProfile allow/deny lists), correctly arrive as
 * toolDefinitions in the LLMProvider call.
 *
 * This is the factory-level chain test per CLAUDE.md UT rule #3.
 * Catches the historical bug: AgentFactory built AgentLoop with default empty LLMOptions,
 * toolDefinitions never injected, so Claude/OpenRouter never saw any tools on the
 * Orchestrator path.
 */
@DisplayName("AgentFactory transmission: Profile → ScopedRegistry → AgentLoop → LLMProvider.toolDefinitions")
class AgentFactoryTransmissionTest {

    // ==================== Annotated tools ====================

    static class SearchTools {
        @ToolFunction(name = "web_search", description = "Search the web")
        public String webSearch(@ToolParam(name = "q", required = true) String q) {
            return "results for " + q;
        }

        @ToolFunction(name = "file_search", description = "Search local files")
        public String fileSearch(@ToolParam(name = "path", required = true) String path) {
            return "found in " + path;
        }

        @ToolFunction(name = "db_query", description = "Query database")
        public String dbQuery(@ToolParam(name = "sql", required = true) String sql) {
            return "rows from " + sql;
        }
    }

    // ==================== Tests ====================

    @Test
    @DisplayName("factory-created AgentLoop carries ALL global tools to LLM when no allow/deny list")
    void allToolsReachLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");
        ToolRegistry globalRegistry = new ToolRegistry();
        globalRegistry.registerObject(new SearchTools());

        AgentProfile profile = AgentProfile.builder()
                .agentId("researcher")
                .systemPrompt("You are a researcher")
                .build();

        AgentFactory factory = new AgentFactory(spy, new StubMemory(), globalRegistry);
        AgentLoop loop = factory.create(profile);

        loop.run("find something", "sess-1");

        List<Map<String, Object>> toolDefs = spy.lastOptions().getToolDefinitions();
        assertNotNull(toolDefs, "toolDefinitions must reach LLMProvider");

        List<String> names = toolDefs.stream()
                .map(d -> (String) d.get("name"))
                .sorted()
                .toList();
        assertEquals(List.of("db_query", "file_search", "web_search"), names,
                "all 3 global tools must be visible");
    }

    @Test
    @DisplayName("factory-created AgentLoop filters tools by allow list")
    void allowListFilters() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");
        ToolRegistry globalRegistry = new ToolRegistry();
        globalRegistry.registerObject(new SearchTools());

        AgentProfile profile = AgentProfile.builder()
                .agentId("web-only")
                .systemPrompt("You only do web search")
                .toolAllowList(Set.of("web_search"))
                .build();

        AgentFactory factory = new AgentFactory(spy, new StubMemory(), globalRegistry);
        AgentLoop loop = factory.create(profile);

        loop.run("search", "sess-2");

        List<Map<String, Object>> toolDefs = spy.lastOptions().getToolDefinitions();
        assertEquals(1, toolDefs.size());
        assertEquals("web_search", toolDefs.get(0).get("name"));
    }

    @Test
    @DisplayName("factory-created AgentLoop filters tools by deny list")
    void denyListFilters() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");
        ToolRegistry globalRegistry = new ToolRegistry();
        globalRegistry.registerObject(new SearchTools());

        AgentProfile profile = AgentProfile.builder()
                .agentId("no-db")
                .systemPrompt("No database access")
                .toolDenyList(Set.of("db_query"))
                .build();

        AgentFactory factory = new AgentFactory(spy, new StubMemory(), globalRegistry);
        AgentLoop loop = factory.create(profile);

        loop.run("search", "sess-3");

        List<Map<String, Object>> toolDefs = spy.lastOptions().getToolDefinitions();
        List<String> names = toolDefs.stream()
                .map(d -> (String) d.get("name"))
                .sorted()
                .toList();
        assertEquals(List.of("file_search", "web_search"), names,
                "db_query must be denied");
    }

    @Test
    @DisplayName("system prompt from AgentProfile reaches the LLM messages")
    void systemPromptTransmission() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        ToolRegistry globalRegistry = new ToolRegistry();

        AgentProfile profile = AgentProfile.builder()
                .agentId("greeter")
                .systemPrompt("You are a friendly greeter")
                .build();

        AgentFactory factory = new AgentFactory(spy, new StubMemory(), globalRegistry);
        AgentLoop loop = factory.create(profile);

        loop.run("hello", "sess-4");

        var messages = spy.lastMessages();
        assertNotNull(messages);
        assertFalse(messages.isEmpty());

        String systemContent = messages.get(0).getTextContent();
        assertTrue(systemContent.contains("You are a friendly greeter"),
                "system prompt from profile must reach LLM messages");
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
