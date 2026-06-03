package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AnnotatedToolScannerTest {

    // ==================== Test helper classes ====================

    static class SimpleTools {
        @ToolFunction(name = "greet", description = "Say hello")
        public String greet(@ToolParam(name = "name", description = "Person name", required = true) String name) {
            return "Hello, " + name + "!";
        }

        @ToolFunction(description = "Get status")
        public String getServerStatus() {
            return "OK";
        }
    }

    static class NoAnnotations {
        public String regular() {
            return "not a tool";
        }
    }

    static class WithPrivateMethod {
        @ToolFunction(name = "secret", description = "Private tool")
        private String secretMethod() {
            return "secret result";
        }
    }

    static class ParentTool {
        @ToolFunction(name = "parent_tool", description = "From parent")
        public String parentMethod() {
            return "parent";
        }
    }

    static class ChildTool extends ParentTool {
        @ToolFunction(name = "child_tool", description = "From child")
        public String childMethod() {
            return "child";
        }
    }

    // ==================== Tests ====================

    @Test
    void scanNullTargetThrowsNPE() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    @Test
    void scanObjectWithNoAnnotationsReturnsEmpty() {
        List<Tool> tools = AnnotatedToolScanner.scan(new NoAnnotations());
        assertNotNull(tools);
        assertTrue(tools.isEmpty(), "Expected empty list for class with no @ToolFunction methods");
    }

    @Test
    void scanFindsAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());
        assertEquals(2, tools.size(),
                "SimpleTools has 2 @ToolFunction methods, scanner should find exactly 2 tools");
    }

    @Test
    void scanAssignsCorrectNames() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());

        assertTrue(names.contains("greet"),
                "Expected tool named 'greet' from explicit @ToolFunction(name = \"greet\")");
        assertTrue(names.contains("get_server_status"),
                "Expected tool named 'get_server_status' from camelToSnake(\"getServerStatus\")");
    }

    @Test
    void scanAssignsCorrectDescription() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());

        Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool 'greet' not found"));
        assertEquals("Say hello", greetTool.getDescription());

        Tool statusTool = tools.stream()
                .filter(t -> t.getName().equals("get_server_status"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool 'get_server_status' not found"));
        assertEquals("Get status", statusTool.getDescription());
    }

    @Test
    void scanHandlesExplicitName() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());

        Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst()
                .orElse(null);
        assertNotNull(greetTool, "Tool with explicit name 'greet' should exist");
        assertEquals("greet", greetTool.getName());
    }

    @Test
    void scanHandlesDefaultName() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());

        Tool statusTool = tools.stream()
                .filter(t -> t.getName().equals("get_server_status"))
                .findFirst()
                .orElse(null);
        assertNotNull(statusTool,
                "Method 'getServerStatus' with default name should produce tool 'get_server_status'");
        assertEquals("get_server_status", statusTool.getName());
    }

    @Test
    void scanIncludesPrivateMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new WithPrivateMethod());
        assertEquals(1, tools.size(), "Private @ToolFunction method should be discovered via getDeclaredMethods()");
        assertEquals("secret", tools.get(0).getName());
        assertEquals("Private tool", tools.get(0).getDescription());
    }

    @Test
    void scanIncludesInheritedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildTool());
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());

        assertTrue(names.contains("parent_tool"),
                "Inherited @ToolFunction from ParentTool should be included");
        assertTrue(names.contains("child_tool"),
                "Own @ToolFunction from ChildTool should be included");
        assertEquals(2, tools.size(),
                "ChildTool should have exactly 2 tools: one own + one inherited");
    }

    @Test
    void scanDoesNotDuplicateOwnMethods() {
        // ChildTool's own method (child_tool) should appear once, not twice
        // (getDeclaredMethods finds it, getMethods also finds it but the declaring-class check skips it)
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildTool());
        long childToolCount = tools.stream()
                .filter(t -> t.getName().equals("child_tool"))
                .count();
        assertEquals(1, childToolCount,
                "Own method 'child_tool' must not be duplicated across getDeclaredMethods/getMethods scan");
    }

    @Test
    void scannedToolCanExecute() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());

        // Test greet tool execution
        Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst()
                .orElseThrow();
        ToolResult greetResult = greetTool.execute(Map.of("name", "Alice"));
        assertFalse(greetResult.isError(), "greet execution should succeed");
        assertEquals("Hello, Alice!", greetResult.getContent(),
                "greet(\"Alice\") should return 'Hello, Alice!'");

        // Test getServerStatus tool execution (no args)
        Tool statusTool = tools.stream()
                .filter(t -> t.getName().equals("get_server_status"))
                .findFirst()
                .orElseThrow();
        ToolResult statusResult = statusTool.execute(Map.of());
        assertFalse(statusResult.isError(), "getServerStatus execution should succeed");
        assertEquals("OK", statusResult.getContent(),
                "getServerStatus() should return 'OK'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaIncludesRequiredParameters() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());
        Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst()
                .orElseThrow();

        ToolSchema schema = greetTool.getSchema();
        Map<String, Object> schemaMap = schema.toMap();

        List<String> required = (List<String>) schemaMap.get("required");
        assertNotNull(required, "Schema should have a 'required' list since @ToolParam(required = true) is set");
        assertTrue(required.contains("name"),
                "Parameter 'name' marked required = true should appear in schema's required list");
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaIncludesParameterDescription() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());
        Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst()
                .orElseThrow();

        ToolSchema schema = greetTool.getSchema();
        Map<String, Object> schemaMap = schema.toMap();

        Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
        assertNotNull(properties, "Schema should have properties");

        Map<String, Object> nameProp = (Map<String, Object>) properties.get("name");
        assertNotNull(nameProp, "Schema properties should include 'name' parameter");
        assertEquals("Person name", nameProp.get("description"),
                "Parameter description should match @ToolParam(description = \"Person name\")");
    }
}
