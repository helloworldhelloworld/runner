package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeModelCapability - Claude 模型能力描述")
class ClaudeModelCapabilityTest {

    @ParameterizedTest
    @CsvSource({
            "claude-3-5-sonnet-20241022, 200000, 8192",
            "claude-3-opus-20240229,     200000, 4096",
            "claude-3-sonnet-20240229,   200000, 4096",
            "claude-3-haiku-20240307,    200000, 4096"
    })
    @DisplayName("不同模型 → 正确的 context 和 output token 限制")
    void modelSpecificTokenLimits(String modelId, int expectedContext, int expectedOutput) {
        ClaudeModelCapability cap = new ClaudeModelCapability(modelId);

        assertEquals(modelId, cap.getModelId());
        assertEquals(expectedContext, cap.getMaxContextTokens());
        assertEquals(expectedOutput, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("未知模型使用默认值")
    void unknownModelUsesDefaults() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-future-model");

        assertEquals(200000, cap.getMaxContextTokens());
        assertEquals(4096, cap.getMaxOutputTokens());
    }

    @Test
    @DisplayName("Claude 3 模型支持所有核心功能")
    void supportsAllCoreFeatures() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");
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
    @DisplayName("getSupportedFeatures 返回副本 — 修改不影响原始数据")
    void featuresAreDefensiveCopy() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
        Set<ModelFeature> features = cap.getSupportedFeatures();
        features.clear();

        assertEquals(6, cap.getSupportedFeatures().size());
    }

    @Test
    @DisplayName("实现 ModelCapability 接口")
    void implementsModelCapability() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-haiku-20240307");
        assertTrue(cap instanceof ModelCapability);
    }

    @Test
    @DisplayName("toString 包含模型标识信息")
    void toStringContainsModelInfo() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");
        String str = cap.toString();

        assertTrue(str.contains("claude-3-5-sonnet"));
        assertTrue(str.contains("200000"));
        assertTrue(str.contains("8192"));
    }
}
