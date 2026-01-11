package com.lightweightai.kernel.instruction;

import com.lightweightai.kernel.llm.ConversationMessage;

import java.util.List;

/**
 * Provider Adapter Interface
 *
 * Adapts instruction packages to specific LLM provider formats.
 *
 * Different LLM providers have different ways of handling instructions:
 * - Claude: Rich system messages with detailed instructions
 * - OpenAI: System messages with role-based prompts
 * - Gemini: Instruction tuning via context
 * - Llama: May not support system messages at all
 *
 * This adapter handles the conversion between the generic InstructionPackage
 * format and provider-specific message formats.
 */
public interface ProviderAdapter {

    /**
     * Get the provider name this adapter supports
     *
     * Examples: "claude", "openai", "gemini", "llama"
     */
    String getProviderName();

    /**
     * Check if this adapter supports the given provider
     */
    boolean supports(String providerName);

    /**
     * Format an instruction package as a system message
     *
     * @param instructionPackage The instruction package to format
     * @return A system message formatted for this provider
     */
    ConversationMessage formatAsSystemMessage(InstructionPackage instructionPackage);

    /**
     * Format an instruction package as a user message prefix
     *
     * Some providers don't support system messages, so instructions
     * need to be prepended to user messages.
     *
     * @param instructionPackage The instruction package to format
     * @param userMessage The original user message
     * @return A modified user message with instructions prepended
     */
    ConversationMessage formatAsUserPrefix(InstructionPackage instructionPackage, String userMessage);

    /**
     * Inject instruction packages into a conversation
     *
     * This is the main method for integrating instructions into a conversation.
     * Different providers may inject differently:
     * - As system message at the beginning
     * - As user message prefix
     * - As context in a special format
     *
     * @param messages The original conversation messages
     * @param instructionPackages The instruction packages to inject
     * @return Modified conversation with instructions injected
     */
    List<ConversationMessage> injectInstructions(
        List<ConversationMessage> messages,
        List<InstructionPackage> instructionPackages
    );

    /**
     * Get capabilities of this provider
     */
    ProviderCapabilities getCapabilities();

    /**
     * Provider capabilities descriptor
     */
    class ProviderCapabilities {
        private final boolean supportsSystemMessages;
        private final boolean supportsInstructions;
        private final boolean supportsResources;
        private final int maxInstructionLength;
        private final boolean supportsMultipleInstructions;

        public ProviderCapabilities(
            boolean supportsSystemMessages,
            boolean supportsInstructions,
            boolean supportsResources,
            int maxInstructionLength,
            boolean supportsMultipleInstructions
        ) {
            this.supportsSystemMessages = supportsSystemMessages;
            this.supportsInstructions = supportsInstructions;
            this.supportsResources = supportsResources;
            this.maxInstructionLength = maxInstructionLength;
            this.supportsMultipleInstructions = supportsMultipleInstructions;
        }

        public boolean supportsSystemMessages() {
            return supportsSystemMessages;
        }

        public boolean supportsInstructions() {
            return supportsInstructions;
        }

        public boolean supportsResources() {
            return supportsResources;
        }

        public int getMaxInstructionLength() {
            return maxInstructionLength;
        }

        public boolean supportsMultipleInstructions() {
            return supportsMultipleInstructions;
        }

        /**
         * Claude capabilities
         */
        public static ProviderCapabilities claude() {
            return new ProviderCapabilities(
                true,   // supportsSystemMessages
                true,   // supportsInstructions
                true,   // supportsResources
                200000, // maxInstructionLength
                true    // supportsMultipleInstructions
            );
        }

        /**
         * OpenAI capabilities
         */
        public static ProviderCapabilities openai() {
            return new ProviderCapabilities(
                true,   // supportsSystemMessages
                true,   // supportsInstructions
                false,  // supportsResources (limited)
                128000, // maxInstructionLength (GPT-4)
                true    // supportsMultipleInstructions
            );
        }

        /**
         * Generic/minimal capabilities
         */
        public static ProviderCapabilities minimal() {
            return new ProviderCapabilities(
                false,  // supportsSystemMessages
                true,   // supportsInstructions
                false,  // supportsResources
                4096,   // maxInstructionLength
                false   // supportsMultipleInstructions
            );
        }
    }
}
