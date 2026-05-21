package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner — discovers @ToolFunction methods")
class AnnotatedToolScannerTest {

    // ==================== Test tool classes ====================

    static class TwoTools {
        @ToolFunction(name = "alpha", description = "First tool")
        public String alpha() {
            return "a";
        }

        @ToolFunction(name = "beta", description = "Second tool")
        public String beta() {
            return "b";
        }
    }

    static class NoTools {
        public String notATool() {
            return "skip";
        }
    }

    static class PrivateTool {
        @ToolFunction(name = "secret", description = "Private method tool")
        private String secret() {
            return "hidden";
        }
    }

    static class ParentTools {
        @ToolFunction(name = "parent_tool", description = "Inherited tool")
        public String parentTool() {
            return "from parent";
        }
    }

    static class ChildTools extends ParentTools {
        @ToolFunction(name = "child_tool", description = "Child-only tool")
        public String childTool() {
            return "from child";
        }
    }

    static class AutoNameTools {
        @ToolFunction(description = "Auto-named from method")
        public String getUserProfile() {
            return "profile";
        }

        @ToolFunction(description = "Another auto-named")
        public String sendMessage() {
            return "sent";
        }
    }

    static class ToolWithParams {
        @ToolFunction(name = "search", description = "Search items")
        public String search(
            @ToolParam(name = "query", description = "Search query", required = true) String query,
            @ToolParam(name = "limit", description = "Max results") int limit
        ) {
            return "found " + limit + " for " + query;
        }
    }

    // ==================== Tests ====================

    @Test
    @DisplayName("discovers all @ToolFunction methods")
    void discoversAllAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new TwoTools());
        assertEquals(2, tools.size());

        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
        assertTrue(names.contains("alpha"));
        assertTrue(names.contains("beta"));
    }

    @Test
    @DisplayName("returns empty list for object without annotations")
    void emptyForNoAnnotations() {
        List<Tool> tools = AnnotatedToolScanner.scan(new NoTools());
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("discovers private @ToolFunction methods")
    void discoversPrivateMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new PrivateTool());
        assertEquals(1, tools.size());
        assertEquals("secret", tools.get(0).getName());

        ToolResult result = tools.get(0).execute(Map.of());
        assertFalse(result.isError());
        assertEquals("hidden", result.getContent());
    }

    @Test
    @DisplayName("discovers inherited @ToolFunction from parent class")
    void discoversInheritedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());

        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
        assertTrue(names.contains("child_tool"), "child's own tool must be found");
        assertTrue(names.contains("parent_tool"), "inherited tool must be found");
    }

    @Test
    @DisplayName("auto-generates snake_case names from camelCase method names")
    void autoGeneratesSnakeNames() {
        List<Tool> tools = AnnotatedToolScanner.scan(new AutoNameTools());

        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
        assertTrue(names.contains("get_user_profile"));
        assertTrue(names.contains("send_message"));
    }

    @Test
    @DisplayName("scanned tools are executable with correct parameter handling")
    void scannedToolsAreExecutable() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ToolWithParams());
        Tool searchTool = tools.get(0);

        ToolResult result = searchTool.execute(Map.of("query", "test", "limit", 5));
        assertFalse(result.isError());
        assertEquals("found 5 for test", result.getContent());
    }

    @Test
    @DisplayName("scanned tool has correct schema with required fields")
    void scannedToolHasSchema() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ToolWithParams());
        Tool searchTool = tools.get(0);

        Map<String, Object> schema = searchTool.getSchema().toMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("query"));
        assertTrue(properties.containsKey("limit"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.contains("query"));
        assertFalse(required.contains("limit"));
    }

    @Test
    @DisplayName("rejects null target")
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    @Test
    @DisplayName("scan result is independent — modifying list doesn't affect re-scan")
    void resultIsIndependent() {
        TwoTools target = new TwoTools();
        List<Tool> first = AnnotatedToolScanner.scan(target);
        List<Tool> second = AnnotatedToolScanner.scan(target);

        assertEquals(first.size(), second.size());
        assertNotSame(first, second);
    }
}
