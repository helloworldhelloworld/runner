package com.lightweightai.web.controller;

import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.web.config.AgentConfig;
import com.lightweightai.web.config.DynamicLLMProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("ModelConfigController - runtime LLM config management")
class ModelConfigControllerTest {

    @Mock private DynamicLLMProvider dynamicProvider;
    @Mock private AgentConfig agentConfig;
    @Mock private HttpServletRequest httpRequest;
    @Mock private LLMProvider delegateProvider;

    private ModelConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new ModelConfigController(dynamicProvider, agentConfig);
    }

    // ==================== getConfig ====================

    @Test
    @DisplayName("getConfig returns current provider configuration")
    void getConfigReturnsCurrent() {
        when(dynamicProvider.getDelegate()).thenReturn(delegateProvider);
        when(delegateProvider.getProviderName()).thenReturn("ClaudeProvider");

        Map<String, Object> config = controller.getConfig();

        assertEquals("ClaudeProvider", config.get("providerName"));
        assertFalse((boolean) config.get("hasApiKey"));
    }

    // ==================== updateConfig ====================

    @Test
    @DisplayName("updateConfig rejects non-admin requests")
    void updateConfigRejectsNonAdmin() {
        when(httpRequest.getAttribute("userRole")).thenReturn("USER");

        assertThrows(ModelConfigController.ForbiddenException.class, () ->
            controller.updateConfig(Map.of("providerType", "openrouter"), httpRequest));
    }

    @Test
    @DisplayName("updateConfig succeeds for admin and updates provider")
    void updateConfigSucceedsForAdmin() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
        LLMProvider newProvider = mock(LLMProvider.class);
        when(newProvider.getProviderName()).thenReturn("OpenRouter");
        when(agentConfig.buildProvider("openrouter", "sk-123", "gpt-4", ""))
            .thenReturn(newProvider);

        Map<String, Object> result = controller.updateConfig(
            Map.of("providerType", "openrouter", "apiKey", "sk-123", "model", "gpt-4"),
            httpRequest);

        assertEquals("OpenRouter", result.get("providerName"));
        verify(dynamicProvider).setDelegate(newProvider);
    }

    @Test
    @DisplayName("updateConfig preserves existing apiKey when empty")
    void updateConfigPreservesApiKeyWhenEmpty() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");
        LLMProvider newProvider = mock(LLMProvider.class);
        when(newProvider.getProviderName()).thenReturn("TestProvider");
        when(agentConfig.buildProvider(eq("openrouter"), eq(""), eq(""), eq("")))
            .thenReturn(newProvider);

        controller.updateConfig(Map.of("providerType", "openrouter"), httpRequest);

        verify(agentConfig).buildProvider("openrouter", "", "", "");
    }

    // ==================== presets ====================

    @Test
    @DisplayName("listPresets returns empty initially")
    void listPresetsInitiallyEmpty() {
        List<Map<String, Object>> presets = controller.listPresets();
        assertTrue(presets.isEmpty());
    }

    @Test
    @DisplayName("savePreset saves current config as named preset")
    void savePresetSavesConfig() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

        Map<String, Object> result = controller.savePreset(Map.of("name", "prod"), httpRequest);

        assertNotNull(result.get("message"));
        assertEquals(1, controller.listPresets().size());
    }

    @Test
    @DisplayName("savePreset rejects empty name")
    void savePresetRejectsEmptyName() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

        assertThrows(IllegalArgumentException.class, () ->
            controller.savePreset(Map.of("name", ""), httpRequest));
    }

    @Test
    @DisplayName("savePreset rejects non-admin")
    void savePresetRejectsNonAdmin() {
        when(httpRequest.getAttribute("userRole")).thenReturn("USER");

        assertThrows(ModelConfigController.ForbiddenException.class, () ->
            controller.savePreset(Map.of("name", "test"), httpRequest));
    }

    @Test
    @DisplayName("applyPreset loads and applies saved preset")
    void applyPresetLoadsAndApplies() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

        // First save a preset
        controller.savePreset(Map.of("name", "staging"), httpRequest);

        // Then apply it
        LLMProvider newProvider = mock(LLMProvider.class);
        when(newProvider.getProviderName()).thenReturn("Applied");
        when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(newProvider);

        Map<String, Object> result = controller.applyPreset("staging", httpRequest);

        assertEquals("Applied", result.get("providerName"));
        verify(dynamicProvider).setDelegate(newProvider);
    }

    @Test
    @DisplayName("applyPreset throws for non-existent preset")
    void applyPresetThrowsForMissing() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

        assertThrows(IllegalArgumentException.class, () ->
            controller.applyPreset("nonexistent", httpRequest));
    }

    @Test
    @DisplayName("deletePreset removes existing preset")
    void deletePresetRemoves() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

        controller.savePreset(Map.of("name", "old"), httpRequest);
        assertEquals(1, controller.listPresets().size());

        controller.deletePreset("old", httpRequest);
        assertTrue(controller.listPresets().isEmpty());
    }

    @Test
    @DisplayName("deletePreset throws for non-existent preset")
    void deletePresetThrowsForMissing() {
        when(httpRequest.getAttribute("userRole")).thenReturn("ADMIN");

        assertThrows(IllegalArgumentException.class, () ->
            controller.deletePreset("nonexistent", httpRequest));
    }
}
