package com.lightweightai.kernel.llm;

import java.util.Set;

/**
 * Describes the capabilities and characteristics of an LLM model.
 * Different models have different context windows, features, and message formats.
 */
public interface ModelCapability {

    /**
     * @return The model identifier (e.g., "claude-3-5-sonnet-20241022")
     */
    String getModelId();

    /**
     * @return Maximum context window size in tokens
     */
    int getMaxContextTokens();

    /**
     * @return Maximum output tokens
     */
    int getMaxOutputTokens();

    /**
     * @return Message formatter for this model
     */
    MessageFormatter getMessageFormatter();

    /**
     * @return Token counter for this model
     */
    TokenCounter getTokenCounter();

    /**
     * @return Set of features supported by this model
     */
    Set<ModelFeature> getSupportedFeatures();

    /**
     * Features that models may support
     */
    enum ModelFeature {
        TOOL_CALLING,
        MULTIMODAL,
        SYSTEM_MESSAGE,
        STREAMING,
        FUNCTION_CALLING,
        JSON_MODE
    }
}
