package com.lightweightai.tools.client;

import com.lightweightai.kernel.agent.ClientToolAlias;
import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExecOsApiTool — 多 toolName 共享同一上下行指令")
class ExecOsApiToolTest {

    @Test
    @DisplayName("注解 aliases 出现在 DirectiveDescriptor.getAllToolNames()")
    void shouldExposeAllToolNamesFromAnnotation() {
        ExecOsApiTool tool = new ExecOsApiTool(noopDispatcher());

        DirectiveDescriptor descriptor = tool.getDirectiveDescriptor();
        assertEquals("ExecOsApi", descriptor.getTool());
        assertEquals(List.of("ExecuteOSAPI", "OsApiInvoke"), descriptor.getAliases());
        assertEquals(List.of("ExecOsApi", "ExecuteOSAPI", "OsApiInvoke"),
            descriptor.getAllToolNames());
    }

    @Test
    @DisplayName("注册到 ToolRegistry 后，主名与别名都对 LLM 可见")
    void shouldRegisterAllAliasesAsDistinctLlmTools() {
        ToolRegistry registry = new ToolRegistry();
        ExecOsApiTool tool = new ExecOsApiTool(capturingDispatcher());

        registry.register(tool);

        assertTrue(registry.has("ExecOsApi"));
        assertTrue(registry.has("ExecuteOSAPI"));
        assertTrue(registry.has("OsApiInvoke"));

        List<Map<String, Object>> defs = registry.getToolDefinitions();
        List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("ExecOsApi"));
        assertTrue(names.contains("ExecuteOSAPI"));
        assertTrue(names.contains("OsApiInvoke"));

        Tool aliasView = registry.get("ExecuteOSAPI").orElseThrow();
        assertInstanceOf(ClientToolAlias.class, aliasView);
        assertEquals("ExecuteOSAPI", aliasView.getName());
        assertSame(tool, ((ClientToolAlias) aliasView).getPrimary());
    }

    @Test
    @DisplayName("DirectiveRegistry 中别名解析到同一组 down/up action")
    void shouldRegisterDirectiveForEachAlias() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ExecOsApiTool(noopDispatcher()));

        for (String name : List.of("ExecOsApi", "ExecuteOSAPI", "OsApiInvoke")) {
            DirectiveDescriptor d = registry.getDirectiveRegistry().get(name).orElseThrow();
            assertEquals("ExecuteOSAPI", d.getDownAction(), "downAction for " + name);
            assertEquals("ExecuteOSAPIRsp", d.getUpAction(), "upAction for " + name);
            assertEquals("FunctionExecute", d.getNamespace(), "namespace for " + name);
            assertEquals(30_000, d.getTimeoutMs(), "timeoutMs for " + name);
        }
    }

    @Test
    @DisplayName("通过别名 execute() 与通过主名 execute() 落到同一个 down action")
    void aliasInvocationDispatchesSameDownAction() {
        CapturingDispatcher dispatcher = capturingDispatcher();
        ToolRegistry registry = new ToolRegistry();
        ExecOsApiTool primary = new ExecOsApiTool(dispatcher);
        registry.register(primary);

        ToolResult viaPrimary = registry.get("ExecOsApi").orElseThrow()
            .execute(Map.of("apiName", "getDeviceInfo"));
        assertFalse(viaPrimary.isError(), viaPrimary.getContent());
        assertEquals("ExecuteOSAPI", dispatcher.lastDirectiveName);

        dispatcher.reset();
        ToolResult viaAlias = registry.get("ExecuteOSAPI").orElseThrow()
            .execute(Map.of("apiName", "getDeviceInfo"));
        assertFalse(viaAlias.isError(), viaAlias.getContent());
        assertEquals("ExecuteOSAPI", dispatcher.lastDirectiveName,
            "alias 调用必须仍然走 ExecuteOSAPI 下行指令");
    }

    @Test
    @DisplayName("buildPayload 强制要求 apiName")
    void shouldRejectMissingApiName() {
        ExecOsApiTool tool = new ExecOsApiTool(noopDispatcher());
        ToolResult result = tool.execute(Map.of("params", Map.of("x", 1)));
        assertTrue(result.isError());
    }

    private static ClientToolDispatcher noopDispatcher() {
        return (callId, toolName, directive) -> CompletableFuture.completedFuture(
            ToolResult.success("ok", Map.of(
                "header", Map.of("namespace", "FunctionExecute", "name", "ExecuteOSAPIRsp"),
                "payload", Map.of("errorCode", "0")
            ))
        );
    }

    private static CapturingDispatcher capturingDispatcher() {
        return new CapturingDispatcher();
    }

    private static class CapturingDispatcher implements ClientToolDispatcher {
        String lastDirectiveName;
        String lastToolName;
        Map<String, Object> lastPayload;

        @Override
        public CompletableFuture<ToolResult> dispatch(String callId, String toolName, Directive directive) {
            this.lastToolName = toolName;
            this.lastDirectiveName = directive.getName();
            this.lastPayload = directive.getPayload();
            return CompletableFuture.completedFuture(ToolResult.success("ok", Map.of(
                "header", Map.of("namespace", "FunctionExecute", "name", "ExecuteOSAPIRsp"),
                "payload", Map.of("errorCode", "0", "data", Map.of("v", 1))
            )));
        }

        void reset() {
            lastDirectiveName = null;
            lastToolName = null;
            lastPayload = null;
        }
    }
}
