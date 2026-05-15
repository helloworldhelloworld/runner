package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolUse - Claude 工具调用请求解析")
class ToolUseTest {

    @Nested
    @DisplayName("构造与字段")
    class Construction {

        @Test
        @DisplayName("正常构造携带 id, name, input")
        void normalConstruction() {
            ToolUse tu = new ToolUse("toolu_01abc", "get_weather", Map.of("city", "Beijing"));

            assertEquals("toolu_01abc", tu.getId());
            assertEquals("get_weather", tu.getName());
            assertEquals("Beijing", tu.getInput().get("city"));
        }

        @Test
        @DisplayName("input 为 null 时默认为空 Map")
        void nullInputDefaultsToEmptyMap() {
            ToolUse tu = new ToolUse("id1", "tool", null);

            assertNotNull(tu.getInput());
            assertTrue(tu.getInput().isEmpty());
        }

        @Test
        @DisplayName("getInput 返回不可修改视图")
        void inputIsUnmodifiable() {
            ToolUse tu = new ToolUse("id1", "tool", Map.of("k", "v"));

            assertThrows(UnsupportedOperationException.class,
                () -> tu.getInput().put("extra", "x"));
        }
    }

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("id 为 null 时抛出异常")
        void nullIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse(null, "tool", Map.of()));
        }

        @Test
        @DisplayName("id 为空字符串时抛出异常")
        void emptyIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("  ", "tool", Map.of()));
        }

        @Test
        @DisplayName("name 为 null 时抛出异常")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("id1", null, Map.of()));
        }

        @Test
        @DisplayName("name 为空字符串时抛出异常")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("id1", "", Map.of()));
        }
    }

    @Nested
    @DisplayName("fromContentBlock 解析")
    class FromContentBlock {

        @Test
        @DisplayName("解析标准 Claude tool_use content block")
        void parseStandardBlock() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_01xyz");
            block.put("name", "search");
            block.put("input", Map.of("query", "weather"));

            ToolUse tu = ToolUse.fromContentBlock(block);

            assertEquals("toolu_01xyz", tu.getId());
            assertEquals("search", tu.getName());
            assertEquals("weather", tu.getInput().get("query"));
        }

        @Test
        @DisplayName("block 为 null 时抛出异常")
        void nullBlockThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ToolUse.fromContentBlock(null));
        }

        @Test
        @DisplayName("type 不是 tool_use 时抛出异常")
        void wrongTypeThrows() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "text");
            block.put("id", "id1");
            block.put("name", "tool");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ToolUse.fromContentBlock(block));
            assertTrue(ex.getMessage().contains("text"));
        }

        @Test
        @DisplayName("input 缺失时解析为空 Map")
        void missingInputParsesToEmptyMap() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_01");
            block.put("name", "noop");

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
            ToolUse a = new ToolUse("id1", "tool_a", Map.of("x", 1));
            ToolUse b = new ToolUse("id1", "tool_b", Map.of("y", 2));

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同 id 的 ToolUse 不相等")
        void differentIdNotEqual() {
            ToolUse a = new ToolUse("id1", "tool", Map.of());
            ToolUse b = new ToolUse("id2", "tool", Map.of());

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("与 null 不相等")
        void notEqualToNull() {
            ToolUse tu = new ToolUse("id1", "tool", Map.of());
            assertNotEquals(null, tu);
        }

        @Test
        @DisplayName("与其他类型不相等")
        void notEqualToOtherType() {
            ToolUse tu = new ToolUse("id1", "tool", Map.of());
            assertNotEquals("id1", tu);
        }

        @Test
        @DisplayName("自身相等")
        void equalsItself() {
            ToolUse tu = new ToolUse("id1", "tool", Map.of());
            assertEquals(tu, tu);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("包含 id, name, input")
        void containsKeyFields() {
            ToolUse tu = new ToolUse("toolu_01", "weather", Map.of("city", "NYC"));
            String str = tu.toString();

            assertTrue(str.contains("toolu_01"));
            assertTrue(str.contains("weather"));
            assertTrue(str.contains("city"));
        }
    }
}
