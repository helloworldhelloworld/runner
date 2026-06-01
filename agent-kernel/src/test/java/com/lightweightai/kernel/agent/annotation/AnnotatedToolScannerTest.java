package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner — @ToolFunction 方法扫描")
class AnnotatedToolScannerTest {

    @Nested
    @DisplayName("scan — 正常扫描")
    class ScanTests {

        @Test
        @DisplayName("扫描带 @ToolFunction 的公有方法")
        void scansPublicMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new PublicToolsFixture());

            assertFalse(tools.isEmpty());
            assertTrue(tools.stream().anyMatch(t -> "greet".equals(t.getName())),
                    "应发现 greet 工具");
        }

        @Test
        @DisplayName("扫描带 @ToolFunction 的私有方法")
        void scansPrivateMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new PrivateToolFixture());

            assertTrue(tools.stream().anyMatch(t -> "secret".equals(t.getName())),
                    "应发现 private 方法的 secret 工具");
        }

        @Test
        @DisplayName("扫描到的工具可正常执行")
        void scannedToolIsExecutable() {
            List<Tool> tools = AnnotatedToolScanner.scan(new PublicToolsFixture());
            Tool greetTool = tools.stream()
                    .filter(t -> "greet".equals(t.getName()))
                    .findFirst().orElseThrow();

            ToolResult result = greetTool.execute(Map.of("name", "World"));
            assertEquals("Hello, World!", result.getContent());
        }

        @Test
        @DisplayName("无 @ToolFunction 方法的对象返回空列表")
        void noAnnotationsReturnsEmpty() {
            List<Tool> tools = AnnotatedToolScanner.scan(new NoAnnotationsFixture());
            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("多个 @ToolFunction 方法全部被扫描")
        void multipleMethodsAllScanned() {
            List<Tool> tools = AnnotatedToolScanner.scan(new MultipleToolsFixture());

            assertEquals(2, tools.size());
            assertTrue(tools.stream().anyMatch(t -> "tool_a".equals(t.getName())));
            assertTrue(tools.stream().anyMatch(t -> "tool_b".equals(t.getName())));
        }
    }

    @Nested
    @DisplayName("scan — 继承")
    class InheritanceTests {

        @Test
        @DisplayName("扫描到父类的 @ToolFunction 方法")
        void scansInheritedMethods() {
            List<Tool> tools = AnnotatedToolScanner.scan(new ChildToolsFixture());

            assertTrue(tools.stream().anyMatch(t -> "parent_tool".equals(t.getName())),
                    "应发现父类的 parent_tool");
            assertTrue(tools.stream().anyMatch(t -> "child_tool".equals(t.getName())),
                    "应发现子类的 child_tool");
        }
    }

    @Nested
    @DisplayName("scan — 异常处理")
    class ErrorHandlingTests {

        @Test
        @DisplayName("传入 null 抛出 NullPointerException")
        void nullTargetThrows() {
            assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
        }
    }

    @Nested
    @DisplayName("Schema 生成")
    class SchemaTests {

        @Test
        @DisplayName("@ToolParam 注解的参数生成正确 Schema")
        void paramAnnotationsGenerateSchema() {
            List<Tool> tools = AnnotatedToolScanner.scan(new PublicToolsFixture());
            Tool greetTool = tools.stream()
                    .filter(t -> "greet".equals(t.getName()))
                    .findFirst().orElseThrow();

            Map<String, Object> schema = greetTool.getSchema().toMap();
            assertNotNull(schema.get("properties"), "Schema 应包含 properties");

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertTrue(props.containsKey("name"), "Schema 应包含 name 属性");
        }

        @Test
        @DisplayName("getDescription 返回注解中声明的描述")
        void descriptionFromAnnotation() {
            List<Tool> tools = AnnotatedToolScanner.scan(new PublicToolsFixture());
            Tool greetTool = tools.stream()
                    .filter(t -> "greet".equals(t.getName()))
                    .findFirst().orElseThrow();

            assertEquals("Say hello to someone", greetTool.getDescription());
        }
    }

    // ==================== Fixtures ====================

    public static class PublicToolsFixture {
        @ToolFunction(name = "greet", description = "Say hello to someone")
        public String greet(@ToolParam(name = "name", required = true) String name) {
            return "Hello, " + name + "!";
        }
    }

    private static class PrivateToolFixture {
        @ToolFunction(name = "secret", description = "Secret tool")
        private String secret() {
            return "classified";
        }
    }

    public static class NoAnnotationsFixture {
        public String regularMethod() { return "not a tool"; }
    }

    public static class MultipleToolsFixture {
        @ToolFunction(name = "tool_a", description = "Tool A")
        public String toolA() { return "a"; }

        @ToolFunction(name = "tool_b", description = "Tool B")
        public String toolB() { return "b"; }
    }

    public static class ParentToolsFixture {
        @ToolFunction(name = "parent_tool", description = "From parent")
        public String parentTool() { return "parent"; }
    }

    public static class ChildToolsFixture extends ParentToolsFixture {
        @ToolFunction(name = "child_tool", description = "From child")
        public String childTool() { return "child"; }
    }
}
