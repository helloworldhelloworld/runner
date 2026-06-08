package com.lightweightai.kernel.memory.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool - 记忆搜索工具")
class MemorySearchToolTest {

    @Nested
    @DisplayName("MemorySearchResult")
    class SearchResultTests {

        @Test
        @DisplayName("成功结果的 toText 包含匹配数量和内容")
        void successToText() {
            var matches = List.of(
                new MemorySearchTool.MemoryMatch("content1", "file1.md", 0.95f, "snippet1"),
                new MemorySearchTool.MemoryMatch("content2", "file2.md", 0.80f, "snippet2")
            );

            var result = new MemorySearchTool.MemorySearchResult(true, null, matches);

            String text = result.toText();
            assertTrue(text.contains("2 relevant memories"));
            assertTrue(text.contains("file1.md"));
            assertTrue(text.contains("0.950"));
            assertTrue(text.contains("snippet1"));
        }

        @Test
        @DisplayName("空结果的 toText 返回 no matches 消息")
        void emptyResultsToText() {
            var result = new MemorySearchTool.MemorySearchResult(true, null, List.of());
            assertEquals("No matching memories found.", result.toText());
        }

        @Test
        @DisplayName("失败结果的 toText 包含错误信息")
        void errorToText() {
            var result = new MemorySearchTool.MemorySearchResult(false, "Query parameter is required", List.of());
            assertEquals("Error: Query parameter is required", result.toText());
        }
    }

    @Nested
    @DisplayName("getToolSchema 工具定义")
    class ToolSchemaTests {

        @Test
        @DisplayName("schema 包含正确的 name 和 description")
        void schemaNameAndDescription() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            assertEquals("memory_search", schema.get("name"));
            assertNotNull(schema.get("description"));
        }

        @Test
        @DisplayName("schema 包含 query 必填参数")
        @SuppressWarnings("unchecked")
        void schemaHasQueryRequired() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            List<String> required = (List<String>) inputSchema.get("required");
            assertTrue(required.contains("query"));
        }

        @Test
        @DisplayName("schema 包含 top_k 和 vector_weight 可选参数")
        @SuppressWarnings("unchecked")
        void schemaHasOptionalParams() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            assertTrue(properties.containsKey("top_k"));
            assertTrue(properties.containsKey("vector_weight"));
        }
    }

    @Test
    @DisplayName("常量定义正确")
    void constantsDefined() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
        assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
        assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isEmpty());
    }
}
