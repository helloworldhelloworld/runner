package com.lightweightai.kernel.llm.openrouter;

import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenRouterModelCapability")
class OpenRouterModelCapabilityTest {

    // Shared base features all models must have
    private static final Set<ModelFeature> BASE_FEATURES = Set.of(
        ModelFeature.TOOL_CALLING,
        ModelFeature.SYSTEM_MESSAGE,
        ModelFeature.STREAMING,
        ModelFeature.FUNCTION_CALLING
    );

    @Test
    @DisplayName("Claude model has 200k context and 8192 output tokens")
    void claudeModelTokenLimits() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-sonnet");

        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(8192, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("Claude model supports base features plus MULTIMODAL")
    void claudeModelFeatures() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-sonnet");
        Set<ModelFeature> features = cap.getSupportedFeatures();

        for (ModelFeature base : BASE_FEATURES) {
            assertTrue(features.contains(base), "Claude should support " + base);
        }
        assertTrue(features.contains(ModelFeature.MULTIMODAL),
            "Claude models should support MULTIMODAL");
    }

    @Test
    @DisplayName("Claude-3 model adds JSON_MODE")
    void claude3ModelHasJsonMode() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-opus");
        Set<ModelFeature> features = cap.getSupportedFeatures();

        assertTrue(features.contains(ModelFeature.JSON_MODE),
            "claude-3 models should support JSON_MODE");
        assertTrue(features.contains(ModelFeature.MULTIMODAL),
            "claude-3 models should support MULTIMODAL");
    }

    @Test
    @DisplayName("GPT-4 model has 128k context and 4096 output tokens")
    void gpt4ModelTokenLimits() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");

        assertEquals(128000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("GPT-4 model supports base features plus MULTIMODAL and JSON_MODE")
    void gpt4ModelFeatures() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
        Set<ModelFeature> features = cap.getSupportedFeatures();

        for (ModelFeature base : BASE_FEATURES) {
            assertTrue(features.contains(base), "GPT-4 should support " + base);
        }
        assertTrue(features.contains(ModelFeature.MULTIMODAL),
            "GPT-4 models should support MULTIMODAL");
        assertTrue(features.contains(ModelFeature.JSON_MODE),
            "GPT-4 models should support JSON_MODE");
    }

    @Test
    @DisplayName("Unknown model has 100k context and 4096 output tokens")
    void unknownModelTokenLimits() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("meta/llama-3-70b");

        assertEquals(100000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("Unknown model supports only base features (no MULTIMODAL, no JSON_MODE)")
    void unknownModelFeatures() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("meta/llama-3-70b");
        Set<ModelFeature> features = cap.getSupportedFeatures();

        for (ModelFeature base : BASE_FEATURES) {
            assertTrue(features.contains(base), "Unknown model should support " + base);
        }
        assertFalse(features.contains(ModelFeature.MULTIMODAL),
            "Unknown model should NOT support MULTIMODAL");
        assertFalse(features.contains(ModelFeature.JSON_MODE),
            "Unknown model should NOT support JSON_MODE");
    }

    @Test
    @DisplayName("Vision model gets MULTIMODAL but not JSON_MODE")
    void visionModelFeatures() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("custom/vision-model-v2");
        Set<ModelFeature> features = cap.getSupportedFeatures();

        assertTrue(features.contains(ModelFeature.MULTIMODAL),
            "Vision model should support MULTIMODAL");
        assertFalse(features.contains(ModelFeature.JSON_MODE),
            "Vision-only model should NOT support JSON_MODE");
        // Vision model is unknown tier for token limits
        assertEquals(100000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("getModelId returns the model identifier passed to constructor")
    void getModelIdReturnsConstructorValue() {
        String modelId = "anthropic/claude-3-5-sonnet-20241022";
        OpenRouterModelCapability cap = new OpenRouterModelCapability(modelId);

        assertEquals(modelId, cap.getModelId());
    }

    @Test
    @DisplayName("getSupportedFeatures returns a defensive copy")
    void getSupportedFeaturesReturnsDefensiveCopy() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");

        Set<ModelFeature> features1 = cap.getSupportedFeatures();
        int originalSize = features1.size();

        // Mutate the returned set
        features1.clear();

        // Second call should still return the full set
        Set<ModelFeature> features2 = cap.getSupportedFeatures();
        assertEquals(originalSize, features2.size(),
            "Clearing the returned set should not affect subsequent calls");
    }

    @Test
    @DisplayName("toString includes model id and token counts")
    void toStringIncludesRelevantInfo() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-sonnet");
        String str = cap.toString();

        assertTrue(str.contains("claude-3-sonnet"), "toString should contain the model id");
        assertTrue(str.contains("200000"), "toString should contain maxContextTokens");
        assertTrue(str.contains("8192"), "toString should contain maxOutputTokens");
    }
}
