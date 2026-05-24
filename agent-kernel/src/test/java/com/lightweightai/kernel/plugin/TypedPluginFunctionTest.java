package com.lightweightai.kernel.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypedPluginFunction — type-safe plugin function with auto-generated schema")
class TypedPluginFunctionTest {

    public static class CalculatorInput {
        @JsonProperty("expression")
        public String expression;

        @JsonProperty(value = "precision", required = true)
        public int precision;
    }

    public static class GreetInput {
        @JsonProperty("name")
        public String name;
    }

    @Test
    @DisplayName("executes handler with typed parameter conversion")
    void executesWithTypedArgs() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "greet", "Greet someone",
                GreetInput.class,
                (GreetInput input) -> "Hello, " + input.name + "!");

        FunctionResult result = fn.execute(Map.of("name", "Alice"));

        assertTrue(result.isSuccess());
        assertEquals("Hello, Alice!", result.getValue());
    }

    @Test
    @DisplayName("returns error result when handler throws exception")
    void handlerException() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "fail", "Always fails",
                GreetInput.class,
                (GreetInput input) -> { throw new RuntimeException("boom"); });

        FunctionResult result = fn.execute(Map.of("name", "Bob"));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("boom"));
    }

    @Test
    @DisplayName("returns error result when type conversion fails")
    void typeConversionFailure() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "calc", "Calculate",
                CalculatorInput.class,
                (CalculatorInput input) -> input.expression);

        FunctionResult result = fn.execute(Map.of("precision", "not-a-number"));

        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("generates input schema from parameter class")
    void generatesSchema() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "calc", "Calculate",
                CalculatorInput.class,
                (CalculatorInput input) -> "result");

        JsonSchema schema = fn.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.getType());
        assertTrue(schema.getProperties().containsKey("expression"));
        assertTrue(schema.getProperties().containsKey("precision"));
    }

    @Test
    @DisplayName("toClaudeToolDefinition contains name, description, input_schema")
    void toClaudeToolDefinition() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "my_fn", "My function",
                GreetInput.class,
                (GreetInput input) -> "hi");

        Map<String, Object> def = fn.toClaudeToolDefinition();

        assertEquals("my_fn", def.get("name"));
        assertEquals("My function", def.get("description"));
        assertNotNull(def.get("input_schema"));
    }

    @Test
    @DisplayName("toJsonSchema returns same as toClaudeToolDefinition")
    void toJsonSchemaMatchesTool() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "test", "Test",
                GreetInput.class,
                (GreetInput input) -> "ok");

        assertEquals(fn.toClaudeToolDefinition(), fn.toJsonSchema());
    }

    @Test
    @DisplayName("executeAsync returns completed future")
    void executeAsync() throws Exception {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "greet", "Greet",
                GreetInput.class,
                (GreetInput input) -> "Hello, " + input.name);

        CompletableFuture<FunctionResult> future =
                fn.executeAsync(Map.of("name", "Eve"));

        FunctionResult result = future.get();
        assertTrue(result.isSuccess());
        assertEquals("Hello, Eve", result.getValue());
    }

    @Test
    @DisplayName("getName and getDescription return constructor values")
    void gettersReturnConstructorValues() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "my_func", "desc",
                GreetInput.class,
                (GreetInput input) -> "ok");

        assertEquals("my_func", fn.getName());
        assertEquals("desc", fn.getDescription());
        assertEquals(GreetInput.class, fn.getParameterType());
    }

    @Test
    @DisplayName("null name throws")
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction<>(null, "d", GreetInput.class, (GreetInput i) -> "ok"));
    }

    @Test
    @DisplayName("null parameter type throws")
    void nullParameterTypeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction<>("n", "d", null, (Object i) -> "ok"));
    }

    @Test
    @DisplayName("null handler throws")
    void nullHandlerThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction<>("n", "d", GreetInput.class, null));
    }

    @Test
    @DisplayName("null description defaults to empty string")
    void nullDescriptionDefaults() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "n", null, GreetInput.class, (GreetInput i) -> "ok");
        assertEquals("", fn.getDescription());
    }

    @Test
    @DisplayName("getParameters returns empty list (compatibility)")
    void getParametersEmpty() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "n", "d", GreetInput.class, (GreetInput i) -> "ok");
        assertTrue(fn.getParameters().isEmpty());
    }

    @Test
    @DisplayName("toString contains name and description")
    void toStringContent() {
        TypedPluginFunction fn = new TypedPluginFunction<>(
                "my_func", "My function", GreetInput.class, (GreetInput i) -> "ok");
        String s = fn.toString();
        assertTrue(s.contains("my_func"));
        assertTrue(s.contains("My function"));
    }
}
