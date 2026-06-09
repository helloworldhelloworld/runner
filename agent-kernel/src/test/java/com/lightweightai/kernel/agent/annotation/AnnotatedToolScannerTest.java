package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner — scan @ToolFunction methods into Tools")
class AnnotatedToolScannerTest {

    @Nested
    @DisplayName("Scanning basics")
    class ScanningBasics {

        @Test
        void scansAnnotatedMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            assertFalse(tools.isEmpty());
            assertTrue(tools.stream().anyMatch(t -> t.getName().equals("greet")));
        }

        @Test
        void rejectsNullTarget() {
            assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
        }

        @Test
        void objectWithNoAnnotationsReturnsEmptyList() {
            List<Tool> tools = AnnotatedToolScanner.scan(new NoToolMethods());
            assertTrue(tools.isEmpty());
        }

        @Test
        void methodNameConvertedToSnakeCase() {
            List<Tool> tools = AnnotatedToolScanner.scan(new CamelCaseTools());
            assertTrue(tools.stream().anyMatch(t -> t.getName().equals("get_user_name")));
        }

        @Test
        void explicitNameOverridesMethodName() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            assertTrue(tools.stream().anyMatch(t -> t.getName().equals("greet")));
        }
    }

    @Nested
    @DisplayName("Tool execution via reflection")
    class ToolExecution {

        @Test
        void executesMethodAndReturnsResult() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst().orElseThrow();

            ToolResult result = greetTool.execute(Map.of("name", "World"));
            assertFalse(result.isError());
            assertEquals("Hello, World!", result.getContent());
        }

        @Test
        void executesNoArgMethod() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool statusTool = tools.stream()
                .filter(t -> t.getName().equals("get_status"))
                .findFirst().orElseThrow();

            ToolResult result = statusTool.execute(Map.of());
            assertFalse(result.isError());
            assertEquals("OK", result.getContent());
        }

        @Test
        void methodExceptionReturnsErrorResult() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool failTool = tools.stream()
                .filter(t -> t.getName().equals("always_fail"))
                .findFirst().orElseThrow();

            ToolResult result = failTool.execute(Map.of());
            assertTrue(result.isError());
            assertTrue(result.getContent().contains("intentional"));
        }
    }

    @Nested
    @DisplayName("Schema generation")
    class SchemaGeneration {

        @Test
        void noParamMethodHasEmptySchema() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool statusTool = tools.stream()
                .filter(t -> t.getName().equals("get_status"))
                .findFirst().orElseThrow();

            assertNotNull(statusTool.getSchema());
            assertTrue(statusTool.getSchema().getProperties().isEmpty());
        }

        @Test
        void annotatedParamsAppearInSchema() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst().orElseThrow();

            Map<String, Object> props = greetTool.getSchema().getProperties();
            assertTrue(props.containsKey("name"));
        }

        @Test
        void descriptionAndAutoExecuteFromAnnotation() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst().orElseThrow();

            assertEquals("Say hello to someone", greetTool.getDescription());
            assertTrue(greetTool.isAutoExecute());
        }
    }

    @Nested
    @DisplayName("Inheritance")
    class Inheritance {

        @Test
        void scansInheritedPublicMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());
            assertTrue(tools.stream().anyMatch(t -> t.getName().equals("parent_tool")));
        }

        @Test
        void scansChildAndParentMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());
            assertTrue(tools.stream().anyMatch(t -> t.getName().equals("child_tool")));
            assertTrue(tools.stream().anyMatch(t -> t.getName().equals("parent_tool")));
        }
    }

    @Nested
    @DisplayName("camelToSnake utility")
    class CamelToSnake {

        @Test
        void convertsSimpleCamelCase() {
            assertEquals("get_user_name", AnnotatedToolWrapper.camelToSnake("getUserName"));
        }

        @Test
        void handlesAllLowerCase() {
            assertEquals("simple", AnnotatedToolWrapper.camelToSnake("simple"));
        }

        @Test
        void handlesLeadingUpperCase() {
            assertEquals("my_tool", AnnotatedToolWrapper.camelToSnake("MyTool"));
        }

        @Test
        void handlesSingleChar() {
            assertEquals("a", AnnotatedToolWrapper.camelToSnake("a"));
        }
    }

    @Nested
    @DisplayName("javaTypeToJsonType utility")
    class TypeMapping {

        @Test
        void mapsCommonTypes() {
            assertEquals("string", AnnotatedToolWrapper.javaTypeToJsonType(String.class));
            assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(int.class));
            assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(Integer.class));
            assertEquals("number", AnnotatedToolWrapper.javaTypeToJsonType(double.class));
            assertEquals("boolean", AnnotatedToolWrapper.javaTypeToJsonType(boolean.class));
            assertEquals("array", AnnotatedToolWrapper.javaTypeToJsonType(List.class));
            assertEquals("object", AnnotatedToolWrapper.javaTypeToJsonType(Map.class));
        }

        @Test
        void unknownTypeDefaultsToString() {
            assertEquals("string", AnnotatedToolWrapper.javaTypeToJsonType(Object.class));
        }
    }

    // ==================== Test fixtures ====================

    static class SampleTools {
        @ToolFunction(name = "greet", description = "Say hello to someone")
        public String greet(@ToolParam(name = "name", required = true) String name) {
            return "Hello, " + name + "!";
        }

        @ToolFunction(description = "Get server status")
        public String getStatus() {
            return "OK";
        }

        @ToolFunction(description = "Always fails")
        public String alwaysFail() {
            throw new RuntimeException("intentional failure");
        }
    }

    static class CamelCaseTools {
        @ToolFunction(description = "Get user name")
        public String getUserName() {
            return "Alice";
        }
    }

    static class NoToolMethods {
        public String notATool() {
            return "nope";
        }
    }

    static class ParentTools {
        @ToolFunction(description = "Parent tool")
        public String parentTool() {
            return "from parent";
        }
    }

    static class ChildTools extends ParentTools {
        @ToolFunction(description = "Child tool")
        public String childTool() {
            return "from child";
        }
    }
}
