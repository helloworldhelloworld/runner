package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotatedToolScanner — 注解工具扫描器")
class AnnotatedToolScannerTest {

    // ==================== 基础扫描 ====================

    @Test
    @DisplayName("扫描包含 @ToolFunction 的对象返回 Tool 列表")
    void scanFindsAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new BasicTools());
        assertEquals(1, tools.size());
        assertEquals("hello", tools.get(0).getName());
    }

    @Test
    @DisplayName("扫描空对象返回空列表")
    void scanEmptyObjectReturnsEmpty() {
        List<Tool> tools = AnnotatedToolScanner.scan(new Object());
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("null 目标抛出 NullPointerException")
    void scanNullThrows() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    // ==================== 继承链扫描 ====================

    @Test
    @DisplayName("扫描子类时也发现父类的 @ToolFunction 方法")
    void scanFindsInheritedToolFunctions() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());

        assertTrue(names.contains("parent_action"), "应发现父类方法");
        assertTrue(names.contains("child_action"), "应发现子类方法");
        assertTrue(tools.size() >= 2);
    }

    @Test
    @DisplayName("继承的工具可执行并返回正确结果 — 传输链验证")
    void inheritedToolExecutesCorrectly() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());
        Tool parentTool = tools.stream()
            .filter(t -> t.getName().equals("parent_action"))
            .findFirst()
            .orElseThrow();

        ToolResult result = parentTool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("parent", result.getContent());
    }

    // ==================== 多方法扫描 ====================

    @Test
    @DisplayName("扫描包含多个 @ToolFunction 的对象")
    void scanFindsMultipleMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new MultiTools());
        assertEquals(3, tools.size());

        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
        assertTrue(names.contains("tool_a"));
        assertTrue(names.contains("tool_b"));
        assertTrue(names.contains("tool_c"));
    }

    // ==================== private 方法扫描 ====================

    @Test
    @DisplayName("扫描 private @ToolFunction 方法")
    void scanFindsPrivateMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new PrivateTools());
        assertEquals(1, tools.size());
        assertEquals("secret", tools.get(0).getName());

        ToolResult result = tools.get(0).execute(Map.of());
        assertFalse(result.isError());
        assertEquals("hidden", result.getContent());
    }

    // ==================== 无注解方法不被扫描 ====================

    @Test
    @DisplayName("非 @ToolFunction 方法不被扫描")
    void nonAnnotatedMethodsIgnored() {
        List<Tool> tools = AnnotatedToolScanner.scan(new MixedTools());
        assertEquals(1, tools.size());
        assertEquals("annotated", tools.get(0).getName());
    }

    // ==================== 测试用类 ====================

    static class BasicTools {
        @ToolFunction(name = "hello", description = "Say hello")
        public String hello() {
            return "Hello!";
        }
    }

    static class ParentTools {
        @ToolFunction(name = "parent_action", description = "Parent action")
        public String parentAction() {
            return "parent";
        }
    }

    static class ChildTools extends ParentTools {
        @ToolFunction(name = "child_action", description = "Child action")
        public String childAction() {
            return "child";
        }
    }

    static class MultiTools {
        @ToolFunction(name = "tool_a", description = "Tool A")
        public String a() { return "a"; }

        @ToolFunction(name = "tool_b", description = "Tool B")
        public String b() { return "b"; }

        @ToolFunction(name = "tool_c", description = "Tool C")
        public String c() { return "c"; }
    }

    static class PrivateTools {
        @ToolFunction(name = "secret", description = "Secret tool")
        private String secret() {
            return "hidden";
        }
    }

    static class MixedTools {
        @ToolFunction(name = "annotated", description = "Has annotation")
        public String annotated() { return "yes"; }

        public String notAnnotated() { return "no"; }
    }
}
