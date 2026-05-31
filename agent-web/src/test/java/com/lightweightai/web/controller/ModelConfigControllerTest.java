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
@DisplayName("ModelConfigController — runtime LLM configuration endpoints")
class ModelConfigControllerTest {

    @Mock private AgentConfig agentConfig;
    @Mock private LLMProvider initialProvider;
    @Mock private LLMProvider newProvider;

    private DynamicLLMProvider dynamicProvider;
    private ModelConfigController controller;

    @BeforeEach
    void setUp() {
        when(initialProvider.getProviderName()).thenReturn("openrouter");
        dynamicProvider = new DynamicLLMProvider(initialProvider);
        controller = new ModelConfigController(dynamicProvider, agentConfig);
    }

    @Nested
    @DisplayName("GET /api/model-config")
    class GetConfigTests {

        @Test
        @DisplayName("returns current provider info")
        void returnsCurrentConfig() {
            Map<String, Object> config = controller.getConfig();

            assertEquals("openrouter", config.get("providerName"));
            assertNotNull(config.get("providerType"));
            assertFalse((Boolean) config.get("hasApiKey"));
        }
    }

    @Nested
    @DisplayName("PUT /api/model-config — update config")
    class UpdateConfigTests {

        @Test
        @DisplayName("admin can update provider config")
        void adminCanUpdate() {
            HttpServletRequest request = mockAdminRequest();
            when(newProvider.getProviderName()).thenReturn("claude-api");
            when(agentConfig.buildProvider("api", "sk-test", "claude-3", ""))
                    .thenReturn(newProvider);

            Map<String, Object> result = controller.updateConfig(
                    Map.of("providerType", "api", "apiKey", "sk-test", "model", "claude-3"),
                    request);

            assertEquals("claude-api", result.get("providerName"));
            assertSame(newProvider, dynamicProvider.getDelegate());
        }

        @Test
        @DisplayName("non-admin gets Forbidden exception")
        void nonAdminForbidden() {
            HttpServletRequest request = mockNonAdminRequest();

            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.updateConfig(Map.of("providerType", "api"), request));
        }

        @Test
        @DisplayName("masked apiKey placeholder keeps existing key")
        void maskedApiKeyKeepsExisting() {
            HttpServletRequest adminReq = mockAdminRequest();
            when(newProvider.getProviderName()).thenReturn("openrouter");
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(newProvider);

            controller.updateConfig(
                    Map.of("providerType", "openrouter", "apiKey", "sk-realkey1234", "model", "gpt-4"),
                    adminReq);

            controller.updateConfig(
                    Map.of("providerType", "openrouter", "apiKey", "***masked***", "model", "gpt-4o"),
                    adminReq);

            verify(agentConfig).buildProvider("openrouter", "sk-realkey1234", "gpt-4o", "");
        }
    }

    @Nested
    @DisplayName("Preset management")
    class PresetTests {

        @Test
        @DisplayName("save and list presets")
        void saveAndListPresets() {
            HttpServletRequest adminReq = mockAdminRequest();

            controller.savePreset(Map.of("name", "dev-config"), adminReq);

            List<Map<String, Object>> presets = controller.listPresets();
            assertEquals(1, presets.size());
            assertEquals("dev-config", presets.get(0).get("name"));
        }

        @Test
        @DisplayName("apply preset switches provider")
        void applyPreset() {
            HttpServletRequest adminReq = mockAdminRequest();
            when(newProvider.getProviderName()).thenReturn("claude-api");
            when(agentConfig.buildProvider(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(newProvider);

            controller.updateConfig(
                    Map.of("providerType", "api", "apiKey", "key123", "model", "claude-3"),
                    adminReq);
            controller.savePreset(Map.of("name", "prod"), adminReq);

            dynamicProvider.setDelegate(initialProvider);

            Map<String, Object> result = controller.applyPreset("prod", adminReq);

            assertSame(newProvider, dynamicProvider.getDelegate());
            assertTrue(result.get("message").toString().contains("prod"));
        }

        @Test
        @DisplayName("delete preset removes it")
        void deletePreset() {
            HttpServletRequest adminReq = mockAdminRequest();

            controller.savePreset(Map.of("name", "temp"), adminReq);
            assertEquals(1, controller.listPresets().size());

            controller.deletePreset("temp", adminReq);
            assertEquals(0, controller.listPresets().size());
        }

        @Test
        @DisplayName("apply nonexistent preset throws")
        void applyNonexistentThrows() {
            HttpServletRequest adminReq = mockAdminRequest();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.applyPreset("no-such", adminReq));
        }

        @Test
        @DisplayName("delete nonexistent preset throws")
        void deleteNonexistentThrows() {
            HttpServletRequest adminReq = mockAdminRequest();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.deletePreset("no-such", adminReq));
        }

        @Test
        @DisplayName("save preset with blank name throws")
        void blankNameThrows() {
            HttpServletRequest adminReq = mockAdminRequest();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.savePreset(Map.of("name", "  "), adminReq));
        }

        @Test
        @DisplayName("non-admin cannot manage presets")
        void nonAdminCannotManagePresets() {
            HttpServletRequest req = mockNonAdminRequest();

            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.savePreset(Map.of("name", "x"), req));
            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.applyPreset("x", req));
            assertThrows(ModelConfigController.ForbiddenException.class,
                    () -> controller.deletePreset("x", req));
        }
    }

    private HttpServletRequest mockAdminRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("userRole")).thenReturn("ADMIN");
        return req;
    }

    private HttpServletRequest mockNonAdminRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("userRole")).thenReturn("USER");
        return req;
    }
}
