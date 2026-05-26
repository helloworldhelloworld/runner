package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeModelCapabilityTest {

    @Test
    void claude35SonnetHas8192Output() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(8192, cap.getMaxOutputTokens());
    }

    @Test
    void claude3OpusHas4096Output() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    void unknownModelUsesDefaults() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-future-model");
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @ParameterizedTest
    @ValueSource(strings = {"claude-3-5-sonnet-20241022", "claude-3-opus-20240229", "claude-3-haiku-20240307"})
    void allClaude3ModelsHaveFullFeatureSet(String modelId) {
        Set<ModelFeature> features = new ClaudeModelCapability(modelId).getSupportedFeatures();
        assertTrue(features.contains(ModelFeature.TOOL_CALLING));
        assertTrue(features.contains(ModelFeature.MULTIMODAL));
        assertTrue(features.contains(ModelFeature.SYSTEM_MESSAGE));
        assertTrue(features.contains(ModelFeature.STREAMING));
        assertTrue(features.contains(ModelFeature.FUNCTION_CALLING));
        assertTrue(features.contains(ModelFeature.JSON_MODE));
        assertEquals(6, features.size());
    }

    @Test
    void featureSetIsDefensiveCopy() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
        cap.getSupportedFeatures().clear();
        assertEquals(6, cap.getSupportedFeatures().size());
    }

    @Test
    void implementsModelCapability() {
        assertInstanceOf(ModelCapability.class, new ClaudeModelCapability("claude-3-opus-20240229"));
    }

    @Test
    void toStringContainsModelId() {
        assertTrue(new ClaudeModelCapability("claude-3-opus-20240229").toString().contains("claude-3-opus"));
    }
}
