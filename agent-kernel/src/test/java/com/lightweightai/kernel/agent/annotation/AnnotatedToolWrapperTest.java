package com.lightweightai.kernel.agent.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnnotatedToolWrapperTest {

    // ==================== 测试用的工具类 ====================

    static class MathTools {
        @ToolFunction(name = "add", description = "Add two numbers", category = "math",
                      tags = {"math", "calculation"}, readOnly = true, idempotent = true, openWorld = false)
        public String add(
            @ToolParam(name = "a", description = "First number", required = true) double a,
            @ToolParam(name = "b", description = "Second number", required = true) double b
        ) {
            double result = a + b;
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        }

        @ToolFunction(description = "Multiply two integers", category = "math",
                      tags = {"math", "calculation"})
        public String multiplyNumbers(
            @ToolParam(name = "x", description = "First integer", required = true) int x,
            @ToolParam(name = "y", description = "Second integer", required = true) int y
        ) {
            return String.valueOf(x * y);
        }
    }

    static class TimeTools {
        @ToolFunction(name = "get_time", description = "Get current time", readOnly = true)
        public String getTime() {
            return "2026-03-02T12:00:00";
        }
    }

    static class ToolResultTools {
        @ToolFunction(name = "safe_divide", description = "Divide two numbers safely")
        public ToolResult safeDivide(
            @ToolParam(name = "a", description = "Dividend", required = true) double a,
            @ToolParam(name = "b", description = "Divisor", required = true) double b
        ) {
            if (b == 0) {
                return ToolResult.error("Division by zero");
            }
            return ToolResult.success(String.valueOf(a / b));
        }
    }

    static class TypeTestTools {
        @ToolFunction(name = "type_test", description = "Test various parameter types")
        public String typeTest(
            @ToolParam(name = "text", required = true) String text,
            @ToolParam(name = "count") int count,
            @ToolParam(name = "ratio") double ratio,
            @ToolParam(name = "flag") boolean flag
        ) {
            return text + ":" + count + ":" + ratio + ":" + flag;
        }
    }

    static class ErrorTool {
        @ToolFunction(name = "fail", description = "Always throws")
        public String fail() {
            throw new RuntimeException("Something went wrong");
        }
    }

    static class AutoExecuteTools {
        @ToolFunction(name = "dangerous", description = "Needs confirmation", autoExecute = false)
        public String dangerous() {
            return "done";
        }
    }

    static class TypeOverrideTools {
        @ToolFunction(name = "custom_type", description = "Custom type override")
        public String customType(
            @ToolParam(name = "data", description = "Custom data", type = "object") Map<String, Object> data
        ) {
            return data.toString();
        }
    }

    // ==================== 基本功能测试 ====================

    @Test
    void testBasicToolCreation() {
        MathTools mathTools = new MathTools();
        List<Tool> tools = AnnotatedToolScanner.scan(mathTools);

        assertEquals(2, tools.size());

        Tool addTool = tools.stream().filter(t -> t.getName().equals("add")).findFirst().orElseThrow();
        assertEquals("add", addTool.getName());
        assertEquals("Add two numbers", addTool.getDescription());
    }

    @Test
    void testAutoNameFromMethod() {
        MathTools mathTools = new MathTools();
        List<Tool> tools = AnnotatedToolScanner.scan(mathTools);

        // multiplyNumbers -> multiply_numbers
        Tool multiplyTool = tools.stream()
            .filter(t -> t.getName().equals("multiply_numbers"))
            .findFirst()
            .orElseThrow();
        assertEquals("multiply_numbers", multiplyTool.getName());
        assertEquals("Multiply two integers", multiplyTool.getDescription());
    }

    // ==================== Schema 测试 ====================

    @Test
    void testSchemaGeneration() {
        MathTools mathTools = new MathTools();
        List<Tool> tools = AnnotatedToolScanner.scan(mathTools);
        Tool addTool = tools.stream().filter(t -> t.getName().equals("add")).findFirst().orElseThrow();

        ToolSchema schema = addTool.getSchema();
        Map<String, Object> schemaMap = schema.toMap();

        assertEquals("object", schemaMap.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("a"));
        assertTrue(properties.containsKey("b"));

        @SuppressWarnings("unchecked")
        Map<String, Object> propA = (Map<String, Object>) properties.get("a");
        assertEquals("number", propA.get("type"));
        assertEquals("First number", propA.get("description"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schemaMap.get("required");
        assertNotNull(required);
        assertTrue(required.contains("a"));
        assertTrue(required.contains("b"));
    }

    @Test
    void testEmptySchema() {
        TimeTools timeTools = new TimeTools();
        List<Tool> tools = AnnotatedToolScanner.scan(timeTools);
        Tool timeTool = tools.get(0);

        ToolSchema schema = timeTool.getSchema();
        Map<String, Object> schemaMap = schema.toMap();
        assertEquals("object", schemaMap.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
        assertTrue(properties.isEmpty());
    }

    @Test
    void testSchemaTypeMapping() {
        TypeTestTools typeTools = new TypeTestTools();
        List<Tool> tools = AnnotatedToolScanner.scan(typeTools);
        Tool tool = tools.get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) tool.getSchema().toMap().get("properties");

        @SuppressWarnings("unchecked")
        Map<String, Object> textProp = (Map<String, Object>) properties.get("text");
        assertEquals("string", textProp.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> countProp = (Map<String, Object>) properties.get("count");
        assertEquals("integer", countProp.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ratioProp = (Map<String, Object>) properties.get("ratio");
        assertEquals("number", ratioProp.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> flagProp = (Map<String, Object>) properties.get("flag");
        assertEquals("boolean", flagProp.get("type"));
    }

    @Test
    void testTypeOverride() {
        TypeOverrideTools tools = new TypeOverrideTools();
        List<Tool> toolList = AnnotatedToolScanner.scan(tools);
        Tool tool = toolList.get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) tool.getSchema().toMap().get("properties");

        @SuppressWarnings("unchecked")
        Map<String, Object> dataProp = (Map<String, Object>) properties.get("data");
        assertEquals("object", dataProp.get("type"));
    }

    // ==================== 执行测试 ====================

    @Test
    void testExecuteWithArgs() {
        MathTools mathTools = new MathTools();
        List<Tool> tools = AnnotatedToolScanner.scan(mathTools);
        Tool addTool = tools.stream().filter(t -> t.getName().equals("add")).findFirst().orElseThrow();

        ToolResult result = addTool.execute(Map.of("a", 10, "b", 20));
        assertFalse(result.isError());
        assertEquals("30", result.getContent());
    }

    @Test
    void testExecuteWithDoubles() {
        MathTools mathTools = new MathTools();
        List<Tool> tools = AnnotatedToolScanner.scan(mathTools);
        Tool addTool = tools.stream().filter(t -> t.getName().equals("add")).findFirst().orElseThrow();

        ToolResult result = addTool.execute(Map.of("a", 1.5, "b", 2.3));
        assertFalse(result.isError());
        assertEquals("3.8", result.getContent());
    }

    @Test
    void testExecuteNoArgs() {
        TimeTools timeTools = new TimeTools();
        List<Tool> tools = AnnotatedToolScanner.scan(timeTools);
        Tool timeTool = tools.get(0);

        ToolResult result = timeTool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("2026-03-02T12:00:00", result.getContent());
    }

    @Test
    void testExecuteReturnsToolResult() {
        ToolResultTools trTools = new ToolResultTools();
        List<Tool> tools = AnnotatedToolScanner.scan(trTools);
        Tool tool = tools.get(0);

        // 正常除法
        ToolResult result = tool.execute(Map.of("a", 10.0, "b", 2.0));
        assertFalse(result.isError());
        assertEquals("5.0", result.getContent());

        // 除以零
        ToolResult errorResult = tool.execute(Map.of("a", 10.0, "b", 0.0));
        assertTrue(errorResult.isError());
        assertEquals("Division by zero", errorResult.getContent());
    }

    @Test
    void testExecuteError() {
        ErrorTool errorTool = new ErrorTool();
        List<Tool> tools = AnnotatedToolScanner.scan(errorTool);
        Tool tool = tools.get(0);

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertEquals("Something went wrong", result.getContent());
    }

    @Test
    void testIntegerArgConversion() {
        MathTools mathTools = new MathTools();
        List<Tool> tools = AnnotatedToolScanner.scan(mathTools);
        Tool multiply = tools.stream()
            .filter(t -> t.getName().equals("multiply_numbers"))
            .findFirst().orElseThrow();

        // JSON 解析后 Number 类型可能是 Integer 或 Double
        ToolResult result = multiply.execute(Map.of("x", 3, "y", 7));
        assertFalse(result.isError());
        assertEquals("21", result.getContent());
    }

    @Test
    void testTypeConversions() {
        TypeTestTools typeTools = new TypeTestTools();
        List<Tool> tools = AnnotatedToolScanner.scan(typeTools);
        Tool tool = tools.get(0);

        ToolResult result = tool.execute(Map.of(
            "text", "hello",
            "count", 5,
            "ratio", 3.14,
            "flag", true
        ));
        assertFalse(result.isError());
        assertEquals("hello:5:3.14:true", result.getContent());
    }

    @Test
    void testMissingOptionalArgs() {
        TypeTestTools typeTools = new TypeTestTools();
        List<Tool> tools = AnnotatedToolScanner.scan(typeTools);
        Tool tool = tools.get(0);

        // 只传必填项，可选项使用默认值
        ToolResult result = tool.execute(Map.of("text", "hi"));
        assertFalse(result.isError());
        assertEquals("hi:0:0.0:false", result.getContent());
    }

    // ==================== 元数据测试 ====================

    @Test
    void testMetadata() {
        MathTools mathTools = new MathTools();
        List<Tool> tools = AnnotatedToolScanner.scan(mathTools);
        Tool addTool = tools.stream().filter(t -> t.getName().equals("add")).findFirst().orElseThrow();

        assertTrue(addTool instanceof ToolMetadata);
        ToolMetadata meta = (ToolMetadata) addTool;

        assertEquals("math", meta.getCategory());
        assertEquals(List.of("math", "calculation"), meta.getTags());
        assertTrue(meta.isReadOnly());
        assertTrue(meta.isIdempotent());
        assertFalse(meta.isOpenWorld());
    }

    @Test
    void testAutoExecute() {
        AutoExecuteTools tools = new AutoExecuteTools();
        List<Tool> toolList = AnnotatedToolScanner.scan(tools);
        Tool tool = toolList.get(0);

        assertFalse(tool.isAutoExecute());
    }

    // ==================== 驼峰转下划线测试 ====================

    @Test
    void testCamelToSnake() {
        assertEquals("get_user_name", AnnotatedToolWrapper.camelToSnake("getUserName"));
        assertEquals("hello", AnnotatedToolWrapper.camelToSnake("hello"));
        assertEquals("get_h_t_t_p_response", AnnotatedToolWrapper.camelToSnake("getHTTPResponse"));
        assertEquals("a", AnnotatedToolWrapper.camelToSnake("a"));
        assertEquals("", AnnotatedToolWrapper.camelToSnake(""));
    }

    // ==================== Java 类型 → JSON Schema 类型测试 ====================

    @Test
    void testJavaTypeToJsonType() {
        assertEquals("string", AnnotatedToolWrapper.javaTypeToJsonType(String.class));
        assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(int.class));
        assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(Integer.class));
        assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(long.class));
        assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(Long.class));
        assertEquals("number", AnnotatedToolWrapper.javaTypeToJsonType(double.class));
        assertEquals("number", AnnotatedToolWrapper.javaTypeToJsonType(Double.class));
        assertEquals("number", AnnotatedToolWrapper.javaTypeToJsonType(float.class));
        assertEquals("number", AnnotatedToolWrapper.javaTypeToJsonType(Float.class));
        assertEquals("boolean", AnnotatedToolWrapper.javaTypeToJsonType(boolean.class));
        assertEquals("boolean", AnnotatedToolWrapper.javaTypeToJsonType(Boolean.class));
        assertEquals("array", AnnotatedToolWrapper.javaTypeToJsonType(List.class));
        assertEquals("object", AnnotatedToolWrapper.javaTypeToJsonType(Map.class));
    }

    // ==================== ToolRegistry 整合测试 ====================

    @Test
    void testRegisterObject() {
        ToolRegistry registry = new ToolRegistry();
        int count = registry.registerObject(new MathTools());

        assertEquals(2, count);
        assertEquals(2, registry.size());
        assertTrue(registry.has("add"));
        assertTrue(registry.has("multiply_numbers"));

        // 执行
        Tool addTool = registry.get("add").orElseThrow();
        ToolResult result = addTool.execute(Map.of("a", 3, "b", 4));
        assertEquals("7", result.getContent());
    }

    @Test
    void testRegisterMultipleObjects() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new MathTools());
        registry.registerObject(new TimeTools());

        assertEquals(3, registry.size());
        assertTrue(registry.has("add"));
        assertTrue(registry.has("multiply_numbers"));
        assertTrue(registry.has("get_time"));
    }

    @Test
    void testToolDefinitionsForLLM() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new TimeTools());

        List<Map<String, Object>> definitions = registry.getToolDefinitions();
        assertEquals(1, definitions.size());

        Map<String, Object> def = definitions.get(0);
        assertEquals("get_time", def.get("name"));
        assertEquals("Get current time", def.get("description"));
        assertNotNull(def.get("input_schema"));
    }

    @Test
    void testCategoryAndTagQueries() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new MathTools());
        registry.registerObject(new TimeTools());

        List<Tool> mathTools = registry.getByCategory("math");
        assertEquals(2, mathTools.size());

        List<Tool> calcTools = registry.getByTag("calculation");
        assertEquals(2, calcTools.size());
    }

    // ==================== 边界情况 ====================

    @Test
    void testScanEmptyObject() {
        List<Tool> tools = AnnotatedToolScanner.scan(new Object());
        assertTrue(tools.isEmpty());
    }

    @Test
    void testScanNull() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    @Test
    void testRegisterObjectNull() {
        ToolRegistry registry = new ToolRegistry();
        assertThrows(NullPointerException.class, () -> registry.registerObject(null));
    }
}
