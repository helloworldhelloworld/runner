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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelConfigController - LLM model configuration API")
class ModelConfigControllerTest {

    @Mock
    private DynamicLLMProvider dynamicProvider;

    @Mock
    private AgentConfig agentConfig;

    @Mock
    private LLMProvider delegateProvider;

    private ModelConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new ModelConfigController(dynamicProvider, agentConfig);
    }

    private HttpServletRequest adminRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userRole")).thenReturn("ADMIN");
        return request;
    }

    private HttpServletRequest nonAdminRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userRole")).thenReturn("USER");
        return request;
    }

    @Nested
    @DisplayName("GET /api/model-config - get current config")
    class GetConfig {

        @Test
        @DisplayName("returns current provider config with masked API key")
        void returnsCurrentConfig() {
            when(dynamicProvider.getDelegate()).thenReturn(delegateProvider);
            when(delegateProvider.getProviderName()).thenReturn("openrouter");

            Map<String, Object> config = controller.getConfig();

            assertEquals("openrouter", config.get("providerName"));
            assertNotNull(config.get("hasApiKey"));
            assertNotNull(config.get("model"));
        }

        @Test
        @DisplayName("providerType falls back to delegate name when not explicitly set")
        void providerTypeFallback() {
            when(dynamicProvider.getDelegate()).thenReturn(delegateProvider);
            when(delegateProvider.getProviderName()).thenReturn("claude");

            Map<String, Object> config = controller.getConfig();

            assertEquals("claude", config.get("providerType"));
        }
    }

    @Nested
    @DisplayName("PUT /api/model-config - update config")
    class UpdateConfig {

        @Test
        @DisplayName("admin can update model configuration")
        void adminCanUpdate() {
            LLMProvider newProvider = mock(LLMProvider.class);
            when(newProvider.getProviderName()).thenReturn("openrouter");
            when(agentConfig.buildProvider("openrouter", "sk-key123", "gpt-4", ""))
                    .thenReturn(newProvider);

            Map<String, String> body = Map.of(
                    "providerType", "openrouter",
                    "apiKey", "sk-key123",
                    "model", "gpt-4"
            );

            Map<String, Object> result = controller.updateConfig(body, adminRequest());

            verify(dynamicProvider).setDelegate(newProvider);
            assertEquals("openrouter", result.get("providerName"));
            assertEquals("gpt-4", result.get("model"));
            assertNotNull(result.get("message"));
        }

        @Test
        @DisplayName("non-admin gets ForbiddenException")
        void nonAdminForbidden() {
            Map<String, String> body = Map.of("providerType", "openrouter");

            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.updateConfig(body, nonAdminRequest()));
        }

        @Test
        @DisplayName("masked apiKey placeholder keeps existing key")
        void maskedApiKeyKeepsExisting() {
            // First set a real key via admin update
            LLMProvider provider1 = mock(LLMProvider.class);
            when(provider1.getProviderName()).thenReturn("openrouter");
            when(agentConfig.buildProvider("openrouter", "sk-real-key-value", "model1", ""))
                    .thenReturn(provider1);

            controller.updateConfig(
                    Map.of("providerType", "openrouter", "apiKey", "sk-real-key-value", "model", "model1"),
                    adminRequest());

            // Now update with masked key - should reuse the existing key
            LLMProvider provider2 = mock(LLMProvider.class);
            when(provider2.getProviderName()).thenReturn("openrouter");
            when(agentConfig.buildProvider("openrouter", "sk-real-key-value", "model2", ""))
                    .thenReturn(provider2);

            controller.updateConfig(
                    Map.of("providerType", "openrouter", "apiKey", "***masked***", "model", "model2"),
                    adminRequest());

            verify(agentConfig).buildProvider("openrouter", "sk-real-key-value", "model2", "");
        }
    }

    @Nested
    @DisplayName("Preset management endpoints")
    class PresetManagement {

        @Test
        @DisplayName("list presets returns empty list initially")
        void listPresetsEmpty() {
            List<Map<String, Object>> presets = controller.listPresets();

            assertNotNull(presets);
            assertTrue(presets.isEmpty());
        }

        @Test
        @DisplayName("save and list preset")
        void saveAndListPreset() {
            // First set current config by updating
            LLMProvider provider = mock(LLMProvider.class);
            when(provider.getProviderName()).thenReturn("openrouter");
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(provider);
            controller.updateConfig(
                    Map.of("providerType", "openrouter", "apiKey", "sk-test", "model", "gpt-4"),
                    adminRequest());

            // Save preset
            Map<String, Object> saveResult = controller.savePreset(
                    Map.of("name", "my-preset"), adminRequest());

            assertNotNull(saveResult.get("message"));

            // Verify preset appears in list
            List<Map<String, Object>> presets = controller.listPresets();
            assertEquals(1, presets.size());
            assertEquals("my-preset", presets.get(0).get("name"));
            assertEquals("openrouter", presets.get(0).get("providerType"));
            assertEquals("gpt-4", presets.get(0).get("model"));
        }

        @Test
        @DisplayName("save preset without name throws IllegalArgumentException")
        void savePresetWithoutName() {
            assertThrows(IllegalArgumentException.class,
                    () -> controller.savePreset(Map.of("name", ""), adminRequest()));
        }

        @Test
        @DisplayName("save preset as non-admin throws ForbiddenException")
        void savePresetNonAdmin() {
            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.savePreset(Map.of("name", "test"), nonAdminRequest()));
        }

        @Test
        @DisplayName("apply preset switches provider configuration")
        void applyPreset() {
            // Set up initial config and save as preset
            LLMProvider provider1 = mock(LLMProvider.class);
            when(provider1.getProviderName()).thenReturn("openrouter");
            when(agentConfig.buildProvider("openrouter", "sk-key1", "model-a", ""))
                    .thenReturn(provider1);
            controller.updateConfig(
                    Map.of("providerType", "openrouter", "apiKey", "sk-key1", "model", "model-a"),
                    adminRequest());
            controller.savePreset(Map.of("name", "preset-a"), adminRequest());

            // Change config
            LLMProvider provider2 = mock(LLMProvider.class);
            when(provider2.getProviderName()).thenReturn("claude");
            when(agentConfig.buildProvider("api", "sk-key2", "claude-3", ""))
                    .thenReturn(provider2);
            controller.updateConfig(
                    Map.of("providerType", "api", "apiKey", "sk-key2", "model", "claude-3"),
                    adminRequest());

            // Apply saved preset
            LLMProvider provider3 = mock(LLMProvider.class);
            when(provider3.getProviderName()).thenReturn("openrouter-restored");
            when(agentConfig.buildProvider("openrouter", "sk-key1", "model-a", ""))
                    .thenReturn(provider3);

            Map<String, Object> result = controller.applyPreset("preset-a", adminRequest());

            assertEquals("openrouter-restored", result.get("providerName"));
            assertEquals("model-a", result.get("model"));
        }

        @Test
        @DisplayName("apply non-existent preset throws IllegalArgumentException")
        void applyNonExistentPreset() {
            assertThrows(IllegalArgumentException.class,
                    () -> controller.applyPreset("non-existent", adminRequest()));
        }

        @Test
        @DisplayName("apply preset as non-admin throws ForbiddenException")
        void applyPresetNonAdmin() {
            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.applyPreset("any", nonAdminRequest()));
        }

        @Test
        @DisplayName("delete preset removes it from list")
        void deletePreset() {
            // Save a preset first
            LLMProvider provider = mock(LLMProvider.class);
            when(provider.getProviderName()).thenReturn("test");
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(provider);
            controller.updateConfig(
                    Map.of("providerType", "openrouter", "apiKey", "sk", "model", "m"),
                    adminRequest());
            controller.savePreset(Map.of("name", "to-delete"), adminRequest());

            assertEquals(1, controller.listPresets().size());

            // Delete it
            Map<String, Object> result = controller.deletePreset("to-delete", adminRequest());

            assertNotNull(result.get("message"));
            assertTrue(controller.listPresets().isEmpty());
        }

        @Test
        @DisplayName("delete non-existent preset throws IllegalArgumentException")
        void deleteNonExistentPreset() {
            assertThrows(IllegalArgumentException.class,
                    () -> controller.deletePreset("no-such-preset", adminRequest()));
        }

        @Test
        @DisplayName("delete preset as non-admin throws ForbiddenException")
        void deletePresetNonAdmin() {
            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.deletePreset("any", nonAdminRequest()));
        }
    }
}
