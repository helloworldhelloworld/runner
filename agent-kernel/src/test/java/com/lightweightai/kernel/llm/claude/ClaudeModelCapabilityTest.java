package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeModelCapability - Claude 模型能力描述")
class ClaudeModelCapabilityTest {

    @Nested
    @DisplayName("模型 token 限制")
    class TokenLimits {

        @Test
        @DisplayName("claude-3-5-sonnet 系列：200k context, 8192 output")
        void sonnet35HasHigherOutputLimit() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");

            assertEquals("claude-3-5-sonnet-20241022", cap.getModelId());
            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(8192, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("claude-3-opus 系列：200k context, 4096 output")
        void opusHasStandardOutputLimit() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");

            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("claude-3-sonnet 系列：200k context, 4096 output")
        void sonnet3HasStandardOutputLimit() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-sonnet-20240229");

            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("claude-3-haiku 系列：200k context, 4096 output")
        void haikuHasStandardOutputLimit() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-haiku-20240307");

            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("未知模型使用默认值：200k context, 4096 output")
        void unknownModelUsesDefaults() {
            ClaudeModelCapability cap = new ClaudeModelCapability("some-future-model");

            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("包含 claude-3-5-sonnet 子串的任意 ID 都匹配 8192")
        void sonnet35MatchingIsSubstringBased() {
            ClaudeModelCapability cap = new ClaudeModelCapability("prefix-claude-3-5-sonnet-suffix");

            assertEquals(8192, cap.getMaxOutputTokens());
        }
    }

    @Nested
    @DisplayName("支持的功能特性")
    class SupportedFeatures {

        @Test
        @DisplayName("所有 Claude 模型支持全部 6 种特性")
        void allSixFeaturesSupported() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");

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
        void featuresAreDefensiveCopy() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");

            Set<ModelFeature> features1 = cap.getSupportedFeatures();
            Set<ModelFeature> features2 = cap.getSupportedFeatures();

            assertNotSame(features1, features2);
            assertEquals(features1, features2);

            features1.clear();
            assertEquals(6, cap.getSupportedFeatures().size());
        }
    }

    @Nested
    @DisplayName("TODO 占位方法")
    class PlaceholderMethods {

        @Test
        @DisplayName("getMessageFormatter 返回 null")
        void messageFormatterIsNull() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
            assertNull(cap.getMessageFormatter());
        }

        @Test
        @DisplayName("getTokenCounter 返回 null")
        void tokenCounterIsNull() {
            ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
            assertNull(cap.getTokenCounter());
        }
    }

    @Test
    @DisplayName("toString 包含模型 ID 和关键参数")
    void toStringContainsKeyInfo() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-5-sonnet-20241022");

        String str = cap.toString();
        assertTrue(str.contains("claude-3-5-sonnet-20241022"));
        assertTrue(str.contains("200000"));
        assertTrue(str.contains("8192"));
        assertTrue(str.contains("6"));
    }

    @Test
    @DisplayName("实现 ModelCapability 接口")
    void implementsModelCapability() {
        ClaudeModelCapability cap = new ClaudeModelCapability("claude-3-opus-20240229");
        assertInstanceOf(ModelCapability.class, cap);
    }
}
