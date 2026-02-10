package com.lightweightai.kernel.llm.openrouter;

import com.lightweightai.kernel.llm.MessageFormatter;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.TokenCounter;

import java.util.HashSet;
import java.util.Set;

/**
 * Model capability descriptor for OpenRouter models
 */
public class OpenRouterModelCapability implements ModelCapability {

    private final String modelId;
    private final int maxContextTokens;
    private final int maxOutputTokens;
    private final Set<ModelFeature> supportedFeatures;

    public OpenRouterModelCapability(String modelId) {
        this.modelId = modelId;

        // Set capabilities based on model
        if (modelId.contains("claude")) {
            this.maxContextTokens = 200000;
            this.maxOutputTokens = 8192;
        } else if (modelId.contains("gpt-4")) {
            this.maxContextTokens = 128000;
            this.maxOutputTokens = 4096;
        } else {
            // Default values
            this.maxContextTokens = 100000;
            this.maxOutputTokens = 4096;
        }

        // Most modern models support these features
        this.supportedFeatures = new HashSet<>();
        this.supportedFeatures.add(ModelFeature.TOOL_CALLING);
        this.supportedFeatures.add(ModelFeature.SYSTEM_MESSAGE);
        this.supportedFeatures.add(ModelFeature.STREAMING);
        this.supportedFeatures.add(ModelFeature.FUNCTION_CALLING);

        // Vision support for specific models
        if (modelId.contains("claude") || modelId.contains("gpt-4") || modelId.contains("vision")) {
            this.supportedFeatures.add(ModelFeature.MULTIMODAL);
        }

        // JSON mode for specific models
        if (modelId.contains("gpt-4") || modelId.contains("claude-3")) {
            this.supportedFeatures.add(ModelFeature.JSON_MODE);
        }
    }

    @Override
    public String getModelId() {
        return modelId;
    }

    @Override
    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    @Override
    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    @Override
    public MessageFormatter getMessageFormatter() {
        // OpenRouter uses OpenAI-compatible format, no special formatting needed
        return null;
    }

    @Override
    public TokenCounter getTokenCounter() {
        // TODO: Implement token counter
        return null;
    }

    @Override
    public Set<ModelFeature> getSupportedFeatures() {
        return new HashSet<>(supportedFeatures);
    }

    @Override
    public String toString() {
        return "OpenRouterModelCapability{" +
            "modelId='" + modelId + '\'' +
            ", maxContextTokens=" + maxContextTokens +
            ", maxOutputTokens=" + maxOutputTokens +
            ", features=" + supportedFeatures.size() +
            '}';
    }
}
