package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the tool calling loop for LLM conversations.
 * When an LLM wants to use tools, this class:
 * 1. Executes the tool calls
 * 2. Sends results back to the LLM
 * 3. Repeats until the LLM provides a final response
 */
public class ToolCallingLoop {

    private final LLMProvider provider;
    private final ToolExecutor toolExecutor;
    private final int maxIterations;

    /**
     * Create a new ToolCallingLoop
     *
     * @param provider     LLM provider to use
     * @param toolExecutor Tool executor for running tools
     * @param maxIterations Maximum number of tool calling iterations (prevents infinite loops)
     */
    public ToolCallingLoop(LLMProvider provider, ToolExecutor toolExecutor, int maxIterations) {
        if (provider == null) {
            throw new IllegalArgumentException("LLM provider cannot be null");
        }
        if (toolExecutor == null) {
            throw new IllegalArgumentException("Tool executor cannot be null");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("Max iterations must be at least 1");
        }

        this.provider = provider;
        this.toolExecutor = toolExecutor;
        this.maxIterations = maxIterations;
    }

    /**
     * Execute a conversation with tool calling support
     *
     * @param messages     Initial conversation messages
     * @param options      LLM options (including tools definition)
     * @return Final LLM response after tool calling loop completes
     */
    public LLMResponse executeWithTools(List<ConversationMessage> messages, LLMOptions options) {
        List<ConversationMessage> conversation = new ArrayList<>(messages);
        int iteration = 0;

        while (iteration < maxIterations) {
            // Call LLM
            LLMResponse response = provider.complete(conversation, options);

            // Check if LLM wants to call tools
            if (!response.hasToolCalls()) {
                // No tool calls, we're done
                return response;
            }

            // Execute all tool calls
            List<ToolCall> toolCalls = response.getToolCalls();
            List<ToolResult> toolResults = toolExecutor.executeToolCalls(toolCalls);

            // Add assistant's response (with tool calls) to conversation
            conversation.add(response.getMessage());

            // Add tool results as user messages to conversation
            for (ToolResult toolResult : toolResults) {
                ConversationMessage toolResultMessage = createToolResultMessage(toolResult);
                conversation.add(toolResultMessage);
            }

            iteration++;
        }

        throw new RuntimeException(
            "Tool calling loop exceeded maximum iterations: " + maxIterations +
            ". This may indicate an infinite loop or the LLM is stuck calling tools."
        );
    }

    /**
     * Create a conversation message from a tool result
     *
     * @param toolResult The tool execution result
     * @return ConversationMessage with the tool result
     */
    private ConversationMessage createToolResultMessage(ToolResult toolResult) {
        // Convert tool result to a user message format
        // The exact format depends on the LLM provider's requirements
        // For now, create a simple message with the tool result content

        return ConversationMessage.builder()
            .role(MessageRole.TOOL)
            .textContent(toolResult.getContent())
            .metadata(Map.of(
                "tool_use_id", toolResult.getToolUseId(),
                "is_error", toolResult.isError()
            ))
            .build();
    }

    /**
     * Get the maximum number of iterations
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * Builder for ToolCallingLoop
     */
    public static class Builder {
        private LLMProvider provider;
        private ToolExecutor toolExecutor;
        private int maxIterations = 10; // Default to 10 iterations

        public Builder provider(LLMProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder toolExecutor(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public ToolCallingLoop build() {
            return new ToolCallingLoop(provider, toolExecutor, maxIterations);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "ToolCallingLoop{" +
            "provider=" + provider.getProviderName() +
            ", maxIterations=" + maxIterations +
            ", toolCount=" + toolExecutor.getFunctionCount() +
            '}';
    }
}
