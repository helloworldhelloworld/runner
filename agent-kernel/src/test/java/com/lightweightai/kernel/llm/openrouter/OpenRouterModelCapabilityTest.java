package com.lightweightai.kernel.llm.openrouter;

import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenRouterModelCapability - 模型能力描述")
class OpenRouterModelCapabilityTest {

    @Nested
    @DisplayName("Claude 模型")
    class ClaudeModels {

        @Test
        @DisplayName("Claude 模型 - 200k 上下文, 8192 输出")
        void shouldSetClaudeTokenLimits() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-sonnet");
            assertEquals(200000, cap.getMaxContextTokens());
            assertEquals(8192, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("Claude 支持多模态")
        void shouldSupportMultimodal() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-opus");
            assertTrue(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
        }

        @Test
        @DisplayName("Claude-3 支持 JSON_MODE")
        void shouldSupportJsonModeForClaude3() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("anthropic/claude-3-sonnet");
            assertTrue(cap.getSupportedFeatures().contains(ModelFeature.JSON_MODE));
        }
    }

    @Nested
    @DisplayName("GPT-4 模型")
    class Gpt4Models {

        @Test
        @DisplayName("GPT-4 模型 - 128k 上下文, 4096 输出")
        void shouldSetGpt4TokenLimits() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
            assertEquals(128000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("GPT-4 支持多模态和 JSON_MODE")
        void shouldSupportMultimodalAndJsonMode() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("openai/gpt-4-turbo");
            Set<ModelFeature> features = cap.getSupportedFeatures();
            assertTrue(features.contains(ModelFeature.MULTIMODAL));
            assertTrue(features.contains(ModelFeature.JSON_MODE));
        }
    }

    @Nested
    @DisplayName("其他模型")
    class OtherModels {

        @Test
        @DisplayName("默认 - 100k 上下文, 4096 输出")
        void shouldSetDefaultTokenLimits() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("meta/llama-3");
            assertEquals(100000, cap.getMaxContextTokens());
            assertEquals(4096, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("不支持多模态（非视觉模型）")
        void shouldNotSupportMultimodal() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("meta/llama-3");
            assertFalse(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
        }

        @Test
        @DisplayName("不支持 JSON_MODE")
        void shouldNotSupportJsonMode() {
            OpenRouterModelCapability cap = new OpenRouterModelCapability("meta/llama-3");
            assertFalse(cap.getSupportedFeatures().contains(ModelFeature.JSON_MODE));
        }
    }

    @Test
    @DisplayName("所有模型都支持基础特性")
    void shouldSupportBasicFeatures() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("any-model");
        Set<ModelFeature> features = cap.getSupportedFeatures();
        assertTrue(features.contains(ModelFeature.TOOL_CALLING));
        assertTrue(features.contains(ModelFeature.SYSTEM_MESSAGE));
        assertTrue(features.contains(ModelFeature.STREAMING));
        assertTrue(features.contains(ModelFeature.FUNCTION_CALLING));
    }

    @Test
    @DisplayName("getSupportedFeatures 返回防御性副本")
    void shouldReturnDefensiveCopy() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("test-model");
        Set<ModelFeature> features = cap.getSupportedFeatures();
        features.clear(); // 修改副本不影响原对象
        assertFalse(cap.getSupportedFeatures().isEmpty());
    }

    @Test
    @DisplayName("getModelId 返回正确值")
    void shouldReturnCorrectModelId() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("my-model-id");
        assertEquals("my-model-id", cap.getModelId());
    }

    @Test
    @DisplayName("getMessageFormatter 和 getTokenCounter 返回 null")
    void shouldReturnNullForUnimplemented() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("test");
        assertNull(cap.getMessageFormatter());
        assertNull(cap.getTokenCounter());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void shouldContainKeyInfoInToString() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("test-model");
        String str = cap.toString();
        assertTrue(str.contains("test-model"));
        assertTrue(str.contains("100000"));
    }

    @Test
    @DisplayName("vision 模型支持多模态")
    void shouldSupportMultimodalForVisionModels() {
        OpenRouterModelCapability cap = new OpenRouterModelCapability("some/model-vision");
        assertTrue(cap.getSupportedFeatures().contains(ModelFeature.MULTIMODAL));
    }
}
