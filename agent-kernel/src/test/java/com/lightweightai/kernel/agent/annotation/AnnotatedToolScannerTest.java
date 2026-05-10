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

@DisplayName("AnnotatedToolScanner — @ToolFunction discovery and Tool generation")
class AnnotatedToolScannerTest {

    // ==================== Test fixtures ====================

    static class BasicTools {
        @ToolFunction(name = "greet", description = "Say hello")
        public String greet(@ToolParam(name = "name", required = true) String name) {
            return "Hello, " + name + "!";
        }

        @ToolFunction(description = "Get server status")
        public String getServerStatus() {
            return "OK";
        }
    }

    static class MetadataTools {
        @ToolFunction(name = "read_file", description = "Read a file",
                category = "file", tags = {"io", "read"}, readOnly = true, idempotent = true)
        public String readFile(@ToolParam(name = "path", required = true, description = "file path") String path) {
            return "content of " + path;
        }
    }

    static class MultiParamTools {
        @ToolFunction(name = "add", description = "Add two numbers")
        public String add(
                @ToolParam(name = "a", required = true, description = "First number") double a,
                @ToolParam(name = "b", required = true, description = "Second number") double b) {
            return String.valueOf(a + b);
        }

        @ToolFunction(name = "concat", description = "Concat strings")
        public String concat(
                @ToolParam(name = "left", required = true) String left,
                @ToolParam(name = "right") String right) {
            return left + (right != null ? right : "");
        }
    }

    static class ToolResultReturningTools {
        @ToolFunction(name = "safe_op", description = "Returns ToolResult directly")
        public ToolResult safeOp(@ToolParam(name = "input", required = true) String input) {
            if ("fail".equals(input)) {
                return ToolResult.error("intentional failure");
            }
            return ToolResult.success("processed: " + input);
        }
    }

    static class ParentClass {
        @ToolFunction(name = "inherited_tool", description = "Inherited from parent")
        public String parentTool() {
            return "from parent";
        }
    }

    static class ChildClass extends ParentClass {
        @ToolFunction(name = "child_tool", description = "Defined in child")
        public String childTool() {
            return "from child";
        }
    }

    // ==================== Tests ====================

    @Nested
    @DisplayName("Discovery")
    class DiscoveryTests {

        @Test
        @DisplayName("scan finds all @ToolFunction methods and generates correct tool names")
        void scanFindsAnnotatedMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new BasicTools());

            assertEquals(2, tools.size());
            assertTrue(tools.stream().anyMatch(t -> "greet".equals(t.getName())),
                    "Should find 'greet' tool");
            assertTrue(tools.stream().anyMatch(t -> "get_server_status".equals(t.getName())),
                    "Should find 'get_server_status' tool (camelCase -> snake_case)");
        }

        @Test
        @DisplayName("scan discovers inherited @ToolFunction from parent class")
        void scanFindsInheritedMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ChildClass());

            assertTrue(tools.stream().anyMatch(t -> "child_tool".equals(t.getName())),
                    "Should find tool from child class");
            assertTrue(tools.stream().anyMatch(t -> "inherited_tool".equals(t.getName())),
                    "Should find tool inherited from parent class");
        }

        @Test
        @DisplayName("scan on object with no @ToolFunction returns empty list")
        void scanEmptyObjectReturnsEmpty() {
            List<Tool> tools = AnnotatedToolScanner.scan(new Object());
            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("scan throws NPE on null target")
        void scanNullThrows() {
            assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
        }
    }

    @Nested
    @DisplayName("Schema generation")
    class SchemaTests {

        @Test
        @DisplayName("tool with required param generates correct schema with 'required' array")
        void requiredParamInSchema() {
            List<Tool> tools = AnnotatedToolScanner.scan(new BasicTools());
            Tool greet = tools.stream().filter(t -> "greet".equals(t.getName())).findFirst().orElseThrow();

            Map<String, Object> schema = greet.getSchema().toMap();
            assertEquals("object", schema.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertTrue(props.containsKey("name"), "Should have 'name' property");

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertNotNull(required, "Should have 'required' array");
            assertTrue(required.contains("name"), "name should be required");
        }

        @Test
        @DisplayName("no-arg tool generates empty schema")
        void noArgToolEmptySchema() {
            List<Tool> tools = AnnotatedToolScanner.scan(new BasicTools());
            Tool status = tools.stream().filter(t -> "get_server_status".equals(t.getName())).findFirst().orElseThrow();

            Map<String, Object> props = status.getSchema().getProperties();
            assertTrue(props.isEmpty(), "No-arg tool should have empty properties");
        }

        @Test
        @DisplayName("multi-param tool generates schema with correct types and required subset")
        void multiParamSchema() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTools());
            Tool concat = tools.stream().filter(t -> "concat".equals(t.getName())).findFirst().orElseThrow();

            Map<String, Object> schema = concat.getSchema().toMap();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertTrue(props.containsKey("left"));
            assertTrue(props.containsKey("right"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertNotNull(required);
            assertTrue(required.contains("left"), "left should be required");
            assertFalse(required.contains("right"), "right should NOT be required");
        }

        @Test
        @DisplayName("numeric param type maps to correct JSON Schema type")
        void numericTypeMapping() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTools());
            Tool add = tools.stream().filter(t -> "add".equals(t.getName())).findFirst().orElseThrow();

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) add.getSchema().toMap().get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> paramA = (Map<String, Object>) props.get("a");
            assertEquals("number", paramA.get("type"), "double should map to 'number'");
        }
    }

    @Nested
    @DisplayName("Execution — payload travels through the chain")
    class ExecutionTests {

        @Test
        @DisplayName("execute with correct args returns success with actual result content")
        void executeReturnsContent() {
            List<Tool> tools = AnnotatedToolScanner.scan(new BasicTools());
            Tool greet = tools.stream().filter(t -> "greet".equals(t.getName())).findFirst().orElseThrow();

            ToolResult result = greet.execute(Map.of("name", "World"));

            assertFalse(result.isError());
            assertEquals("Hello, World!", result.getContent());
        }

        @Test
        @DisplayName("execute with numeric args performs type conversion correctly")
        void executeWithTypeConversion() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTools());
            Tool add = tools.stream().filter(t -> "add".equals(t.getName())).findFirst().orElseThrow();

            ToolResult result = add.execute(Map.of("a", 3, "b", 4));

            assertFalse(result.isError());
            assertEquals("7.0", result.getContent());
        }

        @Test
        @DisplayName("execute passes through ToolResult returned by method")
        void executePassesThroughToolResult() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ToolResultReturningTools());
            Tool op = tools.stream().filter(t -> "safe_op".equals(t.getName())).findFirst().orElseThrow();

            ToolResult success = op.execute(Map.of("input", "hello"));
            assertFalse(success.isError());
            assertEquals("processed: hello", success.getContent());

            ToolResult failure = op.execute(Map.of("input", "fail"));
            assertTrue(failure.isError());
        }

        @Test
        @DisplayName("execute with missing optional arg uses default value")
        void executeMissingOptionalArg() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTools());
            Tool concat = tools.stream().filter(t -> "concat".equals(t.getName())).findFirst().orElseThrow();

            ToolResult result = concat.execute(Map.of("left", "abc"));
            assertFalse(result.isError());
            assertEquals("abc", result.getContent());
        }
    }

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("ToolMetadata is populated from @ToolFunction annotations")
        void metadataFromAnnotation() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MetadataTools());
            Tool readFile = tools.stream().filter(t -> "read_file".equals(t.getName())).findFirst().orElseThrow();

            assertInstanceOf(ToolMetadata.class, readFile);
            ToolMetadata meta = (ToolMetadata) readFile;

            assertEquals("file", meta.getCategory());
            assertEquals(List.of("io", "read"), meta.getTags());
            assertTrue(meta.isReadOnly());
            assertTrue(meta.isIdempotent());
        }

        @Test
        @DisplayName("description is correctly populated")
        void descriptionPopulated() {
            List<Tool> tools = AnnotatedToolScanner.scan(new BasicTools());
            Tool greet = tools.stream().filter(t -> "greet".equals(t.getName())).findFirst().orElseThrow();

            assertEquals("Say hello", greet.getDescription());
        }
    }

    @Nested
    @DisplayName("camelToSnake")
    class CamelToSnakeTests {

        @Test
        @DisplayName("converts camelCase to snake_case")
        void basicConversion() {
            assertEquals("get_user_name", AnnotatedToolWrapper.camelToSnake("getUserName"));
            assertEquals("get_server_status", AnnotatedToolWrapper.camelToSnake("getServerStatus"));
        }

        @Test
        @DisplayName("single lowercase word unchanged")
        void singleWord() {
            assertEquals("hello", AnnotatedToolWrapper.camelToSnake("hello"));
        }

        @Test
        @DisplayName("already snake_case unchanged")
        void alreadySnake() {
            assertEquals("get_name", AnnotatedToolWrapper.camelToSnake("get_name"));
        }
    }
}
