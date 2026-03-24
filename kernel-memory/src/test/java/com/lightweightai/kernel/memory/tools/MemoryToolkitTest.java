package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryToolkit - Tool orchestration and dispatch")
class MemoryToolkitTest {

    @TempDir
    Path tempDir;

    private FileMemoryManager memoryManager;
    private MemoryToolkit toolkit;

    @BeforeEach
    void setUp() {
        Path agentRoot = tempDir.resolve("test-agent");
        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
        memoryManager = new FileMemoryManager(agentRoot, "test-agent", embeddingProvider);
        toolkit = new MemoryToolkit(memoryManager);
    }

    @AfterEach
    void tearDown() {
        if (memoryManager != null) {
            memoryManager.close();
        }
    }

    // ==================== Execute search_memory tool ====================

    @Test
    @DisplayName("Execute search_memory tool returns MemorySearchResult")
    void shouldExecuteSearchMemoryTool() {
        memoryManager.writeDurable("Artificial intelligence is transforming industries");

        Object result = toolkit.execute(MemorySearchTool.TOOL_NAME, Map.of(
            "query", "artificial intelligence"
        ));

        assertNotNull(result);
        assertInstanceOf(MemorySearchTool.MemorySearchResult.class, result);

        MemorySearchTool.MemorySearchResult searchResult =
            (MemorySearchTool.MemorySearchResult) result;
        assertTrue(searchResult.success());
    }

    @Test
    @DisplayName("Execute search_memory with empty query returns error result")
    void shouldReturnErrorForEmptySearchQuery() {
        Object result = toolkit.execute(MemorySearchTool.TOOL_NAME, Map.of(
            "query", ""
        ));

        assertInstanceOf(MemorySearchTool.MemorySearchResult.class, result);
        MemorySearchTool.MemorySearchResult searchResult =
            (MemorySearchTool.MemorySearchResult) result;
        assertFalse(searchResult.success());
        assertTrue(searchResult.error().contains("required"));
    }

    @Test
    @DisplayName("Execute search_memory with no matching content returns empty matches")
    void shouldReturnEmptyMatchesForNonexistentContent() {
        Object result = toolkit.execute(MemorySearchTool.TOOL_NAME, Map.of(
            "query", "completely nonexistent content xyz123"
        ));

        assertInstanceOf(MemorySearchTool.MemorySearchResult.class, result);
        MemorySearchTool.MemorySearchResult searchResult =
            (MemorySearchTool.MemorySearchResult) result;
        assertTrue(searchResult.success());
        assertTrue(searchResult.matches().isEmpty());
    }

    // ==================== Execute write_memory tool ====================

    @Test
    @DisplayName("Execute write_memory tool returns WriteMemoryResult")
    void shouldExecuteWriteMemoryTool() {
        Object result = toolkit.execute(WriteMemoryTool.TOOL_NAME, Map.of(
            "content", "Remember this important fact",
            "type", "ephemeral"
        ));

        assertNotNull(result);
        assertInstanceOf(WriteMemoryTool.WriteMemoryResult.class, result);

        WriteMemoryTool.WriteMemoryResult writeResult =
            (WriteMemoryTool.WriteMemoryResult) result;
        assertTrue(writeResult.success());
    }

    @Test
    @DisplayName("Execute write_memory tool with durable type succeeds")
    void shouldExecuteWriteMemoryDurable() {
        Object result = toolkit.execute(WriteMemoryTool.TOOL_NAME, Map.of(
            "content", "Long-term knowledge",
            "type", "durable",
            "section", "Facts"
        ));

        assertInstanceOf(WriteMemoryTool.WriteMemoryResult.class, result);
        WriteMemoryTool.WriteMemoryResult writeResult =
            (WriteMemoryTool.WriteMemoryResult) result;
        assertTrue(writeResult.success());
        assertTrue(writeResult.message().contains("Facts"));
    }

    // ==================== Execute unknown tool ====================

    @Test
    @DisplayName("Execute unknown tool throws IllegalArgumentException")
    void shouldThrowForUnknownTool() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            toolkit.execute("unknown_tool", Map.of())
        );

        assertTrue(ex.getMessage().contains("Unknown tool"));
        assertTrue(ex.getMessage().contains("unknown_tool"));
    }

    @Test
    @DisplayName("Execute another unknown tool name throws IllegalArgumentException")
    void shouldThrowForAnotherUnknownTool() {
        assertThrows(IllegalArgumentException.class, () ->
            toolkit.execute("delete_memory", Map.of("id", "123"))
        );
    }

    // ==================== getToolSchemas ====================

    @Test
    @DisplayName("getToolSchemas returns exactly two schemas")
    void shouldReturnTwoSchemas() {
        List<Map<String, Object>> schemas = toolkit.getToolSchemas();

        assertNotNull(schemas);
        assertEquals(2, schemas.size());
    }

    @Test
    @DisplayName("getToolSchemas includes search_memory schema")
    void shouldIncludeSearchMemorySchema() {
        List<Map<String, Object>> schemas = toolkit.getToolSchemas();

        boolean hasSearch = schemas.stream()
            .anyMatch(s -> MemorySearchTool.TOOL_NAME.equals(s.get("name")));
        assertTrue(hasSearch, "Should contain search_memory schema");
    }

    @Test
    @DisplayName("getToolSchemas includes write_memory schema")
    void shouldIncludeWriteMemorySchema() {
        List<Map<String, Object>> schemas = toolkit.getToolSchemas();

        boolean hasWrite = schemas.stream()
            .anyMatch(s -> WriteMemoryTool.TOOL_NAME.equals(s.get("name")));
        assertTrue(hasWrite, "Should contain write_memory schema");
    }

    @Test
    @DisplayName("Each schema has name, description, and input_schema")
    void shouldHaveRequiredFieldsInEachSchema() {
        List<Map<String, Object>> schemas = toolkit.getToolSchemas();

        for (Map<String, Object> schema : schemas) {
            assertNotNull(schema.get("name"), "Schema should have name");
            assertNotNull(schema.get("description"), "Schema should have description");
            assertNotNull(schema.get("input_schema"), "Schema should have input_schema");
        }
    }

    // ==================== getExecutor ====================

    @Test
    @DisplayName("getExecutor returns a functional executor")
    void shouldReturnFunctionalExecutor() {
        Function<MemoryToolkit.ToolCall, String> executor = toolkit.getExecutor();

        assertNotNull(executor);
    }

    @Test
    @DisplayName("Executor handles search_memory call and returns text")
    void shouldExecuteSearchViaExecutor() {
        Function<MemoryToolkit.ToolCall, String> executor = toolkit.getExecutor();

        String result = executor.apply(new MemoryToolkit.ToolCall(
            MemorySearchTool.TOOL_NAME,
            Map.of("query", "test query")
        ));

        assertNotNull(result);
        // Empty search returns "No matching memories found."
        assertTrue(result.contains("No matching") || result.contains("Found"));
    }

    @Test
    @DisplayName("Executor handles write_memory call and returns text")
    void shouldExecuteWriteViaExecutor() {
        Function<MemoryToolkit.ToolCall, String> executor = toolkit.getExecutor();

        String result = executor.apply(new MemoryToolkit.ToolCall(
            WriteMemoryTool.TOOL_NAME,
            Map.of("content", "Executor test content", "type", "ephemeral")
        ));

        assertNotNull(result);
        assertTrue(result.contains("ephemeral"));
    }

    @Test
    @DisplayName("Executor handles write_memory error and returns error text")
    void shouldReturnErrorTextViaExecutor() {
        Function<MemoryToolkit.ToolCall, String> executor = toolkit.getExecutor();

        String result = executor.apply(new MemoryToolkit.ToolCall(
            WriteMemoryTool.TOOL_NAME,
            Map.of("content", "")
        ));

        assertNotNull(result);
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    @DisplayName("Executor throws for unknown tool")
    void shouldThrowForUnknownToolViaExecutor() {
        Function<MemoryToolkit.ToolCall, String> executor = toolkit.getExecutor();

        assertThrows(IllegalArgumentException.class, () ->
            executor.apply(new MemoryToolkit.ToolCall("nonexistent", Map.of()))
        );
    }

    // ==================== Factory method ====================

    @Test
    @DisplayName("Static create factory method returns valid toolkit")
    void shouldCreateToolkitViaFactory() {
        MemoryToolkit created = MemoryToolkit.create(memoryManager);

        assertNotNull(created);
        assertNotNull(created.getSearchTool());
        assertNotNull(created.getWriteTool());
        assertEquals(2, created.getToolSchemas().size());
    }
}
