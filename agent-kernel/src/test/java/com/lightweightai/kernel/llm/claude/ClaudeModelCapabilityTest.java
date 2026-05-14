package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClaudeModelCapability 单元测试
 */
class ClaudeModelCapabilityTest {

    @Test
    @DisplayName("claude-3-5-sonnet 模型 - maxOutputTokens = 8192, maxContextTokens = 200000")
    void claude35SonnetCapabilities() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");

        assertEquals("claude-3-5-sonnet-20241022", cap.getModelId());
        assertEquals(200_000, cap.getMaxContextTokens());
        assertEquals(8192, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("claude-3-opus 模型 - maxOutputTokens = 4096, maxContextTokens = 200000")
    void claude3OpusCapabilities() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");

        assertEquals("claude-3-opus-20240229", cap.getModelId());
        assertEquals(200_000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("claude-3-sonnet 模型 - maxOutputTokens = 4096, maxContextTokens = 200000")
    void claude3SonnetCapabilities() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-sonnet-20240229");

        assertEquals("claude-3-sonnet-20240229", cap.getModelId());
        assertEquals(200_000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("claude-3-haiku 模型 - maxOutputTokens = 4096, maxContextTokens = 200000")
    void claude3HaikuCapabilities() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-haiku-20240307");

        assertEquals("claude-3-haiku-20240307", cap.getModelId());
        assertEquals(200_000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("未知模型 - 使用默认值 maxOutputTokens = 4096, maxContextTokens = 200000")
    void unknownModelUsesDefaults() {
        ClaudeModelCapability cap = new ClaudeModelCapability("some-unknown-model");

        assertEquals("some-unknown-model", cap.getModelId());
        assertEquals(200_000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("所有模型支持 6 项功能特性")
    void allModelsSupportAllSixFeatures() {
        Set<ModelFeature> expectedFeatures = Set.of(
                ModelFeature.TOOL_CALLING,
                ModelFeature.MULTIMODAL,
                ModelFeature.SYSTEM_MESSAGE,
                ModelFeature.STREAMING,
                ModelFeature.FUNCTION_CALLING,
                ModelFeature.JSON_MODE
        );

        // Verify for multiple model variants
        for (String modelId : new String[]{
                "claude-3-5-sonnet-20241022",
                "claude-3-opus-20240229",
                "claude-3-sonnet-20240229",
                "claude-3-haiku-20240307",
                "unknown-model"}) {

            ClaudeModelCapability cap = new ClaudeModelCapability(modelId);
            Set<ModelFeature> features = cap.getSupportedFeatures();

            assertEquals(6, features.size(), "Model " + modelId + " should support exactly 6 features");
            assertEquals(expectedFeatures, features,
                    "Model " + modelId + " should support all expected features");
        }
    }

    @Test
    @DisplayName("getSupportedFeatures 返回防御性副本 - 修改返回集合不影响内部状态")
    void getSupportedFeaturesReturnsDefensiveCopy() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");

        Set<ModelFeature> features = cap.getSupportedFeatures();
        features.clear();

        // Internal state should be unaffected
        assertEquals(6, cap.getSupportedFeatures().size());
    }

    @Test
    @DisplayName("getModelId 返回构造时传入的 modelId")
    void getModelIdReturnsPassedValue() {
        String modelId = "claude-3-5-sonnet-20241022";
        ClaudeModelCapability cap = new ClaudeModelCapability(modelId);

        assertEquals(modelId, cap.getModelId());
    }

    @Test
    @DisplayName("claude-3-5-sonnet 模型名包含检测 - 只要 modelId 包含子串即匹配")
    void modelIdContainsMatching() {
        // Verify the "contains" logic works for variant suffixes
        ClaudeModelCapability cap = new ClaudeModelCapability("my-custom-claude-3-5-sonnet-variant");

        assertEquals(8192, cap.getMaxOutputTokens());
        assertEquals(200_000, cap.getMaxContextTokens());
    }

    @Test
    @DisplayName("claude-3-sonnet 不会误匹配 claude-3-5-sonnet (contains 顺序优先级)")
    void claude3SonnetDoesNotMatchClaude35Sonnet() {
        // "claude-3-5-sonnet" contains "claude-3-5-sonnet" -> matches first branch (8192)
        // "claude-3-sonnet" does NOT contain "claude-3-5-sonnet" -> falls through to third branch (4096)
        ClaudeModelCapability cap35 = new ClaudeModelCapability("claude-3-5-sonnet-latest");
        ClaudeModelCapability cap3 = new ClaudeModelCapability("claude-3-sonnet-20240229");

        assertEquals(8192, cap35.getMaxOutputTokens());
        assertEquals(4096, cap3.getMaxOutputTokens());
    }
}
