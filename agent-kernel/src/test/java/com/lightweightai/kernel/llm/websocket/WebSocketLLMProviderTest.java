package com.lightweightai.kernel.llm.websocket;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.ModelCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketLLMProvider — 核心逻辑单元测试")
class WebSocketLLMProviderTest {

    @Test
    @DisplayName("providerName 返回 websocket")
    void providerName() {
        WebSocketLLMProvider provider = new WebSocketLLMProvider("ws://localhost:8080", "test-model");
        assertEquals("websocket", provider.getProviderName());
    }

    @Test
    @DisplayName("未连接时 isConnected 返回 false")
    void notConnectedByDefault() {
        WebSocketLLMProvider provider = new WebSocketLLMProvider("ws://localhost:8080", "test-model");
        assertFalse(provider.isConnected());
    }

    @Test
    @DisplayName("disconnect 对未连接的 provider 不抛异常")
    void disconnectWhenNotConnected() {
        WebSocketLLMProvider provider = new WebSocketLLMProvider("ws://localhost:8080", "test-model");
        assertDoesNotThrow(provider::disconnect);
    }

    @Test
    @DisplayName("shutdown 对未连接的 provider 不抛异常")
    void shutdownWhenNotConnected() {
        WebSocketLLMProvider provider = new WebSocketLLMProvider("ws://localhost:8080", "test-model");
        assertDoesNotThrow(provider::shutdown);
    }

    @Test
    @DisplayName("modelCapability 返回正确的 modelId 和 features")
    void modelCapability() {
        WebSocketLLMProvider provider = new WebSocketLLMProvider("ws://localhost:8080", "claude-3-opus");
        ModelCapability cap = provider.getModelCapability();

        assertNotNull(cap);
        assertEquals("claude-3-opus", cap.getModelId());
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(8192, cap.getMaxOutputTokens());

        Set<ModelCapability.ModelFeature> features = cap.getSupportedFeatures();
        assertTrue(features.contains(ModelCapability.ModelFeature.TOOL_CALLING));
        assertTrue(features.contains(ModelCapability.ModelFeature.STREAMING));
        assertTrue(features.contains(ModelCapability.ModelFeature.SYSTEM_MESSAGE));
    }

    @Test
    @DisplayName("modelCapability 的 messageFormatter 和 tokenCounter 可以为 null")
    void modelCapabilityNullables() {
        WebSocketLLMProvider provider = new WebSocketLLMProvider("ws://localhost:8080", "test-model");
        ModelCapability cap = provider.getModelCapability();
        assertNull(cap.getMessageFormatter());
        assertNull(cap.getTokenCounter());
    }

    @Test
    @DisplayName("complete 对非法端口 URL 抛出 RuntimeException（OkHttp URL 校验）")
    void completeWithInvalidPortThrows() {
        WebSocketLLMProvider provider = new WebSocketLLMProvider("ws://localhost:99999", "test-model");
        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(MessageRole.USER)
                        .textContent("hello")
                        .build()
        );

        assertThrows(RuntimeException.class, () -> provider.complete(messages, null));
        provider.shutdown();
    }

    @Test
    @DisplayName("completeAsync 对非法端口 URL 最终异常完成")
    void completeAsyncWithInvalidPortFails() {
        WebSocketLLMProvider provider = new WebSocketLLMProvider("ws://localhost:99999", "test-model");
        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(MessageRole.USER)
                        .textContent("hello")
                        .build()
        );

        var future = provider.completeAsync(messages, null);
        assertNotNull(future);
        assertThrows(Exception.class, future::get);
        provider.shutdown();
    }
}
