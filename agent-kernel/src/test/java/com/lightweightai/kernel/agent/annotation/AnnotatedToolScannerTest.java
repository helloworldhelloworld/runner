package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner — @ToolFunction 注解扫描")
class AnnotatedToolScannerTest {

    @Test
    @DisplayName("扫描到 @ToolFunction 方法并生成 Tool")
    void scanFindsAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        assertFalse(tools.isEmpty());
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("greet")),
                "should find tool named 'greet'");
    }

    @Test
    @DisplayName("未标注 @ToolFunction 的方法不被扫描到")
    void skipsUnannotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        assertTrue(tools.stream().noneMatch(t -> t.getName().equals("helper")),
                "unannotated method 'helper' should not appear as tool");
    }

    @Test
    @DisplayName("null 目标抛出 NullPointerException")
    void nullTargetThrows() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    @Test
    @DisplayName("无 @ToolFunction 方法的对象返回空列表")
    void noAnnotationsReturnsEmpty() {
        assertTrue(AnnotatedToolScanner.scan(new NoToolMethods()).isEmpty());
    }

    @Test
    @DisplayName("扫描到的 Tool 可执行并返回正确结果")
    void scannedToolExecutesCorrectly() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SampleTools());
        Tool greetTool = tools.stream()
                .filter(t -> t.getName().equals("greet"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("greet tool not found"));

        ToolResult result = greetTool.execute(Map.of("name", "World"));
        assertNotNull(result);
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Hello") && result.getContent().contains("World"),
                "greet tool should return greeting with name, actual: " + result.getContent());
    }

    // ==================== Test Targets ====================

    public static class SampleTools {
        @ToolFunction(name = "greet", description = "Say hello")
        public String greet(@ToolParam(name = "name", required = true) String name) {
            return "Hello, " + name + "!";
        }

        public String helper() {
            return "not a tool";
        }
    }

    public static class NoToolMethods {
        public void doSomething() {}
    }
}
