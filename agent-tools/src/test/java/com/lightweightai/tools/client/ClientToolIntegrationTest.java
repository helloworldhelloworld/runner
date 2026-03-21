package com.lightweightai.tools.client;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("@ClientTool + ClientTool 集成")
class ClientToolIntegrationTest {

    @Test
    @DisplayName("注册带 @ClientTool 的工具会写入 DirectiveRegistry")
    void shouldRegisterDirectiveMetadata() {
        ToolRegistry registry = new ToolRegistry();
        GetPositionTool tool = new GetPositionTool(successDispatcher());

        registry.register(tool);

        assertTrue(registry.has("GetPosition"));
        assertTrue(registry.getDirectiveRegistry().has("GetPosition"));

        DirectiveDescriptor descriptor = registry.getDirectiveRegistry()
            .get("GetPosition")
            .orElseThrow();

        assertEquals("GetPosition", descriptor.getTool());
        assertEquals("GeoInformation", descriptor.getNamespace());
        assertEquals("GetPosition", descriptor.getDownAction());
        assertEquals("PositionInfo", descriptor.getUpAction());
        assertEquals(8_000, descriptor.getTimeoutMs());
        assertEquals("1.1", descriptor.getVersion());
    }

    @Test
    @DisplayName("ClientTool 模板方法会按上下行 action 组包与验包")
    void shouldInvokeViaTemplateMethod() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        GetPositionTool tool = new GetPositionTool(dispatcher);

        Map<String, Object> result = tool.invoke(Map.of("precision", 3, "needCityName", "true"));

        assertEquals("118.773", result.get("longitude"));
        assertEquals("31.984", result.get("latitude"));
        assertEquals("南京", result.get("city"));

        assertEquals("GetPosition", tool.getName());
        assertEquals("GetPosition", dispatcher.dispatchedToolName);
        assertEquals(3, dispatcher.dispatchedArgs.get("precision"));
        assertEquals("true", dispatcher.dispatchedArgs.get("needCityName"));

        ToolResult toolResult = tool.execute(Map.of("precision", 3));
        assertFalse(toolResult.isError());
        assertTrue(toolResult.hasStructuredContent());
    }

    @Test
    @DisplayName("上行 header.name 不匹配 upAction 时应失败")
    void shouldFailWhenUplinkActionMismatch() {
        ClientToolDispatcher mismatchDispatcher = (callId, toolName, args) -> CompletableFuture.completedFuture(
            ToolResult.success("ok", Map.of(
                "header", Map.of("namespace", "GeoInformation", "name", "OtherAction"),
                "payload", Map.of("errorCode", "0")
            ))
        );
        GetPositionTool tool = new GetPositionTool(mismatchDispatcher);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> tool.invoke(Map.of("precision", 3)));
        assertTrue(ex.getMessage().contains("Unexpected uplink header"));
    }

    private static ClientToolDispatcher successDispatcher() {
        return (callId, toolName, args) -> CompletableFuture.completedFuture(
            ToolResult.success("ok", Map.of(
                "header", Map.of("namespace", "GeoInformation", "name", "PositionInfo"),
                "payload", Map.of(
                    "errorCode", "0",
                    "position", Map.of(
                        "longitude", "118.773",
                        "latitude", "31.984",
                        "locationSystem", "BD09LL"
                    ),
                    "city", "南京"
                )
            ))
        );
    }

    private static class CapturingDispatcher implements ClientToolDispatcher {
        private String dispatchedToolName;
        private Map<String, Object> dispatchedArgs;

        @Override
        public CompletableFuture<ToolResult> dispatch(String callId, String toolName, Map<String, Object> args) {
            this.dispatchedToolName = toolName;
            this.dispatchedArgs = args;
            return successDispatcher().dispatch(callId, toolName, args);
        }
    }

}
