package com.lightweightai.kernel.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypedPluginFunction — 类型安全插件函数 + Schema → Claude Tool 链")
class TypedPluginFunctionTest {

    static class AddRequest {
        @JsonProperty(value = "a", required = true)
        public int a;

        @JsonProperty(value = "b", required = true)
        public int b;
    }

    static class GreetRequest {
        @JsonProperty("name")
        public String name;

        @JsonProperty("language")
        public String language;
    }

    @Nested
    @DisplayName("构造验证")
    class ConstructorValidation {

        @Test
        @DisplayName("null name 抛 IllegalArgumentException")
        void rejectsNullName() {
            assertThrows(IllegalArgumentException.class, () ->
                    new TypedPluginFunction(null, "desc", AddRequest.class, r -> null));
        }

        @Test
        @DisplayName("空 name 抛 IllegalArgumentException")
        void rejectsEmptyName() {
            assertThrows(IllegalArgumentException.class, () ->
                    new TypedPluginFunction("  ", "desc", AddRequest.class, r -> null));
        }

        @Test
        @DisplayName("null parameterType 抛 IllegalArgumentException")
        void rejectsNullParameterType() {
            assertThrows(IllegalArgumentException.class, () ->
                    new TypedPluginFunction("fn", "desc", null, r -> null));
        }

        @Test
        @DisplayName("null handler 抛 IllegalArgumentException")
        void rejectsNullHandler() {
            assertThrows(IllegalArgumentException.class, () ->
                    new TypedPluginFunction("fn", "desc", AddRequest.class, null));
        }

        @Test
        @DisplayName("null description 默认为空字符串")
        void nullDescriptionDefaultsToEmpty() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "fn", null, AddRequest.class, r -> "ok");
            assertEquals("", fn.getDescription());
        }
    }

    @Nested
    @DisplayName("同步执行")
    class SyncExecution {

        @Test
        @DisplayName("Map 参数正确转换为类型对象并执行 handler")
        void executesWithTypedConversion() {
            TypedPluginFunction addFn = new TypedPluginFunction(
                    "add", "Add two numbers", AddRequest.class,
                    (AddRequest req) -> req.a + req.b);

            FunctionResult result = addFn.execute(Map.of("a", 3, "b", 7));

            assertTrue(result.isSuccess());
            assertEquals(10, result.getValue());
        }

        @Test
        @DisplayName("字符串类型参数正确转换")
        void executesWithStringParams() {
            TypedPluginFunction greetFn = new TypedPluginFunction(
                    "greet", "Greet someone", GreetRequest.class,
                    (GreetRequest req) -> "Hello, " + req.name + " (" + req.language + ")");

            FunctionResult result = greetFn.execute(Map.of("name", "Alice", "language", "en"));

            assertTrue(result.isSuccess());
            assertEquals("Hello, Alice (en)", result.getValue());
        }

        @Test
        @DisplayName("handler 抛异常时返回 error FunctionResult")
        void handlerExceptionReturnsError() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "fail", "Always fails", AddRequest.class,
                    (AddRequest req) -> { throw new RuntimeException("boom"); });

            FunctionResult result = fn.execute(Map.of("a", 1, "b", 2));

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("boom"));
        }

        @Test
        @DisplayName("参数类型不匹配时返回 error")
        void typeMismatchReturnsError() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "add", "desc", AddRequest.class, (AddRequest r) -> r.a + r.b);

            FunctionResult result = fn.execute(Map.of("a", "not_a_number", "b", 2));

            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("异步执行")
    class AsyncExecution {

        @Test
        @DisplayName("executeAsync 返回成功结果")
        void asyncReturnsResult() throws Exception {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "add", "desc", AddRequest.class,
                    (AddRequest req) -> req.a + req.b);

            CompletableFuture<FunctionResult> future = fn.executeAsync(Map.of("a", 5, "b", 3));
            FunctionResult result = future.get(5, TimeUnit.SECONDS);

            assertTrue(result.isSuccess());
            assertEquals(8, result.getValue());
        }
    }

    @Nested
    @DisplayName("Schema 生成 → Claude Tool Definition 传输链")
    class SchemaToToolDefinitionChain {

        @Test
        @DisplayName("toClaudeToolDefinition 包含 name, description, input_schema")
        @SuppressWarnings("unchecked")
        void claudeToolDefinitionHasRequiredFields() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "add", "Add two numbers", AddRequest.class,
                    (AddRequest req) -> req.a + req.b);

            Map<String, Object> toolDef = fn.toClaudeToolDefinition();

            assertEquals("add", toolDef.get("name"));
            assertEquals("Add two numbers", toolDef.get("description"));

            Map<String, Object> inputSchema = (Map<String, Object>) toolDef.get("input_schema");
            assertNotNull(inputSchema, "input_schema must be present for Claude API");
            assertEquals("object", inputSchema.get("type"));

            Map<String, Object> props = (Map<String, Object>) inputSchema.get("properties");
            assertNotNull(props);
            assertTrue(props.containsKey("a"));
            assertTrue(props.containsKey("b"));
        }

        @Test
        @DisplayName("toJsonSchema 与 toClaudeToolDefinition 返回相同结果")
        void toJsonSchemaMatchesClaudeToolDef() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "test", "Test function", GreetRequest.class,
                    (GreetRequest r) -> "hi");

            assertEquals(fn.toJsonSchema(), fn.toClaudeToolDefinition());
        }

        @Test
        @DisplayName("getInputSchema 返回非 null JsonSchema 对象")
        void getInputSchemaReturnsNonNull() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "fn", "desc", AddRequest.class, (AddRequest r) -> null);

            JsonSchema schema = fn.getInputSchema();
            assertNotNull(schema);
            assertEquals("object", schema.getType());
        }

        @Test
        @DisplayName("getParameterType 返回原始参数类型")
        void getParameterTypeReturnsCorrect() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "fn", "desc", AddRequest.class, (AddRequest r) -> null);

            assertEquals(AddRequest.class, fn.getParameterType());
        }

        @Test
        @DisplayName("@JsonProperty(required=true) 在 input_schema 中标记为 required")
        @SuppressWarnings("unchecked")
        void requiredAnnotationReflectedInSchema() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "add", "desc", AddRequest.class, (AddRequest r) -> r.a + r.b);

            Map<String, Object> toolDef = fn.toClaudeToolDefinition();
            Map<String, Object> inputSchema = (Map<String, Object>) toolDef.get("input_schema");

            Object required = inputSchema.get("required");
            assertNotNull(required, "required field should be present in schema");
            assertTrue(required instanceof java.util.List);
            java.util.List<String> requiredList = (java.util.List<String>) required;
            assertTrue(requiredList.contains("a"));
            assertTrue(requiredList.contains("b"));
        }
    }

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("getName 返回函数名")
        void getNameReturnsName() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "my_func", "desc", AddRequest.class, (AddRequest r) -> null);
            assertEquals("my_func", fn.getName());
        }

        @Test
        @DisplayName("getParameters 返回空列表（参数来自 schema）")
        void getParametersReturnsEmpty() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "fn", "desc", AddRequest.class, (AddRequest r) -> null);
            assertTrue(fn.getParameters().isEmpty());
        }

        @Test
        @DisplayName("toString 包含函数名")
        void toStringContainsName() {
            TypedPluginFunction fn = new TypedPluginFunction(
                    "my_func", "test desc", AddRequest.class, (AddRequest r) -> null);
            assertTrue(fn.toString().contains("my_func"));
        }
    }
}
