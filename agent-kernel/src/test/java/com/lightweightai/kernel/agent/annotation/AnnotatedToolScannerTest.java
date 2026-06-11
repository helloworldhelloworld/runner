package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner - @ToolFunction scanning and execution")
class AnnotatedToolScannerTest {

    @Nested
    @DisplayName("Scanning")
    class ScanningTests {

        @Test
        @DisplayName("scans annotated methods and generates Tool list")
        void scansAnnotatedMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());

            assertFalse(tools.isEmpty());
            List<String> names = tools.stream().map(Tool::getName).toList();
            assertTrue(names.contains("greet"));
            assertTrue(names.contains("get_status"));
        }

        @Test
        @DisplayName("null target throws NullPointerException")
        void nullTargetThrows() {
            assertThrows(NullPointerException.class, () ->
                    AnnotatedToolScanner.scan(null));
        }

        @Test
        @DisplayName("object without annotations returns empty list")
        void noAnnotationsReturnsEmpty() {
            List<Tool> tools = AnnotatedToolScanner.scan(new Object());
            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("method without explicit name uses camelToSnake")
        void autoNameFromMethodName() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            List<String> names = tools.stream().map(Tool::getName).toList();
            assertTrue(names.contains("get_status"));
        }
    }

    @Nested
    @DisplayName("Execution")
    class ExecutionTests {

        @Test
        @DisplayName("executes tool with parameters and returns result payload")
        void executesWithParams() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool greet = tools.stream().filter(t -> t.getName().equals("greet")).findFirst().orElseThrow();

            ToolResult result = greet.execute(Map.of("name", "World"));

            assertFalse(result.isError());
            assertEquals("Hello, World!", result.getContent());
        }

        @Test
        @DisplayName("executes tool with no parameters")
        void executesNoParams() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool status = tools.stream().filter(t -> t.getName().equals("get_status")).findFirst().orElseThrow();

            ToolResult result = status.execute(Map.of());

            assertFalse(result.isError());
            assertEquals("OK", result.getContent());
        }

        @Test
        @DisplayName("handles method throwing exception gracefully")
        void handlesException() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool failing = tools.stream().filter(t -> t.getName().equals("fail_tool")).findFirst().orElseThrow();

            ToolResult result = failing.execute(Map.of());

            assertTrue(result.isError());
        }

        @Test
        @DisplayName("type converts Number args to target type (int)")
        void convertsNumberToInt() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool add = tools.stream().filter(t -> t.getName().equals("add")).findFirst().orElseThrow();

            ToolResult result = add.execute(Map.of("a", 3.0, "b", 4.0));

            assertFalse(result.isError());
            assertEquals("7", result.getContent());
        }

        @Test
        @DisplayName("null args provides default values for primitives")
        void nullArgsDefaultPrimitives() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool add = tools.stream().filter(t -> t.getName().equals("add")).findFirst().orElseThrow();

            ToolResult result = add.execute(null);

            assertFalse(result.isError());
            assertEquals("0", result.getContent());
        }
    }

    @Nested
    @DisplayName("Schema generation")
    class SchemaTests {

        @Test
        @DisplayName("generates schema with required params from @ToolParam")
        void generatesSchemaWithRequired() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool greet = tools.stream().filter(t -> t.getName().equals("greet")).findFirst().orElseThrow();

            var schema = greet.getSchema();
            assertNotNull(schema);

            Map<String, Object> schemaMap = schema.toMap();
            assertNotNull(schemaMap.get("properties"));

            @SuppressWarnings("unchecked")
            var required = (List<String>) schemaMap.get("required");
            assertNotNull(required);
            assertTrue(required.contains("name"));
        }

        @Test
        @DisplayName("no-param method returns empty schema")
        void noParamEmptySchema() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool status = tools.stream().filter(t -> t.getName().equals("get_status")).findFirst().orElseThrow();

            var schema = status.getSchema();
            assertTrue(schema.toMap().isEmpty() || !schema.toMap().containsKey("properties"));
        }
    }

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("exposes category and tags from annotation")
        void exposesMetadata() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool greet = tools.stream().filter(t -> t.getName().equals("greet")).findFirst().orElseThrow();

            assertTrue(greet instanceof ToolMetadata);
            ToolMetadata meta = (ToolMetadata) greet;
            assertEquals("social", meta.getCategory());
            assertTrue(meta.getTags().contains("greeting"));
            assertTrue(meta.isReadOnly());
        }
    }

    @Nested
    @DisplayName("camelToSnake utility")
    class CamelToSnakeTests {

        @Test
        void simpleConversion() {
            assertEquals("get_user_name", AnnotatedToolWrapper.camelToSnake("getUserName"));
        }

        @Test
        void alreadySnake() {
            assertEquals("hello", AnnotatedToolWrapper.camelToSnake("hello"));
        }

        @Test
        void leadingLowercase() {
            assertEquals("my_method", AnnotatedToolWrapper.camelToSnake("myMethod"));
        }
    }

    @Nested
    @DisplayName("javaTypeToJsonType mapping")
    class TypeMappingTests {

        @Test
        void stringType() {
            assertEquals("string", AnnotatedToolWrapper.javaTypeToJsonType(String.class));
        }

        @Test
        void integerTypes() {
            assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(int.class));
            assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(Integer.class));
            assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(long.class));
        }

        @Test
        void numberTypes() {
            assertEquals("number", AnnotatedToolWrapper.javaTypeToJsonType(double.class));
            assertEquals("number", AnnotatedToolWrapper.javaTypeToJsonType(float.class));
        }

        @Test
        void booleanType() {
            assertEquals("boolean", AnnotatedToolWrapper.javaTypeToJsonType(boolean.class));
            assertEquals("boolean", AnnotatedToolWrapper.javaTypeToJsonType(Boolean.class));
        }

        @Test
        void listType() {
            assertEquals("array", AnnotatedToolWrapper.javaTypeToJsonType(List.class));
        }

        @Test
        void mapType() {
            assertEquals("object", AnnotatedToolWrapper.javaTypeToJsonType(Map.class));
        }

        @Test
        void unknownTypeDefaultsToString() {
            assertEquals("string", AnnotatedToolWrapper.javaTypeToJsonType(Object.class));
        }
    }

    // ==================== Test fixtures ====================

    static class SampleTools {

        @ToolFunction(name = "greet", description = "Say hello",
                category = "social", tags = {"greeting"}, readOnly = true)
        public String greet(@ToolParam(name = "name", required = true, description = "person") String name) {
            return "Hello, " + name + "!";
        }

        @ToolFunction(description = "Get server status")
        public String getStatus() {
            return "OK";
        }

        @ToolFunction(name = "add", description = "Add two ints")
        public String add(@ToolParam(name = "a") int a, @ToolParam(name = "b") int b) {
            return String.valueOf(a + b);
        }

        @ToolFunction(name = "fail_tool", description = "Always fails")
        public String failTool() {
            throw new RuntimeException("boom");
        }
    }
}
