package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AnnotatedToolScanner}.
 *
 * Focuses on the scan() entry point: discovery, naming, schema, execution,
 * inheritance, and edge cases. Follows CLAUDE.md UT rules -- every assertion
 * targets the actual payload (tool name, description, schema contents,
 * execution result content), never just "not null" / "no exception".
 */
class AnnotatedToolScannerTest {

    // ==================== Test fixture classes ====================

    static class SampleTools {
        @ToolFunction(name = "greet", description = "Say hello")
        public String greet(
            @ToolParam(name = "name", description = "Person name", required = true) String name
        ) {
            return "Hello, " + name + "!";
        }

        @ToolFunction(description = "Get current time")
        public String getCurrentTime() {
            return "12:00";
        }
    }

    static class MultiParamTools {
        @ToolFunction(name = "compute", description = "Compute result from mixed params")
        public String compute(
            @ToolParam(name = "label", description = "A label", required = true) String label,
            @ToolParam(name = "count", description = "How many", required = true) int count,
            @ToolParam(name = "factor", description = "Scale factor") double factor,
            @ToolParam(name = "verbose", description = "Verbose output") boolean verbose
        ) {
            return label + ":" + count + ":" + factor + ":" + verbose;
        }
    }

    static class ThrowingTool {
        @ToolFunction(name = "explode", description = "Always throws")
        public String explode() {
            throw new IllegalStateException("Boom!");
        }
    }

    static class NoAnnotationClass {
        public String ordinaryMethod() {
            return "not a tool";
        }
    }

    /** Private annotated method -- should still be discovered via getDeclaredMethods. */
    static class PrivateMethodTool {
        @ToolFunction(name = "secret", description = "Private tool")
        private String secretMethod(
            @ToolParam(name = "code", required = true) String code
        ) {
            return "secret:" + code;
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

    static class CamelCaseTools {
        @ToolFunction(description = "Get user name")
        public String getUserName() {
            return "alice";
        }

        @ToolFunction(description = "Process HTTP request")
        public String processHttpRequest() {
            return "processed";
        }

        @ToolFunction(description = "Simple")
        public String simple() {
            return "ok";
        }
    }

    static class ToolResultReturner {
        @ToolFunction(name = "raw_result", description = "Returns ToolResult directly")
        public ToolResult rawResult(
            @ToolParam(name = "fail", description = "Whether to fail", required = true) boolean fail
        ) {
            if (fail) {
                return ToolResult.error("Intentional failure");
            }
            return ToolResult.success("Direct success");
        }
    }

    static class NullReturner {
        @ToolFunction(name = "null_return", description = "Returns null")
        public String nullReturn() {
            return null;
        }
    }

    // ==================== Discovery tests ====================

    @Nested
    class Discovery {

        @Test
        void scanFindsAllAnnotatedMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());

            assertEquals(2, tools.size());
            Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
            assertEquals(Set.of("greet", "get_current_time"), names);
        }

        @Test
        void scanFindsPrivateMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new PrivateMethodTool());

            assertEquals(1, tools.size());
            assertEquals("secret", tools.get(0).getName());
            assertEquals("Private tool", tools.get(0).getDescription());
        }

        @Test
        void scanFindsInheritedPublicMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());

            assertEquals(2, tools.size());
            Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
            assertTrue(names.contains("parent_tool"),
                "Inherited @ToolFunction from parent should be discovered");
            assertTrue(names.contains("child_tool"),
                "Child's own @ToolFunction should be discovered");
        }

        @Test
        void scanWithNoAnnotatedMethodsReturnsEmptyList() {
            List<Tool> tools = AnnotatedToolScanner.scan(new NoAnnotationClass());

            assertTrue(tools.isEmpty(),
                "Object with no @ToolFunction methods should yield empty list");
        }

        @Test
        void scanPlainObjectReturnsEmptyList() {
            List<Tool> tools = AnnotatedToolScanner.scan(new Object());

            assertTrue(tools.isEmpty());
        }

        @Test
        void scanNullTargetThrowsNPE() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                () -> AnnotatedToolScanner.scan(null));
            assertEquals("Target object cannot be null", ex.getMessage());
        }
    }

    // ==================== Naming tests ====================

    @Nested
    class Naming {

        @Test
        void explicitNameOverridesMethodName() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool greet = findTool(tools, "greet");

            assertEquals("greet", greet.getName(),
                "Explicit @ToolFunction.name should be used verbatim");
        }

        @Test
        void missingNameFallsToCamelToSnake() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool time = findTool(tools, "get_current_time");

            assertEquals("get_current_time", time.getName(),
                "getCurrentTime should become get_current_time via camelToSnake");
        }

        @Test
        void camelToSnakeConversionsThroughActualToolNames() {
            List<Tool> tools = AnnotatedToolScanner.scan(new CamelCaseTools());
            Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());

            assertTrue(names.contains("get_user_name"),
                "getUserName -> get_user_name");
            assertTrue(names.contains("process_http_request"),
                "processHttpRequest -> process_http_request");
            assertTrue(names.contains("simple"),
                "simple -> simple (no uppercase, no underscores added)");
        }
    }

    // ==================== Description tests ====================

    @Nested
    class Description {

        @Test
        void descriptionMatchesAnnotation() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());

            Tool greet = findTool(tools, "greet");
            assertEquals("Say hello", greet.getDescription());

            Tool time = findTool(tools, "get_current_time");
            assertEquals("Get current time", time.getDescription());
        }

        @Test
        void descriptionForMultiParamTool() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTools());
            Tool compute = tools.get(0);

            assertEquals("Compute result from mixed params", compute.getDescription());
        }
    }

    // ==================== Schema tests ====================

    @Nested
    class Schema {

        @Test
        void schemaContainsCorrectPropertiesAndRequired() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool greet = findTool(tools, "greet");

            ToolSchema schema = greet.getSchema();
            Map<String, Object> schemaMap = schema.toMap();

            assertEquals("object", schemaMap.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
            assertTrue(properties.containsKey("name"), "Should have 'name' property");

            @SuppressWarnings("unchecked")
            Map<String, Object> nameProp = (Map<String, Object>) properties.get("name");
            assertEquals("string", nameProp.get("type"));
            assertEquals("Person name", nameProp.get("description"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schemaMap.get("required");
            assertNotNull(required, "Required list should be present for tool with required params");
            assertTrue(required.contains("name"));
        }

        @Test
        void schemaForNoParamMethodIsEmpty() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool time = findTool(tools, "get_current_time");

            ToolSchema schema = time.getSchema();
            Map<String, Object> schemaMap = schema.toMap();

            assertEquals("object", schemaMap.get("type"));
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
            assertTrue(properties.isEmpty(), "No-param tool should have empty properties");
        }

        @Test
        void schemaTypesMappedFromJavaTypes() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTools());
            Tool compute = tools.get(0);

            @SuppressWarnings("unchecked")
            Map<String, Object> properties =
                (Map<String, Object>) compute.getSchema().toMap().get("properties");

            @SuppressWarnings("unchecked")
            Map<String, Object> labelProp = (Map<String, Object>) properties.get("label");
            assertEquals("string", labelProp.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> countProp = (Map<String, Object>) properties.get("count");
            assertEquals("integer", countProp.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> factorProp = (Map<String, Object>) properties.get("factor");
            assertEquals("number", factorProp.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> verboseProp = (Map<String, Object>) properties.get("verbose");
            assertEquals("boolean", verboseProp.get("type"));
        }

        @Test
        void schemaRequiredOnlyIncludesRequiredParams() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTools());
            Tool compute = tools.get(0);

            Map<String, Object> schemaMap = compute.getSchema().toMap();

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schemaMap.get("required");
            assertNotNull(required);
            assertEquals(2, required.size(), "Only label and count are required=true");
            assertTrue(required.contains("label"));
            assertTrue(required.contains("count"));
            assertFalse(required.contains("factor"), "factor is optional");
            assertFalse(required.contains("verbose"), "verbose is optional");
        }

        @Test
        void schemaOmitsRequiredKeyWhenNoRequiredParams() {
            // CamelCaseTools methods have no @ToolParam(required=true)
            List<Tool> tools = AnnotatedToolScanner.scan(new CamelCaseTools());
            Tool simple = findTool(tools, "simple");

            Map<String, Object> schemaMap = simple.getSchema().toMap();
            // When no params, schema is empty() which has no "required" key
            assertFalse(schemaMap.containsKey("required"),
                "Schema with no required params should omit 'required' key");
        }

        @Test
        void schemaPropertyDescriptionOmittedWhenEmpty() {
            List<Tool> tools = AnnotatedToolScanner.scan(new PrivateMethodTool());
            Tool secret = tools.get(0);

            @SuppressWarnings("unchecked")
            Map<String, Object> properties =
                (Map<String, Object>) secret.getSchema().toMap().get("properties");

            @SuppressWarnings("unchecked")
            Map<String, Object> codeProp = (Map<String, Object>) properties.get("code");
            assertEquals("string", codeProp.get("type"));
            // @ToolParam(name = "code", required = true) has no description=""
            // AnnotatedToolWrapper omits description when it's empty
            assertFalse(codeProp.containsKey("description"),
                "Empty description should be omitted from schema property");
        }
    }

    // ==================== Execution tests ====================

    @Nested
    class Execution {

        @Test
        void executeWithValidArgsReturnsSuccessWithCorrectContent() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool greet = findTool(tools, "greet");

            ToolResult result = greet.execute(Map.of("name", "World"));

            assertFalse(result.isError(), "Successful execution should not be error");
            assertEquals("Hello, World!", result.getContent(),
                "Content should be the method's return value");
        }

        @Test
        void executeNoArgMethodReturnsSuccess() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
            Tool time = findTool(tools, "get_current_time");

            ToolResult result = time.execute(Map.of());

            assertFalse(result.isError());
            assertEquals("12:00", result.getContent());
        }

        @Test
        void executeMultiParamToolPassesAllArgs() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTools());
            Tool compute = tools.get(0);

            ToolResult result = compute.execute(Map.of(
                "label", "test",
                "count", 3,
                "factor", 2.5,
                "verbose", true
            ));

            assertFalse(result.isError());
            assertEquals("test:3:2.5:true", result.getContent());
        }

        @Test
        void executeMissingOptionalArgsUsesDefaults() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTools());
            Tool compute = tools.get(0);

            // Only pass required args; optional int/double/boolean default to 0/0.0/false
            ToolResult result = compute.execute(Map.of("label", "sparse", "count", 1));

            assertFalse(result.isError());
            assertEquals("sparse:1:0.0:false", result.getContent());
        }

        @Test
        void executeMethodThatThrowsReturnsErrorToolResult() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ThrowingTool());
            Tool explode = tools.get(0);

            ToolResult result = explode.execute(Map.of());

            assertTrue(result.isError(), "Exception should produce error ToolResult");
            assertEquals("Boom!", result.getContent(),
                "Error content should be the exception message");
        }

        @Test
        void executePrivateMethodWorks() {
            List<Tool> tools = AnnotatedToolScanner.scan(new PrivateMethodTool());
            Tool secret = tools.get(0);

            ToolResult result = secret.execute(Map.of("code", "42"));

            assertFalse(result.isError());
            assertEquals("secret:42", result.getContent());
        }

        @Test
        void executeInheritedMethodWorks() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());
            Tool parent = findTool(tools, "parent_tool");

            ToolResult result = parent.execute(Map.of());

            assertFalse(result.isError());
            assertEquals("from parent", result.getContent());
        }

        @Test
        void executeMethodReturningToolResultDirectly() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ToolResultReturner());
            Tool raw = tools.get(0);

            // Success path
            ToolResult success = raw.execute(Map.of("fail", false));
            assertFalse(success.isError());
            assertEquals("Direct success", success.getContent());

            // Error path (method returns ToolResult.error, not an exception)
            ToolResult failure = raw.execute(Map.of("fail", true));
            assertTrue(failure.isError());
            assertEquals("Intentional failure", failure.getContent());
        }

        @Test
        void executeMethodReturningNullYieldsEmptyContent() {
            List<Tool> tools = AnnotatedToolScanner.scan(new NullReturner());
            Tool tool = tools.get(0);

            ToolResult result = tool.execute(Map.of());

            assertFalse(result.isError());
            assertEquals("", result.getContent(),
                "Null return should be converted to empty string");
        }
    }

    // ==================== No-duplicate test for inherited methods ====================

    @Nested
    class InheritanceDuplicates {

        @Test
        void childOwnMethodNotDuplicated() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());

            // child_tool should appear exactly once (declared in ChildTools, also
            // visible via getMethods, but scan skips methods whose declaring class == clazz)
            long childCount = tools.stream()
                .filter(t -> t.getName().equals("child_tool"))
                .count();
            assertEquals(1, childCount,
                "Child's own method should not be duplicated by the inherited-method scan");
        }
    }

    // ==================== Helper ====================

    private static Tool findTool(List<Tool> tools, String name) {
        return tools.stream()
            .filter(t -> t.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "Expected tool '" + name + "' not found in: "
                + tools.stream().map(Tool::getName).collect(Collectors.toList())));
    }
}
