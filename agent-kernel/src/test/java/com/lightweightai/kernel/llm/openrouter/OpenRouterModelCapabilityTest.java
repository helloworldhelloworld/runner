package com.lightweightai.kernel.llm.openrouter;

import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenRouterModelCapability - Model capability detection by model ID")
class OpenRouterModelCapabilityTest {

    @Test
    @DisplayName("Claude model gets 200K context and 8K output")
    void claudeModelCapacity() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3.5-sonnet");

        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(8192, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("GPT-4 model gets 128K context and 4K output")
    void gpt4ModelCapacity() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");

        assertEquals(128000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("Unknown model gets default 100K context and 4K output")
    void unknownModelDefaultCapacity() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("some/unknown-model");

        assertEquals(100000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("All models support TOOL_CALLING, STREAMING, SYSTEM_MESSAGE, FUNCTION_CALLING")
    void baseFeatures() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("some/basic-model");
        Set<ModelFeature> features = cap.getSupportedFeatures();

        assertTrue(features.contains(ModelFeature.TOOL_CALLING));
        assertTrue(features.contains(ModelFeature.STREAMING));
        assertTrue(features.contains(ModelFeature.SYSTEM_MESSAGE));
        assertTrue(features.contains(ModelFeature.FUNCTION_CALLING));
    }

    @Test
    @DisplayName("Claude model supports MULTIMODAL")
    void claudeMultimodal() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-opus");
        assertTrue(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
    }

    @Test
    @DisplayName("GPT-4 model supports MULTIMODAL")
    void gpt4Multimodal() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-vision");
        assertTrue(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
    }

    @Test
    @DisplayName("Vision model supports MULTIMODAL")
    void visionModelMultimodal() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("meta/llava-vision");
        assertTrue(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
    }

    @Test
    @DisplayName("Unknown model does NOT support MULTIMODAL")
    void unknownNoMultimodal() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("some/text-only-model");
        assertFalse(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
    }

    @Test
    @DisplayName("GPT-4 model supports JSON_MODE")
    void gpt4JsonMode() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
        assertTrue(cap.getSupportedFeatures().contains(ModelFeature.JSON_MODE));
    }

    @Test
    @DisplayName("Claude-3 model supports JSON_MODE")
    void claude3JsonMode() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-sonnet");
        assertTrue(cap.getSupportedFeatures().contains(ModelFeature.JSON_MODE));
    }

    @Test
    @DisplayName("Model without gpt-4 or claude-3 in name does NOT support JSON_MODE")
    void noJsonMode() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("mistral/mixtral-8x7b");
        assertFalse(cap.getSupportedFeatures().contains(ModelFeature.JSON_MODE));
    }

    @Test
    @DisplayName("getModelId returns constructor value")
    void getModelId() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("test/model-id");
        assertEquals("test/model-id", cap.getModelId());
    }

    @Test
    @DisplayName("getMessageFormatter returns null")
    void messageFormatterNull() {
        assertNull(new OpenRouterModelCapability("test").getMessageFormatter());
    }

    @Test
    @DisplayName("getTokenCounter returns null")
    void tokenCounterNull() {
        assertNull(new OpenRouterModelCapability("test").getTokenCounter());
    }

    @Test
    @DisplayName("getSupportedFeatures returns defensive copy")
    void defensiveCopy() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("test");
        Set<ModelFeature> first = cap.getSupportedFeatures();
        Set<ModelFeature> second = cap.getSupportedFeatures();

        assertEquals(first, second);
        assertNotSame(first, second);
    }

    @Test
    @DisplayName("toString includes modelId and token counts")
    void toStringContainsInfo() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
        String str = cap.toString();

        assertTrue(str.contains("gpt-4-turbo"));
        assertTrue(str.contains("128000"));
    }
}
