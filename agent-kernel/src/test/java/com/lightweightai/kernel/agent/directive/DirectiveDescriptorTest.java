package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveDescriptor - validation, normalization, aliases")
class DirectiveDescriptorTest {

    @Nested
    @DisplayName("Construction and validation")
    class ConstructionValidation {

        @Test
        @DisplayName("valid parameters create descriptor successfully")
        void validParametersCreateDescriptor() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "PlayAudio", "playAudio", "audioPlayed",
                    "audio", 30_000, "1.0");

            assertEquals("PlayAudio", desc.getTool());
            assertEquals("playAudio", desc.getDownAction());
            assertEquals("audioPlayed", desc.getUpAction());
            assertEquals("audio", desc.getNamespace());
            assertEquals(30_000, desc.getTimeoutMs());
            assertEquals("1.0", desc.getVersion());
        }

        @Test
        @DisplayName("blank tool throws IllegalArgumentException")
        void blankToolThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("  ", "down", "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("null tool throws NullPointerException")
        void nullToolThrows() {
            assertThrows(NullPointerException.class, () ->
                    new DirectiveDescriptor(null, "down", "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("blank downAction throws")
        void blankDownActionThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("Tool", "", "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("blank upAction throws")
        void blankUpActionThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("Tool", "down", "  ", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("blank namespace throws")
        void blankNamespaceThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("Tool", "down", "up", "", 1000, "1.0"));
        }

        @Test
        @DisplayName("blank version throws")
        void blankVersionThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("Tool", "down", "up", "ns", 1000, "  "));
        }
    }

    @Nested
    @DisplayName("Timeout defaults")
    class TimeoutDefaults {

        @Test
        @DisplayName("zero timeout defaults to 60000ms")
        void zeroTimeoutDefaultsTo60s() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", "down", "up", "ns", 0, "1.0");
            assertEquals(60_000, desc.getTimeoutMs());
        }

        @Test
        @DisplayName("negative timeout defaults to 60000ms")
        void negativeTimeoutDefaultsTo60s() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", "down", "up", "ns", -100, "1.0");
            assertEquals(60_000, desc.getTimeoutMs());
        }

        @Test
        @DisplayName("positive timeout is preserved")
        void positiveTimeoutPreserved() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", "down", "up", "ns", 5000, "1.0");
            assertEquals(5000, desc.getTimeoutMs());
        }
    }

    @Nested
    @DisplayName("Alias normalization")
    class AliasNormalization {

        @Test
        @DisplayName("aliases are trimmed")
        void aliasesTrimmed() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", Arrays.asList("  alias1  ", "alias2"),
                    "down", "up", "ns", 1000, "1.0");

            assertTrue(desc.getAliases().contains("alias1"));
            assertTrue(desc.getAliases().contains("alias2"));
        }

        @Test
        @DisplayName("null aliases are filtered out")
        void nullAliasesFiltered() {
            List<String> aliases = Arrays.asList("valid", null, "also-valid");
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", aliases, "down", "up", "ns", 1000, "1.0");

            assertEquals(2, desc.getAliases().size());
            assertFalse(desc.getAliases().contains(null));
        }

        @Test
        @DisplayName("blank aliases are filtered out")
        void blankAliasesFiltered() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", Arrays.asList("valid", "  ", ""),
                    "down", "up", "ns", 1000, "1.0");

            assertEquals(1, desc.getAliases().size());
            assertTrue(desc.getAliases().contains("valid"));
        }

        @Test
        @DisplayName("aliases matching the primary tool name are filtered out")
        void aliasMatchingPrimaryFiltered() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", Arrays.asList("Tool", "alias1"),
                    "down", "up", "ns", 1000, "1.0");

            assertEquals(1, desc.getAliases().size());
            assertFalse(desc.getAliases().contains("Tool"));
        }

        @Test
        @DisplayName("duplicate aliases are deduplicated")
        void duplicateAliasesDeduplicated() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", Arrays.asList("alias1", "alias1", "alias2"),
                    "down", "up", "ns", 1000, "1.0");

            assertEquals(2, desc.getAliases().size());
        }

        @Test
        @DisplayName("null alias list produces empty aliases")
        void nullAliasListProducesEmpty() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", (List<String>) null,
                    "down", "up", "ns", 1000, "1.0");

            assertTrue(desc.getAliases().isEmpty());
        }

        @Test
        @DisplayName("empty alias list produces empty aliases")
        void emptyAliasListProducesEmpty() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", Collections.emptyList(),
                    "down", "up", "ns", 1000, "1.0");

            assertTrue(desc.getAliases().isEmpty());
        }

        @Test
        @DisplayName("aliases list is unmodifiable")
        void aliasesUnmodifiable() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", List.of("alias1"),
                    "down", "up", "ns", 1000, "1.0");

            assertThrows(UnsupportedOperationException.class,
                    () -> desc.getAliases().add("injected"));
        }
    }

    @Nested
    @DisplayName("getAllToolNames")
    class GetAllToolNames {

        @Test
        @DisplayName("returns primary tool name first, followed by aliases")
        void primaryFirst() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", List.of("alias1", "alias2"),
                    "down", "up", "ns", 1000, "1.0");

            List<String> all = desc.getAllToolNames();
            assertEquals(3, all.size());
            assertEquals("Tool", all.get(0));
            assertEquals("alias1", all.get(1));
            assertEquals("alias2", all.get(2));
        }

        @Test
        @DisplayName("no aliases returns singleton list with primary name")
        void noAliases() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "Tool", "down", "up", "ns", 1000, "1.0");

            List<String> all = desc.getAllToolNames();
            assertEquals(1, all.size());
            assertEquals("Tool", all.get(0));
        }
    }

    @Nested
    @DisplayName("Convenience methods")
    class ConvenienceMethods {

        @Test
        @DisplayName("toToolName returns the primary tool name")
        void toToolName() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "PlayAudio", "playAudio", "audioPlayed",
                    "audio", 1000, "1.0");

            assertEquals("PlayAudio", desc.toToolName());
        }

        @Test
        @DisplayName("toDownlinkToolName returns the downAction")
        void toDownlinkToolName() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "PlayAudio", "playAudio", "audioPlayed",
                    "audio", 1000, "1.0");

            assertEquals("playAudio", desc.toDownlinkToolName());
        }
    }

    @Nested
    @DisplayName("Whitespace trimming on fields")
    class WhitespaceTrimming {

        @Test
        @DisplayName("leading/trailing whitespace is trimmed from all fields")
        void allFieldsTrimmed() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "  Tool  ", "  down  ", "  up  ",
                    "  ns  ", 1000, "  1.0  ");

            assertEquals("Tool", desc.getTool());
            assertEquals("down", desc.getDownAction());
            assertEquals("up", desc.getUpAction());
            assertEquals("ns", desc.getNamespace());
            assertEquals("1.0", desc.getVersion());
        }
    }
}
