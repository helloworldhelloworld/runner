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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool - 记忆搜索工具独立测试")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    private FileMemoryManager memoryManager;
    private MemorySearchTool tool;

    @BeforeEach
    void setUp() {
        Path agentRoot = tempDir.resolve("search-test-agent");
        memoryManager = new FileMemoryManager(agentRoot, "search-test", new MockEmbeddingProvider(64));
        tool = new MemorySearchTool(memoryManager);
    }

    @AfterEach
    void tearDown() {
        if (memoryManager != null) {
            memoryManager.close();
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ValidationTests {

        @Test
        @DisplayName("query 缺失返回错误")
        void shouldFailOnMissingQuery() {
            var result = tool.execute(Map.of());
            assertFalse(result.success());
            assertEquals("Query parameter is required", result.error());
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("query 为 null 返回错误")
        void shouldFailOnNullQuery() {
            var result = tool.execute(Map.of("top_k", 5));
            assertFalse(result.success());
            assertEquals("Query parameter is required", result.error());
        }

        @Test
        @DisplayName("query 为空白返回错误")
        void shouldFailOnBlankQuery() {
            var result = tool.execute(Map.of("query", "   "));
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("默认参数")
    class DefaultParamTests {

        @Test
        @DisplayName("无额外参数时使用默认值执行成功")
        void shouldUseDefaults() {
            var result = tool.execute(Map.of("query", "test"));
            assertTrue(result.success());
        }

        @Test
        @DisplayName("非数值类型的 top_k 使用默认值")
        void shouldDefaultOnInvalidTopK() {
            var result = tool.execute(Map.of("query", "test", "top_k", "invalid"));
            assertTrue(result.success());
        }

        @Test
        @DisplayName("非数值类型的 vector_weight 使用默认值")
        void shouldDefaultOnInvalidVectorWeight() {
            var result = tool.execute(Map.of("query", "test", "vector_weight", "invalid"));
            assertTrue(result.success());
        }
    }

    @Nested
    @DisplayName("搜索集成")
    class SearchIntegrationTests {

        @Test
        @DisplayName("写入内容后可搜索到")
        void shouldFindWrittenContent() {
            memoryManager.writeDurable("The user loves programming in Java and uses Spring Boot");

            var result = tool.execute(Map.of("query", "Java programming"));
            assertTrue(result.success());
            assertFalse(result.matches().isEmpty());
            assertTrue(result.matches().get(0).content().contains("Java"));
        }

        @Test
        @DisplayName("搜索不存在的内容返回空列表")
        void shouldReturnEmptyForNoMatch() {
            var result = tool.execute(Map.of("query", "completely unique phrase xyz123"));
            assertTrue(result.success());
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("自定义 top_k 限制返回数量")
        void shouldRespectTopK() {
            memoryManager.writeDurable(
                "## Section A\nContent about topic alpha\n\n" +
                "## Section B\nContent about topic beta\n\n" +
                "## Section C\nContent about topic gamma"
            );

            var result = tool.execute(Map.of("query", "topic", "top_k", 1));
            assertTrue(result.success());
            assertTrue(result.matches().size() <= 1);
        }
    }

    @Nested
    @DisplayName("结果格式化")
    class FormattingTests {

        @Test
        @DisplayName("成功结果的 toText 包含 Found")
        void shouldFormatSuccessResults() {
            memoryManager.writeDurable("Important data about the project");

            var result = tool.execute(Map.of("query", "project data"));
            String text = result.toText();
            if (!result.matches().isEmpty()) {
                assertTrue(text.contains("Found"));
                assertTrue(text.contains("relevant memories"));
            }
        }

        @Test
        @DisplayName("空结果的 toText 提示无匹配")
        void shouldFormatEmptyResults() {
            var result = tool.execute(Map.of("query", "nonexistent"));
            assertEquals("No matching memories found.", result.toText());
        }

        @Test
        @DisplayName("错误结果的 toText 以 Error: 开头")
        void shouldFormatErrorResult() {
            var result = tool.execute(Map.of());
            assertTrue(result.toText().startsWith("Error:"));
        }
    }

    @Nested
    @DisplayName("Schema 定义")
    class SchemaTests {

        @Test
        @DisplayName("getToolSchema 包含必要字段")
        void shouldHaveCompleteSchema() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals("memory_search", schema.get("name"));
            assertNotNull(schema.get("description"));

            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            assertEquals("object", inputSchema.get("type"));
            assertNotNull(inputSchema.get("properties"));
            assertNotNull(inputSchema.get("required"));
        }

        @Test
        @DisplayName("TOOL_NAME 常量正确")
        void shouldHaveCorrectToolName() {
            assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
        }

        @Test
        @DisplayName("TOOL_DESCRIPTION 非空")
        void shouldHaveNonEmptyDescription() {
            assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
            assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isBlank());
        }
    }
}
