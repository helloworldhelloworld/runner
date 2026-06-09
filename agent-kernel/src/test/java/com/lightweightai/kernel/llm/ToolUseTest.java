package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolUse — parsed tool call request from LLM")
class ToolUseTest {

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        void rejectsNullId() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse(null, "tool", Map.of()));
        }

        @Test
        void rejectsEmptyId() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("", "tool", Map.of()));
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("  ", "tool", Map.of()));
        }

        @Test
        void rejectsNullName() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("id-1", null, Map.of()));
        }

        @Test
        void rejectsEmptyName() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("id-1", "", Map.of()));
        }

        @Test
        void nullInputBecomesEmptyMap() {
            ToolUse tu = new ToolUse("id-1", "tool", null);
            assertNotNull(tu.getInput());
            assertTrue(tu.getInput().isEmpty());
        }

        @Test
        void validConstructionPreservesFields() {
            Map<String, Object> input = Map.of("city", "Beijing");
            ToolUse tu = new ToolUse("toolu_01", "get_weather", input);
            assertEquals("toolu_01", tu.getId());
            assertEquals("get_weather", tu.getName());
            assertEquals("Beijing", tu.getInput().get("city"));
        }
    }

    @Nested
    @DisplayName("getInput() — defensive copy")
    class InputDefensiveCopy {

        @Test
        void returnsUnmodifiableMap() {
            ToolUse tu = new ToolUse("id-1", "tool", Map.of("k", "v"));
            assertThrows(UnsupportedOperationException.class,
                () -> tu.getInput().put("new", "val"));
        }
    }

    @Nested
    @DisplayName("fromContentBlock — factory method")
    class FromContentBlock {

        @Test
        void parsesValidToolUseBlock() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_abc");
            block.put("name", "search");
            block.put("input", Map.of("query", "test"));

            ToolUse tu = ToolUse.fromContentBlock(block);
            assertEquals("toolu_abc", tu.getId());
            assertEquals("search", tu.getName());
            assertEquals("test", tu.getInput().get("query"));
        }

        @Test
        void rejectsNullBlock() {
            assertThrows(IllegalArgumentException.class,
                () -> ToolUse.fromContentBlock(null));
        }

        @Test
        void rejectsWrongType() {
            Map<String, Object> block = Map.of("type", "text", "text", "hello");
            assertThrows(IllegalArgumentException.class,
                () -> ToolUse.fromContentBlock(block));
        }

        @Test
        void handlesNullInputInBlock() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_xyz");
            block.put("name", "noop");
            block.put("input", null);

            ToolUse tu = ToolUse.fromContentBlock(block);
            assertNotNull(tu.getInput());
            assertTrue(tu.getInput().isEmpty());
        }
    }

    @Nested
    @DisplayName("equals/hashCode — based on ID")
    class EqualsHashCode {

        @Test
        void sameIdMeansEqual() {
            ToolUse a = new ToolUse("id-1", "tool_a", Map.of());
            ToolUse b = new ToolUse("id-1", "tool_b", Map.of("k", "v"));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void differentIdMeansNotEqual() {
            ToolUse a = new ToolUse("id-1", "tool", Map.of());
            ToolUse b = new ToolUse("id-2", "tool", Map.of());
            assertNotEquals(a, b);
        }

        @Test
        void notEqualToNull() {
            ToolUse tu = new ToolUse("id-1", "tool", Map.of());
            assertNotEquals(null, tu);
        }

        @Test
        void notEqualToOtherType() {
            ToolUse tu = new ToolUse("id-1", "tool", Map.of());
            assertNotEquals("id-1", tu);
        }
    }

    @Test
    @DisplayName("toString includes all fields")
    void toStringContainsFields() {
        ToolUse tu = new ToolUse("toolu_01", "get_weather", Map.of("city", "NYC"));
        String s = tu.toString();
        assertTrue(s.contains("toolu_01"));
        assertTrue(s.contains("get_weather"));
    }
}
