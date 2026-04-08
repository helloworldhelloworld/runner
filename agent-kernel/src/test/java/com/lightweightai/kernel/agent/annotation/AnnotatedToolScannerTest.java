package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnnotatedToolScanner 单元测试
 *
 * 覆盖关键路径：
 * - 扫描 @ToolFunction 方法并生成 Tool 列表
 * - 空类应返回空列表
 * - 非 Tool 方法应被忽略
 * - 继承父类 public @ToolFunction 方法应被包含
 * - null target 应抛出 NullPointerException
 */
@DisplayName("AnnotatedToolScanner - @ToolFunction 方法扫描")
class AnnotatedToolScannerTest {

    static class EmptyTools {
        public String notATool() {
            return "no";
        }
    }

    static class SimpleTools {
        @ToolFunction(name = "hello", description = "say hello")
        public String hello(@ToolParam(name = "name", required = true) String name) {
            return "hi " + name;
        }

        @ToolFunction(description = "get status")
        public String getStatus() {
            return "OK";
        }

        public String untaggedHelper() {
            return "helper";
        }
    }

    static class BaseTools {
        @ToolFunction(name = "base_tool", description = "inherited tool")
        public String baseTool() {
            return "base";
        }
    }

    static class ChildTools extends BaseTools {
        @ToolFunction(name = "child_tool", description = "own tool")
        public String childTool() {
            return "child";
        }
    }

    @Test
    @DisplayName("null target 应抛 NullPointerException")
    void shouldRejectNullTarget() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    @Test
    @DisplayName("无 @ToolFunction 方法的类应返回空列表")
    void shouldReturnEmptyListForUntaggedClass() {
        List<Tool> tools = AnnotatedToolScanner.scan(new EmptyTools());
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("扫描应抽取所有 @ToolFunction 方法并忽略无注解方法")
    void shouldScanTaggedMethodsOnly() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());

        assertEquals(2, tools.size());
        List<String> names = tools.stream().map(Tool::getName).sorted().collect(Collectors.toList());
        assertTrue(names.contains("hello"));
        // 第二个 ToolFunction 未指定 name,应使用方法名推导
        assertTrue(
            names.stream().anyMatch(n -> n.equals("getStatus") || n.equals("get_status")),
            "expected getStatus / get_status in " + names
        );
    }

    @Test
    @DisplayName("继承父类的 public @ToolFunction 方法应被扫描到")
    void shouldIncludeInheritedPublicToolMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new ChildTools());

        List<String> names = tools.stream().map(Tool::getName).sorted().collect(Collectors.toList());
        assertTrue(names.contains("base_tool"), "expected base_tool in " + names);
        assertTrue(names.contains("child_tool"), "expected child_tool in " + names);
        assertEquals(2, tools.size(), "expected exactly 2 tools, got " + names);
    }

    @Test
    @DisplayName("扫描结果应为可独立执行的 Tool 实例")
    void shouldProduceExecutableToolInstances() {
        List<Tool> tools = AnnotatedToolScanner.scan(new SimpleTools());
        assertFalse(tools.isEmpty());
        for (Tool t : tools) {
            assertNotNull(t);
            assertNotNull(t.getName());
            assertNotNull(t.getDescription());
        }
    }
}
