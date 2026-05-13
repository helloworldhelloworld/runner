package com.lightweightai.kernel.agent.directive;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ClientTool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolAliasRegistry — 外部配置注入注解 aliases")
class ClientToolAliasRegistryTest {

    @AfterEach
    void clear() {
        ClientToolAliasRegistry.reset();
    }

    @ClientTool(toolName = "Foo", downAction = "FooDown", upAction = "FooUp",
        namespace = "Ns", timeoutMs = 1000, version = "1.0")
    private static final class Annotated {
    }

    @ClientTool(toolName = "Bar", aliases = {"BarAnno"}, downAction = "BarDown", upAction = "BarUp",
        namespace = "Ns", timeoutMs = 1000, version = "1.0")
    private static final class WithAnnotationAlias {
    }

    @Test
    @DisplayName("外部源提供的别名会合并进 DirectiveDescriptor.aliases")
    void shouldMergeExternalAliasesIntoDescriptor() {
        ClientToolAliasRegistry.setSource(name ->
            "Foo".equals(name) ? List.of("FooCfg1", "FooCfg2") : List.of());

        ClientTool annotation = Annotated.class.getAnnotation(ClientTool.class);
        DirectiveDescriptor d = DirectiveDescriptor.from(annotation);

        assertEquals(List.of("FooCfg1", "FooCfg2"), d.getAliases());
        assertEquals(List.of("Foo", "FooCfg1", "FooCfg2"), d.getAllToolNames());
    }

    @Test
    @DisplayName("注解 aliases 与外部 aliases 合并并去重")
    void shouldDedupeBetweenAnnotationAndExternal() {
        ClientToolAliasRegistry.setSource(name ->
            "Bar".equals(name) ? List.of("BarAnno", "BarCfg") : List.of());

        ClientTool annotation = WithAnnotationAlias.class.getAnnotation(ClientTool.class);
        DirectiveDescriptor d = DirectiveDescriptor.from(annotation);

        assertEquals(List.of("BarAnno", "BarCfg"), d.getAliases(),
            "annotation alias must be preserved, external alias appended without duplication");
    }

    @Test
    @DisplayName("PropertiesClientToolAliasSource 从 Properties 解析多别名")
    void propertiesSourceShouldParseCommaSeparated() {
        Properties props = new Properties();
        props.setProperty("ExecOsApi", "ExecuteOSAPI, OsApiInvoke ,  ");
        props.setProperty("GetPosition", "Locate");
        props.setProperty("Empty", "");

        PropertiesClientToolAliasSource source = new PropertiesClientToolAliasSource(props);

        assertEquals(List.of("ExecuteOSAPI", "OsApiInvoke"), source.aliasesFor("ExecOsApi"));
        assertEquals(List.of("Locate"), source.aliasesFor("GetPosition"));
        assertTrue(source.aliasesFor("Empty").isEmpty());
        assertTrue(source.aliasesFor("Missing").isEmpty());
    }

    @Test
    @DisplayName("Properties 源缺失文件时静默返回空，不抛异常")
    void propertiesSourceShouldTolerateMissingFile() {
        PropertiesClientToolAliasSource source =
            new PropertiesClientToolAliasSource("non-existent-config.properties");

        assertTrue(source.snapshot().isEmpty());
        assertTrue(source.aliasesFor("Anything").isEmpty());
    }

    @Test
    @DisplayName("配置源注入后，注册到 ToolRegistry 会自动暴露外部别名")
    void externalAliasShouldFlowThroughToolRegistry() {
        ClientToolAliasRegistry.setSource(name ->
            "Foo".equals(name) ? List.of("FooExt") : List.of());

        ToolRegistry registry = new ToolRegistry();
        registry.register(new FooClientTool(noopDispatcher()));

        assertTrue(registry.has("Foo"));
        assertTrue(registry.has("FooExt"),
            "external alias must be registered as an LLM-visible tool");
        DirectiveDescriptor d = registry.getDirectiveRegistry().get("FooExt").orElseThrow();
        assertEquals("FooDown", d.getDownAction());
    }

    @ClientTool(toolName = "Foo", downAction = "FooDown", upAction = "FooUp",
        namespace = "Ns", timeoutMs = 1000, version = "1.0")
    private static class FooClientTool extends com.lightweightai.kernel.agent.ClientTool<Map<String, Object>> {
        FooClientTool(ClientToolDispatcher dispatcher) {
            super(dispatcher);
        }

        @Override
        protected Map<String, Object> buildPayload(Map<String, Object> args) {
            return Map.of();
        }

        @Override
        protected Map<String, Object> parseResult(ToolResult result) {
            return Map.of();
        }
    }

    private static ClientToolDispatcher noopDispatcher() {
        return (callId, toolName, directive) -> CompletableFuture.completedFuture(
            ToolResult.success("ok", Map.of(
                "header", Map.of("namespace", "Ns", "name", "FooUp"),
                "payload", Map.of()
            ))
        );
    }

    @SuppressWarnings("unused")
    private static ClientTool annotationOf(AnnotatedElement element) {
        return element.getAnnotation(ClientTool.class);
    }
}
