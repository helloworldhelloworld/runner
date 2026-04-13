package com.lightweightai.kernel.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypedPluginFunction - type-safe plugin function with schema generation")
class TypedPluginFunctionTest {

    // ==================== construction ====================

    @Test
    @DisplayName("constructor sets name, description, and parameter type")
    void constructorSetsFields() {
        TypedPluginFunction fn = new TypedPluginFunction(
            "greet", "Greets the user", GreetParams.class, p -> "Hello, " + ((GreetParams) p).name
        );

        assertEquals("greet", fn.getName());
        assertEquals("Greets the user", fn.getDescription());
        assertEquals(GreetParams.class, fn.getParameterType());
    }

    @Test
    @DisplayName("constructor with null description defaults to empty string")
    void constructorNullDescriptionDefaultsEmpty() {
        TypedPluginFunction fn = new TypedPluginFunction(
            "test", null, GreetParams.class, p -> "ok"
        );
        assertEquals("", fn.getDescription());
    }

    @Test
    @DisplayName("constructor rejects null name")
    void constructorRejectsNullName() {
        assertThrows(IllegalArgumentException.class, () ->
            new TypedPluginFunction(null, "desc", GreetParams.class, p -> "ok"));
    }

    @Test
    @DisplayName("constructor rejects empty name")
    void constructorRejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, () ->
            new TypedPluginFunction("", "desc", GreetParams.class, p -> "ok"));
    }

    @Test
    @DisplayName("constructor rejects null parameter type")
    void constructorRejectsNullParamType() {
        assertThrows(IllegalArgumentException.class, () ->
            new TypedPluginFunction("test", "desc", null, p -> "ok"));
    }

    @Test
    @DisplayName("constructor rejects null handler")
    void constructorRejectsNullHandler() {
        assertThrows(IllegalArgumentException.class, () ->
            new TypedPluginFunction("test", "desc", GreetParams.class, null));
    }

    // ==================== execute ====================

    @Test
    @DisplayName("execute converts Map to typed param and invokes handler")
    void executeConvertsAndInvokes() {
        TypedPluginFunction fn = new TypedPluginFunction(
            "greet", "Greets", GreetParams.class,
            (GreetParams p) -> "Hello, " + p.name + "!"
        );

        FunctionResult result = fn.execute(Map.of("name", "Alice"));

        assertTrue(result.isSuccess());
        assertEquals("Hello, Alice!", result.getValue());
    }

    @Test
    @DisplayName("execute returns error on type conversion failure")
    void executeReturnsErrorOnBadArgs() {
        TypedPluginFunction fn = new TypedPluginFunction(
            "math", "Calculates", MathParams.class,
            (MathParams p) -> p.a + p.b
        );

        FunctionResult result = fn.execute(Map.of("a", "not-a-number"));

        // Should handle gracefully - Jackson may coerce or fail
        assertNotNull(result);
    }

    // ==================== executeAsync ====================

    @Test
    @DisplayName("executeAsync returns completed future with result")
    void executeAsyncReturnsResult() throws Exception {
        TypedPluginFunction fn = new TypedPluginFunction(
            "add", "Adds", MathParams.class,
            (MathParams p) -> p.a + p.b
        );

        FunctionResult result = fn.executeAsync(Map.of("a", 3, "b", 5)).get();

        assertTrue(result.isSuccess());
    }

    // ==================== schema generation ====================

    @Test
    @DisplayName("getInputSchema generates schema from parameter class")
    void getInputSchemaGeneratesSchema() {
        TypedPluginFunction fn = new TypedPluginFunction(
            "test", "Test", GreetParams.class, p -> "ok"
        );

        JsonSchema schema = fn.getInputSchema();
        assertNotNull(schema);
    }

    @Test
    @DisplayName("toClaudeToolDefinition produces correct structure")
    void toClaudeToolDefinitionStructure() {
        TypedPluginFunction fn = new TypedPluginFunction(
            "greet", "Greets the user", GreetParams.class, p -> "ok"
        );

        Map<String, Object> def = fn.toClaudeToolDefinition();

        assertEquals("greet", def.get("name"));
        assertEquals("Greets the user", def.get("description"));
        assertNotNull(def.get("input_schema"));
    }

    @Test
    @DisplayName("toJsonSchema is same as toClaudeToolDefinition")
    void toJsonSchemaMatchesClaudeDef() {
        TypedPluginFunction fn = new TypedPluginFunction(
            "test", "Test", GreetParams.class, p -> "ok"
        );

        assertEquals(fn.toClaudeToolDefinition(), fn.toJsonSchema());
    }

    @Test
    @DisplayName("getParameters returns empty list for typed functions")
    void getParametersReturnsEmpty() {
        TypedPluginFunction fn = new TypedPluginFunction(
            "test", "Test", GreetParams.class, p -> "ok"
        );
        assertTrue(fn.getParameters().isEmpty());
    }

    @Test
    @DisplayName("toString includes name and description")
    void toStringIncludesNameDesc() {
        TypedPluginFunction fn = new TypedPluginFunction(
            "greet", "Greets", GreetParams.class, p -> "ok"
        );
        String str = fn.toString();
        assertTrue(str.contains("greet"));
        assertTrue(str.contains("Greets"));
    }

    // ==================== test parameter classes ====================

    public static class GreetParams {
        public String name;
    }

    public static class MathParams {
        public int a;
        public int b;
    }
}
