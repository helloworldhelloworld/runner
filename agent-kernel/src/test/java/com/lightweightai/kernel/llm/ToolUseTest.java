package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolUse - LLM tool_use 请求解析")
class ToolUseTest {

    @Nested
    @DisplayName("构造与基本属性")
    class ConstructionTests {

        @Test
        @DisplayName("正常构造并获取字段")
        void normalConstruction() {
            Map<String, Object> input = Map.of("city", "Beijing");
            ToolUse tu = new ToolUse("toolu_abc", "get_weather", input);

            assertEquals("toolu_abc", tu.getId());
            assertEquals("get_weather", tu.getName());
            assertEquals("Beijing", tu.getInput().get("city"));
        }

        @Test
        @DisplayName("null input 默认为空 Map")
        void nullInputDefaultsToEmptyMap() {
            ToolUse tu = new ToolUse("id1", "tool1", null);
            assertNotNull(tu.getInput());
            assertTrue(tu.getInput().isEmpty());
        }

        @Test
        @DisplayName("getInput 返回不可变视图")
        void inputIsUnmodifiable() {
            Map<String, Object> input = new HashMap<>();
            input.put("key", "val");
            ToolUse tu = new ToolUse("id1", "tool1", input);

            assertThrows(UnsupportedOperationException.class,
                    () -> tu.getInput().put("new", "entry"));
        }

        @Test
        @DisplayName("null id 抛出 IllegalArgumentException")
        void nullIdThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse(null, "tool", Map.of()));
        }

        @Test
        @DisplayName("空白 id 抛出 IllegalArgumentException")
        void blankIdThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("  ", "tool", Map.of()));
        }

        @Test
        @DisplayName("null name 抛出 IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("id1", null, Map.of()));
        }

        @Test
        @DisplayName("空白 name 抛出 IllegalArgumentException")
        void blankNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("id1", "  ", Map.of()));
        }
    }

    @Nested
    @DisplayName("fromContentBlock 静态工厂")
    class FromContentBlockTests {

        @Test
        @DisplayName("解析合法 tool_use content block")
        void parsesValidBlock() {
            Map<String, Object> block = Map.of(
                    "type", "tool_use",
                    "id", "toolu_123",
                    "name", "echo",
                    "input", Map.of("text", "hello")
            );

            ToolUse tu = ToolUse.fromContentBlock(block);
            assertEquals("toolu_123", tu.getId());
            assertEquals("echo", tu.getName());
            assertEquals("hello", tu.getInput().get("text"));
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
            Map<String, Object> block = Map.of(
                    "type", "text",
                    "text", "hello"
            );
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ToolUse.fromContentBlock(block));
            assertTrue(ex.getMessage().contains("tool_use"));
        }
    }

    @Nested
    @DisplayName("equals / hashCode")
    class EqualityTests {

        @Test
        @DisplayName("相同 id 的 ToolUse 相等")
        void sameIdEquals() {
            ToolUse a = new ToolUse("id1", "tool_a", Map.of());
            ToolUse b = new ToolUse("id1", "tool_b", Map.of("key", "val"));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同 id 的 ToolUse 不相等")
        void differentIdNotEquals() {
            ToolUse a = new ToolUse("id1", "tool", Map.of());
            ToolUse b = new ToolUse("id2", "tool", Map.of());
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("与 null 不相等")
        void notEqualToNull() {
            ToolUse a = new ToolUse("id1", "tool", Map.of());
            assertNotEquals(null, a);
        }
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringContainsKeyFields() {
        ToolUse tu = new ToolUse("toolu_abc", "get_weather", Map.of("city", "NYC"));
        String s = tu.toString();
        assertTrue(s.contains("toolu_abc"));
        assertTrue(s.contains("get_weather"));
    }
}
