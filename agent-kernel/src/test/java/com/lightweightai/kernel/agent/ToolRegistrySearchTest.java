package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolRegistry.search — keyword-based tool discovery")
class ToolRegistrySearchTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register(simpleTool("web_search", "Search the web for information"));
        registry.register(simpleTool("file_read", "Read a file from disk"));
        registry.register(simpleTool("shell_exec", "Execute a shell command"));
        registry.register(simpleTool("web_fetch", "Fetch content from a web URL"));
    }

    @Test
    @DisplayName("search by name returns matching tools")
    void searchByName() {
        List<Tool> results = registry.search("web");

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(t -> t.getName().equals("web_search")));
        assertTrue(results.stream().anyMatch(t -> t.getName().equals("web_fetch")));
    }

    @Test
    @DisplayName("search by description returns matching tools")
    void searchByDescription() {
        List<Tool> results = registry.search("shell");

        assertEquals(1, results.size());
        assertEquals("shell_exec", results.get(0).getName());
    }

    @Test
    @DisplayName("search is case-insensitive")
    void searchIsCaseInsensitive() {
        List<Tool> results = registry.search("SEARCH");

        assertEquals(1, results.size());
        assertEquals("web_search", results.get(0).getName());
    }

    @Test
    @DisplayName("null/blank keyword returns all enabled tools")
    void nullKeywordReturnsAll() {
        assertEquals(4, registry.search(null).size());
        assertEquals(4, registry.search("").size());
        assertEquals(4, registry.search("   ").size());
    }

    @Test
    @DisplayName("search excludes disabled tools")
    void searchExcludesDisabled() {
        registry.disable("web_search");

        List<Tool> results = registry.search("web");

        assertEquals(1, results.size());
        assertEquals("web_fetch", results.get(0).getName());
    }

    @Test
    @DisplayName("no matches returns empty list")
    void noMatchesReturnsEmpty() {
        List<Tool> results = registry.search("nonexistent_xyz");

        assertTrue(results.isEmpty());
    }

    private static Tool simpleTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
