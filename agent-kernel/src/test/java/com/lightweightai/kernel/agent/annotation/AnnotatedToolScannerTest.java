package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner")
class AnnotatedToolScannerTest {

    @Test
    @DisplayName("scans @ToolFunction methods and returns Tool list")
    void scansAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());

        assertEquals(2, tools.size());
        List<String> names = tools.stream().map(Tool::getName).sorted().toList();
        assertTrue(names.contains("greet"));
        assertTrue(names.contains("get_status"));
    }

    @Test
    @DisplayName("scanned tool executes correctly with arguments")
    void scannedToolExecutes() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst().orElseThrow();

        ToolResult result = greetTool.execute(Map.of("name", "World"));
        assertFalse(result.isError());
        assertEquals("Hello, World!", result.getContent());
    }

    @Test
    @DisplayName("scanned tool with no parameters works")
    void noParamToolWorks() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        Tool statusTool = tools.stream()
                .filter(t -> t.getName().equals("get_status"))
                .findFirst().orElseThrow();

        ToolResult result = statusTool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("OK", result.getContent());
    }

    @Test
    @DisplayName("throws on null target")
    void throwsOnNull() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    @Test
    @DisplayName("returns empty list for object with no @ToolFunction methods")
    void emptyForNoAnnotations() {
        List<Tool> tools = AnnotatedToolScanner.scan(new Object());
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("scans inherited @ToolFunction methods from parent class")
    void scansInheritedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());
        List<String> names = tools.stream().map(Tool::getName).sorted().toList();
        assertTrue(names.contains("greet"), "should include parent's greet tool");
        assertTrue(names.contains("child_only"), "should include child's own tool");
    }

    @Test
    @DisplayName("tool description is preserved from annotation")
    void descriptionPreserved() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst().orElseThrow();
        assertEquals("Say hello", greetTool.getDescription());
    }

    static class SampleTools {
        @ToolFunction(name = "greet", description = "Say hello")
        public String greet(@ToolParam(name = "name", required = true) String name) {
            return "Hello, " + name + "!";
        }

        @ToolFunction(name = "get_status", description = "Get status")
        public String getStatus() {
            return "OK";
        }
    }

    static class ChildTools extends SampleTools {
        @ToolFunction(name = "child_only", description = "Child tool")
        public String childOnly() {
            return "child";
        }
    }
}
