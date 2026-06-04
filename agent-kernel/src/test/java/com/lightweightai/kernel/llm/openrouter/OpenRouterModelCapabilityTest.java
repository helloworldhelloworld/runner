package com.lightweightai.kernel.llm.openrouter;

import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenRouterModelCapability - OpenRouter 模型能力描述")
class OpenRouterModelCapabilityTest {

    @Nested
    @DisplayName("模型 token 限制")
    class TokenLimits {

        @Test
        @DisplayName("Claude 模型：200k context, 8192 output")
        void claudeModelLimits() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-opus");

            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(8192, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("GPT-4 模型：128k context, 4096 output")
        void gpt4ModelLimits() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");

            assertEquals(128000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("其他模型使用默认值：100k context, 4096 output")
        void defaultModelLimits() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("mistral/mixtral-8x7b");

            assertEquals(100000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }
    }

    @Nested
    @DisplayName("支持的功能特性")
    class SupportedFeatures {

        @Test
        @DisplayName("所有模型至少支持 4 种基础特性")
        void baseFeatures() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("mistral/mixtral-8x7b");

            Set<ModelFeature> features = cap.getSupportedFeatures();
            assertTrue(features.contains(ModelFeature.TOOL_CALLING));
            assertTrue(features.contains(ModelFeature.SYSTEM_MESSAGE));
            assertTrue(features.contains(ModelFeature.STREAMING));
            assertTrue(features.contains(ModelFeature.FUNCTION_CALLING));
        }

        @Test
        @DisplayName("Claude 模型支持 MULTIMODAL")
        void claudeSupportsMultimodal() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-opus");

            assertTrue(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
        }

        @Test
        @DisplayName("GPT-4 模型支持 MULTIMODAL 和 JSON_MODE")
        void gpt4SupportsMultimodalAndJsonMode() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");

            Set<ModelFeature> features = cap.getSupportedFeatures();
            assertTrue(features.contains(ModelFeature.MULTIMODAL));
            assertTrue(features.contains(ModelFeature.JSON_MODE));
        }

        @Test
        @DisplayName("claude-3 模型支持 JSON_MODE")
        void claude3SupportsJsonMode() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-opus");

            assertTrue(cap.getSupportedFeatures().contains(ModelFeature.JSON_MODE));
        }

        @Test
        @DisplayName("纯文本模型不支持 MULTIMODAL")
        void textOnlyModelNoMultimodal() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("mistral/mixtral-8x7b");

            assertFalse(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
        }

        @Test
        @DisplayName("vision 模型支持 MULTIMODAL")
        void visionModelSupportsMultimodal() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("some-model-vision-v2");

            assertTrue(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
        }

        @Test
        @DisplayName("getSupportedFeatures 返回防御性拷贝")
        void featuresAreDefensiveCopy() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");

            Set<ModelFeature> features1 = cap.getSupportedFeatures();
            int originalSize = features1.size();
            features1.clear();

            assertEquals(originalSize, cap.getSupportedFeatures().size());
        }
    }

    @Nested
    @DisplayName("占位方法")
    class PlaceholderMethods {

        @Test
        @DisplayName("getMessageFormatter 返回 null")
        void messageFormatterIsNull() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
            assertNull(cap.getMessageFormatter());
        }

        @Test
        @DisplayName("getTokenCounter 返回 null")
        void tokenCounterIsNull() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
            assertNull(cap.getTokenCounter());
        }
    }

    @Test
    @DisplayName("getModelId 返回构造时传入的 ID")
    void modelIdPreserved() {
        String modelId = "anthropic/claude-3-opus";
        OpenRouterModelCapability cap = new OpenRouterModelCapability(modelId);

        assertEquals(modelId, cap.getModelId());
    }

    @Test
    @DisplayName("toString 包含模型 ID 和关键参数")
    void toStringContainsKeyInfo() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");

        String str = cap.toString();
        assertTrue(str.contains("gpt-4-turbo"));
        assertTrue(str.contains("128000"));
    }

    @Test
    @DisplayName("实现 ModelCapability 接口")
    void implementsModelCapability() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("test-model");
        assertInstanceOf(ModelCapability.class, cap);
    }
}
