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
    @DisplayName("构造器验证")
    class ConstructorValidation {

        @Test
        @DisplayName("正常构造并获取字段")
        void normalConstruction() {
            Map<String, Object> input = Map.of("city", "Shanghai");
            ToolUse tu = new ToolUse("toolu_123", "get_weather", input);

            assertEquals("toolu_123", tu.getId());
            assertEquals("get_weather", tu.getName());
            assertEquals("Shanghai", tu.getInput().get("city"));
        }

        @Test
        @DisplayName("null id 抛出 IllegalArgumentException")
        void nullIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse(null, "tool", Map.of()));
        }

        @Test
        @DisplayName("空 id 抛出 IllegalArgumentException")
        void emptyIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("  ", "tool", Map.of()));
        }

        @Test
        @DisplayName("null name 抛出 IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("id_1", null, Map.of()));
        }

        @Test
        @DisplayName("空 name 抛出 IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("id_1", "", Map.of()));
        }

        @Test
        @DisplayName("null input 默认为空 Map")
        void nullInputDefaultsToEmptyMap() {
            ToolUse tu = new ToolUse("id_1", "tool", null);
            assertNotNull(tu.getInput());
            assertTrue(tu.getInput().isEmpty());
        }

        @Test
        @DisplayName("getInput 返回不可变 Map")
        void inputIsUnmodifiable() {
            Map<String, Object> input = new HashMap<>();
            input.put("key", "value");
            ToolUse tu = new ToolUse("id_1", "tool", input);

            assertThrows(UnsupportedOperationException.class,
                () -> tu.getInput().put("new", "value"));
        }
    }

    @Nested
    @DisplayName("fromContentBlock 工厂方法")
    class FromContentBlock {

        @Test
        @DisplayName("解析有效的 tool_use content block")
        void parseValidBlock() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_abc");
            block.put("name", "search");
            block.put("input", Map.of("query", "hello"));

            ToolUse tu = ToolUse.fromContentBlock(block);
            assertEquals("toolu_abc", tu.getId());
            assertEquals("search", tu.getName());
            assertEquals("hello", tu.getInput().get("query"));
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
            assertThrows(IllegalArgumentException.class,
                () -> ToolUse.fromContentBlock(block));
        }

        @Test
        @DisplayName("缺少 input 字段时 input 为空 Map")
        void missingInputDefaultsToEmpty() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_1");
            block.put("name", "ping");

            ToolUse tu = ToolUse.fromContentBlock(block);
            assertTrue(tu.getInput().isEmpty());
        }
    }

    @Nested
    @DisplayName("equals 和 hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("相同 id 的 ToolUse 相等")
        void sameIdEquals() {
            ToolUse a = new ToolUse("id_1", "tool_a", Map.of("x", 1));
            ToolUse b = new ToolUse("id_1", "tool_b", Map.of("y", 2));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同 id 的 ToolUse 不相等")
        void differentIdNotEquals() {
            ToolUse a = new ToolUse("id_1", "tool", Map.of());
            ToolUse b = new ToolUse("id_2", "tool", Map.of());
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("与 null 不相等")
        void notEqualsNull() {
            ToolUse tu = new ToolUse("id_1", "tool", Map.of());
            assertNotEquals(null, tu);
        }
    }

    @Test
    @DisplayName("toString 包含 id 和 name")
    void toStringContainsFields() {
        ToolUse tu = new ToolUse("toolu_x", "my_tool", Map.of());
        String str = tu.toString();
        assertTrue(str.contains("toolu_x"));
        assertTrue(str.contains("my_tool"));
    }
}
