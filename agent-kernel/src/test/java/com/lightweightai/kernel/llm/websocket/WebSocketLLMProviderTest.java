package com.lightweightai.kernel.llm.websocket;

import com.lightweightai.kernel.llm.ModelCapability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketLLMProvider — WebSocket LLM 提供者")
class WebSocketLLMProviderTest {

    private WebSocketLLMProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WebSocketLLMProvider("ws://localhost:8080/ws", "test-model");
    }

    @AfterEach
    void tearDown() {
        provider.shutdown();
    }

    // ==================== 基本属性 ====================

    @Test
    @DisplayName("providerName 返回 websocket")
    void providerNameIsWebsocket() {
        assertEquals("websocket", provider.getProviderName());
    }

    @Test
    @DisplayName("modelCapability 返回正确的 modelId")
    void modelCapabilityHasCorrectModelId() {
        ModelCapability cap = provider.getModelCapability();
        assertEquals("test-model", cap.getModelId());
    }

    @Test
    @DisplayName("modelCapability 上下文窗口为 200K")
    void modelCapabilityContextWindow() {
        ModelCapability cap = provider.getModelCapability();
        assertEquals(200000, cap.getMaxContextTokens());
    }

    @Test
    @DisplayName("modelCapability 最大输出 8192")
    void modelCapabilityMaxOutput() {
        ModelCapability cap = provider.getModelCapability();
        assertEquals(8192, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("modelCapability 支持 TOOL_CALLING 和 STREAMING 特性")
    void modelCapabilitySupportedFeatures() {
        Set<ModelCapability.ModelFeature> features = provider.getModelCapability().getSupportedFeatures();
        assertTrue(features.contains(ModelCapability.ModelFeature.TOOL_CALLING));
        assertTrue(features.contains(ModelCapability.ModelFeature.STREAMING));
        assertTrue(features.contains(ModelCapability.ModelFeature.SYSTEM_MESSAGE));
        assertTrue(features.contains(ModelCapability.ModelFeature.FUNCTION_CALLING));
    }

    @Test
    @DisplayName("modelCapability messageFormatter 和 tokenCounter 为 null")
    void modelCapabilityNullFormatterAndCounter() {
        ModelCapability cap = provider.getModelCapability();
        assertNull(cap.getMessageFormatter());
        assertNull(cap.getTokenCounter());
    }

    // ==================== 连接状态 ====================

    @Test
    @DisplayName("初始状态未连接")
    void initiallyNotConnected() {
        assertFalse(provider.isConnected());
    }

    @Test
    @DisplayName("disconnect 幂等 — 多次调用不抛异常")
    void disconnectIdempotent() {
        assertDoesNotThrow(() -> {
            provider.disconnect();
            provider.disconnect();
        });
    }

    @Test
    @DisplayName("shutdown 幂等 — 多次调用不抛异常")
    void shutdownIdempotent() {
        assertDoesNotThrow(() -> {
            provider.shutdown();
            provider.shutdown();
        });
    }

    // ==================== 未连接时同步调用异常 ====================

    @Test
    @DisplayName("complete 未连接时抛出 RuntimeException")
    void completeWithoutConnectionThrows() {
        assertThrows(RuntimeException.class, () ->
            provider.complete(java.util.List.of(), null)
        );
    }

    // ==================== 不同构造函数 ====================

    @Test
    @DisplayName("使用自定义 OkHttpClient 构造")
    void constructWithCustomClient() {
        okhttp3.OkHttpClient customClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build();

        WebSocketLLMProvider customProvider = new WebSocketLLMProvider(
            "ws://localhost:9999/ws", "custom-model", customClient
        );

        assertEquals("websocket", customProvider.getProviderName());
        assertEquals("custom-model", customProvider.getModelCapability().getModelId());
        customProvider.shutdown();
    }
}
