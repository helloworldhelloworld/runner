package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner - 注解工具扫描")
class AnnotatedToolScannerTest {

    static class BasicTools {
        @ToolFunction(name = "add", description = "Add numbers")
        public String add(
                @ToolParam(name = "a", required = true) double a,
                @ToolParam(name = "b", required = true) double b) {
            return String.valueOf(a + b);
        }
    }

    static class NoAnnotation {
        public String hello() { return "hi"; }
    }

    static class MultiTool {
        @ToolFunction(name = "tool_a", description = "A")
        public String a() { return "a"; }

        @ToolFunction(name = "tool_b", description = "B")
        public String b() { return "b"; }

        @ToolFunction(name = "tool_c", description = "C")
        public String c() { return "c"; }
    }

    static class ParentTools {
        @ToolFunction(name = "parent_tool", description = "From parent")
        public String parentMethod() { return "parent"; }
    }

    static class ChildTools extends ParentTools {
        @ToolFunction(name = "child_tool", description = "From child")
        public String childMethod() { return "child"; }
    }

    @Test
    @DisplayName("扫描带 @ToolFunction 的方法 → 返回 Tool 列表")
    void scansAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new BasicTools());

        assertEquals(1, tools.size());
        assertEquals("add", tools.get(0).getName());
        assertEquals("Add numbers", tools.get(0).getDescription());
    }

    @Test
    @DisplayName("无 @ToolFunction 的对象 → 返回空列表")
    void emptyForUnannotated() {
        List<Tool> tools = AnnotatedToolScanner.scan(new NoAnnotation());
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("多个 @ToolFunction → 全部扫描")
    void scansMultiple() {
        List<Tool> tools = AnnotatedToolScanner.scan(new MultiTool());

        assertEquals(3, tools.size());
        List<String> names = tools.stream().map(Tool::getName).sorted().collect(Collectors.toList());
        assertEquals(List.of("tool_a", "tool_b", "tool_c"), names);
    }

    @Test
    @DisplayName("扫描子类 → 包含父类的 @ToolFunction")
    void includesInheritedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());

        List<String> names = tools.stream().map(Tool::getName).sorted().collect(Collectors.toList());
        assertTrue(names.contains("child_tool"));
        assertTrue(names.contains("parent_tool"));
    }

    @Test
    @DisplayName("扫描结果可执行 — 返回正确的 payload")
    void scannedToolsAreExecutable() {
        List<Tool> tools = AnnotatedToolScanner.scan(new BasicTools());
        Tool addTool = tools.get(0);

        ToolResult result = addTool.execute(Map.of("a", 2.5, "b", 3.5));

        assertFalse(result.isError());
        assertEquals("6.0", result.getContent());
    }

    @Test
    @DisplayName("null 对象 → NullPointerException")
    void nullThrows() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    @Test
    @DisplayName("普通 Object → 空列表")
    void plainObjectReturnsEmpty() {
        assertTrue(AnnotatedToolScanner.scan(new Object()).isEmpty());
    }
}
