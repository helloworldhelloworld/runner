package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PropertiesClientToolAliasSource — properties file → alias resolution chain.
 *
 * Tests the full flow: parse properties → aliasesFor() → snapshot().
 * This is part of the ClientTool alias transmission chain:
 *   properties file → PropertiesClientToolAliasSource → ClientToolAliasRegistry
 *   → DirectiveDescriptor → ClientToolAlias → ToolRegistry
 */
@DisplayName("PropertiesClientToolAliasSource — alias config parsing")
class PropertiesClientToolAliasSourceTest {

    @Test
    @DisplayName("parses properties with comma-separated aliases")
    void parsesCommaSeparatedAliases() {
        Properties props = new Properties();
        props.setProperty("ExecOsApi", "ExecuteOSAPI,OsApiInvoke");
        props.setProperty("GetPosition", "Locate");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        List<String> execAliases = source.aliasesFor("ExecOsApi");
        assertEquals(2, execAliases.size());
        assertTrue(execAliases.contains("ExecuteOSAPI"));
        assertTrue(execAliases.contains("OsApiInvoke"));

        List<String> posAliases = source.aliasesFor("GetPosition");
        assertEquals(1, posAliases.size());
        assertEquals("Locate", posAliases.get(0));
    }

    @Test
    @DisplayName("returns empty list for unknown tool name")
    void returnsEmptyForUnknown() {
        Properties props = new Properties();
        props.setProperty("ExecOsApi", "Alias1");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertTrue(source.aliasesFor("NonExistent").isEmpty());
    }

    @Test
    @DisplayName("returns empty list for null tool name")
    void returnsEmptyForNull() {
        Properties props = new Properties();
        props.setProperty("ExecOsApi", "Alias1");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);
        assertTrue(source.aliasesFor(null).isEmpty());
    }

    @Test
    @DisplayName("trims whitespace from keys and values")
    void trimsWhitespace() {
        Properties props = new Properties();
        props.setProperty("  MyTool  ", " Alias1 , Alias2 ,  Alias3  ");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        List<String> aliases = source.aliasesFor("MyTool");
        assertEquals(3, aliases.size());
        assertEquals("Alias1", aliases.get(0));
        assertEquals("Alias2", aliases.get(1));
        assertEquals("Alias3", aliases.get(2));
    }

    @Test
    @DisplayName("skips empty values after split")
    void skipsEmptyValues() {
        Properties props = new Properties();
        props.setProperty("Tool", "Alias1,,,,Alias2,");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        List<String> aliases = source.aliasesFor("Tool");
        assertEquals(2, aliases.size());
    }

    @Test
    @DisplayName("empty properties produce empty snapshot")
    void emptyProperties() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(new Properties());
        assertTrue(source.snapshot().isEmpty());
    }

    @Test
    @DisplayName("null properties produce empty snapshot")
    void nullProperties() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource((Properties) null);
        assertTrue(source.snapshot().isEmpty());
    }

    @Test
    @DisplayName("skips keys with all-blank values")
    void skipsBlankValues() {
        Properties props = new Properties();
        props.setProperty("EmptyTool", "  ,  ,  ");
        props.setProperty("GoodTool", "Alias1");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertTrue(source.aliasesFor("EmptyTool").isEmpty());
        assertEquals(1, source.aliasesFor("GoodTool").size());
    }

    @Test
    @DisplayName("snapshot returns unmodifiable view")
    void snapshotIsUnmodifiable() {
        Properties props = new Properties();
        props.setProperty("Tool", "Alias");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);
        Map<String, List<String>> snapshot = source.snapshot();

        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.put("NewTool", List.of("X")));
    }

    @Test
    @DisplayName("aliases list is unmodifiable")
    void aliasListIsUnmodifiable() {
        Properties props = new Properties();
        props.setProperty("Tool", "Alias1,Alias2");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);
        List<String> aliases = source.aliasesFor("Tool");

        assertThrows(UnsupportedOperationException.class, () ->
                aliases.add("Hacked"));
    }

    @Test
    @DisplayName("loads from classpath resource when file exists")
    void loadsFromClasspathNonExistent() {
        PropertiesClientToolAliasSource source =
                new PropertiesClientToolAliasSource("non-existent-file.properties");
        assertTrue(source.snapshot().isEmpty());
    }

    @Test
    @DisplayName("skips empty key")
    void skipsEmptyKey() {
        Properties props = new Properties();
        props.setProperty("", "ShouldBeSkipped");
        props.setProperty("  ", "AlsoSkipped");
        props.setProperty("Valid", "Alias");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);
        assertEquals(1, source.snapshot().size());
        assertTrue(source.snapshot().containsKey("Valid"));
    }
}
