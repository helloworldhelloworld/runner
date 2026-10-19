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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelConfigController - LLM 模型配置 API")
class ModelConfigControllerTest {

    @Mock
    private DynamicLLMProvider dynamicProvider;

    @Mock
    private AgentConfig agentConfig;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private LLMProvider delegateProvider;

    private ModelConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new ModelConfigController(dynamicProvider, agentConfig);
    }

    @Nested
    @DisplayName("GET /api/model-config - 获取当前配置")
    class GetConfig {

        @Test
        @DisplayName("返回当前模型配置信息")
        void returnsCurrentConfig() {
            when(dynamicProvider.getDelegate()).thenReturn(delegateProvider);
            when(delegateProvider.getProviderName()).thenReturn("claude");

            Map<String, Object> config = controller.getConfig();

            // Since controller just initialized, volatile fields are empty strings
            assertEquals("claude", config.get("providerType"),
                    "should fall back to delegate providerName when currentProviderType is empty");
            assertEquals("claude", config.get("providerName"));
            assertEquals("", config.get("model"));
            assertEquals("", config.get("baseUrl"));
            assertEquals(false, config.get("hasApiKey"));
            assertEquals("", config.get("apiKeyMasked"),
                    "maskApiKey returns empty string for empty key");
        }

        @Test
        @DisplayName("更新后 getConfig 反映新配置")
        void reflectsUpdatedConfig() {
            // First update config as admin
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            LLMProvider newProvider = mock(LLMProvider.class);
            when(newProvider.getProviderName()).thenReturn("openrouter");
            when(agentConfig.buildProvider("openrouter", "sk-abcdef1234567890", "gpt-4", "https://api.example.com"))
                    .thenReturn(newProvider);

            Map<String, String> body = new HashMap<>();
            body.put("providerType", "openrouter");
            body.put("apiKey", "sk-abcdef1234567890");
            body.put("model", "gpt-4");
            body.put("baseUrl", "https://api.example.com");
            controller.updateConfig(body, httpRequest);

            // Now getConfig
            when(dynamicProvider.getDelegate()).thenReturn(newProvider);
            Map<String, Object> config = controller.getConfig();

            assertEquals("openrouter", config.get("providerType"));
            assertEquals("openrouter", config.get("providerName"));
            assertEquals("gpt-4", config.get("model"));
            assertEquals("https://api.example.com", config.get("baseUrl"));
            assertEquals(true, config.get("hasApiKey"));
            assertEquals("sk-a***7890", config.get("apiKeyMasked"),
                    "maskApiKey keeps first 4 and last 4 chars");
        }
    }

    @Nested
    @DisplayName("PUT /api/model-config - 更新配置")
    class UpdateConfig {

        @Test
        @DisplayName("管理员成功更新模型配置")
        void adminUpdatesConfig() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            LLMProvider newProvider = mock(LLMProvider.class);
            when(newProvider.getProviderName()).thenReturn("openrouter");
            when(agentConfig.buildProvider("openrouter", "sk-key123", "claude-3", ""))
                    .thenReturn(newProvider);

            Map<String, String> body = new HashMap<>();
            body.put("providerType", "openrouter");
            body.put("apiKey", "sk-key123");
            body.put("model", "claude-3");
            body.put("baseUrl", "");

            Map<String, Object> result = controller.updateConfig(body, httpRequest);

            assertEquals("模型配置已更新", result.get("message"));
            assertEquals("openrouter", result.get("providerName"));
            assertEquals("claude-3", result.get("model"));
            assertEquals("", result.get("baseUrl"));
            verify(dynamicProvider).setDelegate(newProvider);
        }

        @Test
        @DisplayName("非管理员更新配置抛出 ForbiddenException")
        void nonAdminDenied() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            Map<String, String> body = Map.of("providerType", "openrouter");

            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.updateConfig(body, httpRequest));
            verify(dynamicProvider, never()).setDelegate(any());
        }

        @Test
        @DisplayName("空 apiKey 保留现有 key")
        void emptyApiKeyKeepsExisting() {
            // First set a key
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            LLMProvider firstProvider = mock(LLMProvider.class);
            when(firstProvider.getProviderName()).thenReturn("p1");
            when(agentConfig.buildProvider("openrouter", "sk-original-key1", "m1", ""))
                    .thenReturn(firstProvider);

            Map<String, String> firstBody = new HashMap<>();
            firstBody.put("providerType", "openrouter");
            firstBody.put("apiKey", "sk-original-key1");
            firstBody.put("model", "m1");
            firstBody.put("baseUrl", "");
            controller.updateConfig(firstBody, httpRequest);

            // Now update with empty apiKey
            LLMProvider secondProvider = mock(LLMProvider.class);
            when(secondProvider.getProviderName()).thenReturn("p2");
            when(agentConfig.buildProvider("openrouter", "sk-original-key1", "m2", ""))
                    .thenReturn(secondProvider);

            Map<String, String> secondBody = new HashMap<>();
            secondBody.put("providerType", "openrouter");
            secondBody.put("apiKey", "");
            secondBody.put("model", "m2");
            secondBody.put("baseUrl", "");
            controller.updateConfig(secondBody, httpRequest);

            // The buildProvider should have been called with the ORIGINAL key
            verify(agentConfig).buildProvider("openrouter", "sk-original-key1", "m2", "");
        }

        @Test
        @DisplayName("掩码格式的 apiKey 保留现有 key")
        void maskedApiKeyKeepsExisting() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            LLMProvider firstProvider = mock(LLMProvider.class);
            when(firstProvider.getProviderName()).thenReturn("p1");
            when(agentConfig.buildProvider("openrouter", "sk-real-api-key11", "m1", ""))
                    .thenReturn(firstProvider);

            Map<String, String> firstBody = new HashMap<>();
            firstBody.put("providerType", "openrouter");
            firstBody.put("apiKey", "sk-real-api-key11");
            firstBody.put("model", "m1");
            firstBody.put("baseUrl", "");
            controller.updateConfig(firstBody, httpRequest);

            // Now update with masked apiKey
            LLMProvider secondProvider = mock(LLMProvider.class);
            when(secondProvider.getProviderName()).thenReturn("p2");
            when(agentConfig.buildProvider("openrouter", "sk-real-api-key11", "m2", ""))
                    .thenReturn(secondProvider);

            Map<String, String> secondBody = new HashMap<>();
            secondBody.put("providerType", "openrouter");
            secondBody.put("apiKey", "***masked***");
            secondBody.put("model", "m2");
            secondBody.put("baseUrl", "");
            controller.updateConfig(secondBody, httpRequest);

            verify(agentConfig).buildProvider("openrouter", "sk-real-api-key11", "m2", "");
        }

        @Test
        @DisplayName("userRole 为 null 时拒绝更新")
        void nullRoleDenied() {
            when(httpRequest.getAttribute("userRole")).thenReturn(null);

            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.updateConfig(Map.of(), httpRequest));
        }
    }

    @Nested
    @DisplayName("GET /api/model-config/presets - 预设列表")
    class ListPresets {

        @Test
        @DisplayName("初始预设列表为空")
        void emptyPresets() {
            List<Map<String, Object>> presets = controller.listPresets();

            assertNotNull(presets);
            assertTrue(presets.isEmpty());
        }

        @Test
        @DisplayName("保存后预设出现在列表中")
        void presetsAfterSave() {
            // Save a preset first
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            // Need to set current config first via updateConfig
            LLMProvider provider = mock(LLMProvider.class);
            when(provider.getProviderName()).thenReturn("claude");
            when(agentConfig.buildProvider("api", "sk-test1234abcd", "claude-3", ""))
                    .thenReturn(provider);

            Map<String, String> configBody = new HashMap<>();
            configBody.put("providerType", "api");
            configBody.put("apiKey", "sk-test1234abcd");
            configBody.put("model", "claude-3");
            configBody.put("baseUrl", "");
            controller.updateConfig(configBody, httpRequest);

            // Save as preset
            controller.savePreset(Map.of("name", "my-preset"), httpRequest);

            List<Map<String, Object>> presets = controller.listPresets();

            assertEquals(1, presets.size());
            assertEquals("my-preset", presets.get(0).get("name"));
            assertEquals("api", presets.get(0).get("providerType"));
            assertEquals("claude-3", presets.get(0).get("model"));
            assertEquals("", presets.get(0).get("baseUrl"));
            assertEquals("sk-t***abcd", presets.get(0).get("apiKeyMasked"));
        }
    }

    @Nested
    @DisplayName("POST /api/model-config/presets - 保存预设")
    class SavePreset {

        @Test
        @DisplayName("管理员成功保存预设")
        void adminSavesPreset() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            Map<String, Object> result = controller.savePreset(
                    Map.of("name", "production"), httpRequest);

            assertNotNull(result);
            assertTrue(((String) result.get("message")).contains("production"));
        }

        @Test
        @DisplayName("非管理员保存预设抛出 ForbiddenException")
        void nonAdminDenied() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.savePreset(Map.of("name", "test"), httpRequest));
        }

        @Test
        @DisplayName("空名称抛出 IllegalArgumentException")
        void emptyNameThrows() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            assertThrows(IllegalArgumentException.class,
                    () -> controller.savePreset(Map.of("name", "  "), httpRequest));
        }

        @Test
        @DisplayName("null 名称抛出 IllegalArgumentException")
        void nullNameThrows() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            assertThrows(IllegalArgumentException.class,
                    () -> controller.savePreset(new HashMap<>(), httpRequest));
        }
    }

    @Nested
    @DisplayName("PUT /api/model-config/presets/{name}/apply - 应用预设")
    class ApplyPreset {

        @Test
        @DisplayName("管理员成功应用已保存的预设")
        void adminAppliesPreset() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            // Setup: update config, then save as preset
            LLMProvider provider = mock(LLMProvider.class);
            when(provider.getProviderName()).thenReturn("openrouter");
            when(agentConfig.buildProvider("openrouter", "sk-preset-key12", "gpt-4", "https://or.api"))
                    .thenReturn(provider);

            Map<String, String> configBody = new HashMap<>();
            configBody.put("providerType", "openrouter");
            configBody.put("apiKey", "sk-preset-key12");
            configBody.put("model", "gpt-4");
            configBody.put("baseUrl", "https://or.api");
            controller.updateConfig(configBody, httpRequest);
            controller.savePreset(Map.of("name", "fast-gpt"), httpRequest);

            // Now change config to something else
            LLMProvider otherProvider = mock(LLMProvider.class);
            when(otherProvider.getProviderName()).thenReturn("mock");
            // apiKey="" triggers the controller to keep the existing key ("sk-preset-key12")
            when(agentConfig.buildProvider("mock", "sk-preset-key12", "mock-model", ""))
                    .thenReturn(otherProvider);

            Map<String, String> otherBody = new HashMap<>();
            otherBody.put("providerType", "mock");
            otherBody.put("apiKey", "");
            otherBody.put("model", "mock-model");
            otherBody.put("baseUrl", "");
            controller.updateConfig(otherBody, httpRequest);

            // Apply the preset
            // Re-stub buildProvider for the preset's config
            when(agentConfig.buildProvider("openrouter", "sk-preset-key12", "gpt-4", "https://or.api"))
                    .thenReturn(provider);

            Map<String, Object> result = controller.applyPreset("fast-gpt", httpRequest);

            assertTrue(((String) result.get("message")).contains("fast-gpt"));
            assertEquals("openrouter", result.get("providerName"));
            assertEquals("gpt-4", result.get("model"));
            assertEquals("https://or.api", result.get("baseUrl"));
        }

        @Test
        @DisplayName("应用不存在的预设抛出 IllegalArgumentException")
        void applyNonexistentPreset() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            assertThrows(IllegalArgumentException.class,
                    () -> controller.applyPreset("does-not-exist", httpRequest));
        }

        @Test
        @DisplayName("非管理员应用预设抛出 ForbiddenException")
        void nonAdminDenied() {
            when(httpRequest.getAttribute("userRole")).thenReturn(null);

            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.applyPreset("any", httpRequest));
        }
    }

    @Nested
    @DisplayName("DELETE /api/model-config/presets/{name} - 删除预设")
    class DeletePreset {

        @Test
        @DisplayName("管理员成功删除预设")
        void adminDeletesPreset() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            // Save a preset first
            controller.savePreset(Map.of("name", "to-delete"), httpRequest);

            Map<String, Object> result = controller.deletePreset("to-delete", httpRequest);

            assertTrue(((String) result.get("message")).contains("to-delete"));

            // Verify it's actually gone
            List<Map<String, Object>> presets = controller.listPresets();
            assertTrue(presets.isEmpty());
        }

        @Test
        @DisplayName("删除不存在的预设抛出 IllegalArgumentException")
        void deleteNonexistentPreset() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

            assertThrows(IllegalArgumentException.class,
                    () -> controller.deletePreset("nope", httpRequest));
        }

        @Test
        @DisplayName("非管理员删除预设抛出 ForbiddenException")
        void nonAdminDenied() {
            when(httpRequest.getAttribute("userRole")).thenReturn("USER");

            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.deletePreset("any", httpRequest));
        }
    }

    @Nested
    @DisplayName("maskApiKey - API Key 掩码行为")
    class MaskApiKey {

        @Test
        @DisplayName("getConfig 中可以间接验证 maskApiKey 行为")
        void verifyMaskingViaGetConfig() {
            // Set a key through updateConfig, then verify via getConfig
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            LLMProvider provider = mock(LLMProvider.class);
            when(provider.getProviderName()).thenReturn("test");
            when(agentConfig.buildProvider("api", "sk-abcdefghij1234", "m", ""))
                    .thenReturn(provider);

            Map<String, String> body = new HashMap<>();
            body.put("providerType", "api");
            body.put("apiKey", "sk-abcdefghij1234");
            body.put("model", "m");
            body.put("baseUrl", "");
            controller.updateConfig(body, httpRequest);

            when(dynamicProvider.getDelegate()).thenReturn(provider);
            Map<String, Object> config = controller.getConfig();

            assertEquals("sk-a***1234", config.get("apiKeyMasked"),
                    "first 4 chars + *** + last 4 chars");
            assertEquals(true, config.get("hasApiKey"));
        }

        @Test
        @DisplayName("短 key (<8 chars) 返回空字符串")
        void shortKeyMaskedAsEmpty() {
            // Short key gets treated as empty via maskApiKey's length check
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            LLMProvider provider = mock(LLMProvider.class);
            when(provider.getProviderName()).thenReturn("test");
            when(agentConfig.buildProvider("api", "short", "m", ""))
                    .thenReturn(provider);

            Map<String, String> body = new HashMap<>();
            body.put("providerType", "api");
            body.put("apiKey", "short");
            body.put("model", "m");
            body.put("baseUrl", "");
            controller.updateConfig(body, httpRequest);

            when(dynamicProvider.getDelegate()).thenReturn(provider);
            Map<String, Object> config = controller.getConfig();

            assertEquals("", config.get("apiKeyMasked"),
                    "keys shorter than 8 chars are masked to empty string");
        }

        @Test
        @DisplayName("exactly 8 char key 正确掩码")
        void exactlyEightCharKey() {
            when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
            LLMProvider provider = mock(LLMProvider.class);
            when(provider.getProviderName()).thenReturn("test");
            when(agentConfig.buildProvider("api", "12345678", "m", ""))
                    .thenReturn(provider);

            Map<String, String> body = new HashMap<>();
            body.put("providerType", "api");
            body.put("apiKey", "12345678");
            body.put("model", "m");
            body.put("baseUrl", "");
            controller.updateConfig(body, httpRequest);

            when(dynamicProvider.getDelegate()).thenReturn(provider);
            Map<String, Object> config = controller.getConfig();

            assertEquals("1234***5678", config.get("apiKeyMasked"),
                    "8-char key: first 4 + *** + last 4");
        }
    }
}
