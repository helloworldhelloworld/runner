package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveDescriptor - immutable directive metadata")
class DirectiveDescriptorTest {

    // ==================== successful construction ====================

    @Test
    @DisplayName("Given valid fields, when constructed, then all getters return the correct values")
    void constructorSetsAllFields() {
        // Given & When
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Camera.TakePhoto",
            "takePhoto_down",
            "takePhoto_up",
            "camera-ns",
            5000,
            "1.0"
        );

        // Then
        assertEquals("Camera.TakePhoto", d.getTool());
        assertEquals("takePhoto_down", d.getDownAction());
        assertEquals("takePhoto_up", d.getUpAction());
        assertEquals("camera-ns", d.getNamespace());
        assertEquals(5000, d.getTimeoutMs());
        assertEquals("1.0", d.getVersion());
        assertTrue(d.getAliases().isEmpty(), "No-alias constructor should produce empty alias list");
    }

    @Test
    @DisplayName("Given valid fields with aliases, when constructed, then aliases are accessible")
    void constructorWithAliasesSetsAllFields() {
        // Given & When
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Camera.TakePhoto",
            Arrays.asList("photo", "snap"),
            "takePhoto_down",
            "takePhoto_up",
            "camera-ns",
            5000,
            "1.0"
        );

        // Then
        assertEquals("Camera.TakePhoto", d.getTool());
        assertEquals(2, d.getAliases().size());
        assertTrue(d.getAliases().contains("photo"));
        assertTrue(d.getAliases().contains("snap"));
    }

    @Test
    @DisplayName("Given whitespace-padded fields, when constructed, then values are trimmed")
    void constructorTrimsWhitespace() {
        // Given & When
        DirectiveDescriptor d = new DirectiveDescriptor(
            "  myTool  ",
            "  down_action  ",
            "  up_action  ",
            "  ns  ",
            1000,
            "  2.0  "
        );

        // Then
        assertEquals("myTool", d.getTool());
        assertEquals("down_action", d.getDownAction());
        assertEquals("up_action", d.getUpAction());
        assertEquals("ns", d.getNamespace());
        assertEquals("2.0", d.getVersion());
    }

    // ==================== validation ====================

    @Nested
    @DisplayName("Validation - blank/null fields throw")
    class ValidationTests {

        @Test
        @DisplayName("Given null tool, when constructed, then NullPointerException is thrown")
        void nullToolThrows() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor(null, "down", "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Given blank tool, when constructed, then IllegalArgumentException is thrown")
        void blankToolThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("   ", "down", "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Given empty tool, when constructed, then IllegalArgumentException is thrown")
        void emptyToolThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("", "down", "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Given null downAction, when constructed, then NullPointerException is thrown")
        void nullDownActionThrows() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("tool", null, "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Given blank downAction, when constructed, then IllegalArgumentException is thrown")
        void blankDownActionThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("tool", "   ", "up", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Given null upAction, when constructed, then NullPointerException is thrown")
        void nullUpActionThrows() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("tool", "down", null, "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Given blank upAction, when constructed, then IllegalArgumentException is thrown")
        void blankUpActionThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("tool", "down", "  ", "ns", 1000, "1.0"));
        }

        @Test
        @DisplayName("Given null namespace, when constructed, then NullPointerException is thrown")
        void nullNamespaceThrows() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("tool", "down", "up", null, 1000, "1.0"));
        }

        @Test
        @DisplayName("Given blank namespace, when constructed, then IllegalArgumentException is thrown")
        void blankNamespaceThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("tool", "down", "up", "  ", 1000, "1.0"));
        }

        @Test
        @DisplayName("Given null version, when constructed, then NullPointerException is thrown")
        void nullVersionThrows() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("tool", "down", "up", "ns", 1000, null));
        }

        @Test
        @DisplayName("Given blank version, when constructed, then IllegalArgumentException is thrown")
        void blankVersionThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("tool", "down", "up", "ns", 1000, "   "));
        }
    }

    // ==================== timeoutMs defaults ====================

    @Test
    @DisplayName("Given timeoutMs is 0, when constructed, then it defaults to 60000")
    void timeoutZeroDefaultsTo60000() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "tool", "down", "up", "ns", 0, "1.0");
        assertEquals(60_000, d.getTimeoutMs());
    }

    @Test
    @DisplayName("Given timeoutMs is negative, when constructed, then it defaults to 60000")
    void timeoutNegativeDefaultsTo60000() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "tool", "down", "up", "ns", -100, "1.0");
        assertEquals(60_000, d.getTimeoutMs());
    }

    @Test
    @DisplayName("Given timeoutMs is positive, when constructed, then it keeps the provided value")
    void timeoutPositiveIsPreserved() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "tool", "down", "up", "ns", 30000, "1.0");
        assertEquals(30000, d.getTimeoutMs());
    }

    @Test
    @DisplayName("Given timeoutMs is 1, when constructed, then it keeps the value 1")
    void timeoutOneIsPreserved() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "tool", "down", "up", "ns", 1, "1.0");
        assertEquals(1, d.getTimeoutMs());
    }

    // ==================== normalizeAliases ====================

    @Nested
    @DisplayName("Alias normalization")
    class AliasNormalizationTests {

        @Test
        @DisplayName("Given null aliases list, when constructed, then aliases is empty")
        void nullAliasesListResultsInEmpty() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", null, "down", "up", "ns", 1000, "1.0");
            assertTrue(d.getAliases().isEmpty());
        }

        @Test
        @DisplayName("Given empty aliases list, when constructed, then aliases is empty")
        void emptyAliasesListResultsInEmpty() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", Collections.emptyList(), "down", "up", "ns", 1000, "1.0");
            assertTrue(d.getAliases().isEmpty());
        }

        @Test
        @DisplayName("Given aliases containing the primary name, when constructed, then primary is removed from aliases")
        void primaryNameRemovedFromAliases() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "myTool", Arrays.asList("alias1", "myTool", "alias2"),
                "down", "up", "ns", 1000, "1.0");

            assertFalse(d.getAliases().contains("myTool"),
                "Primary name should be removed from aliases");
            assertEquals(2, d.getAliases().size());
            assertTrue(d.getAliases().contains("alias1"));
            assertTrue(d.getAliases().contains("alias2"));
        }

        @Test
        @DisplayName("Given aliases containing duplicates, when constructed, then duplicates are removed")
        void duplicateAliasesAreRemoved() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", Arrays.asList("alias1", "alias2", "alias1", "alias2"),
                "down", "up", "ns", 1000, "1.0");

            assertEquals(2, d.getAliases().size());
            assertTrue(d.getAliases().contains("alias1"));
            assertTrue(d.getAliases().contains("alias2"));
        }

        @Test
        @DisplayName("Given aliases containing blank strings, when constructed, then blanks are removed")
        void blankAliasesAreRemoved() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", Arrays.asList("alias1", "", "  ", "alias2"),
                "down", "up", "ns", 1000, "1.0");

            assertEquals(2, d.getAliases().size());
            assertTrue(d.getAliases().contains("alias1"));
            assertTrue(d.getAliases().contains("alias2"));
        }

        @Test
        @DisplayName("Given aliases containing nulls, when constructed, then nulls are removed")
        void nullAliasesAreRemoved() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", Arrays.asList("alias1", null, "alias2"),
                "down", "up", "ns", 1000, "1.0");

            assertEquals(2, d.getAliases().size());
            assertTrue(d.getAliases().contains("alias1"));
            assertTrue(d.getAliases().contains("alias2"));
        }

        @Test
        @DisplayName("Given aliases with whitespace, when constructed, then aliases are trimmed")
        void aliasesAreTrimmed() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", Arrays.asList("  alias1  ", "  alias2  "),
                "down", "up", "ns", 1000, "1.0");

            assertTrue(d.getAliases().contains("alias1"),
                "Aliases should be trimmed");
            assertTrue(d.getAliases().contains("alias2"),
                "Aliases should be trimmed");
        }

        @Test
        @DisplayName("Given aliases list, when getAliases is called, then the returned list is unmodifiable")
        void aliasesListIsUnmodifiable() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", Arrays.asList("alias1", "alias2"),
                "down", "up", "ns", 1000, "1.0");

            assertThrows(UnsupportedOperationException.class,
                () -> d.getAliases().add("should_fail"),
                "Aliases list should be unmodifiable");
        }
    }

    // ==================== getAllToolNames ====================

    @Nested
    @DisplayName("getAllToolNames")
    class GetAllToolNamesTests {

        @Test
        @DisplayName("Given no aliases, when getAllToolNames is called, then it returns only the primary name")
        void noAliasesReturnsSingletonList() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "myTool", "down", "up", "ns", 1000, "1.0");

            List<String> names = d.getAllToolNames();
            assertEquals(1, names.size());
            assertEquals("myTool", names.get(0));
        }

        @Test
        @DisplayName("Given aliases exist, when getAllToolNames is called, then it returns primary + aliases")
        void withAliasesReturnsPrimaryPlusAliases() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "myTool", Arrays.asList("alias1", "alias2"),
                "down", "up", "ns", 1000, "1.0");

            List<String> names = d.getAllToolNames();
            assertEquals(3, names.size());
            assertEquals("myTool", names.get(0), "Primary name should be first");
            assertTrue(names.contains("alias1"));
            assertTrue(names.contains("alias2"));
        }

        @Test
        @DisplayName("Given aliases exist, when getAllToolNames is called, then the returned list is unmodifiable")
        void getAllToolNamesReturnsUnmodifiableList() {
            DirectiveDescriptor d = new DirectiveDescriptor(
                "tool", Arrays.asList("a1"),
                "down", "up", "ns", 1000, "1.0");

            assertThrows(UnsupportedOperationException.class,
                () -> d.getAllToolNames().add("fail"),
                "Returned list should be unmodifiable");
        }
    }

    // ==================== toToolName / toDownlinkToolName ====================

    @Test
    @DisplayName("Given a descriptor, when toToolName is called, then it returns the tool name")
    void toToolNameReturnsTool() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Camera.TakePhoto", "down", "up", "ns", 1000, "1.0");
        assertEquals("Camera.TakePhoto", d.toToolName());
    }

    @Test
    @DisplayName("Given a descriptor, when toDownlinkToolName is called, then it returns the downAction")
    void toDownlinkToolNameReturnsDownAction() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "Camera.TakePhoto", "takePhoto_down", "takePhoto_up", "ns", 1000, "1.0");
        assertEquals("takePhoto_down", d.toDownlinkToolName());
    }
}
