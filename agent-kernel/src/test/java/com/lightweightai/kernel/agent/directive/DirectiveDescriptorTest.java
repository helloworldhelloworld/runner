package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveDescriptor - validation, alias normalization, and tool name resolution")
class DirectiveDescriptorTest {

    @Nested
    @DisplayName("Construction and validation")
    class ConstructionTests {

        @Test
        @DisplayName("creates with all required fields")
        void createsWithAllFields() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "play_music", "execute", "result", "media", 5000, "1.0.0");

            assertEquals("play_music", desc.getTool());
            assertEquals("execute", desc.getDownAction());
            assertEquals("result", desc.getUpAction());
            assertEquals("media", desc.getNamespace());
            assertEquals(5000, desc.getTimeoutMs());
            assertEquals("1.0.0", desc.getVersion());
        }

        @Test
        @DisplayName("negative timeout defaults to 60000")
        void negativeTimeoutDefaultsTo60s() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "tool", "down", "up", "ns", -1, "1.0");
            assertEquals(60_000, desc.getTimeoutMs());
        }

        @Test
        @DisplayName("zero timeout defaults to 60000")
        void zeroTimeoutDefaultsTo60s() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "tool", "down", "up", "ns", 0, "1.0");
            assertEquals(60_000, desc.getTimeoutMs());
        }

        @Test
        @DisplayName("blank tool name throws IllegalArgumentException")
        void blankToolThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("  ", "down", "up", "ns", 5000, "1.0"));
        }

        @Test
        @DisplayName("null tool name throws NullPointerException")
        void nullToolThrows() {
            assertThrows(NullPointerException.class, () ->
                    new DirectiveDescriptor(null, "down", "up", "ns", 5000, "1.0"));
        }

        @Test
        @DisplayName("blank downAction throws")
        void blankDownActionThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("tool", "", "up", "ns", 5000, "1.0"));
        }

        @Test
        @DisplayName("blank upAction throws")
        void blankUpActionThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("tool", "down", "  ", "ns", 5000, "1.0"));
        }

        @Test
        @DisplayName("blank namespace throws")
        void blankNamespaceThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("tool", "down", "up", "", 5000, "1.0"));
        }

        @Test
        @DisplayName("blank version throws")
        void blankVersionThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DirectiveDescriptor("tool", "down", "up", "ns", 5000, ""));
        }

        @Test
        @DisplayName("trims whitespace from fields")
        void trimsWhitespace() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "  tool  ", "  down  ", "  up  ", "  ns  ", 5000, "  1.0  ");
            assertEquals("tool", desc.getTool());
            assertEquals("down", desc.getDownAction());
            assertEquals("up", desc.getUpAction());
            assertEquals("ns", desc.getNamespace());
            assertEquals("1.0", desc.getVersion());
        }
    }

    @Nested
    @DisplayName("Alias normalization")
    class AliasTests {

        @Test
        @DisplayName("no aliases returns singleton list with primary tool name")
        void noAliases() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "tool", "down", "up", "ns", 5000, "1.0");

            List<String> all = desc.getAllToolNames();
            assertEquals(1, all.size());
            assertEquals("tool", all.get(0));
            assertTrue(desc.getAliases().isEmpty());
        }

        @Test
        @DisplayName("aliases included in allToolNames after primary")
        void withAliases() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "tool", List.of("alias1", "alias2"), "down", "up", "ns", 5000, "1.0");

            List<String> all = desc.getAllToolNames();
            assertEquals(3, all.size());
            assertEquals("tool", all.get(0));
            assertTrue(all.contains("alias1"));
            assertTrue(all.contains("alias2"));
        }

        @Test
        @DisplayName("aliases deduplicates entries")
        void deduplicatesAliases() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "tool", List.of("alias1", "alias1", "alias2"), "down", "up", "ns", 5000, "1.0");

            List<String> aliases = desc.getAliases();
            assertEquals(2, aliases.size());
        }

        @Test
        @DisplayName("alias matching primary name is excluded")
        void aliasMatchingPrimaryExcluded() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "tool", List.of("tool", "alias1"), "down", "up", "ns", 5000, "1.0");

            List<String> aliases = desc.getAliases();
            assertFalse(aliases.contains("tool"));
            assertTrue(aliases.contains("alias1"));
        }

        @Test
        @DisplayName("null and blank aliases are filtered out")
        void nullAndBlankAliasesFiltered() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "tool", Arrays.asList(null, "", "  ", "valid"), "down", "up", "ns", 5000, "1.0");

            List<String> aliases = desc.getAliases();
            assertEquals(1, aliases.size());
            assertEquals("valid", aliases.get(0));
        }

        @Test
        @DisplayName("null alias list returns empty aliases")
        void nullAliasListReturnsEmpty() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "tool", null, "down", "up", "ns", 5000, "1.0");

            assertTrue(desc.getAliases().isEmpty());
        }
    }

    @Nested
    @DisplayName("Tool name resolution")
    class ToolNameTests {

        @Test
        @DisplayName("toToolName returns primary tool name")
        void toToolName() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "play_music", "execute", "result", "media", 5000, "1.0.0");
            assertEquals("play_music", desc.toToolName());
        }

        @Test
        @DisplayName("toDownlinkToolName returns downAction")
        void toDownlinkToolName() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                    "play_music", "execute_cmd", "result", "media", 5000, "1.0.0");
            assertEquals("execute_cmd", desc.toDownlinkToolName());
        }
    }
}
