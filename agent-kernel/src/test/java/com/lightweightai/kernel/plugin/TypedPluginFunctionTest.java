package com.lightweightai.kernel.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests TypedPluginFunction — verifies the full chain:
 * Map arguments → Jackson deserialization → handler execution → FunctionResult
 *
 * This is a producer→consumer transmission test: the LLM produces Map<String,Object>
 * args, and the handler consumes a typed Java object. Any marshaling error here
 * means tool calls silently fail.
 */
@DisplayName("TypedPluginFunction - 类型安全函数执行")
class TypedPluginFunctionTest {

    @Test
    @DisplayName("execute 将 Map 参数转为类型化对象并调用 handler")
    void executeConvertsMapToTypedParam() {
        TypedPluginFunction fn = new TypedPluginFunction(
                "add",
                "Add two numbers",
                AddParams.class,
                (AddParams p) -> p.a + p.b
        );

        FunctionResult result = fn.execute(Map.of("a", 3, "b", 7));

        assertTrue(result.isSuccess());
        assertEquals(10, result.getValue());
    }

    @Test
    @DisplayName("execute 处理嵌套对象参数")
    void executeHandlesNestedObjects() {
        TypedPluginFunction fn = new TypedPluginFunction(
                "greet",
                "Greet a person",
                GreetParams.class,
                (GreetParams p) -> "Hello, " + p.name + "! Age: " + p.age
        );

        FunctionResult result = fn.execute(Map.of("name", "Alice", "age", 30));

        assertTrue(result.isSuccess());
        assertEquals("Hello, Alice! Age: 30", result.getValue());
    }

    @Test
    @DisplayName("execute 在类型转换失败时返回 error result")
    void executeReturnsErrorOnInvalidArgs() {
        TypedPluginFunction fn = new TypedPluginFunction(
                "add",
                "Add two numbers",
                AddParams.class,
                (AddParams p) -> p.a + p.b
        );

        // "a" cannot be deserialized from a non-numeric string in strict mode
        FunctionResult result = fn.execute(Map.of("a", "not_a_number", "b", 5));

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    @DisplayName("toClaudeToolDefinition 生成正确的 Claude API 格式")
    void toClaudeToolDefinitionProducesCorrectFormat() {
        TypedPluginFunction fn = new TypedPluginFunction(
                "calculate",
                "Perform calculation",
                AddParams.class,
                (AddParams p) -> p.a + p.b
        );

        Map<String, Object> toolDef = fn.toClaudeToolDefinition();

        assertEquals("calculate", toolDef.get("name"));
        assertEquals("Perform calculation", toolDef.get("description"));
        assertNotNull(toolDef.get("input_schema"), "must produce input_schema for Claude API");

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) toolDef.get("input_schema");
        assertEquals("object", schema.get("type"));
        assertNotNull(schema.get("properties"), "schema must have properties");
    }

    @Test
    @DisplayName("toJsonSchema 返回与 toClaudeToolDefinition 相同的结果")
    void toJsonSchemaMatchesClaudeFormat() {
        TypedPluginFunction fn = new TypedPluginFunction(
                "test",
                "test desc",
                AddParams.class,
                (AddParams p) -> "ok"
        );

        assertEquals(fn.toClaudeToolDefinition(), fn.toJsonSchema());
    }

    @Test
    @DisplayName("executeAsync 异步执行并返回结果")
    void executeAsyncReturnsResult() throws Exception {
        TypedPluginFunction fn = new TypedPluginFunction(
                "multiply",
                "Multiply",
                AddParams.class,
                (AddParams p) -> p.a * p.b
        );

        CompletableFuture<FunctionResult> future = fn.executeAsync(Map.of("a", 4, "b", 5));
        FunctionResult result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals(20, result.getValue());
    }

    @Test
    @DisplayName("getName 和 getDescription 返回构造时的值")
    void gettersReturnConstructorValues() {
        TypedPluginFunction fn = new TypedPluginFunction(
                "my_func",
                "My description",
                AddParams.class,
                (AddParams p) -> null
        );

        assertEquals("my_func", fn.getName());
        assertEquals("My description", fn.getDescription());
        assertEquals(AddParams.class, fn.getParameterType());
    }

    @Test
    @DisplayName("null name 抛出 IllegalArgumentException")
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction(null, "desc", AddParams.class, (AddParams p) -> null));
    }

    @Test
    @DisplayName("空 name 抛出 IllegalArgumentException")
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction("", "desc", AddParams.class, (AddParams p) -> null));
    }

    @Test
    @DisplayName("null parameterType 抛出 IllegalArgumentException")
    void nullParamTypeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction("fn", "desc", null, (Object p) -> null));
    }

    @Test
    @DisplayName("null handler 抛出 IllegalArgumentException")
    void nullHandlerThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new TypedPluginFunction("fn", "desc", AddParams.class, null));
    }

    @Test
    @DisplayName("null description 默认为空字符串")
    void nullDescriptionDefaultsToEmpty() {
        TypedPluginFunction fn = new TypedPluginFunction(
                "fn", null, AddParams.class, (AddParams p) -> null);

        assertEquals("", fn.getDescription());
    }

    @Test
    @DisplayName("使用 @JsonProperty 注解的字段名正确映射")
    void jsonPropertyAnnotationRespected() {
        TypedPluginFunction fn = new TypedPluginFunction(
                "query",
                "Search query",
                SearchParams.class,
                (SearchParams p) -> "Found: " + p.searchQuery
        );

        FunctionResult result = fn.execute(Map.of("search_query", "hello"));

        assertTrue(result.isSuccess());
        assertEquals("Found: hello", result.getValue());
    }

    @Test
    @DisplayName("getInputSchema 返回非 null 的 JsonSchema")
    void getInputSchemaNotNull() {
        TypedPluginFunction fn = new TypedPluginFunction(
                "fn", "desc", AddParams.class, (AddParams p) -> null);

        JsonSchema schema = fn.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.getType());
    }

    // ==================== Parameter classes ====================

    public static class AddParams {
        public int a;
        public int b;
    }

    public static class GreetParams {
        public String name;
        public int age;
    }

    public static class SearchParams {
        @JsonProperty("search_query")
        public String searchQuery;
    }
}
