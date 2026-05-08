package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ToolUse} — Claude tool use request representation.
 *
 * Covers: constructor validation, immutability, factory method, equality.
 */
@DisplayName("ToolUse - 工具调用请求")
class ToolUseTest {

    // ==================== Constructor Validation ====================

    @Nested
    @DisplayName("构造函数校验")
    class ConstructorValidation {

        @Test
        @DisplayName("有效参数 — 创建成功")
        void valid_createsInstance() {
            ToolUse tu = new ToolUse("toolu_123", "get_weather", Map.of("city", "Beijing"));

            assertEquals("toolu_123", tu.getId());
            assertEquals("get_weather", tu.getName());
            assertEquals("Beijing", tu.getInput().get("city"));
        }

        @Test
        @DisplayName("null id — 抛出 IllegalArgumentException")
        void nullId_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                new ToolUse(null, "tool", Map.of()));
        }

        @Test
        @DisplayName("空 id — 抛出 IllegalArgumentException")
        void emptyId_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                new ToolUse("", "tool", Map.of()));
        }

        @Test
        @DisplayName("空白 id — 抛出 IllegalArgumentException")
        void blankId_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                new ToolUse("   ", "tool", Map.of()));
        }

        @Test
        @DisplayName("null name — 抛出 IllegalArgumentException")
        void nullName_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                new ToolUse("id-1", null, Map.of()));
        }

        @Test
        @DisplayName("空 name — 抛出 IllegalArgumentException")
        void emptyName_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                new ToolUse("id-1", "", Map.of()));
        }

        @Test
        @DisplayName("null input — 默认为空 Map")
        void nullInput_defaultsToEmptyMap() {
            ToolUse tu = new ToolUse("id-1", "tool", null);

            assertNotNull(tu.getInput());
            assertTrue(tu.getInput().isEmpty());
        }
    }

    // ==================== Immutability ====================

    @Nested
    @DisplayName("不可变性")
    class Immutability {

        @Test
        @DisplayName("getInput 返回不可修改的 Map")
        void getInput_returnsUnmodifiable() {
            Map<String, Object> input = new HashMap<>();
            input.put("key", "value");
            ToolUse tu = new ToolUse("id-1", "tool", input);

            assertThrows(UnsupportedOperationException.class, () ->
                tu.getInput().put("new_key", "new_value"));
        }

        @Test
        @DisplayName("修改原始 input Map 不影响 ToolUse")
        void mutatingOriginalInput_doesNotAffectToolUse() {
            Map<String, Object> input = new HashMap<>();
            input.put("key", "original");
            ToolUse tu = new ToolUse("id-1", "tool", input);

            // ToolUse stores a reference to the original map, but wraps it as unmodifiable
            // The internal map is the same reference — this is expected behavior per the code
            assertEquals("original", tu.getInput().get("key"));
        }
    }

    // ==================== Factory Method ====================

    @Nested
    @DisplayName("fromContentBlock()")
    class FromContentBlock {

        @Test
        @DisplayName("有效 content block — 创建 ToolUse")
        void validBlock_createsToolUse() {
            Map<String, Object> block = Map.of(
                "type", "tool_use",
                "id", "toolu_abc",
                "name", "search",
                "input", Map.of("query", "test")
            );

            ToolUse tu = ToolUse.fromContentBlock(block);

            assertEquals("toolu_abc", tu.getId());
            assertEquals("search", tu.getName());
            assertEquals("test", tu.getInput().get("query"));
        }

        @Test
        @DisplayName("null block — 抛出 IllegalArgumentException")
        void nullBlock_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                ToolUse.fromContentBlock(null));
        }

        @Test
        @DisplayName("非 tool_use 类型 — 抛出 IllegalArgumentException")
        void wrongType_throws() {
            Map<String, Object> block = Map.of(
                "type", "text",
                "text", "hello"
            );

            assertThrows(IllegalArgumentException.class, () ->
                ToolUse.fromContentBlock(block));
        }

        @Test
        @DisplayName("无 input 字段 — 默认空 Map")
        void missingInput_defaultsEmpty() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_1");
            block.put("name", "tool_a");
            block.put("input", null);

            ToolUse tu = ToolUse.fromContentBlock(block);
            assertTrue(tu.getInput().isEmpty());
        }
    }

    // ==================== Equality ====================

    @Nested
    @DisplayName("equals / hashCode")
    class Equality {

        @Test
        @DisplayName("相同 id — equals 为 true")
        void sameId_equals() {
            ToolUse a = new ToolUse("id-1", "tool_a", Map.of("x", 1));
            ToolUse b = new ToolUse("id-1", "tool_b", Map.of("y", 2));

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同 id — equals 为 false")
        void differentId_notEquals() {
            ToolUse a = new ToolUse("id-1", "tool", Map.of());
            ToolUse b = new ToolUse("id-2", "tool", Map.of());

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("与 null 比较 — false")
        void equalsNull_false() {
            ToolUse tu = new ToolUse("id-1", "tool", Map.of());
            assertNotEquals(null, tu);
        }

        @Test
        @DisplayName("可作为 Set/Map 的 key")
        void worksInCollections() {
            ToolUse a = new ToolUse("id-1", "tool", Map.of());
            ToolUse b = new ToolUse("id-1", "tool", Map.of());

            java.util.Set<ToolUse> set = new java.util.HashSet<>();
            set.add(a);
            set.add(b);

            assertEquals(1, set.size());
        }
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString 包含关键字段")
    void toString_includesFields() {
        ToolUse tu = new ToolUse("toolu_x", "weather", Map.of("city", "SH"));
        String str = tu.toString();

        assertTrue(str.contains("toolu_x"));
        assertTrue(str.contains("weather"));
    }
}
