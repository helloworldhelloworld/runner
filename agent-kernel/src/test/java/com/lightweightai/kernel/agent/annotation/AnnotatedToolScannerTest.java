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

@DisplayName("AnnotatedToolScanner - annotation-based tool discovery")
class AnnotatedToolScannerTest {

    // ==================== Test fixtures ====================

    static class SingleToolClass {
        @ToolFunction(name = "greet", description = "Say hello")
        public String greet(@ToolParam(name = "name", required = true) String name) {
            return "Hello, " + name + "!";
        }
    }

    static class MultiToolClass {
        @ToolFunction(name = "add", description = "Add two numbers")
        public String add(
                @ToolParam(name = "a", required = true) double a,
                @ToolParam(name = "b", required = true) double b) {
            return String.valueOf(a + b);
        }

        @ToolFunction(description = "Get server status")
        public String getServerStatus() {
            return "OK";
        }

        public String notATool() {
            return "ignored";
        }
    }

    static class ParentClass {
        @ToolFunction(name = "parent_tool", description = "From parent")
        public String parentMethod() {
            return "parent";
        }
    }

    static class ChildClass extends ParentClass {
        @ToolFunction(name = "child_tool", description = "From child")
        public String childMethod() {
            return "child";
        }
    }

    static class NoToolClass {
        public String regularMethod() { return "nothing"; }
    }

    // ==================== Tests ====================

    @Test
    @DisplayName("discovers single @ToolFunction method")
    void shouldDiscoverSingleTool() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SingleToolClass());

        assertEquals(1, tools.size());
        assertEquals("greet", tools.get(0).getName());
        assertEquals("Say hello", tools.get(0).getDescription());
    }

    @Test
    @DisplayName("discovered tool executes correctly with payload assertion")
    void shouldExecuteDiscoveredToolAndReturnPayload() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SingleToolClass());
        Tool greetTool = tools.get(0);

        ToolResult result = greetTool.execute(Map.of("name", "World"));

        assertFalse(result.isError());
        assertEquals("Hello, World!", result.getContent());
    }

    @Test
    @DisplayName("discovers multiple @ToolFunction methods, ignores unannotated")
    void shouldDiscoverMultipleToolsAndIgnoreUnannotated() {
        List<Tool> tools = AnnotatedToolScanner.scan(new MultiToolClass());

        assertEquals(2, tools.size());
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
        assertTrue(names.contains("add"));
        assertTrue(names.contains("get_server_status"));
    }

    @Test
    @DisplayName("auto-generates snake_case name from camelCase method name")
    void shouldAutoGenerateSnakeCaseName() {
        List<Tool> tools = AnnotatedToolScanner.scan(new MultiToolClass());
        Tool statusTool = tools.stream()
                .filter(t -> t.getName().equals("get_server_status"))
                .findFirst().orElseThrow();

        assertEquals("get_server_status", statusTool.getName());
        assertEquals("Get server status", statusTool.getDescription());
    }

    @Test
    @DisplayName("discovers tools from both child and parent class")
    void shouldDiscoverInheritedTools() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildClass());

        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
        assertTrue(names.contains("child_tool"), "Should find child method");
        assertTrue(names.contains("parent_tool"), "Should find inherited parent method");
    }

    @Test
    @DisplayName("returns empty list for class with no @ToolFunction methods")
    void shouldReturnEmptyForNoAnnotations() {
        List<Tool> tools = AnnotatedToolScanner.scan(new NoToolClass());
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("rejects null target")
    void shouldRejectNullTarget() {
        assertThrows(NullPointerException.class, () ->
                AnnotatedToolScanner.scan(null));
    }

    @Test
    @DisplayName("generated schema includes required parameters")
    void shouldGenerateSchemaWithRequiredParams() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SingleToolClass());
        Tool greetTool = tools.get(0);

        Map<String, Object> schema = greetTool.getSchema().toMap();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("name"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.contains("name"));
    }

    @Test
    @DisplayName("tool converts Number args to correct Java types")
    void shouldConvertNumberArgs() {
        List<Tool> tools = AnnotatedToolScanner.scan(new MultiToolClass());
        Tool addTool = tools.stream()
                .filter(t -> t.getName().equals("add"))
                .findFirst().orElseThrow();

        ToolResult result = addTool.execute(Map.of("a", 3, "b", 4));
        assertFalse(result.isError());
        assertEquals("7.0", result.getContent());
    }
}
