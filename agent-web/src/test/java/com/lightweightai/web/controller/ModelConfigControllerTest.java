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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelConfigController - model config CRUD and admin access control")
class ModelConfigControllerTest {

    @Mock private DynamicLLMProvider dynamicProvider;
    @Mock private AgentConfig agentConfig;
    @Mock private LLMProvider mockProvider;

    private ModelConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new ModelConfigController(dynamicProvider, agentConfig);
        when(dynamicProvider.getDelegate()).thenReturn(mockProvider);
        when(mockProvider.getProviderName()).thenReturn("test-provider");
    }

    @Nested
    @DisplayName("GET /api/model-config")
    class GetConfig {

        @Test
        @DisplayName("returns current provider info")
        void returnsProviderInfo() {
            Map<String, Object> config = controller.getConfig();

            assertEquals("test-provider", config.get("providerType"));
            assertEquals("test-provider", config.get("providerName"));
            assertFalse((Boolean) config.get("hasApiKey"));
        }
    }

    @Nested
    @DisplayName("PUT /api/model-config - admin access")
    class UpdateConfig {

        @Test
        @DisplayName("non-admin is rejected with ForbiddenException")
        void nonAdminRejected() {
            HttpServletRequest request = mockRequest(null);

            assertThrows(ModelConfigController.ForbiddenException.class, () ->
                    controller.updateConfig(Map.of("providerType", "claude"), request));
        }

        @Test
        @DisplayName("admin can update config")
        void adminCanUpdate() {
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockProvider);

            HttpServletRequest request = mockRequest("ADMIN");

            Map<String, Object> result = controller.updateConfig(
                    Map.of("providerType", "claude", "apiKey", "sk-test123456", "model", "claude-3"),
                    request);

            assertNotNull(result.get("message"));
            assertEquals("test-provider", result.get("providerName"));
        }
    }

    @Nested
    @DisplayName("Preset management")
    class Presets {

        @Test
        @DisplayName("initially no presets")
        void initiallyEmpty() {
            List<Map<String, Object>> presets = controller.listPresets();
            assertTrue(presets.isEmpty());
        }

        @Test
        @DisplayName("save and list preset")
        void saveAndList() {
            HttpServletRequest request = mockRequest("ADMIN");

            Map<String, Object> result = controller.savePreset(Map.of("name", "fast"), request);
            assertNotNull(result.get("message"));

            List<Map<String, Object>> presets = controller.listPresets();
            assertEquals(1, presets.size());
            assertEquals("fast", presets.get(0).get("name"));
        }

        @Test
        @DisplayName("apply preset switches provider")
        void applyPreset() {
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockProvider);

            HttpServletRequest request = mockRequest("ADMIN");
            controller.savePreset(Map.of("name", "preset1"), request);

            Map<String, Object> result = controller.applyPreset("preset1", request);
            assertNotNull(result.get("message"));
        }

        @Test
        @DisplayName("apply non-existent preset throws")
        void applyNonExistentThrows() {
            HttpServletRequest request = mockRequest("ADMIN");
            assertThrows(IllegalArgumentException.class, () ->
                    controller.applyPreset("ghost", request));
        }

        @Test
        @DisplayName("delete preset removes it")
        void deletePreset() {
            HttpServletRequest request = mockRequest("ADMIN");
            controller.savePreset(Map.of("name", "to_delete"), request);

            controller.deletePreset("to_delete", request);

            assertTrue(controller.listPresets().isEmpty());
        }

        @Test
        @DisplayName("delete non-existent preset throws")
        void deleteNonExistentThrows() {
            HttpServletRequest request = mockRequest("ADMIN");
            assertThrows(IllegalArgumentException.class, () ->
                    controller.deletePreset("ghost", request));
        }

        @Test
        @DisplayName("save preset with blank name throws")
        void blankNameThrows() {
            HttpServletRequest request = mockRequest("ADMIN");
            assertThrows(IllegalArgumentException.class, () ->
                    controller.savePreset(Map.of("name", "  "), request));
        }

        @Test
        @DisplayName("non-admin cannot save preset")
        void nonAdminCannotSave() {
            HttpServletRequest request = mockRequest(null);
            assertThrows(ModelConfigController.ForbiddenException.class, () ->
                    controller.savePreset(Map.of("name", "test"), request));
        }
    }

    private static HttpServletRequest mockRequest(String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userRole")).thenReturn(role);
        return request;
    }
}
