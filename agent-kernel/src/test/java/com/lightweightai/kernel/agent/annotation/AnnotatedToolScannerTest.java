package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner + AnnotatedToolWrapper — 注解→Tool 传输链")
class AnnotatedToolScannerTest {

    // ==================== Test Tool Classes ====================

    static class SimpleTool {
        @ToolFunction(name = "greet", description = "Say hello to someone")
        public String greet(
                @ToolParam(name = "name", description = "Person to greet", required = true) String name
        ) {
            return "Hello, " + name + "!";
        }
    }

    static class MultiParamTool {
        @ToolFunction(name = "add", description = "Add two numbers")
        public String add(
                @ToolParam(name = "a", description = "First number", required = true) double a,
                @ToolParam(name = "b", description = "Second number", required = true) double b
        ) {
            return String.valueOf(a + b);
        }
    }

    static class AutoNameTool {
        @ToolFunction(description = "Get server status")
        public String getServerStatus() {
            return "OK";
        }
    }

    static class MultiToolHolder {
        @ToolFunction(name = "tool_a", description = "Tool A")
        public String toolA() { return "A"; }

        @ToolFunction(name = "tool_b", description = "Tool B")
        public String toolB() { return "B"; }

        public String notATool() { return "not a tool"; }
    }

    static class MetadataTool {
        @ToolFunction(
                name = "search",
                description = "Search the web",
                category = "web",
                tags = {"web", "search"},
                readOnly = true,
                idempotent = true,
                openWorld = true,
                autoExecute = false,
                clientSide = true,
                deviceType = "phone",
                versionRange = ">=2.0.0"
        )
        public String search(@ToolParam(name = "q", required = true) String query) {
            return "results for " + query;
        }
    }

    static class ErrorTool {
        @ToolFunction(name = "fail", description = "Always fails")
        public String fail() {
            throw new RuntimeException("intentional failure");
        }
    }

    static class ToolResultReturnTool {
        @ToolFunction(name = "custom_result", description = "Returns ToolResult directly")
        public ToolResult customResult(
                @ToolParam(name = "msg", required = true) String msg
        ) {
            return ToolResult.success("Custom: " + msg);
        }
    }

    // ==================== Scanner Tests ====================

    @Nested
    @DisplayName("Scanner")
    class ScannerTests {

        @Test
        @DisplayName("扫描单个 @ToolFunction 方法")
        void scansSingleAnnotatedMethod() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTool());
            assertEquals(1, tools.size());
            assertEquals("greet", tools.get(0).getName());
        }

        @Test
        @DisplayName("扫描多个 @ToolFunction 方法，忽略非注解方法")
        void scansMultipleAndIgnoresPlain() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiToolHolder());
            assertEquals(2, tools.size());

            List<String> names = tools.stream().map(Tool::getName).sorted().toList();
            assertEquals(List.of("tool_a", "tool_b"), names);
        }

        @Test
        @DisplayName("null target 抛 NPE")
        void rejectsNullTarget() {
            assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
        }

        @Test
        @DisplayName("无 @ToolFunction 方法的类返回空列表")
        void emptyClassReturnsEmptyList() {
            List<Tool> tools = AnnotatedToolScanner.scan(new Object());
            assertTrue(tools.isEmpty());
        }
    }

    // ==================== Wrapper - Name Resolution ====================

    @Nested
    @DisplayName("Name Resolution")
    class NameResolutionTests {

        @Test
        @DisplayName("显式 name 属性覆盖方法名")
        void explicitNameOverridesMethodName() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTool());
            assertEquals("greet", tools.get(0).getName());
        }

        @Test
        @DisplayName("name 为空时自动驼峰转下划线")
        void autoNameConvertsToSnakeCase() {
            List<Tool> tools = AnnotatedToolScanner.scan(new AutoNameTool());
            assertEquals("get_server_status", tools.get(0).getName());
        }

        @Test
        @DisplayName("camelToSnake 转换正确")
        void camelToSnakeConversion() {
            assertEquals("get_user_name", AnnotatedToolWrapper.camelToSnake("getUserName"));
            assertEquals("simple", AnnotatedToolWrapper.camelToSnake("simple"));
            assertEquals("a_b_c", AnnotatedToolWrapper.camelToSnake("aBC"));
            assertEquals("", AnnotatedToolWrapper.camelToSnake(""));
        }
    }

    // ==================== Wrapper - Schema Generation ====================

    @Nested
    @DisplayName("Schema Generation（注解→JSON Schema 传输链）")
    class SchemaTests {

        @Test
        @DisplayName("带 @ToolParam 的参数生成正确 schema（payload 断言）")
        @SuppressWarnings("unchecked")
        void schemaContainsAnnotatedParams() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTool());
            ToolSchema schema = tools.get(0).getSchema();

            Map<String, Object> map = schema.toMap();
            assertEquals("object", map.get("type"));

            Map<String, Object> props = (Map<String, Object>) map.get("properties");
            assertNotNull(props);
            assertTrue(props.containsKey("name"));

            Map<String, Object> nameProp = (Map<String, Object>) props.get("name");
            assertEquals("string", nameProp.get("type"));
            assertEquals("Person to greet", nameProp.get("description"));

            List<String> required = (List<String>) map.get("required");
            assertTrue(required.contains("name"));
        }

        @Test
        @DisplayName("多参数生成正确 schema 和 required 列表")
        @SuppressWarnings("unchecked")
        void multiParamSchemaCorrect() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTool());
            Map<String, Object> map = tools.get(0).getSchema().toMap();

            Map<String, Object> props = (Map<String, Object>) map.get("properties");
            assertEquals(2, props.size());

            Map<String, Object> aProp = (Map<String, Object>) props.get("a");
            assertEquals("number", aProp.get("type"));

            List<String> required = (List<String>) map.get("required");
            assertTrue(required.contains("a"));
            assertTrue(required.contains("b"));
        }

        @Test
        @DisplayName("无参数方法生成空 schema")
        void noParamMethodGeneratesEmptySchema() {
            List<Tool> tools = AnnotatedToolScanner.scan(new AutoNameTool());
            Map<String, Object> props = tools.get(0).getSchema().getProperties();
            assertTrue(props.isEmpty());
        }
    }

    // ==================== Wrapper - Execution ====================

    @Nested
    @DisplayName("Execution（参数转换→反射调用→ToolResult 传输链）")
    class ExecutionTests {

        @Test
        @DisplayName("正确传递参数并返回结果")
        void executesWithCorrectArgs() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTool());
            ToolResult result = tools.get(0).execute(Map.of("name", "Alice"));

            assertFalse(result.isError());
            assertEquals("Hello, Alice!", result.getContent());
        }

        @Test
        @DisplayName("数值参数正确转换")
        void numericArgsConvertedCorrectly() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTool());
            ToolResult result = tools.get(0).execute(Map.of("a", 3, "b", 7));

            assertFalse(result.isError());
            assertEquals("10.0", result.getContent());
        }

        @Test
        @DisplayName("方法抛异常时返回 error ToolResult")
        void exceptionReturnsError() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ErrorTool());
            ToolResult result = tools.get(0).execute(Map.of());

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("intentional failure"));
        }

        @Test
        @DisplayName("方法返回 ToolResult 时直接使用（不包装）")
        void toolResultReturnTypeUsedDirectly() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ToolResultReturnTool());
            ToolResult result = tools.get(0).execute(Map.of("msg", "test"));

            assertFalse(result.isError());
            assertEquals("Custom: test", result.getContent());
        }

        @Test
        @DisplayName("缺失的参数使用默认值")
        void missingArgsUseDefaults() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTool());
            ToolResult result = tools.get(0).execute(Map.of());

            assertFalse(result.isError());
            assertEquals("0.0", result.getContent());
        }

        @Test
        @DisplayName("null args map 不崩溃")
        void nullArgsHandled() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultiParamTool());
            ToolResult result = tools.get(0).execute(null);

            assertFalse(result.isError());
        }
    }

    // ==================== Wrapper - Metadata ====================

    @Nested
    @DisplayName("ToolMetadata")
    class MetadataTests {

        @Test
        @DisplayName("完整元数据正确提取")
        void fullMetadataExtracted() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MetadataTool());
            Tool tool = tools.get(0);

            assertEquals("search", tool.getName());
            assertEquals("Search the web", tool.getDescription());
            assertFalse(tool.isAutoExecute());

            AnnotatedToolWrapper wrapper = (AnnotatedToolWrapper) tool;
            assertEquals("web", wrapper.getCategory());
            assertEquals(List.of("web", "search"), wrapper.getTags());
            assertTrue(wrapper.isReadOnly());
            assertTrue(wrapper.isIdempotent());
            assertTrue(wrapper.isOpenWorld());
            assertTrue(wrapper.isClientSide());
            assertTrue(wrapper.hasDeviceBinding());
            assertEquals("phone", wrapper.getDeviceType());
            assertEquals(">=2.0.0", wrapper.getVersionRange());
        }

        @Test
        @DisplayName("默认元数据值")
        void defaultMetadataValues() {
            List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTool());
            AnnotatedToolWrapper wrapper = (AnnotatedToolWrapper) tools.get(0);

            assertEquals("default", wrapper.getCategory());
            assertTrue(wrapper.getTags().isEmpty());
            assertFalse(wrapper.isReadOnly());
            assertFalse(wrapper.isIdempotent());
            assertTrue(wrapper.isAutoExecute());
            assertFalse(wrapper.isClientSide());
            assertFalse(wrapper.hasDeviceBinding());
        }
    }

    // ==================== Type Conversion ====================

    @Nested
    @DisplayName("javaTypeToJsonType")
    class TypeConversionTests {

        @Test
        @DisplayName("基本类型映射正确")
        void basicTypeMappings() {
            assertEquals("string", AnnotatedToolWrapper.javaTypeToJsonType(String.class));
            assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(int.class));
            assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(Integer.class));
            assertEquals("integer", AnnotatedToolWrapper.javaTypeToJsonType(long.class));
            assertEquals("number", AnnotatedToolWrapper.javaTypeToJsonType(double.class));
            assertEquals("number", AnnotatedToolWrapper.javaTypeToJsonType(float.class));
            assertEquals("boolean", AnnotatedToolWrapper.javaTypeToJsonType(boolean.class));
            assertEquals("boolean", AnnotatedToolWrapper.javaTypeToJsonType(Boolean.class));
            assertEquals("array", AnnotatedToolWrapper.javaTypeToJsonType(List.class));
            assertEquals("object", AnnotatedToolWrapper.javaTypeToJsonType(Map.class));
        }

        @Test
        @DisplayName("未知类型默认为 string")
        void unknownTypeDefaultsToString() {
            assertEquals("string", AnnotatedToolWrapper.javaTypeToJsonType(Object.class));
        }
    }
}
