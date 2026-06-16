package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolUse - Claude tool_use content block parsing")
class ToolUseTest {

    @Nested
    @DisplayName("Construction and validation")
    class ConstructionTests {

        @Test
        @DisplayName("valid construction preserves fields")
        void validConstruction() {
            Map<String, Object> input = Map.of("city", "Tokyo");
            ToolUse toolUse = new ToolUse("toolu_123", "get_weather", input);

            assertEquals("toolu_123", toolUse.getId());
            assertEquals("get_weather", toolUse.getName());
            assertEquals(Map.of("city", "Tokyo"), toolUse.getInput());
        }

        @Test
        @DisplayName("null input defaults to empty map")
        void nullInputDefaultsToEmptyMap() {
            ToolUse toolUse = new ToolUse("toolu_1", "tool", null);
            assertNotNull(toolUse.getInput());
            assertTrue(toolUse.getInput().isEmpty());
        }

        @Test
        @DisplayName("null id throws IllegalArgumentException")
        void nullIdThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse(null, "tool", Map.of()));
        }

        @Test
        @DisplayName("empty id throws IllegalArgumentException")
        void emptyIdThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("  ", "tool", Map.of()));
        }

        @Test
        @DisplayName("null name throws IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("id_1", null, Map.of()));
        }

        @Test
        @DisplayName("empty name throws IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolUse("id_1", "", Map.of()));
        }

        @Test
        @DisplayName("input map is unmodifiable")
        void inputIsUnmodifiable() {
            Map<String, Object> mutable = new HashMap<>();
            mutable.put("key", "value");
            ToolUse toolUse = new ToolUse("id_1", "tool", mutable);

            assertThrows(UnsupportedOperationException.class,
                    () -> toolUse.getInput().put("extra", "nope"));
        }
    }

    @Nested
    @DisplayName("fromContentBlock parsing")
    class ParseTests {

        @Test
        @DisplayName("parses valid tool_use content block")
        void parsesValidBlock() {
            Map<String, Object> block = Map.of(
                    "type", "tool_use",
                    "id", "toolu_abc",
                    "name", "calculator",
                    "input", Map.of("expression", "2+2")
            );

            ToolUse toolUse = ToolUse.fromContentBlock(block);

            assertEquals("toolu_abc", toolUse.getId());
            assertEquals("calculator", toolUse.getName());
            assertEquals("2+2", toolUse.getInput().get("expression"));
        }

        @Test
        @DisplayName("null block throws IllegalArgumentException")
        void nullBlockThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ToolUse.fromContentBlock(null));
        }

        @Test
        @DisplayName("wrong type throws IllegalArgumentException")
        void wrongTypeThrows() {
            Map<String, Object> block = Map.of(
                    "type", "text",
                    "text", "hello"
            );
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ToolUse.fromContentBlock(block));
            assertTrue(ex.getMessage().contains("text"));
        }

        @Test
        @DisplayName("block with null input results in empty map")
        void blockWithNullInput() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_1");
            block.put("name", "tool");
            block.put("input", null);

            ToolUse toolUse = ToolUse.fromContentBlock(block);
            assertNotNull(toolUse.getInput());
            assertTrue(toolUse.getInput().isEmpty());
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsTests {

        @Test
        @DisplayName("equal ids produce equal objects")
        void equalIds() {
            ToolUse a = new ToolUse("id_1", "tool_a", Map.of("x", 1));
            ToolUse b = new ToolUse("id_1", "tool_b", Map.of("y", 2));

            assertEquals(a, b, "equality is based on id only");
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("different ids produce unequal objects")
        void differentIds() {
            ToolUse a = new ToolUse("id_1", "tool", Map.of());
            ToolUse b = new ToolUse("id_2", "tool", Map.of());

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("not equal to null or different type")
        void notEqualToNullOrDifferentType() {
            ToolUse a = new ToolUse("id_1", "tool", Map.of());
            assertNotEquals(a, null);
            assertNotEquals(a, "id_1");
        }
    }

    @Test
    @DisplayName("toString contains id and name")
    void toStringContainsFields() {
        ToolUse toolUse = new ToolUse("toolu_xyz", "my_tool", Map.of("a", 1));
        String str = toolUse.toString();
        assertTrue(str.contains("toolu_xyz"));
        assertTrue(str.contains("my_tool"));
    }
}
