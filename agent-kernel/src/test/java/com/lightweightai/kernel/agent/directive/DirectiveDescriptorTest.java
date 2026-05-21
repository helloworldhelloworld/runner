package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveDescriptor — tool directive metadata")
class DirectiveDescriptorTest {

    @AfterEach
    void resetRegistry() {
        ClientToolAliasRegistry.reset();
    }

    @Test
    @DisplayName("basic constructor sets all fields")
    void basicConstructor() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "MyTool", "DownAction", "UpAction", "NS", 5000, "2.0");

        assertEquals("MyTool", d.getTool());
        assertEquals("MyTool", d.toToolName());
        assertEquals("DownAction", d.getDownAction());
        assertEquals("DownAction", d.toDownlinkToolName());
        assertEquals("UpAction", d.getUpAction());
        assertEquals("NS", d.getNamespace());
        assertEquals(5000, d.getTimeoutMs());
        assertEquals("2.0", d.getVersion());
        assertTrue(d.getAliases().isEmpty());
    }

    @Test
    @DisplayName("constructor with aliases preserves order and deduplicates")
    void aliasesDeduplication() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Tool", List.of("A", "B", "A", "C"), "Down", "Up", "NS", 1000, "1.0");

        assertEquals(List.of("A", "B", "C"), d.getAliases());
    }

    @Test
    @DisplayName("aliases matching primary tool name are excluded")
    void aliasMatchingPrimaryExcluded() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Tool", List.of("Tool", "Alias1"), "Down", "Up", "NS", 1000, "1.0");

        assertEquals(List.of("Alias1"), d.getAliases());
    }

    @Test
    @DisplayName("null and blank aliases are filtered out")
    void nullAndBlankAliasesFiltered() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Tool", Arrays.asList(null, "", "  ", "Valid"), "Down", "Up", "NS", 1000, "1.0");

        assertEquals(List.of("Valid"), d.getAliases());
    }

    @Test
    @DisplayName("getAllToolNames includes primary + aliases")
    void getAllToolNamesIncludesPrimaryAndAliases() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Primary", List.of("A1", "A2"), "Down", "Up", "NS", 1000, "1.0");

        assertEquals(List.of("Primary", "A1", "A2"), d.getAllToolNames());
    }

    @Test
    @DisplayName("getAllToolNames with no aliases returns only primary")
    void getAllToolNamesNoAliases() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "OnlyTool", "Down", "Up", "NS", 1000, "1.0");

        assertEquals(List.of("OnlyTool"), d.getAllToolNames());
    }

    @Test
    @DisplayName("negative timeout defaults to 60_000ms")
    void negativeTimeoutDefaultsTo60s() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Tool", "Down", "Up", "NS", -1, "1.0");

        assertEquals(60_000, d.getTimeoutMs());
    }

    @Test
    @DisplayName("zero timeout defaults to 60_000ms")
    void zeroTimeoutDefaultsTo60s() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Tool", "Down", "Up", "NS", 0, "1.0");

        assertEquals(60_000, d.getTimeoutMs());
    }

    @Test
    @DisplayName("constructor rejects null tool name")
    void rejectsNullTool() {
        assertThrows(NullPointerException.class, () ->
            new DirectiveDescriptor(null, "Down", "Up", "NS", 1000, "1.0"));
    }

    @Test
    @DisplayName("constructor rejects blank tool name")
    void rejectsBlankTool() {
        assertThrows(IllegalArgumentException.class, () ->
            new DirectiveDescriptor("  ", "Down", "Up", "NS", 1000, "1.0"));
    }

    @Test
    @DisplayName("constructor rejects blank downAction")
    void rejectsBlankDown() {
        assertThrows(IllegalArgumentException.class, () ->
            new DirectiveDescriptor("Tool", "", "Up", "NS", 1000, "1.0"));
    }

    @Test
    @DisplayName("constructor rejects blank upAction")
    void rejectsBlankUp() {
        assertThrows(IllegalArgumentException.class, () ->
            new DirectiveDescriptor("Tool", "Down", "", "NS", 1000, "1.0"));
    }

    @Test
    @DisplayName("constructor rejects blank namespace")
    void rejectsBlankNamespace() {
        assertThrows(IllegalArgumentException.class, () ->
            new DirectiveDescriptor("Tool", "Down", "Up", "", 1000, "1.0"));
    }

    @Test
    @DisplayName("constructor rejects blank version")
    void rejectsBlankVersion() {
        assertThrows(IllegalArgumentException.class, () ->
            new DirectiveDescriptor("Tool", "Down", "Up", "NS", 1000, ""));
    }

    @Test
    @DisplayName("aliases list is immutable")
    void aliasesImmutable() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Tool", List.of("A", "B"), "Down", "Up", "NS", 1000, "1.0");

        assertThrows(UnsupportedOperationException.class, () -> d.getAliases().add("C"));
    }

    @Test
    @DisplayName("getAllToolNames list is immutable")
    void allToolNamesImmutable() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Tool", List.of("A"), "Down", "Up", "NS", 1000, "1.0");

        assertThrows(UnsupportedOperationException.class, () -> d.getAllToolNames().add("X"));
    }

    @Test
    @DisplayName("fields are trimmed of leading/trailing whitespace")
    void fieldsTrimmed() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            " Tool ", " Down ", " Up ", " NS ", 1000, " 1.0 ");

        assertEquals("Tool", d.getTool());
        assertEquals("Down", d.getDownAction());
        assertEquals("Up", d.getUpAction());
        assertEquals("NS", d.getNamespace());
        assertEquals("1.0", d.getVersion());
    }
}
