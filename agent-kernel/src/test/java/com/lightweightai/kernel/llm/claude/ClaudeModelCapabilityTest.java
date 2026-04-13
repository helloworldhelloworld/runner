package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeModelCapability - model capability descriptor for Claude models")
class ClaudeModelCapabilityTest {

    // ==================== model identification ====================

    @Test
    @DisplayName("claude-3-5-sonnet has 200K context and 8192 output")
    void claude35SonnetCapabilities() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");
        assertEquals("claude-3-5-sonnet-20241022", cap.getModelId());
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(8192, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("claude-3-opus has 200K context and 4096 output")
    void claude3OpusCapabilities() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("claude-3-sonnet has 200K context and 4096 output")
    void claude3SonnetCapabilities() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-sonnet-20240229");
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("claude-3-haiku has 200K context and 4096 output")
    void claude3HaikuCapabilities() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-haiku-20240307");
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("unknown model uses defaults")
    void unknownModelDefaults() {
        ClaudeModelCapability cap = new ClaudeModelCapability("unknown-model");
        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    // ==================== feature support ====================

    @ParameterizedTest
    @ValueSource(strings = {"claude-3-5-sonnet", "claude-3-opus", "claude-3-haiku"})
    @DisplayName("all Claude 3 models support core features")
    void allClaude3ModelsHaveCoreFeatures(String modelId) {
        ClaudeModelCapability cap = new ClaudeModelCapability(modelId);
        Set<ModelFeature> features = cap.getSupportedFeatures();

        assertTrue(features.contains(ModelFeature.TOOL_CALLING));
        assertTrue(features.contains(ModelFeature.MULTIMODAL));
        assertTrue(features.contains(ModelFeature.SYSTEM_MESSAGE));
        assertTrue(features.contains(ModelFeature.STREAMING));
        assertTrue(features.contains(ModelFeature.FUNCTION_CALLING));
        assertTrue(features.contains(ModelFeature.JSON_MODE));
    }

    @Test
    @DisplayName("getSupportedFeatures returns defensive copy")
    void getSupportedFeaturesDefensiveCopy() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet");
        Set<ModelFeature> features1 = cap.getSupportedFeatures();
        Set<ModelFeature> features2 = cap.getSupportedFeatures();

        assertNotSame(features1, features2);
        assertEquals(features1, features2);
    }

    // ==================== formatters ====================

    @Test
    @DisplayName("getMessageFormatter returns null (TODO)")
    void messageFormatterIsNull() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet");
        assertNull(cap.getMessageFormatter());
    }

    @Test
    @DisplayName("getTokenCounter returns null (TODO)")
    void tokenCounterIsNull() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet");
        assertNull(cap.getTokenCounter());
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString includes model info")
    void toStringIncludesModelInfo() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet");
        String str = cap.toString();
        assertTrue(str.contains("claude-3-5-sonnet"));
        assertTrue(str.contains("200000"));
    }
}
