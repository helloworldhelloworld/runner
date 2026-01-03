package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.MessageFormatter;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.TokenCounter;

import java.util.HashSet;
import java.util.Set;

/**
 * Model capability descriptor for Claude models
 */
public class ClaudeModelCapability implements ModelCapability {

    private final String modelId;
    private final int maxContextTokens;
    private final int maxOutputTokens;
    private final Set<ModelFeature> supportedFeatures;

    public ClaudeModelCapability(String modelId) {
        this.modelId = modelId;

        // Set capabilities based on model
        if (modelId.contains("claude-3-5-sonnet")) {
            this.maxContextTokens = 200000;
            this.maxOutputTokens = 8192;
        } else if (modelId.contains("claude-3-opus")) {
            this.maxContextTokens = 200000;
            this.maxOutputTokens = 4096;
        } else if (modelId.contains("claude-3-sonnet")) {
            this.maxContextTokens = 200000;
            this.maxOutputTokens = 4096;
        } else if (modelId.contains("claude-3-haiku")) {
            this.maxContextTokens = 200000;
            this.maxOutputTokens = 4096;
        } else {
            // Default values
            this.maxContextTokens = 200000;
            this.maxOutputTokens = 4096;
        }

        // Claude 3 models support all features
        this.supportedFeatures = new HashSet<>();
        this.supportedFeatures.add(ModelFeature.TOOL_CALLING);
        this.supportedFeatures.add(ModelFeature.MULTIMODAL);
        this.supportedFeatures.add(ModelFeature.SYSTEM_MESSAGE);
        this.supportedFeatures.add(ModelFeature.STREAMING);
        this.supportedFeatures.add(ModelFeature.FUNCTION_CALLING);
        this.supportedFeatures.add(ModelFeature.JSON_MODE);
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
        // TODO: Implement Claude-specific message formatter
        return null;
    }

    @Override
    public TokenCounter getTokenCounter() {
        // TODO: Implement Claude-specific token counter
        return null;
    }

    @Override
    public Set<ModelFeature> getSupportedFeatures() {
        return new HashSet<>(supportedFeatures);
    }

    @Override
    public String toString() {
        return "ClaudeModelCapability{" +
            "modelId='" + modelId + '\'' +
            ", maxContextTokens=" + maxContextTokens +
            ", maxOutputTokens=" + maxOutputTokens +
            ", features=" + supportedFeatures.size() +
            '}';
    }
}
