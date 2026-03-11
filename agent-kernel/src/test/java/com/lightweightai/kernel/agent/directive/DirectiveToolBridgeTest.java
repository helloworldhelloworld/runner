package com.lightweightai.kernel.agent.directive;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DirectiveToolBridgeTest {

    private ToolRegistry registry;
    private MockDispatcher dispatcher;
    private DirectiveToolBridge bridge;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        dispatcher = new MockDispatcher();
        bridge = new DirectiveToolBridge(registry, dispatcher);
    }

    @Test
    void registerCapabilities_registersToolsInRegistry() {
        ClientManifest manifest = new ClientManifest("android", "2.1.0", List.of(
            new ClientCapability("Camera", "TakePhoto",
                "Take a photo", Map.of("type", "object"), 30000),
            new ClientCapability("GPS", "GetLocation",
                "Get device location", null, 10000)
        ));

        List<String> registered = bridge.registerCapabilities(manifest);

        assertEquals(2, registered.size());
        assertTrue(registered.contains("Camera.TakePhoto"));
        assertTrue(registered.contains("GPS.GetLocation"));
        assertEquals(2, registry.size());
        assertTrue(registry.has("Camera.TakePhoto"));
        assertTrue(registry.has("GPS.GetLocation"));
    }

    @Test
    void registerCapabilities_toolsAreClientSide() {
        ClientManifest manifest = new ClientManifest("ios", "1.0", List.of(
            new ClientCapability("NFC", "ReadTag", "Read NFC tag", null, 5000)
        ));

        bridge.registerCapabilities(manifest);

        Tool tool = registry.get("NFC.ReadTag").orElse(null);
        assertNotNull(tool);
        assertInstanceOf(ToolMetadata.class, tool);
        assertTrue(((ToolMetadata) tool).isClientSide());
    }

    @Test
    void unregisterCapabilities_removesFromRegistry() {
        ClientManifest manifest = new ClientManifest("android", "2.0", List.of(
            new ClientCapability("Camera", "TakePhoto", "Take photo", null, 30000)
        ));

        bridge.registerCapabilities(manifest);
        assertTrue(registry.has("Camera.TakePhoto"));

        bridge.unregisterCapabilities(manifest);
        assertFalse(registry.has("Camera.TakePhoto"));
    }

    @Test
    void registerCapabilities_emptyManifest() {
        ClientManifest manifest = new ClientManifest("browser", "1.0", List.of());
        List<String> registered = bridge.registerCapabilities(manifest);
        assertTrue(registered.isEmpty());
    }

    @Test
    void toDirective_withNamespace() {
        Directive d = bridge.toDirective("call-1", "Camera.TakePhoto",
            Map.of("res", "1080p"));

        assertEquals("call-1", d.getDirectiveId());
        assertEquals("Camera", d.getNamespace());
        assertEquals("TakePhoto", d.getName());
        assertEquals("1080p", d.getPayload().get("res"));
    }

    @Test
    void fromDirectiveResult_success() {
        DirectiveResult dr = DirectiveResult.success("d-1", "photo.jpg");
        ToolResult tr = bridge.fromDirectiveResult(dr);

        assertFalse(tr.isError());
        assertEquals("photo.jpg", tr.getContent());
    }

    @Test
    void fromDirectiveResult_error() {
        DirectiveResult dr = DirectiveResult.error("d-2", "Permission denied");
        ToolResult tr = bridge.fromDirectiveResult(dr);

        assertTrue(tr.isError());
        assertEquals("Permission denied", tr.getContent());
    }

    @Test
    void constructor_nullRegistry_throws() {
        assertThrows(NullPointerException.class, () ->
            new DirectiveToolBridge(null, dispatcher));
    }

    @Test
    void constructor_nullDispatcher_throws() {
        assertThrows(NullPointerException.class, () ->
            new DirectiveToolBridge(registry, null));
    }

    /**
     * Mock dispatcher for testing — does nothing on dispatch.
     */
    private static class MockDispatcher implements ClientToolDispatcher {
        @Override
        public CompletableFuture<ToolResult> dispatch(String callId, String toolName,
                                                       Map<String, Object> args) {
            return new CompletableFuture<>(); // Never completes (not needed for registration tests)
        }
    }
}
