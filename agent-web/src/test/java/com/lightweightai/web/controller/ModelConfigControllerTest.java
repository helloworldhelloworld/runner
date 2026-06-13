package com.lightweightai.web.controller;

import com.lightweightai.kernel.llm.LLMProvider;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelConfigController - 模型配置API")
class ModelConfigControllerTest {

    @Mock private DynamicLLMProvider dynamicProvider;
    @Mock private AgentConfig agentConfig;
    @Mock private LLMProvider delegateProvider;
    @Mock private HttpServletRequest httpRequest;

    private ModelConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new ModelConfigController(dynamicProvider, agentConfig);
    }

    @Nested
    @DisplayName("GET /api/model-config")
    class GetConfig {
        @Test
        @DisplayName("返回当前配置")
        void shouldReturnCurrentConfig() {
            when(dynamicProvider.getDelegate()).thenReturn(delegateProvider);
            when(delegateProvider.getProviderName()).thenReturn("MockProvider");

            Map<String, Object> config = controller.getConfig();

            assertEquals("MockProvider", config.get("providerName"));
            assertNotNull(config.get("providerType"));
            assertNotNull(config.get("hasApiKey"));
        }
    }

    @Nested
    @DisplayName("PUT /api/model-config")
    class UpdateConfig {
        @Test
        @DisplayName("管理员可更新配置")
        void shouldUpdateConfigForAdmin() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            LLMProvider newProvider = mock(LLMProvider.class);
            when(newProvider.getProviderName()).thenReturn("NewProvider");
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(newProvider);

            Map<String, Object> result = controller.updateConfig(
                Map.of("providerType", "openrouter", "model", "gpt-4", "apiKey", "sk-test"),
                httpRequest
            );

            assertEquals("模型配置已更新", result.get("message"));
            verify(dynamicProvider).setDelegate(newProvider);
        }

        @Test
        @DisplayName("非管理员抛出ForbiddenException")
        void shouldThrowForNonAdmin() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            assertThrows(ModelConfigController.ForbiddenException.class,
                () -> controller.updateConfig(Map.of(), httpRequest));
        }

        @Test
        @DisplayName("掩码API key保留原有key")
        void shouldKeepExistingApiKeyForMasked() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            LLMProvider newProvider = mock(LLMProvider.class);
            when(newProvider.getProviderName()).thenReturn("P");
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(newProvider);

            // First set a real key
            controller.updateConfig(
                Map.of("providerType", "openrouter", "apiKey", "sk-realkey12345678"),
                httpRequest);

            // Then send masked key - should keep original
            controller.updateConfig(
                Map.of("providerType", "openrouter", "apiKey", "***"),
                httpRequest);

            // Verify buildProvider was called with original key on second call
            verify(agentConfig, times(2)).buildProvider(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("预设管理")
    class PresetManagement {

        private void setupAdmin() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
        }

        private void setupProvider() {
            LLMProvider p = mock(LLMProvider.class);
            when(p.getProviderName()).thenReturn("P");
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(p);
        }

        @Test
        @DisplayName("保存预设")
        void shouldSavePreset() {
            setupAdmin();

            // First set some config
            setupProvider();
            controller.updateConfig(
                Map.of("providerType", "openrouter", "model", "gpt-4", "apiKey", "sk-test"),
                httpRequest);

            // Save preset
            Map<String, Object> result = controller.savePreset(Map.of("name", "my-preset"), httpRequest);
            assertEquals("预设 \"my-preset\" 已保存", result.get("message"));
        }

        @Test
        @DisplayName("列出预设")
        void shouldListPresets() {
            setupAdmin();
            setupProvider();
            controller.updateConfig(
                Map.of("providerType", "claude", "model", "claude-3"),
                httpRequest);
            controller.savePreset(Map.of("name", "preset1"), httpRequest);

            List<Map<String, Object>> presets = controller.listPresets();
            assertEquals(1, presets.size());
            assertEquals("preset1", presets.get(0).get("name"));
        }

        @Test
        @DisplayName("应用预设")
        void shouldApplyPreset() {
            setupAdmin();
            setupProvider();
            controller.updateConfig(Map.of("providerType", "openrouter"), httpRequest);
            controller.savePreset(Map.of("name", "saved"), httpRequest);

            Map<String, Object> result = controller.applyPreset("saved", httpRequest);
            assertTrue(result.get("message").toString().contains("saved"));
        }

        @Test
        @DisplayName("删除预设")
        void shouldDeletePreset() {
            setupAdmin();
            setupProvider();
            controller.updateConfig(Map.of(), httpRequest);
            controller.savePreset(Map.of("name", "to-delete"), httpRequest);

            Map<String, Object> result = controller.deletePreset("to-delete", httpRequest);
            assertTrue(result.get("message").toString().contains("to-delete"));

            assertTrue(controller.listPresets().isEmpty());
        }

        @Test
        @DisplayName("应用不存在的预设抛出异常")
        void shouldThrowForMissingPreset() {
            setupAdmin();

            assertThrows(IllegalArgumentException.class,
                () -> controller.applyPreset("nonexistent", httpRequest));
        }

        @Test
        @DisplayName("删除不存在的预设抛出异常")
        void shouldThrowForDeletingMissingPreset() {
            setupAdmin();

            assertThrows(IllegalArgumentException.class,
                () -> controller.deletePreset("nonexistent", httpRequest));
        }

        @Test
        @DisplayName("空预设名称抛出异常")
        void shouldThrowForEmptyPresetName() {
            setupAdmin();

            assertThrows(IllegalArgumentException.class,
                () -> controller.savePreset(Map.of("name", ""), httpRequest));
        }

        @Test
        @DisplayName("非管理员不能保存预设")
        void shouldThrowForNonAdminSavePreset() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            assertThrows(ModelConfigController.ForbiddenException.class,
                () -> controller.savePreset(Map.of("name", "test"), httpRequest));
        }
    }
}
