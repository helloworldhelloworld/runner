package com.lightweightai.tools.web;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebTools - Web search tool (mock mode)")
class WebToolsTest {

    private Tool webSearchTool;

    @BeforeEach
    void setUp() {
        WebTools webTools = new WebTools();
        List<Tool> tools = AnnotatedToolScanner.scan(webTools);
        webSearchTool = tools.stream()
            .filter(t -> t.getName().equals("web_search"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("web_search tool not found"));
    }

    @Nested
    @DisplayName("Tool discovery")
    class ToolDiscovery {

        @Test
        @DisplayName("Scanner discovers web_search tool")
        void shouldDiscoverTool() {
            assertNotNull(webSearchTool);
            assertEquals("web_search", webSearchTool.getName());
        }

        @Test
        @DisplayName("Tool has description for LLM")
        void shouldHaveDescription() {
            assertNotNull(webSearchTool.getDescription());
            assertFalse(webSearchTool.getDescription().isBlank());
        }

        @Test
        @DisplayName("Tool schema defines query as required")
        @SuppressWarnings("unchecked")
        void shouldDefineQueryAsRequired() {
            Map<String, Object> schema = webSearchTool.getSchema().toMap();
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");

            assertNotNull(props.get("query"));

            List<String> required = (List<String>) schema.get("required");
            assertNotNull(required);
            assertTrue(required.contains("query"));
        }
    }

    @Nested
    @DisplayName("Execution (mock mode)")
    class Execution {

        @Test
        @DisplayName("Returns mock results for valid query")
        void shouldReturnMockResults() {
            ToolResult result = webSearchTool.execute(Map.of(
                "query", "latest Java release", "maxResults", 3));

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("latest Java release"));
            assertTrue(result.getContent().contains("Mock search"));
        }

        @Test
        @DisplayName("Returns mock results with default maxResults")
        void shouldUseDefaultMaxResults() {
            ToolResult result = webSearchTool.execute(Map.of("query", "test"));

            assertFalse(result.isError());
            assertNotNull(result.getContent());
            assertTrue(result.getContent().contains("test"));
        }

        @Test
        @DisplayName("Zero maxResults falls back to default")
        void shouldFallBackOnZeroMaxResults() {
            ToolResult result = webSearchTool.execute(Map.of(
                "query", "test", "maxResults", 0));

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("results"));
        }
    }

    @Nested
    @DisplayName("Metadata")
    class Metadata {

        @Test
        @DisplayName("Category is web")
        void shouldHaveWebCategory() {
            assertTrue(webSearchTool instanceof ToolMetadata);
            ToolMetadata meta = (ToolMetadata) webSearchTool;
            assertEquals("web", meta.getCategory());
        }

        @Test
        @DisplayName("Tags include web and search")
        void shouldHaveCorrectTags() {
            ToolMetadata meta = (ToolMetadata) webSearchTool;
            List<String> tags = meta.getTags();
            assertTrue(tags.contains("web"));
            assertTrue(tags.contains("search"));
        }
    }

    @Nested
    @DisplayName("Custom configuration")
    class CustomConfiguration {

        @Test
        @DisplayName("Custom maxResults default is respected")
        void shouldUseCustomDefaultMaxResults() {
            WebTools webTools = new WebTools(null, 5);
            List<Tool> tools = AnnotatedToolScanner.scan(webTools);
            Tool tool = tools.get(0);

            ToolResult result = tool.execute(Map.of("query", "test", "maxResults", 0));
            assertFalse(result.isError());
        }
    }
}
