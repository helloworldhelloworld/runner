package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner — scan @ToolFunction methods into Tool instances")
class AnnotatedToolScannerTest {

    // ==================== Test fixtures ====================

    static class SingleTool {
        @ToolFunction(name = "ping", description = "Ping")
        public String ping() { return "pong"; }
    }

    static class MultipleTools {
        @ToolFunction(name = "alpha", description = "Alpha")
        public String alpha() { return "a"; }

        @ToolFunction(name = "beta", description = "Beta")
        public String beta() { return "b"; }

        @ToolFunction(name = "gamma", description = "Gamma")
        public String gamma() { return "c"; }
    }

    static class MixedMethods {
        @ToolFunction(name = "annotated", description = "Has annotation")
        public String annotated() { return "yes"; }

        public String notAnnotated() { return "no"; }

        private String privateMethod() { return "private"; }
    }

    static class ParentClass {
        @ToolFunction(name = "parent_tool", description = "From parent")
        public String parentTool() { return "parent"; }
    }

    static class ChildClass extends ParentClass {
        @ToolFunction(name = "child_tool", description = "From child")
        public String childTool() { return "child"; }
    }

    static class AutoNameTools {
        @ToolFunction(description = "Gets user profile")
        public String getUserProfile() { return "profile"; }

        @ToolFunction(description = "Does multi word thing")
        public String doMultiWordThing() { return "done"; }
    }

    static class PrivateAnnotatedTool {
        @ToolFunction(name = "secret", description = "Private method tool")
        private String secret() { return "hidden"; }
    }

    // ==================== Tests ====================

    @Nested
    @DisplayName("Basic scanning")
    class BasicScanning {

        @Test
        @DisplayName("scans single @ToolFunction method")
        void scansSingle() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SingleTool());

            assertEquals(1, tools.size());
            assertEquals("ping", tools.get(0).getName());
            assertEquals("Ping", tools.get(0).getDescription());
        }

        @Test
        @DisplayName("scans multiple @ToolFunction methods")
        void scansMultiple() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultipleTools());

            assertEquals(3, tools.size());
            List<String> names = tools.stream().map(Tool::getName).sorted().toList();
            assertEquals(List.of("alpha", "beta", "gamma"), names);
        }

        @Test
        @DisplayName("only scans annotated methods, ignores others")
        void onlyAnnotated() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MixedMethods());

            assertEquals(1, tools.size());
            assertEquals("annotated", tools.get(0).getName());
        }

        @Test
        @DisplayName("returns empty list for object without annotations")
        void emptyForNoAnnotations() {
            List<Tool> tools = AnnotatedToolScanner.scan(new Object());
            assertTrue(tools.isEmpty());
        }
    }

    @Nested
    @DisplayName("Inheritance")
    class Inheritance {

        @Test
        @DisplayName("scans parent class @ToolFunction methods")
        void scansParentMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ChildClass());

            List<String> names = tools.stream().map(Tool::getName).sorted().toList();
            assertTrue(names.contains("parent_tool"), "should include inherited tool");
            assertTrue(names.contains("child_tool"), "should include own tool");
        }

        @Test
        @DisplayName("parent-only scan returns parent tools")
        void parentOnly() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ParentClass());

            assertEquals(1, tools.size());
            assertEquals("parent_tool", tools.get(0).getName());
        }
    }

    @Nested
    @DisplayName("Auto-naming (camelCase → snake_case)")
    class AutoNaming {

        @Test
        @DisplayName("derives snake_case name from method name when name is empty")
        void autoName() {
            List<Tool> tools = AnnotatedToolScanner.scan(new AutoNameTools());

            List<String> names = tools.stream().map(Tool::getName).sorted().toList();
            assertTrue(names.contains("get_user_profile"));
            assertTrue(names.contains("do_multi_word_thing"));
        }
    }

    @Nested
    @DisplayName("Private methods")
    class PrivateMethods {

        @Test
        @DisplayName("scans private @ToolFunction methods and makes them accessible")
        void scansPrivate() {
            List<Tool> tools = AnnotatedToolScanner.scan(new PrivateAnnotatedTool());

            assertEquals(1, tools.size());
            assertEquals("secret", tools.get(0).getName());

            ToolResult result = tools.get(0).execute(Map.of());
            assertFalse(result.isError());
            assertEquals("hidden", result.getContent());
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws NullPointerException for null target")
        void nullTarget() {
            assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
        }
    }
}
