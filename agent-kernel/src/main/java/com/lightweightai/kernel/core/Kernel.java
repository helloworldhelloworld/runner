package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.memory.ConversationContext;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.Plugin;
import com.lightweightai.kernel.plugin.PluginFunction;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Core Kernel - main entry point for the AI orchestration framework.
 * Integrates LLM providers, plugins, memory, and planning capabilities.
 */
public interface Kernel {

    /**
     * Register a plugin
     *
     * @param plugin Plugin to register
     * @return this kernel for chaining
     */
    Kernel registerPlugin(Plugin plugin);

    /**
     * Execute a plugin function directly
     *
     * @param functionName Fully qualified function name (e.g., "math.add")
     * @param arguments    Function arguments
     * @return Function execution result
     */
    FunctionResult executeFunction(String functionName, java.util.Map<String, Object> arguments);

    /**
     * Execute a plugin function asynchronously
     *
     * @param functionName Fully qualified function name
     * @param arguments    Function arguments
     * @return CompletableFuture of execution result
     */
    CompletableFuture<FunctionResult> executeFunctionAsync(String functionName, java.util.Map<String, Object> arguments);

    /**
     * Simple chat - send a message and get a response
     * No tool calling, no context management
     *
     * @param message User message
     * @return Assistant response text
     */
    String chat(String message);

    /**
     * Chat with full options
     *
     * @param message User message
     * @param options LLM options
     * @return Full LLM response
     */
    LLMResponse chat(String message, LLMOptions options);

    /**
     * Chat asynchronously
     *
     * @param message User message
     * @param options LLM options
     * @return CompletableFuture of LLM response
     */
    CompletableFuture<LLMResponse> chatAsync(String message, LLMOptions options);

    /**
     * Chat with streaming response
     *
     * @param message User message
     * @param options LLM options
     * @param handler Stream event handler
     * @return CompletableFuture that completes when streaming finishes
     */
    CompletableFuture<LLMResponse> chatStream(String message, LLMOptions options, LLMProvider.StreamEventHandler handler);

    /**
     * Chat with automatic tool calling
     * Kernel automatically handles tool_use requests from LLM
     *
     * @param message User message
     * @return Final assistant response after tool execution
     */
    String chatWithTools(String message);

    /**
     * Chat with automatic tool calling (async)
     *
     * @param message User message
     * @param options LLM options
     * @return CompletableFuture of final response
     */
    CompletableFuture<LLMResponse> chatWithToolsAsync(String message, LLMOptions options);

    /**
     * Get or create a conversation context
     *
     * @param conversationId Conversation ID (null for new conversation)
     * @return Conversation context
     */
    ConversationContext getConversation(String conversationId);

    /**
     * Get all available functions
     *
     * @return List of all registered plugin functions
     */
    List<PluginFunction> getAvailableFunctions();

    /**
     * Get the kernel configuration
     *
     * @return Kernel configuration
     */
    KernelConfig getConfig();

    /**
     * Create a new kernel builder
     *
     * @return Kernel builder
     */
    static KernelBuilder builder() {
        return new KernelBuilder();
    }
}
