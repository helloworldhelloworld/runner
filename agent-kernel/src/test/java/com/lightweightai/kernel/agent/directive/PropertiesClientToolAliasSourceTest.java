package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PropertiesClientToolAliasSource - properties-based tool alias loading")
class PropertiesClientToolAliasSourceTest {

    @Test
    @DisplayName("parses comma-separated aliases from Properties")
    void parsesFromProperties() {
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
    void unknownToolReturnsEmpty() {
        Properties props = new Properties();
        props.setProperty("ExecOsApi", "ExecuteOSAPI");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertTrue(source.aliasesFor("NonExistent").isEmpty());
    }

    @Test
    @DisplayName("returns empty list for null tool name")
    void nullToolNameReturnsEmpty() {
        Properties props = new Properties();
        props.setProperty("tool", "alias");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertTrue(source.aliasesFor(null).isEmpty());
    }

    @Test
    @DisplayName("trims whitespace from aliases")
    void trimsWhitespace() {
        Properties props = new Properties();
        props.setProperty("tool", " alias1 , alias2 , alias3 ");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        List<String> aliases = source.aliasesFor("tool");
        assertEquals(3, aliases.size());
        assertEquals("alias1", aliases.get(0));
        assertEquals("alias2", aliases.get(1));
        assertEquals("alias3", aliases.get(2));
    }

    @Test
    @DisplayName("skips empty values in comma-separated list")
    void skipsEmptyValues() {
        Properties props = new Properties();
        props.setProperty("tool", "alias1,,, ,alias2");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        List<String> aliases = source.aliasesFor("tool");
        assertEquals(2, aliases.size());
    }

    @Test
    @DisplayName("empty Properties yields empty snapshot")
    void emptyPropertiesYieldsEmpty() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(new Properties());

        Map<String, List<String>> snapshot = source.snapshot();
        assertTrue(snapshot.isEmpty());
    }

    @Test
    @DisplayName("null Properties yields empty snapshot")
    void nullPropertiesYieldsEmpty() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource((Properties) null);
        assertTrue(source.snapshot().isEmpty());
    }

    @Test
    @DisplayName("non-existent classpath resource yields empty snapshot")
    void nonExistentResourceYieldsEmpty() {
        PropertiesClientToolAliasSource source =
                new PropertiesClientToolAliasSource("nonexistent-file.properties");
        assertTrue(source.snapshot().isEmpty());
    }

    @Test
    @DisplayName("snapshot is unmodifiable")
    void snapshotIsUnmodifiable() {
        Properties props = new Properties();
        props.setProperty("tool", "alias");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertThrows(UnsupportedOperationException.class, () ->
                source.snapshot().put("new", List.of("fail")));
    }
}
