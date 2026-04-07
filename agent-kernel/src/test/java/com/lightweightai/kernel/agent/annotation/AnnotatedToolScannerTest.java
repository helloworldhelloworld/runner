package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AnnotatedToolScannerTest {

    static class Plain {
        public String notATool() { return "no"; }
    }

    static class TwoTools {
        @ToolFunction(name = "alpha", description = "a")
        public String alpha() { return "a"; }

        @ToolFunction(description = "b")
        public String betaTool() { return "b"; }

        public String unannotated() { return "skip"; }
    }

    static class WithPrivateTool {
        @ToolFunction(name = "secret", description = "private")
        private String secret() { return "shh"; }
    }

    static class Base {
        @ToolFunction(name = "inherited", description = "from base")
        public String inherited() { return "base"; }
    }

    static class Child extends Base {
        @ToolFunction(name = "own", description = "from child")
        public String own() { return "child"; }
    }

    @Test
    void shouldRejectNullTarget() {
        assertThrows(NullPointerException.class, () -> AnnotatedToolScanner.scan(null));
    }

    @Test
    void shouldReturnEmptyListWhenNoAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new Plain());
        assertNotNull(tools);
        assertTrue(tools.isEmpty());
    }

    @Test
    void shouldDiscoverAllAnnotatedMethodsAndIgnoreOthers() {
        List<Tool> tools = AnnotatedToolScanner.scan(new TwoTools());
        assertEquals(2, tools.size());
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
        assertTrue(names.contains("alpha"));
        // Default name resolution converts camelCase to snake_case (betaTool -> beta_tool)
        assertTrue(names.stream().anyMatch(n -> n.contains("beta")));
    }

    @Test
    void shouldDiscoverPrivateAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new WithPrivateTool());
        assertEquals(1, tools.size());
        assertEquals("secret", tools.get(0).getName());
    }

    @Test
    void shouldDiscoverInheritedAnnotatedMethods() {
        List<Tool> tools = AnnotatedToolScanner.scan(new Child());
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
        assertTrue(names.contains("own"), "should include child's own tool");
        assertTrue(names.contains("inherited"), "should include inherited tool from base class");
    }
}
