package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PropertiesClientToolAliasSource — properties-based alias config")
class PropertiesClientToolAliasSourceTest {

    @Test
    @DisplayName("parses comma-separated aliases from Properties")
    void parsesCommaSeparatedAliases() {
        Properties props = new Properties();
        props.setProperty("ExecOsApi", "ExecuteOSAPI, OsApiInvoke");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertEquals(List.of("ExecuteOSAPI", "OsApiInvoke"), source.aliasesFor("ExecOsApi"));
    }

    @Test
    @DisplayName("trims whitespace from keys and values")
    void trimsWhitespace() {
        Properties props = new Properties();
        props.setProperty("  SpacedKey  ", "  Alias1 , Alias2  ");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertEquals(List.of("Alias1", "Alias2"), source.aliasesFor("SpacedKey"));
    }

    @Test
    @DisplayName("skips empty values after split")
    void skipsEmptyValues() {
        Properties props = new Properties();
        props.setProperty("Tool", "A,, ,B, ,C");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertEquals(List.of("A", "B", "C"), source.aliasesFor("Tool"));
    }

    @Test
    @DisplayName("empty value string results in no aliases")
    void emptyValueNoAliases() {
        Properties props = new Properties();
        props.setProperty("Tool", "");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertTrue(source.aliasesFor("Tool").isEmpty());
    }

    @Test
    @DisplayName("missing key returns empty list")
    void missingKeyReturnsEmpty() {
        Properties props = new Properties();
        props.setProperty("Existing", "Alias");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertTrue(source.aliasesFor("Missing").isEmpty());
    }

    @Test
    @DisplayName("null primaryToolName returns empty list")
    void nullNameReturnsEmpty() {
        Properties props = new Properties();
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertTrue(source.aliasesFor(null).isEmpty());
    }

    @Test
    @DisplayName("null Properties results in empty aliases")
    void nullPropertiesEmpty() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource((Properties) null);

        assertTrue(source.snapshot().isEmpty());
    }

    @Test
    @DisplayName("empty Properties results in empty aliases")
    void emptyPropertiesEmpty() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(new Properties());

        assertTrue(source.snapshot().isEmpty());
    }

    @Test
    @DisplayName("nonexistent classpath resource returns empty, no exception")
    void missingResourceSilent() {
        PropertiesClientToolAliasSource source =
            new PropertiesClientToolAliasSource("no-such-file.properties");

        assertTrue(source.snapshot().isEmpty());
        assertTrue(source.aliasesFor("Anything").isEmpty());
    }

    @Test
    @DisplayName("snapshot returns immutable copy")
    void snapshotImmutable() {
        Properties props = new Properties();
        props.setProperty("A", "B");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);
        Map<String, List<String>> snapshot = source.snapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("X", List.of()));
    }

    @Test
    @DisplayName("multiple tool entries parsed independently")
    void multipleEntries() {
        Properties props = new Properties();
        props.setProperty("Tool1", "A1,A2");
        props.setProperty("Tool2", "B1");
        props.setProperty("Tool3", "C1,C2,C3");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertEquals(3, source.snapshot().size());
        assertEquals(List.of("A1", "A2"), source.aliasesFor("Tool1"));
        assertEquals(List.of("B1"), source.aliasesFor("Tool2"));
        assertEquals(List.of("C1", "C2", "C3"), source.aliasesFor("Tool3"));
    }

    @Test
    @DisplayName("default constructor tries classpath load without throwing")
    void defaultConstructorSafe() {
        assertDoesNotThrow(() -> new PropertiesClientToolAliasSource());
    }
}
