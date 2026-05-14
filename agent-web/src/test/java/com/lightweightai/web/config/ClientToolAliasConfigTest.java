package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.directive.ClientToolAliasRegistry;
import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.agent.annotation.ClientTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolAliasConfig — YAML 配置 -> ClientToolAliasRegistry 注入")
class ClientToolAliasConfigTest {

    @AfterEach
    void clear() {
        ClientToolAliasRegistry.reset();
    }

    @ClientTool(toolName = "ExecOsApi", aliases = {"ExecuteOSAPI"},
        downAction = "ExecuteOSAPI", upAction = "ExecuteOSAPIRsp",
        namespace = "FunctionExecute", timeoutMs = 30_000, version = "1.1")
    private static final class StubAnnotated {
    }

    @Test
    @DisplayName("PostConstruct 后注解读取会合并 YAML 配置里的别名")
    void postConstructShouldInstallSourceAndMergeAliases() {
        ClientToolAliasProperties props = new ClientToolAliasProperties();
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("ExecOsApi", List.of("ExecuteOSAPI", "OsApiInvoke", "ExtFromYaml"));
        aliases.put("GetPosition", List.of("Locate"));
        props.setAliases(aliases);

        ClientToolAliasConfig config = new ClientToolAliasConfig(props);
        config.install();

        ClientTool annotation = StubAnnotated.class.getAnnotation(ClientTool.class);
        DirectiveDescriptor descriptor = DirectiveDescriptor.from(annotation);

        // 注解里已有 "ExecuteOSAPI"，YAML 重复时不要再加；"OsApiInvoke"、"ExtFromYaml" 增量加入。
        assertEquals(List.of("ExecuteOSAPI", "OsApiInvoke", "ExtFromYaml"), descriptor.getAliases());
        assertEquals(List.of("ExecOsApi", "ExecuteOSAPI", "OsApiInvoke", "ExtFromYaml"),
            descriptor.getAllToolNames());
    }

    @Test
    @DisplayName("空配置时 PostConstruct 安装 no-op 源，不影响注解 aliases")
    void emptyConfigShouldNotBreakAnnotationAliases() {
        ClientToolAliasConfig config = new ClientToolAliasConfig(new ClientToolAliasProperties());
        config.install();

        ClientTool annotation = StubAnnotated.class.getAnnotation(ClientTool.class);
        DirectiveDescriptor descriptor = DirectiveDescriptor.from(annotation);

        assertEquals(List.of("ExecuteOSAPI"), descriptor.getAliases());
    }

    @Test
    @DisplayName("PreDestroy 释放后回到默认 Properties 源行为")
    void uninstallShouldResetRegistry() {
        ClientToolAliasProperties props = new ClientToolAliasProperties();
        props.setAliases(Map.of("ExecOsApi", List.of("ExtFromYaml")));
        ClientToolAliasConfig config = new ClientToolAliasConfig(props);

        config.install();
        assertEquals(List.of("ExtFromYaml"), ClientToolAliasRegistry.aliasesFor("ExecOsApi"));

        config.uninstall();
        // 默认 Properties 源在 classpath 下没有配置 -> 返回空
        assertTrue(ClientToolAliasRegistry.aliasesFor("ExecOsApi").isEmpty());
    }

    @Test
    @DisplayName("setAliases(null) 不会抛 NPE")
    void setAliasesShouldTolerateNull() {
        ClientToolAliasProperties props = new ClientToolAliasProperties();
        props.setAliases(null);
        assertNotNull(props.getAliases());
        assertTrue(props.aliasesFor("Anything").isEmpty());
    }
}
