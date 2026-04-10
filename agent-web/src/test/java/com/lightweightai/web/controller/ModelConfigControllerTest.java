package com.lightweightai.web.controller;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.web.config.AgentConfig;
import com.lightweightai.web.config.DynamicLLMProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ModelConfigController - 模型配置管理")
@ExtendWith(MockitoExtension.class)
class ModelConfigControllerTest {

    @Mock
    private AgentConfig agentConfig;
    @Mock
    private HttpServletRequest adminRequest;
    @Mock
    private HttpServletRequest userRequest;

    private DynamicLLMProvider dynamicProvider;
    private ModelConfigController controller;

    @BeforeEach
    void setUp() {
        dynamicProvider = new DynamicLLMProvider(createStubProvider("initial-provider"));
        controller = new ModelConfigController(dynamicProvider, agentConfig);

        when(adminRequest.getAttribute("userRole")).thenReturn("ADMIN");
        when(userRequest.getAttribute("userRole")).thenReturn("USER");
    }

    // ==================== GET /api/model-config ====================

    @Nested
    @DisplayName("获取当前配置")
    class GetConfig {

        @Test
        @DisplayName("返回当前 provider 信息")
        void shouldReturnCurrentConfig() {
            Map<String, Object> config = controller.getConfig();

            assertNotNull(config);
            assertEquals("initial-provider", config.get("providerName"));
        }

        @Test
        @DisplayName("初始 apiKey 为空时 hasApiKey 为 false")
        void shouldShowNoApiKeyInitially() {
            Map<String, Object> config = controller.getConfig();
            assertEquals(false, config.get("hasApiKey"));
        }
    }

    // ==================== PUT /api/model-config ====================

    @Nested
    @DisplayName("更新模型配置")
    class UpdateConfig {

        @Test
        @DisplayName("管理员可更新配置")
        void shouldUpdateConfigAsAdmin() {
            LLMProvider newProvider = createStubProvider("new-provider");
            when(agentConfig.buildProvider("openrouter", "sk-test", "gpt-4", ""))
                .thenReturn(newProvider);

            Map<String, Object> result = controller.updateConfig(
                Map.of("providerType", "openrouter", "apiKey", "sk-test", "model", "gpt-4"),
                adminRequest
            );

            assertEquals("模型配置已更新", result.get("message"));
            assertEquals("new-provider", result.get("providerName"));
        }

        @Test
        @DisplayName("非管理员更新应抛 403")
        void shouldDenyNonAdmin() {
            assertThrows(ModelConfigController.ForbiddenException.class, () ->
                controller.updateConfig(Map.of(), userRequest));
        }

        @Test
        @DisplayName("空 apiKey 保留已有值")
        void shouldKeepExistingApiKeyWhenEmpty() {
            // 第一次更新设置 apiKey
            LLMProvider provider1 = createStubProvider("p1");
            when(agentConfig.buildProvider(anyString(), eq("real-key"), anyString(), anyString()))
                .thenReturn(provider1);
            controller.updateConfig(
                Map.of("providerType", "openrouter", "apiKey", "real-key", "model", "m1"),
                adminRequest
            );

            // 第二次更新不传 apiKey
            LLMProvider provider2 = createStubProvider("p2");
            when(agentConfig.buildProvider(anyString(), eq("real-key"), anyString(), anyString()))
                .thenReturn(provider2);
            controller.updateConfig(
                Map.of("providerType", "openrouter", "model", "m2"),
                adminRequest
            );

            verify(agentConfig, times(2)).buildProvider(anyString(), eq("real-key"), anyString(), anyString());
        }
    }

    // ==================== Presets ====================

    @Nested
    @DisplayName("预设管理")
    class Presets {

        @Test
        @DisplayName("保存和列出预设")
        void shouldSaveAndListPreset() {
            Map<String, Object> saveResult = controller.savePreset(
                Map.of("name", "test-preset"), adminRequest
            );
            assertTrue(saveResult.get("message").toString().contains("test-preset"));

            List<Map<String, Object>> presets = controller.listPresets();
            assertEquals(1, presets.size());
            assertEquals("test-preset", presets.get(0).get("name"));
        }

        @Test
        @DisplayName("预设名为空应抛异常")
        void shouldThrowOnEmptyPresetName() {
            assertThrows(IllegalArgumentException.class, () ->
                controller.savePreset(Map.of("name", ""), adminRequest));
        }

        @Test
        @DisplayName("应用已保存的预设")
        void shouldApplyPreset() {
            controller.savePreset(Map.of("name", "my-preset"), adminRequest);

            LLMProvider newProvider = createStubProvider("preset-provider");
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(newProvider);

            Map<String, Object> result = controller.applyPreset("my-preset", adminRequest);
            assertTrue(result.get("message").toString().contains("my-preset"));
        }

        @Test
        @DisplayName("应用不存在的预设应抛异常")
        void shouldThrowOnMissingPreset() {
            assertThrows(IllegalArgumentException.class, () ->
                controller.applyPreset("nonexistent", adminRequest));
        }

        @Test
        @DisplayName("删除预设")
        void shouldDeletePreset() {
            controller.savePreset(Map.of("name", "to-delete"), adminRequest);
            assertEquals(1, controller.listPresets().size());

            controller.deletePreset("to-delete", adminRequest);
            assertEquals(0, controller.listPresets().size());
        }

        @Test
        @DisplayName("删除不存在的预设应抛异常")
        void shouldThrowOnDeleteMissingPreset() {
            assertThrows(IllegalArgumentException.class, () ->
                controller.deletePreset("nonexistent", adminRequest));
        }

        @Test
        @DisplayName("非管理员操作预设应抛 403")
        void shouldDenyPresetOpsForNonAdmin() {
            assertThrows(ModelConfigController.ForbiddenException.class, () ->
                controller.savePreset(Map.of("name", "x"), userRequest));
            assertThrows(ModelConfigController.ForbiddenException.class, () ->
                controller.applyPreset("x", userRequest));
            assertThrows(ModelConfigController.ForbiddenException.class, () ->
                controller.deletePreset("x", userRequest));
        }
    }

    // ==================== 辅助方法 ====================

    private static LLMProvider createStubProvider(String name) {
        return new LLMProvider() {
            @Override public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("stub")
                        .build())
                    .build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
                return CompletableFuture.completedFuture(complete(messages, options));
            }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
                return completeAsync(messages, options);
            }
            @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
                return Flux.just(StreamEvent.textDelta("stub"));
            }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return name; }
        };
    }
}
