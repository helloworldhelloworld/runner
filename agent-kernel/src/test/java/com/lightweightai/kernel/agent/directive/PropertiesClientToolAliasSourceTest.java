package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PropertiesClientToolAliasSource - properties-based alias configuration")
class PropertiesClientToolAliasSourceTest {

    @Nested
    @DisplayName("Given Properties constructor")
    class PropertiesConstructor {

        @Test
        @DisplayName("parses comma-separated aliases")
        void shouldParseCommaSeparatedAliases() {
            // Given
            Properties props = new Properties();
            props.setProperty("ExecOsApi", "ExecuteOSAPI,OsApiInvoke");

            // When
            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            // Then
            assertEquals(List.of("ExecuteOSAPI", "OsApiInvoke"), source.aliasesFor("ExecOsApi"));
        }

        @Test
        @DisplayName("trims whitespace around aliases")
        void shouldTrimWhitespace() {
            Properties props = new Properties();
            props.setProperty("Tool", "  Alias1 , Alias2  ,  Alias3  ");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            assertEquals(List.of("Alias1", "Alias2", "Alias3"), source.aliasesFor("Tool"));
        }

        @Test
        @DisplayName("skips empty parts from trailing commas or extra commas")
        void shouldSkipEmptyParts() {
            Properties props = new Properties();
            props.setProperty("Tool", "Alias1,,Alias2,  ,");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            assertEquals(List.of("Alias1", "Alias2"), source.aliasesFor("Tool"));
        }

        @Test
        @DisplayName("empty value results in no aliases for that key")
        void emptyValueResultsInNoAliases() {
            Properties props = new Properties();
            props.setProperty("EmptyTool", "");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            assertTrue(source.aliasesFor("EmptyTool").isEmpty());
        }

        @Test
        @DisplayName("single alias without comma")
        void singleAliasWithoutComma() {
            Properties props = new Properties();
            props.setProperty("GetPosition", "Locate");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            assertEquals(List.of("Locate"), source.aliasesFor("GetPosition"));
        }

        @Test
        @DisplayName("multiple tool entries are parsed independently")
        void multipleEntriesParsedIndependently() {
            Properties props = new Properties();
            props.setProperty("ToolA", "AliasA1,AliasA2");
            props.setProperty("ToolB", "AliasB1");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            assertEquals(List.of("AliasA1", "AliasA2"), source.aliasesFor("ToolA"));
            assertEquals(List.of("AliasB1"), source.aliasesFor("ToolB"));
        }

        @Test
        @DisplayName("null properties results in empty aliases")
        void nullPropertiesResultsInEmpty() {
            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource((Properties) null);

            assertTrue(source.snapshot().isEmpty());
        }

        @Test
        @DisplayName("empty properties results in empty aliases")
        void emptyPropertiesResultsInEmpty() {
            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(new Properties());

            assertTrue(source.snapshot().isEmpty());
        }
    }

    @Nested
    @DisplayName("Given aliasesFor lookup")
    class AliasesForLookup {

        @Test
        @DisplayName("null primaryToolName returns empty list")
        void nullNameReturnsEmptyList() {
            Properties props = new Properties();
            props.setProperty("Tool", "Alias");
            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            List<String> result = source.aliasesFor(null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("unknown tool name returns empty list")
        void unknownNameReturnsEmptyList() {
            Properties props = new Properties();
            props.setProperty("KnownTool", "Alias");
            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            List<String> result = source.aliasesFor("UnknownTool");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Given resource path constructor")
    class ResourcePathConstructor {

        @Test
        @DisplayName("missing resource file returns empty aliases without exception")
        void missingResourceReturnsEmpty() {
            PropertiesClientToolAliasSource source =
                new PropertiesClientToolAliasSource("nonexistent-file.properties");

            assertTrue(source.snapshot().isEmpty());
            assertTrue(source.aliasesFor("AnyTool").isEmpty());
        }
    }

    @Nested
    @DisplayName("Given snapshot")
    class SnapshotTests {

        @Test
        @DisplayName("snapshot returns unmodifiable map of all aliases")
        void snapshotReturnsUnmodifiableMap() {
            Properties props = new Properties();
            props.setProperty("Tool1", "A1,A2");
            props.setProperty("Tool2", "B1");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            var snapshot = source.snapshot();

            assertEquals(2, snapshot.size());
            assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put("NewTool", List.of("X")));
        }
    }
}
