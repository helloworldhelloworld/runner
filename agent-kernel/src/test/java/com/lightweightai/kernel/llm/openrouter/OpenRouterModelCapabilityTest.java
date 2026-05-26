package com.lightweightai.kernel.llm.openrouter;

import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterModelCapabilityTest {

    @Test
    void claudeViaOpenRouterHas200kContext() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-sonnet");
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(8192, cap.getMaxOutputTokens());
    }

    @Test
    void gpt4Has128kContext() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
        assertEquals(128000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    void unknownModelUsesDefaults() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("mistral/mistral-large");
        assertEquals(100000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    void claudeSupportsMultimodal() {
        assertTrue(new OpenRouterModelCapability("anthropic/claude-3-opus").getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
    }

    @Test
    void plainModelLacksMultimodal() {
        assertFalse(new OpenRouterModelCapability("mistral/mistral-7b").getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
    }

    @Test
    void claude3SupportsJsonMode() {
        assertTrue(new OpenRouterModelCapability("anthropic/claude-3-haiku").getSupportedFeatures().contains(ModelFeature.JSON_MODE));
    }

    @Test
    void plainModelLacksJsonMode() {
        assertFalse(new OpenRouterModelCapability("mistral/mistral-7b").getSupportedFeatures().contains(ModelFeature.JSON_MODE));
    }

    @Test
    void featureSetIsDefensiveCopy() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-opus");
        int size = cap.getSupportedFeatures().size();
        cap.getSupportedFeatures().clear();
        assertEquals(size, cap.getSupportedFeatures().size());
    }

    @Test
    void implementsModelCapability() {
        assertInstanceOf(ModelCapability.class, new OpenRouterModelCapability("test"));
    }
}
