package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PropertiesClientToolAliasSource - Properties 别名加载")
class PropertiesClientToolAliasSourceTest {

    @Test
    @DisplayName("从 Properties 解析别名")
    void parseFromProperties() {
        Properties props = new Properties();
        props.setProperty("ExecOsApi", "ExecuteOSAPI,OsApiInvoke");
        props.setProperty("GetPosition", "Locate");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertEquals(List.of("ExecuteOSAPI", "OsApiInvoke"), source.aliasesFor("ExecOsApi"));
        assertEquals(List.of("Locate"), source.aliasesFor("GetPosition"));
    }

    @Test
    @DisplayName("未配置的 tool 返回空列表")
    void unknownToolReturnsEmpty() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(new Properties());
        assertTrue(source.aliasesFor("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("null tool name 返回空列表")
    void nullToolNameReturnsEmpty() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(new Properties());
        assertTrue(source.aliasesFor(null).isEmpty());
    }

    @Test
    @DisplayName("别名中的空值被过滤")
    void emptyAliasesFiltered() {
        Properties props = new Properties();
        props.setProperty("tool", "alias1,,  ,alias2");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);
        assertEquals(List.of("alias1", "alias2"), source.aliasesFor("tool"));
    }

    @Test
    @DisplayName("空 Properties 返回空 snapshot")
    void emptyPropertiesEmptySnapshot() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(new Properties());
        assertTrue(source.snapshot().isEmpty());
    }

    @Test
    @DisplayName("snapshot 返回不可变视图")
    void snapshotIsUnmodifiable() {
        Properties props = new Properties();
        props.setProperty("tool", "alias");
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        Map<String, List<String>> snapshot = source.snapshot();
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.put("new", List.of()));
    }

    @Test
    @DisplayName("不存在的 classpath 资源不报错")
    void missingClasspathResourceNoError() {
        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource("nonexistent.properties");
        assertTrue(source.snapshot().isEmpty());
    }

    @Test
    @DisplayName("空 key 被忽略")
    void emptyKeyIgnored() {
        Properties props = new Properties();
        props.setProperty("", "alias");
        props.setProperty("  ", "alias2");
        props.setProperty("valid", "alias3");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);
        assertEquals(1, source.snapshot().size());
        assertTrue(source.snapshot().containsKey("valid"));
    }
}
