package com.lightweightai.kernel.llm.openrouter;

import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenRouterModelCapability - model capability for OpenRouter models")
class OpenRouterModelCapabilityTest {

    @Test
    @DisplayName("claude model has 200K context and 8192 output")
    void claudeModelCapabilities() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-sonnet");
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(8192, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("gpt-4 model has 128K context and 4096 output")
    void gpt4ModelCapabilities() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
        assertEquals(128000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("unknown model uses defaults")
    void unknownModelDefaults() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("some/unknown-model");
        assertEquals(100000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("claude models support multimodal")
    void claudeSupportsMultimodal() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-opus");
        assertTrue(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
    }

    @Test
    @DisplayName("gpt-4 models support multimodal and JSON mode")
    void gpt4SupportsMultimodalAndJson() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
        Set<ModelFeature> features = cap.getSupportedFeatures();
        assertTrue(features.contains(ModelFeature.MULTIMODAL));
        assertTrue(features.contains(ModelFeature.JSON_MODE));
    }

    @Test
    @DisplayName("claude-3 models support JSON mode")
    void claude3SupportsJsonMode() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-haiku");
        assertTrue(cap.getSupportedFeatures().contains(ModelFeature.JSON_MODE));
    }

    @Test
    @DisplayName("unknown models do not support multimodal by default")
    void unknownModelsNoMultimodal() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("some/llama-70b");
        assertFalse(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
    }

    @Test
    @DisplayName("all models support core features")
    void allModelsHaveCoreFeatures() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("any-model");
        Set<ModelFeature> features = cap.getSupportedFeatures();
        assertTrue(features.contains(ModelFeature.TOOL_CALLING));
        assertTrue(features.contains(ModelFeature.SYSTEM_MESSAGE));
        assertTrue(features.contains(ModelFeature.STREAMING));
        assertTrue(features.contains(ModelFeature.FUNCTION_CALLING));
    }

    @Test
    @DisplayName("toString includes model info")
    void toStringIncludesInfo() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("test-model");
        String str = cap.toString();
        assertTrue(str.contains("test-model"));
    }
}
