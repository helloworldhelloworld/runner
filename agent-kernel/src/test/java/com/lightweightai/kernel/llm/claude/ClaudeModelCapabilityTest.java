package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeModelCapability — model-specific context windows, output limits, features")
class ClaudeModelCapabilityTest {

    @Nested
    @DisplayName("Context window & output limits per model family")
    class TokenLimits {

        @Test
        @DisplayName("claude-3-5-sonnet has 200k context, 8192 output")
        void sonnet35() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");

            assertEquals(200_000, cap.getMaxContextTokens());
            assertEquals(8192, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("claude-3-opus has 200k context, 4096 output")
        void opus() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");

            assertEquals(200_000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("claude-3-sonnet has 200k context, 4096 output")
        void sonnet3() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-sonnet-20240229");

            assertEquals(200_000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("claude-3-haiku has 200k context, 4096 output")
        void haiku() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-haiku-20240307");

            assertEquals(200_000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("unknown model uses default limits")
        void unknownModel() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-future-model-v42");

            assertEquals(200_000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }
    }

    @Nested
    @DisplayName("Feature support")
    class FeatureSupport {

        @ParameterizedTest
        @ValueSource(strings = {
            "claude-3-5-sonnet-20241022",
            "claude-3-opus-20240229",
            "claude-3-sonnet-20240229",
            "claude-3-haiku-20240307"
        })
        @DisplayName("all Claude 3 models support tool calling, multimodal, system, streaming, function, json")
        void allFeaturesSupported(String modelId) {
            ClaudeModelCapability cap = new ClaudeModelCapability(modelId);
            Set<ModelFeature> features = cap.getSupportedFeatures();

            assertTrue(features.contains(ModelFeature.TOOL_CALLING));
            assertTrue(features.contains(ModelFeature.MULTIMODAL));
            assertTrue(features.contains(ModelFeature.SYSTEM_MESSAGE));
            assertTrue(features.contains(ModelFeature.STREAMING));
            assertTrue(features.contains(ModelFeature.FUNCTION_CALLING));
            assertTrue(features.contains(ModelFeature.JSON_MODE));
            assertEquals(6, features.size());
        }

        @Test
        @DisplayName("getSupportedFeatures returns defensive copy")
        void defensiveCopy() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");

            Set<ModelFeature> first = cap.getSupportedFeatures();
            first.clear();

            Set<ModelFeature> second = cap.getSupportedFeatures();
            assertEquals(6, second.size(), "clearing returned set must not affect internal state");
        }
    }

    @Nested
    @DisplayName("Identity & accessors")
    class Identity {

        @Test
        @DisplayName("getModelId returns the model id passed to constructor")
        void modelId() {
            String id = "claude-3-5-sonnet-20241022";
            ClaudeModelCapability cap = new ClaudeModelCapability(id);

            assertEquals(id, cap.getModelId());
        }

        @Test
        @DisplayName("toString includes model id and token limits")
        void toStringFormat() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
            String str = cap.toString();

            assertTrue(str.contains("claude-3-opus-20240229"));
            assertTrue(str.contains("200000"));
        }
    }
}
