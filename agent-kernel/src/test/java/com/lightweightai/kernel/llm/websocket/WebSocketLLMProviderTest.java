package com.lightweightai.kernel.llm.websocket;

import com.lightweightai.kernel.llm.ModelCapability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketLLMProvider — WebSocket-based LLM provider")
class WebSocketLLMProviderTest {

    private WebSocketLLMProvider provider;

    @BeforeEach
    void setup() {
        provider = new WebSocketLLMProvider("ws://localhost:9999/test", "test-model");
    }

    @AfterEach
    void teardown() {
        provider.shutdown();
    }

    @Nested
    @DisplayName("provider identity")
    class Identity {

        @Test
        @DisplayName("provider name is 'websocket'")
        void providerName() {
            assertEquals("websocket", provider.getProviderName());
        }
    }

    @Nested
    @DisplayName("model capability")
    class Capability {

        @Test
        @DisplayName("reports correct model ID")
        void modelId() {
            ModelCapability cap = provider.getModelCapability();
            assertNotNull(cap);
            assertEquals("test-model", cap.getModelId());
        }

        @Test
        @DisplayName("supports tool calling and streaming")
        void supportsFeatures() {
            ModelCapability cap = provider.getModelCapability();
            Set<ModelCapability.ModelFeature> features = cap.getSupportedFeatures();
            assertTrue(features.contains(ModelCapability.ModelFeature.TOOL_CALLING));
            assertTrue(features.contains(ModelCapability.ModelFeature.STREAMING));
            assertTrue(features.contains(ModelCapability.ModelFeature.SYSTEM_MESSAGE));
        }

        @Test
        @DisplayName("reports 200k max context tokens")
        void maxContextTokens() {
            ModelCapability cap = provider.getModelCapability();
            assertEquals(200000, cap.getMaxContextTokens());
        }

        @Test
        @DisplayName("reports 8192 max output tokens")
        void maxOutputTokens() {
            ModelCapability cap = provider.getModelCapability();
            assertEquals(8192, cap.getMaxOutputTokens());
        }
    }

    @Nested
    @DisplayName("connection state")
    class ConnectionState {

        @Test
        @DisplayName("initially not connected")
        void initiallyDisconnected() {
            assertFalse(provider.isConnected());
        }

        @Test
        @DisplayName("disconnect on non-connected provider is safe")
        void disconnectWhenNotConnected() {
            assertDoesNotThrow(() -> provider.disconnect());
        }

        @Test
        @DisplayName("shutdown is safe on non-connected provider")
        void shutdownSafe() {
            assertDoesNotThrow(() -> provider.shutdown());
        }
    }

    @Nested
    @DisplayName("sync complete")
    class SyncComplete {

        @Test
        @DisplayName("complete on disconnected provider throws RuntimeException")
        void completeWhenDisconnectedThrows() {
            assertThrows(RuntimeException.class,
                    () -> provider.complete(java.util.List.of(), null));
        }
    }
}
