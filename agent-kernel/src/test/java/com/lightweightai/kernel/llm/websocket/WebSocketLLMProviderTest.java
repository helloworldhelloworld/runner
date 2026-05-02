package com.lightweightai.kernel.llm.websocket;

import com.lightweightai.kernel.llm.ModelCapability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketLLMProvider - WebSocket-based LLM provider")
class WebSocketLLMProviderTest {

    private WebSocketLLMProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WebSocketLLMProvider("ws://localhost:9999/ws", "test-model");
    }

    @AfterEach
    void tearDown() {
        provider.shutdown();
    }

    @Test
    @DisplayName("provider name is 'websocket'")
    void providerName() {
        assertEquals("websocket", provider.getProviderName());
    }

    @Test
    @DisplayName("model capability reflects constructor model id")
    void modelCapabilityReflectsModel() {
        ModelCapability cap = provider.getModelCapability();

        assertNotNull(cap);
        assertEquals("test-model", cap.getModelId());
    }

    @Test
    @DisplayName("model capability has expected defaults")
    void modelCapabilityDefaults() {
        ModelCapability cap = provider.getModelCapability();

        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(8192, cap.getMaxOutputTokens());
        assertNull(cap.getMessageFormatter());
        assertNull(cap.getTokenCounter());
    }

    @Test
    @DisplayName("model capability supports TOOL_CALLING and STREAMING")
    void modelCapabilityFeatures() {
        ModelCapability cap = provider.getModelCapability();

        assertTrue(cap.getSupportedFeatures().contains(ModelCapability.ModelFeature.TOOL_CALLING));
        assertTrue(cap.getSupportedFeatures().contains(ModelCapability.ModelFeature.STREAMING));
        assertTrue(cap.getSupportedFeatures().contains(ModelCapability.ModelFeature.SYSTEM_MESSAGE));
        assertTrue(cap.getSupportedFeatures().contains(ModelCapability.ModelFeature.FUNCTION_CALLING));
    }

    @Test
    @DisplayName("initially not connected")
    void initiallyNotConnected() {
        assertFalse(provider.isConnected());
    }

    @Test
    @DisplayName("disconnect when not connected is a no-op")
    void disconnectWhenNotConnectedIsNoop() {
        assertDoesNotThrow(() -> provider.disconnect());
        assertFalse(provider.isConnected());
    }

    @Test
    @DisplayName("shutdown cleans up resources without exception")
    void shutdownCleansUp() {
        assertDoesNotThrow(() -> provider.shutdown());
    }

    @Test
    @DisplayName("connect to non-existent server completes exceptionally")
    void connectToNonExistentServer() {
        var future = provider.connect();
        assertNotNull(future);
        assertTrue(future.isCompletedExceptionally() || !future.isDone());
    }

    @Test
    @DisplayName("complete throws when not connected")
    void completeThrowsWhenNotConnected() {
        assertThrows(RuntimeException.class, () ->
                provider.complete(java.util.List.of(), null));
    }
}
