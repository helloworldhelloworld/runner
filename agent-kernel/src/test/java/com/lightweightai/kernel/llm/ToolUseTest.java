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
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("valid construction preserves all fields")
        void validConstruction() {
            Map<String, Object> input = Map.of("city", "Beijing", "unit", "celsius");
            ToolUse toolUse = new ToolUse("toolu_abc123", "get_weather", input);

            assertEquals("toolu_abc123", toolUse.getId());
            assertEquals("get_weather", toolUse.getName());
            assertEquals("Beijing", toolUse.getInput().get("city"));
            assertEquals("celsius", toolUse.getInput().get("unit"));
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
                () -> new ToolUse("", "tool", Map.of()));
        }

        @Test
        @DisplayName("blank id throws IllegalArgumentException")
        void blankIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("   ", "tool", Map.of()));
        }

        @Test
        @DisplayName("null name throws IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("id1", null, Map.of()));
        }

        @Test
        @DisplayName("empty name throws IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolUse("id1", "", Map.of()));
        }

        @Test
        @DisplayName("null input defaults to empty map")
        void nullInputDefaultsToEmptyMap() {
            ToolUse toolUse = new ToolUse("id1", "tool", null);
            assertNotNull(toolUse.getInput());
            assertTrue(toolUse.getInput().isEmpty());
        }
    }

    @Nested
    @DisplayName("getInput defensive copy")
    class DefensiveCopy {

        @Test
        @DisplayName("getInput returns unmodifiable map")
        void inputIsUnmodifiable() {
            ToolUse toolUse = new ToolUse("id1", "tool", Map.of("k", "v"));
            assertThrows(UnsupportedOperationException.class,
                () -> toolUse.getInput().put("new", "val"));
        }

        @Test
        @DisplayName("mutating original map does not affect ToolUse")
        void originalMapMutationSafe() {
            Map<String, Object> input = new HashMap<>();
            input.put("key", "original");
            ToolUse toolUse = new ToolUse("id1", "tool", input);

            input.put("injected", "value");
            assertFalse(toolUse.getInput().containsKey("injected"));
            assertEquals("original", toolUse.getInput().get("key"));
        }
    }

    @Nested
    @DisplayName("fromContentBlock - Claude API response parsing")
    class FromContentBlock {

        @Test
        @DisplayName("parses valid tool_use content block")
        void parsesValidBlock() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "toolu_01A09q90qw90lq917835lq9");
            block.put("name", "get_weather");
            block.put("input", Map.of("location", "San Francisco"));

            ToolUse toolUse = ToolUse.fromContentBlock(block);

            assertEquals("toolu_01A09q90qw90lq917835lq9", toolUse.getId());
            assertEquals("get_weather", toolUse.getName());
            assertEquals("San Francisco", toolUse.getInput().get("location"));
        }

        @Test
        @DisplayName("null block throws IllegalArgumentException")
        void nullBlockThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ToolUse.fromContentBlock(null));
        }

        @Test
        @DisplayName("non-tool_use type throws IllegalArgumentException")
        void wrongTypeThrows() {
            Map<String, Object> block = Map.of("type", "text", "text", "hello");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ToolUse.fromContentBlock(block));
            assertTrue(ex.getMessage().contains("text"));
        }

        @Test
        @DisplayName("missing type field throws IllegalArgumentException")
        void missingTypeThrows() {
            Map<String, Object> block = Map.of("id", "id1", "name", "tool");
            assertThrows(IllegalArgumentException.class,
                () -> ToolUse.fromContentBlock(block));
        }

        @Test
        @DisplayName("block with null input creates ToolUse with empty map")
        void nullInputInBlock() {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "tool_use");
            block.put("id", "id1");
            block.put("name", "tool");
            block.put("input", null);

            ToolUse toolUse = ToolUse.fromContentBlock(block);
            assertTrue(toolUse.getInput().isEmpty());
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("equality based on id only")
        void equalityById() {
            ToolUse a = new ToolUse("id1", "tool_a", Map.of("x", 1));
            ToolUse b = new ToolUse("id1", "tool_b", Map.of("y", 2));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("different ids are not equal")
        void differentIds() {
            ToolUse a = new ToolUse("id1", "tool", Map.of());
            ToolUse b = new ToolUse("id2", "tool", Map.of());
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("not equal to null or different type")
        void notEqualToNullOrOther() {
            ToolUse a = new ToolUse("id1", "tool", Map.of());
            assertNotEquals(null, a);
            assertNotEquals("id1", a);
        }
    }

    @Test
    @DisplayName("toString contains id, name, and input")
    void toStringContainsFields() {
        ToolUse toolUse = new ToolUse("toolu_abc", "get_weather", Map.of("city", "Tokyo"));
        String str = toolUse.toString();
        assertTrue(str.contains("toolu_abc"));
        assertTrue(str.contains("get_weather"));
        assertTrue(str.contains("city"));
    }
}
