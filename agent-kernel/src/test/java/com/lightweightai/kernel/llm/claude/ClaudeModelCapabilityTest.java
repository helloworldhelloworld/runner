package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeModelCapability - Claude 模型能力")
class ClaudeModelCapabilityTest {

    @Nested
    @DisplayName("模型参数配置")
    class ModelConfigTests {

        @Test
        @DisplayName("claude-3-5-sonnet 拥有 200K context / 8K output")
        void sonnet35Params() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");
            assertEquals("claude-3-5-sonnet-20241022", cap.getModelId());
            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(8192, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("claude-3-opus 拥有 200K context / 4K output")
        void opus3Params() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("claude-3-sonnet 拥有 200K context / 4K output")
        void sonnet3Params() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-sonnet-20240229");
            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("claude-3-haiku 拥有 200K context / 4K output")
        void haiku3Params() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-haiku-20240307");
            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("未知模型使用默认值 200K / 4K")
        void unknownModelDefaults() {
            ClaudeModelCapability cap = new ClaudeModelCapability("unknown-model");
            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }
    }

    @Nested
    @DisplayName("Feature 支持")
    class FeatureTests {

        @Test
        @DisplayName("Claude 3 支持全部 6 种 Feature")
        void supportsAllFeatures() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");
            Set<ModelFeature> features = cap.getSupportedFeatures();

            assertEquals(6, features.size());
            assertTrue(features.contains(ModelFeature.TOOL_CALLING));
            assertTrue(features.contains(ModelFeature.MULTIMODAL));
            assertTrue(features.contains(ModelFeature.SYSTEM_MESSAGE));
            assertTrue(features.contains(ModelFeature.STREAMING));
            assertTrue(features.contains(ModelFeature.FUNCTION_CALLING));
            assertTrue(features.contains(ModelFeature.JSON_MODE));
        }

        @Test
        @DisplayName("getSupportedFeatures 返回防御性拷贝")
        void featuresReturnsCopy() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
            Set<ModelFeature> features1 = cap.getSupportedFeatures();
            Set<ModelFeature> features2 = cap.getSupportedFeatures();
            assertNotSame(features1, features2);
            assertEquals(features1, features2);
        }
    }

    @Test
    @DisplayName("getMessageFormatter 和 getTokenCounter 当前返回 null (TODO)")
    void formatterAndCounterAreNull() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");
        assertNull(cap.getMessageFormatter());
        assertNull(cap.getTokenCounter());
    }

    @Test
    @DisplayName("toString 包含模型信息")
    void toStringContainsModelInfo() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
        String s = cap.toString();
        assertTrue(s.contains("claude-3-opus-20240229"));
        assertTrue(s.contains("200000"));
    }
}
