package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PropertiesClientToolAliasSource — load aliases from Properties")
class PropertiesClientToolAliasSourceTest {

    @Nested
    @DisplayName("Parse from Properties object")
    class ParseFromProperties {

        @Test
        @DisplayName("single alias mapping")
        void singleAlias() {
            Properties props = new Properties();
            props.setProperty("ExecOsApi", "ExecuteOSAPI");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            List<String> aliases = source.aliasesFor("ExecOsApi");
            assertEquals(List.of("ExecuteOSAPI"), aliases);
        }

        @Test
        @DisplayName("multiple comma-separated aliases")
        void multipleAliases() {
            Properties props = new Properties();
            props.setProperty("ExecOsApi", "ExecuteOSAPI,OsApiInvoke,RunOS");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            List<String> aliases = source.aliasesFor("ExecOsApi");
            assertEquals(List.of("ExecuteOSAPI", "OsApiInvoke", "RunOS"), aliases);
        }

        @Test
        @DisplayName("trims whitespace from keys and values")
        void trimsWhitespace() {
            Properties props = new Properties();
            props.setProperty("  Navigate  ", "  GoTo , MoveTo  ");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            List<String> aliases = source.aliasesFor("Navigate");
            assertEquals(List.of("GoTo", "MoveTo"), aliases);
        }

        @Test
        @DisplayName("skips empty values after split")
        void skipsEmpty() {
            Properties props = new Properties();
            props.setProperty("Tool", "A,,B, ,C");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            List<String> aliases = source.aliasesFor("Tool");
            assertEquals(List.of("A", "B", "C"), aliases);
        }

        @Test
        @DisplayName("empty properties returns no aliases")
        void emptyProperties() {
            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(new Properties());

            assertTrue(source.aliasesFor("anything").isEmpty());
            assertTrue(source.snapshot().isEmpty());
        }

        @Test
        @DisplayName("null properties returns no aliases")
        void nullProperties() {
            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource((Properties) null);

            assertTrue(source.aliasesFor("anything").isEmpty());
        }
    }

    @Nested
    @DisplayName("Lookup behavior")
    class LookupBehavior {

        @Test
        @DisplayName("aliasesFor returns empty list for unknown tool")
        void unknownTool() {
            Properties props = new Properties();
            props.setProperty("KnownTool", "Alias1");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            assertTrue(source.aliasesFor("UnknownTool").isEmpty());
        }

        @Test
        @DisplayName("aliasesFor null returns empty list")
        void nullTool() {
            Properties props = new Properties();
            props.setProperty("Tool", "Alias");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            assertTrue(source.aliasesFor(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Snapshot")
    class Snapshot {

        @Test
        @DisplayName("snapshot returns all mappings")
        void snapshotAll() {
            Properties props = new Properties();
            props.setProperty("A", "A1,A2");
            props.setProperty("B", "B1");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            Map<String, List<String>> snap = source.snapshot();
            assertEquals(2, snap.size());
            assertEquals(List.of("A1", "A2"), snap.get("A"));
            assertEquals(List.of("B1"), snap.get("B"));
        }

        @Test
        @DisplayName("snapshot is unmodifiable")
        void snapshotImmutable() {
            Properties props = new Properties();
            props.setProperty("X", "Y");

            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

            assertThrows(UnsupportedOperationException.class,
                    () -> source.snapshot().put("hack", List.of()));
        }
    }

    @Nested
    @DisplayName("Classpath loading")
    class ClasspathLoading {

        @Test
        @DisplayName("missing resource file returns empty aliases gracefully")
        void missingResource() {
            PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(
                    "nonexistent-file-12345.properties");

            assertTrue(source.snapshot().isEmpty());
            assertTrue(source.aliasesFor("anything").isEmpty());
        }
    }
}
