package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner - @ToolFunction 扫描器")
class AnnotatedToolScannerTest {

    // ==================== 扫描行为 ====================

    @Test
    @DisplayName("扫描带 @ToolFunction 方法的对象")
    void scanFindsAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        assertFalse(tools.isEmpty());
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("greet")));
    }

    @Test
    @DisplayName("不扫描无注解方法")
    void scanSkipsUnannotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        assertTrue(tools.stream().noneMatch(t -> t.getName().equals("helper")));
    }

    @Test
    @DisplayName("空对象返回空列表")
    void scanEmptyObjectReturnsEmptyList() {
        List<Tool> tools = AnnotatedToolScanner.scan(new EmptyTarget());
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("null 对象抛出 NullPointerException")
    void scanNullThrows() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    @Test
    @DisplayName("继承的 @ToolFunction 方法也被扫描")
    void scanInheritedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("greet")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("child_method")));
    }

    // ==================== 扫描出的 Tool 执行正确 ====================

    @Test
    @DisplayName("扫描出的工具可正确执行并返回结果")
    void scannedToolExecutes() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        Tool greetTool = tools.stream()
            .filter(t -> t.getName().equals("greet"))
            .findFirst().orElseThrow();

        ToolResult result = greetTool.execute(Map.of("name", "World"));
        assertFalse(result.isError());
        assertEquals("Hello, World!", result.getContent());
    }

    @Test
    @DisplayName("扫描出的工具 schema 包含正确参数")
    void scannedToolHasCorrectSchema() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        Tool greetTool = tools.stream()
            .filter(t -> t.getName().equals("greet"))
            .findFirst().orElseThrow();

        assertNotNull(greetTool.getSchema());
        assertEquals("Say hello", greetTool.getDescription());
    }

    @Test
    @DisplayName("方法名自动驼峰转下划线")
    void camelToSnakeMethodName() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("get_server_status")));
    }

    @Test
    @DisplayName("显式指定 name 优先于方法名")
    void explicitNameTakesPrecedence() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("greet")));
    }

    // ==================== 测试用对象 ====================

    public static class SampleTools {
        @ToolFunction(name = "greet", description = "Say hello")
        public String greet(@ToolParam(name = "name", required = true) String name) {
            return "Hello, " + name + "!";
        }

        @ToolFunction(description = "Get server status")
        public String getServerStatus() {
            return "OK";
        }

        public String helper() {
            return "not a tool";
        }
    }

    public static class EmptyTarget {
        public String noAnnotation() { return "x"; }
    }

    public static class ChildTools extends SampleTools {
        @ToolFunction(description = "Child method")
        public String childMethod() {
            return "child";
        }
    }
}
