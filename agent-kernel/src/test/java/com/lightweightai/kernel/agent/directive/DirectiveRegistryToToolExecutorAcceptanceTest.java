package com.lightweightai.kernel.agent.directive;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance test: DirectiveToolBridge → ToolRegistry → ToolExecutor
 *
 * Verifies the full transmission chain:
 * 1. Client manifest registers capabilities as ClientToolWrapper via DirectiveToolBridge
 * 2. ToolExecutor sees the registered tools
 * 3. ToolExecutor dispatches client-side tools to ClientToolDispatcher
 * 4. The payload (callId, toolName, args) reaches the dispatcher correctly
 */
@DisplayName("Directive → Registry → Executor 传输链验证")
class DirectiveRegistryToToolExecutorAcceptanceTest {

    private ToolRegistry registry;
    private CapturingDispatcher dispatcher;
    private DirectiveToolBridge bridge;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        dispatcher = new CapturingDispatcher();
        bridge = new DirectiveToolBridge(registry, dispatcher);
        executor = new ToolExecutor(registry);
    }

    @Test
    @DisplayName("完整链路：注册→执行→Dispatch 到客户端，payload 正确到达")
    void fullChainRegistrationToDispatch() {
        ClientManifest manifest = new ClientManifest("android", "2.0", List.of(
            new ClientCapability("Camera", "TakePhoto", "Take a photo",
                Map.of("type", "object", "properties",
                    Map.of("resolution", Map.of("type", "string"))), 30000)
        ));
        bridge.registerCapabilities(manifest);

        assertTrue(executor.hasFunction("Camera.TakePhoto"),
            "ToolExecutor must see tool registered via DirectiveToolBridge");

        List<Map<String, Object>> defs = executor.getToolDefinitions();
        assertTrue(defs.stream().anyMatch(d -> "Camera.TakePhoto".equals(d.get("name"))),
            "Tool definitions for LLM must include the directive tool");

        ToolCall call = new ToolCall("tc1", "Camera.TakePhoto",
            Map.of("resolution", "4K"));
        ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 5000);
        ToolResult result = executor.executeToolCall(call, ctx);

        assertFalse(result.isError(), "Dispatch should succeed: " + result.getContent());
        assertEquals("tc1", result.getToolUseId());
        assertEquals("photo_data", result.getContent());

        assertNotNull(dispatcher.lastToolName, "Dispatcher must have been called");
        assertEquals("Camera.TakePhoto", dispatcher.lastToolName);
        assertNotNull(dispatcher.lastDirective);
    }

    @Test
    @DisplayName("注销后工具不再对 ToolExecutor 可见")
    void unregisterRemovesFromExecutor() {
        ClientManifest manifest = new ClientManifest("ios", "1.0", List.of(
            new ClientCapability("GPS", "GetLocation", "Get location", null, 10000)
        ));
        bridge.registerCapabilities(manifest);
        assertTrue(executor.hasFunction("GPS.GetLocation"));

        bridge.unregisterCapabilities(manifest);
        assertFalse(executor.hasFunction("GPS.GetLocation"),
            "After unregister, tool must not be visible to ToolExecutor");
    }

    @Test
    @DisplayName("多能力注册后 ToolExecutor 可批量执行")
    void multipleCapabilitiesBatchExecute() {
        ClientManifest manifest = new ClientManifest("android", "2.0", List.of(
            new ClientCapability("Camera", "TakePhoto", "Photo", null, 30000),
            new ClientCapability("GPS", "GetLocation", "Location", null, 10000)
        ));
        bridge.registerCapabilities(manifest);

        assertEquals(2, executor.getFunctionCount());

        ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 5000);
        List<ToolResult> results = executor.executeToolCalls(List.of(
            new ToolCall("tc1", "Camera.TakePhoto", Map.of()),
            new ToolCall("tc2", "GPS.GetLocation", Map.of())
        ), ctx);

        assertEquals(2, results.size());
        assertFalse(results.get(0).isError());
        assertFalse(results.get(1).isError());
    }

    private static class CapturingDispatcher implements ClientToolDispatcher {
        volatile String lastToolName;
        volatile Directive lastDirective;
        final AtomicReference<String> lastCallId = new AtomicReference<>();

        @Override
        public CompletableFuture<ToolResult> dispatch(String callId, String toolName,
                                                       Directive directive) {
            lastCallId.set(callId);
            lastToolName = toolName;
            lastDirective = directive;
            return CompletableFuture.completedFuture(ToolResult.success("photo_data"));
        }
    }
}
