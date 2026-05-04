package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool — 独立搜索工具测试")
class MemorySearchToolTest {

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

    @Nested
    @DisplayName("参数验证")
    class ParameterValidation {

        @Test
        @DisplayName("缺少 query 参数返回错误")
        void missingQueryReturnsError() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of());
            assertFalse(result.success());
            assertTrue(result.error().contains("required"));
        }

        @Test
        @DisplayName("空 query 返回错误")
        void emptyQueryReturnsError() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", ""));
            assertFalse(result.success());
            assertTrue(result.error().contains("required"));
        }

        @Test
        @DisplayName("空白 query 返回错误")
        void blankQueryReturnsError() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "   "));
            assertFalse(result.success());
            assertTrue(result.error().contains("required"));
        }

        @Test
        @DisplayName("top_k 参数正确应用")
        void topKParamApplied() {
            memoryManager.writeDurable("Content about cats. Cats are great pets.");
            memoryManager.writeDurable("Content about dogs. Dogs are loyal companions.");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "pets",
                    "top_k", 1
            ));

            assertTrue(result.success());
            assertTrue(result.matches().size() <= 1);
        }

        @Test
        @DisplayName("vector_weight 参数接受 Number 类型")
        void vectorWeightAcceptsNumber() {
            memoryManager.writeDurable("Test content for vector weight test");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "test",
                    "vector_weight", 0.5
            ));

            assertTrue(result.success());
        }

        @Test
        @DisplayName("默认 top_k=5, vector_weight=0.7 （不传参数）")
        void defaultParametersApplied() {
            memoryManager.writeDurable("Some default test content");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "test"
            ));

            assertTrue(result.success());
        }
    }

    @Nested
    @DisplayName("搜索结果")
    class SearchResults {

        @Test
        @DisplayName("搜索有结果时返回 MemoryMatch 列表")
        void returnsMatchesWhenFound() {
            memoryManager.writeDurable("Java is a popular programming language for enterprise applications");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "Java programming"
            ));

            assertTrue(result.success());
            assertNull(result.error());
            assertFalse(result.matches().isEmpty());

            MemorySearchTool.MemoryMatch match = result.matches().get(0);
            assertNotNull(match.content());
            assertNotNull(match.source());
            assertTrue(match.score() >= 0);
        }

        @Test
        @DisplayName("搜索无结果时返回空列表")
        void returnsEmptyMatchesWhenNotFound() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "xyznonexistent123"
            ));

            assertTrue(result.success());
            assertTrue(result.matches().isEmpty());
        }
    }

    @Nested
    @DisplayName("toText 格式")
    class ToTextFormat {

        @Test
        @DisplayName("错误结果 toText 以 Error: 开头")
        void errorToText() {
            MemorySearchTool.MemorySearchResult error =
                    new MemorySearchTool.MemorySearchResult(false, "Something went wrong", List.of());
            String text = error.toText();
            assertTrue(text.startsWith("Error:"));
            assertTrue(text.contains("Something went wrong"));
        }

        @Test
        @DisplayName("空结果 toText 返回友好提示")
        void emptyToText() {
            MemorySearchTool.MemorySearchResult empty =
                    new MemorySearchTool.MemorySearchResult(true, null, List.of());
            assertEquals("No matching memories found.", empty.toText());
        }

        @Test
        @DisplayName("有结果 toText 包含序号、来源、分数")
        void matchesToText() {
            List<MemorySearchTool.MemoryMatch> matches = List.of(
                    new MemorySearchTool.MemoryMatch("content1", "file1.md", 0.95f, "snippet of content1"),
                    new MemorySearchTool.MemoryMatch("content2", "file2.md", 0.82f, "snippet of content2")
            );
            MemorySearchTool.MemorySearchResult result =
                    new MemorySearchTool.MemorySearchResult(true, null, matches);

            String text = result.toText();
            assertTrue(text.contains("Found 2 relevant memories"));
            assertTrue(text.contains("1."));
            assertTrue(text.contains("2."));
            assertTrue(text.contains("file1.md"));
            assertTrue(text.contains("file2.md"));
            assertTrue(text.contains("0.950"));
            assertTrue(text.contains("0.820"));
            assertTrue(text.contains("snippet of content1"));
        }
    }

    @Nested
    @DisplayName("getToolSchema 结构")
    class ToolSchemaStructure {

        @Test
        @DisplayName("schema 包含 name, description, input_schema")
        void schemaHasRequiredFields() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals("memory_search", schema.get("name"));
            assertNotNull(schema.get("description"));
            assertNotNull(schema.get("input_schema"));
        }

        @Test
        @DisplayName("input_schema 的 query 是 required")
        @SuppressWarnings("unchecked")
        void queryIsRequired() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");

            assertEquals("object", inputSchema.get("type"));

            Map<String, Object> props = (Map<String, Object>) inputSchema.get("properties");
            assertTrue(props.containsKey("query"));
            assertTrue(props.containsKey("top_k"));
            assertTrue(props.containsKey("vector_weight"));

            List<String> required = (List<String>) inputSchema.get("required");
            assertTrue(required.contains("query"));
            assertFalse(required.contains("top_k"));
        }
    }

    @Nested
    @DisplayName("MemoryMatch record")
    class MemoryMatchRecord {

        @Test
        @DisplayName("MemoryMatch 字段可正确访问")
        void fieldsAccessible() {
            MemorySearchTool.MemoryMatch match =
                    new MemorySearchTool.MemoryMatch("content", "source.md", 0.9f, "snippet");

            assertEquals("content", match.content());
            assertEquals("source.md", match.source());
            assertEquals(0.9f, match.score(), 0.001f);
            assertEquals("snippet", match.snippet());
        }
    }
}
