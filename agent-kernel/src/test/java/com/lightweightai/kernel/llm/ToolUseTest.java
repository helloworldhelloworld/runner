package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolUse — LLM 工具调用请求解析")
class ToolUseTest {

    @Nested
    @DisplayName("构造函数验证")
    class ConstructorTests {

        @Test
        @DisplayName("正常构造保留 id, name, input 全部字段")
        void normalConstruction() {
            Map<String, Object> input = Map.of("city", "Beijing");
            ToolUse tu = new ToolUse("toolu_123", "get_weather", input);

            assertEquals("toolu_123", tu.getId());
            assertEquals("get_weather", tu.getName());
            assertEquals("Beijing", tu.getInput().get("city"));
        }

        @Test
        @DisplayName("null input 默认为空 Map")
        void nullInputDefaultsToEmptyMap() {
            ToolUse tu = new ToolUse("toolu_1", "tool_name", null);
            assertNotNull(tu.getInput());
            assertTrue(tu.getInput().isEmpty());
        }

        @Test
        @DisplayName("input 返回不可修改的 Map — 防止外部篡改")
        void inputIsUnmodifiable() {
            ToolUse tu = new ToolUse("toolu_1", "tool", Map.of("k", "v"));
            assertThrows(UnsupportedOperationException.class,
                    () -> tu.getInput().put("new", "value"));
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
                    () -> new ToolUse("toolu_1", null, Map.of()));
        }

        @Test
        @DisplayName("空 name 抛出 IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("toolu_1", "", Map.of()));
        }
    }

    @Nested
    @DisplayName("fromContentBlock 解析")
    class FromContentBlockTests {

        @Test
        @DisplayName("解析标准 tool_use content block")
        void parseStandardBlock() {
            Map<String, Object> block = Map.of(
                    "type", "tool_use",
                    "id", "toolu_abc",
                    "name", "search",
                    "input", Map.of("query", "hello"));

            ToolUse tu = ToolUse.fromContentBlock(block);

            assertEquals("toolu_abc", tu.getId());
            assertEquals("search", tu.getName());
            assertEquals("hello", tu.getInput().get("query"));
        }

        @Test
        @DisplayName("非 tool_use 类型抛出 IllegalArgumentException")
        void wrongTypeThrows() {
            Map<String, Object> block = Map.of("type", "text", "text", "hello");
            assertThrows(IllegalArgumentException.class,
                    () -> ToolUse.fromContentBlock(block));
        }

        @Test
        @DisplayName("null block 抛出 IllegalArgumentException")
        void nullBlockThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ToolUse.fromContentBlock(null));
        }
    }

    @Nested
    @DisplayName("equals/hashCode 语义")
    class EqualityTests {

        @Test
        @DisplayName("相同 id 的 ToolUse 视为相等")
        void sameIdEquals() {
            ToolUse a = new ToolUse("toolu_1", "tool_a", Map.of());
            ToolUse b = new ToolUse("toolu_1", "tool_b", Map.of("k", "v"));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同 id 的 ToolUse 不相等")
        void differentIdNotEquals() {
            ToolUse a = new ToolUse("toolu_1", "tool", Map.of());
            ToolUse b = new ToolUse("toolu_2", "tool", Map.of());
            assertNotEquals(a, b);
        }
    }

    @Test
    @DisplayName("toString 包含 id 和 name")
    void toStringContainsKeyInfo() {
        ToolUse tu = new ToolUse("toolu_x", "my_tool", Map.of("k", "v"));
        String s = tu.toString();
        assertTrue(s.contains("toolu_x"));
        assertTrue(s.contains("my_tool"));
    }
}
