package com.lightweightai.kernel.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypedPluginFunction - type-safe plugin function")
class TypedPluginFunctionTest {

    static class TestParams {
        public String message;
        public int count;
    }

    private TypedPluginFunction createFunction(String name, String description) {
        return new TypedPluginFunction(
            name,
            description,
            TestParams.class,
            (TestParams params) -> "Received: " + params.message + " x" + params.count
        );
    }

    @Nested
    @DisplayName("Given constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("null name throws IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction(null, "desc", TestParams.class, p -> "ok"));
        }

        @Test
        @DisplayName("empty name throws IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction("", "desc", TestParams.class, p -> "ok"));
        }

        @Test
        @DisplayName("blank name throws IllegalArgumentException")
        void blankNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction("   ", "desc", TestParams.class, p -> "ok"));
        }

        @Test
        @DisplayName("null parameterType throws IllegalArgumentException")
        void nullParamTypeThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction("test", "desc", null, p -> "ok"));
        }

        @Test
        @DisplayName("null handler throws IllegalArgumentException")
        void nullHandlerThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction("test", "desc", TestParams.class, null));
        }

        @Test
        @DisplayName("null description defaults to empty string")
        void nullDescriptionDefaultsToEmpty() {
            TypedPluginFunction fn = new TypedPluginFunction(
                "test", null, TestParams.class, (TestParams p) -> "ok"
            );

            assertEquals("", fn.getDescription());
        }

        @Test
        @DisplayName("valid inputs create function successfully")
        void validInputsSucceed() {
            TypedPluginFunction fn = createFunction("greet", "A greeting function");

            assertEquals("greet", fn.getName());
            assertEquals("A greeting function", fn.getDescription());
            assertEquals(TestParams.class, fn.getParameterType());
        }
    }

    @Nested
    @DisplayName("Given execute with arguments")
    class Execution {

        @Test
        @DisplayName("converts map to typed params and returns success result")
        void shouldConvertAndExecuteSuccessfully() {
            // Given
            TypedPluginFunction fn = createFunction("echo", "Echo function");
            Map<String, Object> args = Map.of("message", "hello", "count", 3);

            // When
            FunctionResult result = fn.execute(args);

            // Then
            assertTrue(result.isSuccess());
            assertEquals("Received: hello x3", result.getValue());
        }

        @Test
        @DisplayName("handler exception returns error result")
        void shouldReturnErrorOnHandlerException() {
            // Given
            TypedPluginFunction fn = new TypedPluginFunction(
                "fail", "Failing function",
                TestParams.class,
                (TestParams p) -> { throw new RuntimeException("Intentional failure"); }
            );
            Map<String, Object> args = Map.of("message", "test", "count", 1);

            // When
            FunctionResult result = fn.execute(args);

            // Then
            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("Intentional failure"));
        }

        @Test
        @DisplayName("invalid argument types return error result")
        void shouldReturnErrorOnInvalidArgTypes() {
            // Given
            TypedPluginFunction fn = createFunction("test", "Test");
            // "count" expects an int but gets a non-convertible string
            Map<String, Object> args = Map.of("message", "ok", "count", "not-a-number");

            // When
            FunctionResult result = fn.execute(args);

            // Then
            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
        }
    }

    @Nested
    @DisplayName("Given toClaudeToolDefinition")
    class ToolDefinition {

        @Test
        @DisplayName("returns map with name, description, and input_schema")
        void shouldReturnCorrectStructure() {
            // Given
            TypedPluginFunction fn = createFunction("myTool", "Does stuff");

            // When
            Map<String, Object> definition = fn.toClaudeToolDefinition();

            // Then
            assertEquals("myTool", definition.get("name"));
            assertEquals("Does stuff", definition.get("description"));
            assertNotNull(definition.get("input_schema"));
            assertInstanceOf(Map.class, definition.get("input_schema"));
        }

        @Test
        @DisplayName("input_schema has object type with properties")
        void shouldHaveObjectSchemaWithProperties() {
            // Given
            TypedPluginFunction fn = createFunction("myTool", "Does stuff");

            // When
            Map<String, Object> definition = fn.toClaudeToolDefinition();

            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) definition.get("input_schema");

            // Then
            assertEquals("object", schema.get("type"));
            assertNotNull(schema.get("properties"));

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            assertTrue(properties.containsKey("message"), "schema should have 'message' property");
            assertTrue(properties.containsKey("count"), "schema should have 'count' property");
        }

        @Test
        @DisplayName("toJsonSchema returns same result as toClaudeToolDefinition")
        void toJsonSchemaDelegatesToClaude() {
            TypedPluginFunction fn = createFunction("myTool", "desc");

            assertEquals(fn.toClaudeToolDefinition(), fn.toJsonSchema());
        }
    }

    @Nested
    @DisplayName("Given PluginFunction interface contract")
    class PluginFunctionContract {

        @Test
        @DisplayName("getParameters returns empty list for typed functions")
        void getParametersReturnsEmptyList() {
            TypedPluginFunction fn = createFunction("test", "desc");

            assertTrue(fn.getParameters().isEmpty());
        }

        @Test
        @DisplayName("implements PluginFunction interface")
        void implementsPluginFunction() {
            TypedPluginFunction fn = createFunction("test", "desc");

            assertInstanceOf(PluginFunction.class, fn);
        }

        @Test
        @DisplayName("toString contains name and description")
        void toStringContainsNameAndDescription() {
            TypedPluginFunction fn = createFunction("myFunc", "my description");

            String str = fn.toString();

            assertTrue(str.contains("myFunc"));
            assertTrue(str.contains("my description"));
        }
    }
}
