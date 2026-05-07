package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolUse - LLM 工具调用请求解析")
class ToolUseTest {

    @Nested
    @DisplayName("构造函数")
    class Constructor {

        @Test
        @DisplayName("正常构造 — id/name/input 正确存储")
        void createsWithValidArgs() {
            Map<String, Object> input = Map.of("city", "Beijing", "unit", "celsius");
            ToolUse toolUse = new ToolUse("toolu_123", "get_weather", input);

            assertEquals("toolu_123", toolUse.getId());
            assertEquals("get_weather", toolUse.getName());
            assertEquals("Beijing", toolUse.getInput().get("city"));
            assertEquals("celsius", toolUse.getInput().get("unit"));
        }

        @Test
        @DisplayName("input 为 null 时使用空 Map")
        void nullInputBecomesEmptyMap() {
            ToolUse toolUse = new ToolUse("toolu_1", "test_tool", null);

            assertNotNull(toolUse.getInput());
            assertTrue(toolUse.getInput().isEmpty());
        }

        @Test
        @DisplayName("input 返回不可变 Map")
        void inputIsUnmodifiable() {
            ToolUse toolUse = new ToolUse("toolu_1", "test_tool", Map.of("key", "val"));

            assertThrows(UnsupportedOperationException.class,
                    () -> toolUse.getInput().put("new", "value"));
        }

        @Test
        @DisplayName("id 为 null 时抛出 IllegalArgumentException")
        void nullIdThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse(null, "tool", Map.of()));
        }

        @Test
        @DisplayName("id 为空字符串时抛出 IllegalArgumentException")
        void emptyIdThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("  ", "tool", Map.of()));
        }

        @Test
        @DisplayName("name 为 null 时抛出 IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("toolu_1", null, Map.of()));
        }

        @Test
        @DisplayName("name 为空字符串时抛出 IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("toolu_1", "", Map.of()));
        }
    }

    @Nested
    @DisplayName("fromContentBlock - Claude API 响应解析")
    class FromContentBlock {

        @Test
        @DisplayName("正常解析 tool_use 类型的 content block")
        void parsesValidBlock() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_abc");
            block.put("name", "get_weather");
            block.put("input", Map.of("city", "Shanghai"));

            ToolUse toolUse = ToolUse.fromContentBlock(block);

            assertEquals("toolu_abc", toolUse.getId());
            assertEquals("get_weather", toolUse.getName());
            assertEquals("Shanghai", toolUse.getInput().get("city"));
        }

        @Test
        @DisplayName("null block 抛出 IllegalArgumentException")
        void nullBlockThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ToolUse.fromContentBlock(null));
        }

        @Test
        @DisplayName("非 tool_use 类型抛出 IllegalArgumentException")
        void wrongTypeThrows() {
            Map<String, Object> block = Map.of("type", "text", "text", "hello");

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ToolUse.fromContentBlock(block));
            assertTrue(e.getMessage().contains("text"));
        }

        @Test
        @DisplayName("input 为 null 的 block 也能解析")
        void parsesBlockWithNullInput() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_xyz");
            block.put("name", "get_time");
            block.put("input", null);

            ToolUse toolUse = ToolUse.fromContentBlock(block);

            assertEquals("toolu_xyz", toolUse.getId());
            assertTrue(toolUse.getInput().isEmpty());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class Equality {

        @Test
        @DisplayName("相同 id → equals")
        void sameIdAreEqual() {
            ToolUse a = new ToolUse("toolu_1", "tool_a", Map.of());
            ToolUse b = new ToolUse("toolu_1", "tool_b", Map.of("x", 1));

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同 id → not equals")
        void differentIdNotEqual() {
            ToolUse a = new ToolUse("toolu_1", "tool", Map.of());
            ToolUse b = new ToolUse("toolu_2", "tool", Map.of());

            assertNotEquals(a, b);
        }
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringContainsInfo() {
        ToolUse toolUse = new ToolUse("toolu_42", "calc", Map.of("expr", "1+1"));
        String str = toolUse.toString();

        assertTrue(str.contains("toolu_42"));
        assertTrue(str.contains("calc"));
    }
}
