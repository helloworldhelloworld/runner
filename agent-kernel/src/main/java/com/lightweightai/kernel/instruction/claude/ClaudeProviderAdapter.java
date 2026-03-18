package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.instruction.ProviderAdapter;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import java.util.ArrayList;
import java.util.List;

/**
 * Claude Provider Adapter
 *
 * Formats instruction packages for Claude/Anthropic models.
 *
 * Claude has excellent support for:
 * - Rich system messages
 * - Detailed instructions and guidelines
 * - Multiple instruction packages
 * - Large context windows (200k+ tokens)
 */
public class ClaudeProviderAdapter implements ProviderAdapter {

    @Override
    public String getProviderName() {
        return "claude";
    }

    @Override
    public boolean supports(String providerName) {
        return providerName != null &&
               (providerName.equalsIgnoreCase("claude") ||
                providerName.equalsIgnoreCase("anthropic"));
    }

    @Override
    public ConversationMessage formatAsSystemMessage(InstructionPackage instructionPackage) {
        return ConversationMessage.builder()
            .role(MessageRole.SYSTEM)
            .textContent(instructionPackage.toPromptContext())
            .build();
    }

    @Override
    public ConversationMessage formatAsUserPrefix(
        InstructionPackage instructionPackage,
        String userMessage
    ) {
        // Claude supports system messages, so this is a fallback
        // Format: [Instructions] + User Message
        String combined = String.format(
            "# Instructions\n%s\n\n# User Request\n%s",
            instructionPackage.getInstructions(),
            userMessage
        );

        return ConversationMessage.builder()
            .role(MessageRole.USER)
            .textContent(combined)
            .build();
    }

    @Override
    public List<ConversationMessage> injectInstructions(
        List<ConversationMessage> messages,
        List<InstructionPackage> instructionPackages
    ) {
        if (instructionPackages == null || instructionPackages.isEmpty()) {
            return messages;
        }

        // Build combined system message for all instruction packages
        StringBuilder systemContent = new StringBuilder();
        systemContent.append("# Active Instruction Packages\n\n");
        systemContent.append("You have access to the following instruction packages:\n\n");

        for (InstructionPackage pkg : instructionPackages) {
            systemContent.append("---\n\n");
            systemContent.append(pkg.toPromptContext());
            systemContent.append("\n\n");
        }

        ConversationMessage systemMessage = ConversationMessage.builder()
            .role(MessageRole.SYSTEM)
            .textContent(systemContent.toString())
            .build();

        // Prepend system message to conversation
        List<ConversationMessage> enriched = new ArrayList<>();
        enriched.add(systemMessage);
        enriched.addAll(messages);

        return enriched;
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return ProviderCapabilities.claude();
    }

    /**
     * Singleton instance
     */
    private static final ClaudeProviderAdapter INSTANCE = new ClaudeProviderAdapter();

    public static ClaudeProviderAdapter getInstance() {
        return INSTANCE;
    }
}
