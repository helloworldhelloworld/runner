package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolUse - Claude 工具调用请求")
class ToolUseTest {

    @Nested
    @DisplayName("构造函数")
    class ConstructorTests {

        @Test
        void createNormal() {
            ToolUse tu = new ToolUse("toolu_001", "get_weather", Map.of("city", "Beijing"));
            assertEquals("toolu_001", tu.getId());
            assertEquals("get_weather", tu.getName());
            assertEquals("Beijing", tu.getInput().get("city"));
        }

        @Test
        void nullIdThrows() {
            assertThrows(IllegalArgumentException.class, () -> new ToolUse(null, "tool", Map.of()));
        }

        @Test
        void emptyIdThrows() {
            assertThrows(IllegalArgumentException.class, () -> new ToolUse("  ", "tool", Map.of()));
        }

        @Test
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class, () -> new ToolUse("id1", null, Map.of()));
        }

        @Test
        void nullInputDefaultsToEmpty() {
            ToolUse tu = new ToolUse("id1", "tool", null);
            assertTrue(tu.getInput().isEmpty());
        }

        @Test
        void inputIsUnmodifiable() {
            Map<String, Object> input = new HashMap<>();
            input.put("key", "value");
            ToolUse tu = new ToolUse("id1", "tool", input);
            assertThrows(UnsupportedOperationException.class, () -> tu.getInput().put("x", "y"));
        }
    }

    @Nested
    @DisplayName("fromContentBlock")
    class FromContentBlockTests {

        @Test
        void parseValidBlock() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_abc123");
            block.put("name", "calculate");
            block.put("input", Map.of("expression", "2+2"));
            ToolUse tu = ToolUse.fromContentBlock(block);
            assertEquals("toolu_abc123", tu.getId());
            assertEquals("calculate", tu.getName());
            assertEquals("2+2", tu.getInput().get("expression"));
        }

        @Test
        void nullBlockThrows() {
            assertThrows(IllegalArgumentException.class, () -> ToolUse.fromContentBlock(null));
        }

        @Test
        void wrongTypeThrows() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "text");
            assertThrows(IllegalArgumentException.class, () -> ToolUse.fromContentBlock(block));
        }

        @Test
        void nullInputInBlock() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_xyz");
            block.put("name", "no_args_tool");
            block.put("input", null);
            assertTrue(ToolUse.fromContentBlock(block).getInput().isEmpty());
        }
    }

    @Test
    void sameIdEquals() {
        ToolUse a = new ToolUse("id1", "tool_a", Map.of());
        ToolUse b = new ToolUse("id1", "tool_b", Map.of());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentIdNotEquals() {
        assertNotEquals(new ToolUse("id1", "tool", Map.of()), new ToolUse("id2", "tool", Map.of()));
    }

    @Test
    void toStringContainsFields() {
        ToolUse tu = new ToolUse("toolu_001", "search", Map.of("q", "java"));
        assertTrue(tu.toString().contains("toolu_001"));
        assertTrue(tu.toString().contains("search"));
    }
}
