package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.tools.MemorySearchTool.MemoryMatch;
import com.lightweightai.kernel.memory.tools.MemorySearchTool.MemorySearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool - 记忆搜索工具")
class MemorySearchToolTest {

    @Nested
    @DisplayName("execute 参数校验")
    class ExecuteValidation {

        @TempDir
        Path tempDir;

        private FileMemoryManager memoryManager;
        private MemorySearchTool searchTool;

        @BeforeEach
        void setUp() {
            Path agentRoot = tempDir.resolve("test-agent");
            MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
            memoryManager = new FileMemoryManager(agentRoot, "test-agent", embeddingProvider);
            searchTool = new MemorySearchTool(memoryManager);
        }

        @AfterEach
        void tearDown() {
            if (memoryManager != null) {
                memoryManager.close();
            }
        }

        @Test
        @DisplayName("query 为 null 时返回错误结果")
        void shouldReturnErrorWhenQueryIsNull() {
            Map<String, Object> params = new HashMap<>();

            MemorySearchResult result = searchTool.execute(params);

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("required"));
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("query 为空白字符串时返回错误结果")
        void shouldReturnErrorWhenQueryIsBlank() {
            MemorySearchResult result = searchTool.execute(Map.of("query", "   "));

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("required"));
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("query 为空字符串时返回错误结果")
        void shouldReturnErrorWhenQueryIsEmpty() {
            MemorySearchResult result = searchTool.execute(Map.of("query", ""));

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("required"));
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("无匹配结果时返回成功但 matches 为空")
        void shouldReturnSuccessWithEmptyMatchesWhenNothingFound() {
            MemorySearchResult result = searchTool.execute(Map.of(
                "query", "completely nonexistent content xyz123"
            ));

            assertTrue(result.success());
            assertNull(result.error());
            assertTrue(result.matches().isEmpty());
        }
    }

    @Nested
    @DisplayName("getToolSchema 工具定义")
    class ToolSchema {

        @Test
        @DisplayName("schema 包含 name、description 和 input_schema")
        void shouldContainRequiredTopLevelFields() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals(MemorySearchTool.TOOL_NAME, schema.get("name"));
            assertEquals(MemorySearchTool.TOOL_DESCRIPTION, schema.get("description"));
            assertNotNull(schema.get("input_schema"));
        }

        @Test
        @DisplayName("input_schema 定义 query、top_k、vector_weight 属性")
        @SuppressWarnings("unchecked")
        void shouldDefineExpectedProperties() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");

            assertEquals("object", inputSchema.get("type"));

            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            assertNotNull(properties.get("query"));
            assertNotNull(properties.get("top_k"));
            assertNotNull(properties.get("vector_weight"));
        }

        @Test
        @DisplayName("query 属性标记为 required")
        @SuppressWarnings("unchecked")
        void shouldMarkQueryAsRequired() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            List<String> required = (List<String>) inputSchema.get("required");

            assertNotNull(required);
            assertTrue(required.contains("query"));
        }

        @Test
        @DisplayName("TOOL_NAME 常量值为 memory_search")
        void shouldHaveCorrectToolName() {
            assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
        }
    }

    @Nested
    @DisplayName("MemorySearchResult.toText 格式化输出")
    class ToTextFormatting {

        @Test
        @DisplayName("错误结果输出 Error: 前缀加错误信息")
        void shouldFormatErrorResult() {
            MemorySearchResult result = new MemorySearchResult(false, "Something went wrong", List.of());

            String text = result.toText();

            assertEquals("Error: Something went wrong", text);
        }

        @Test
        @DisplayName("成功但无匹配结果时输出未找到提示")
        void shouldFormatEmptyResults() {
            MemorySearchResult result = new MemorySearchResult(true, null, List.of());

            String text = result.toText();

            assertEquals("No matching memories found.", text);
        }

        @Test
        @DisplayName("有匹配结果时输出包含来源、分数和摘要")
        void shouldFormatResultsWithSourceScoreAndSnippet() {
            List<MemoryMatch> matches = List.of(
                new MemoryMatch("full content 1", "notes.md", 0.95f, "snippet about topic A"),
                new MemoryMatch("full content 2", "journal.md", 0.82f, "snippet about topic B")
            );
            MemorySearchResult result = new MemorySearchResult(true, null, matches);

            String text = result.toText();

            assertTrue(text.startsWith("Found 2 relevant memories:"));
            assertTrue(text.contains("[notes.md]"));
            assertTrue(text.contains("0.950"));
            assertTrue(text.contains("snippet about topic A"));
            assertTrue(text.contains("[journal.md]"));
            assertTrue(text.contains("0.820"));
            assertTrue(text.contains("snippet about topic B"));
            assertTrue(text.contains("1."));
            assertTrue(text.contains("2."));
        }

        @Test
        @DisplayName("单个匹配结果时输出 Found 1 relevant memories")
        void shouldFormatSingleResult() {
            List<MemoryMatch> matches = List.of(
                new MemoryMatch("content", "src/Main.java", 0.5f, "a code snippet")
            );
            MemorySearchResult result = new MemorySearchResult(true, null, matches);

            String text = result.toText();

            assertTrue(text.startsWith("Found 1 relevant memories:"));
            assertTrue(text.contains("[src/Main.java]"));
            assertTrue(text.contains("0.500"));
            assertTrue(text.contains("a code snippet"));
        }
    }

    @Nested
    @DisplayName("MemoryMatch 记录类型")
    class MemoryMatchRecord {

        @Test
        @DisplayName("字段可正确访问")
        void shouldExposeAllFields() {
            MemoryMatch match = new MemoryMatch("the content", "file.txt", 0.75f, "the snippet");

            assertEquals("the content", match.content());
            assertEquals("file.txt", match.source());
            assertEquals(0.75f, match.score(), 0.001f);
            assertEquals("the snippet", match.snippet());
        }

        @Test
        @DisplayName("两个相同字段的 MemoryMatch 相等")
        void shouldBeEqualWhenFieldsMatch() {
            MemoryMatch a = new MemoryMatch("c", "s", 0.5f, "snip");
            MemoryMatch b = new MemoryMatch("c", "s", 0.5f, "snip");

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }
}
