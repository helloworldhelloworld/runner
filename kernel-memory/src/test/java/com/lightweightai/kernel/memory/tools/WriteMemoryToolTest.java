package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WriteMemoryTool - Memory write operations")
class WriteMemoryToolTest {

    @TempDir
    Path tempDir;

    private FileMemoryManager memoryManager;
    private WriteMemoryTool writeTool;

    @BeforeEach
    void setUp() {
        Path agentRoot = tempDir.resolve("test-agent");
        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
        memoryManager = new FileMemoryManager(agentRoot, "test-agent", embeddingProvider);
        writeTool = new WriteMemoryTool(memoryManager);
    }

    @AfterEach
    void tearDown() {
        if (memoryManager != null) {
            memoryManager.close();
        }
    }

    // ==================== Write ephemeral memory ====================

    @Test
    @DisplayName("Write ephemeral memory succeeds and persists content")
    void shouldWriteEphemeralMemory() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", "User likes dark mode",
            "type", "ephemeral"
        ));

        assertTrue(result.success());
        assertNull(result.error());
        assertTrue(result.message().contains("ephemeral"));

        String ephemeral = memoryManager.readTodayEphemeral();
        assertTrue(ephemeral.contains("User likes dark mode"));
    }

    @Test
    @DisplayName("Write ephemeral memory is the default when type is omitted")
    void shouldDefaultToEphemeralWhenTypeOmitted() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", "Default type content"
        ));

        assertTrue(result.success());
        assertTrue(result.message().contains("ephemeral"));

        String ephemeral = memoryManager.readTodayEphemeral();
        assertTrue(ephemeral.contains("Default type content"));
    }

    // ==================== Write durable memory ====================

    @Test
    @DisplayName("Write durable memory with section succeeds")
    void shouldWriteDurableMemoryWithSection() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", "Favorite color is green",
            "type", "durable",
            "section", "User Preferences"
        ));

        assertTrue(result.success());
        assertNull(result.error());
        assertTrue(result.message().contains("User Preferences"));

        String durable = memoryManager.readDurable();
        assertTrue(durable.contains("Favorite color is green"));
        assertTrue(durable.contains("## User Preferences"));
    }

    @Test
    @DisplayName("Write durable memory without section defaults to Notes")
    void shouldWriteDurableMemoryWithDefaultSection() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", "Important fact",
            "type", "durable"
        ));

        assertTrue(result.success());
        assertTrue(result.message().contains("Notes"));

        String durable = memoryManager.readDurable();
        assertTrue(durable.contains("Important fact"));
    }

    // ==================== Missing content parameter ====================

    @Test
    @DisplayName("Missing content parameter returns error")
    void shouldReturnErrorWhenContentMissing() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "type", "ephemeral"
        ));

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("required"));
        assertNull(result.message());
    }

    @Test
    @DisplayName("Empty content parameter returns error")
    void shouldReturnErrorWhenContentEmpty() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", ""
        ));

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("required"));
    }

    @Test
    @DisplayName("Blank content parameter returns error")
    void shouldReturnErrorWhenContentBlank() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", "   "
        ));

        assertFalse(result.success());
        assertTrue(result.error().contains("required"));
    }

    // ==================== Invalid memory type ====================

    @Test
    @DisplayName("Invalid memory type returns error")
    void shouldReturnErrorForInvalidType() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", "Some content",
            "type", "permanent"
        ));

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("Invalid type"));
        assertTrue(result.error().contains("permanent"));
    }

    @Test
    @DisplayName("Another invalid memory type returns error with type name")
    void shouldReturnErrorForAnotherInvalidType() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", "Some content",
            "type", "archive"
        ));

        assertFalse(result.success());
        assertTrue(result.error().contains("Invalid type"));
        assertTrue(result.error().contains("archive"));
    }

    // ==================== Tool schema validation ====================

    @Test
    @DisplayName("Tool schema has required top-level fields")
    void shouldHaveRequiredSchemaFields() {
        Map<String, Object> schema = WriteMemoryTool.getToolSchema();

        assertNotNull(schema.get("name"));
        assertNotNull(schema.get("description"));
        assertNotNull(schema.get("input_schema"));

        assertEquals(WriteMemoryTool.TOOL_NAME, schema.get("name"));
        assertEquals(WriteMemoryTool.TOOL_DESCRIPTION, schema.get("description"));
    }

    @Test
    @DisplayName("Tool schema input_schema defines correct properties")
    @SuppressWarnings("unchecked")
    void shouldDefineCorrectSchemaProperties() {
        Map<String, Object> schema = WriteMemoryTool.getToolSchema();
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");

        assertEquals("object", inputSchema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertNotNull(properties.get("content"));
        assertNotNull(properties.get("type"));
        assertNotNull(properties.get("section"));
    }

    @Test
    @DisplayName("Tool schema marks content as required")
    @SuppressWarnings("unchecked")
    void shouldMarkContentAsRequired() {
        Map<String, Object> schema = WriteMemoryTool.getToolSchema();
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        List<String> required = (List<String>) inputSchema.get("required");

        assertNotNull(required);
        assertTrue(required.contains("content"));
    }

    @Test
    @DisplayName("Tool schema type property has enum values")
    @SuppressWarnings("unchecked")
    void shouldHaveTypeEnumValues() {
        Map<String, Object> schema = WriteMemoryTool.getToolSchema();
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        Map<String, Object> typeProperty = (Map<String, Object>) properties.get("type");

        List<String> enumValues = (List<String>) typeProperty.get("enum");
        assertNotNull(enumValues);
        assertTrue(enumValues.contains("ephemeral"));
        assertTrue(enumValues.contains("durable"));
        assertEquals(2, enumValues.size());
    }

    // ==================== Result toText ====================

    @Test
    @DisplayName("Successful result toText returns message")
    void shouldReturnMessageOnSuccess() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", "Test content",
            "type", "ephemeral"
        ));

        String text = result.toText();
        assertFalse(text.startsWith("Error:"));
        assertTrue(text.contains("ephemeral"));
    }

    @Test
    @DisplayName("Error result toText returns error message")
    void shouldReturnErrorOnFailure() {
        WriteMemoryTool.WriteMemoryResult result = writeTool.execute(Map.of(
            "content", ""
        ));

        String text = result.toText();
        assertTrue(text.startsWith("Error:"));
    }
}
