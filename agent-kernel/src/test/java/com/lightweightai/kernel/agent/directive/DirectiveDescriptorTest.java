package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveDescriptor - Directive metadata and alias handling")
class DirectiveDescriptorTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Basic constructor sets all fields correctly")
        void shouldSetAllFields() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "TakePhoto", "Camera.TakePhoto", "Camera.PhotoResult",
                "Camera", 30000, "1.0"
            );

            assertEquals("TakePhoto", d.getTool());
            assertEquals("Camera.TakePhoto", d.getDownAction());
            assertEquals("Camera.PhotoResult", d.getUpAction());
            assertEquals("Camera", d.getNamespace());
            assertEquals(30000, d.getTimeoutMs());
            assertEquals("1.0", d.getVersion());
            assertTrue(d.getAliases().isEmpty());
        }

        @Test
        @DisplayName("Constructor with aliases includes them in getAllToolNames")
        void shouldIncludeAliases() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "TakePhoto", List.of("Photograph", "Snap"),
                "Camera.TakePhoto", "Camera.PhotoResult",
                "Camera", 30000, "1.0"
            );

            assertEquals(List.of("Photograph", "Snap"), d.getAliases());
            assertEquals(List.of("TakePhoto", "Photograph", "Snap"), d.getAllToolNames());
        }

        @Test
        @DisplayName("Null tool name throws NullPointerException")
        void shouldThrowOnNullTool() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor(null, "down", "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Blank tool name throws IllegalArgumentException")
        void shouldThrowOnBlankTool() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("  ", "down", "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Null downAction throws NullPointerException")
        void shouldThrowOnNullDownAction() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("tool", null, "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Blank upAction throws IllegalArgumentException")
        void shouldThrowOnBlankUpAction() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("tool", "down", "  ", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Negative timeout defaults to 60000")
        void shouldDefaultTimeoutOnNegative() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", "down", "up", "ns", -1, "1.0");
            assertEquals(60000, d.getTimeoutMs());
        }

        @Test
        @DisplayName("Zero timeout defaults to 60000")
        void shouldDefaultTimeoutOnZero() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", "down", "up", "ns", 0, "1.0");
            assertEquals(60000, d.getTimeoutMs());
        }
    }

    @Nested
    @DisplayName("Alias normalization")
    class AliasNormalization {

        @Test
        @DisplayName("Duplicate aliases are deduplicated")
        void shouldDeduplicateAliases() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "Tool", List.of("AliasA", "AliasA", "AliasB"),
                "down", "up", "ns", 1000, "1.0"
            );

            assertEquals(List.of("AliasA", "AliasB"), d.getAliases());
        }

        @Test
        @DisplayName("Alias matching primary tool name is excluded")
        void shouldExcludeAliasMatchingPrimary() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "Tool", List.of("Tool", "RealAlias"),
                "down", "up", "ns", 1000, "1.0"
            );

            assertEquals(List.of("RealAlias"), d.getAliases());
        }

        @Test
        @DisplayName("Null and blank aliases are excluded")
        void shouldExcludeNullAndBlankAliases() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "Tool", java.util.Arrays.asList(null, "", "  ", "Valid"),
                "down", "up", "ns", 1000, "1.0"
            );

            assertEquals(List.of("Valid"), d.getAliases());
        }

        @Test
        @DisplayName("Empty alias list results in empty aliases")
        void shouldHandleEmptyAliasList() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "Tool", Collections.emptyList(),
                "down", "up", "ns", 1000, "1.0"
            );

            assertTrue(d.getAliases().isEmpty());
        }

        @Test
        @DisplayName("Null alias list results in empty aliases")
        void shouldHandleNullAliasList() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "Tool", null,
                "down", "up", "ns", 1000, "1.0"
            );

            assertTrue(d.getAliases().isEmpty());
        }
    }

    @Nested
    @DisplayName("Tool name derivation")
    class ToolNameDerivation {

        @Test
        @DisplayName("toToolName returns primary tool name")
        void toToolNameReturnsPrimary() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "TakePhoto", "Camera.TakePhoto", "Camera.PhotoResult",
                "Camera", 5000, "1.0"
            );

            assertEquals("TakePhoto", d.toToolName());
        }

        @Test
        @DisplayName("toDownlinkToolName returns downAction")
        void toDownlinkToolNameReturnsDownAction() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "TakePhoto", "Camera.TakePhoto", "Camera.PhotoResult",
                "Camera", 5000, "1.0"
            );

            assertEquals("Camera.TakePhoto", d.toDownlinkToolName());
        }

        @Test
        @DisplayName("getAllToolNames with no aliases returns singleton list")
        void getAllToolNamesWithNoAliases() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "Tool", "down", "up", "ns", 1000, "1.0"
            );

            assertEquals(List.of("Tool"), d.getAllToolNames());
        }
    }
}
