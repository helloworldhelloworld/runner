package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Abstract interface for LLM providers (Claude, OpenAI, etc.)
 * Supports both synchronous and asynchronous/streaming modes.
 */
public interface LLMProvider {

    /**
     * Synchronous completion - blocks until response is complete
     *
     * @param messages Conversation messages
     * @param options  Request options
     * @return LLM response
     */
    LLMResponse complete(List<ConversationMessage> messages, LLMOptions options);

    /**
     * Asynchronous completion - non-blocking
     *
     * @param messages Conversation messages
     * @param options  Request options
     * @return CompletableFuture of LLM response
     */
    CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options);

    /**
     * Streaming completion - event-driven streaming response
     *
     * @param messages Conversation messages
     * @param options  Request options
     * @param handler  Event handler for streaming events
     * @return CompletableFuture that completes when streaming finishes
     */
    CompletableFuture<LLMResponse> completeStream(
            List<ConversationMessage> messages,
            LLMOptions options,
            StreamEventHandler handler
    );

    /**
     * Get the model capability descriptor
     *
     * @return Model capabilities
     */
    ModelCapability getModelCapability();

    /**
     * Get provider name
     *
     * @return Provider name (e.g., "claude", "openai")
     */
    String getProviderName();

    /**
     * Reactive streaming completion - returns a Flux of StreamEvents
     *
     * Default implementation bridges the existing callback-based completeStream().
     * Providers can override this to return a native Flux.
     *
     * @param messages Conversation messages
     * @param options  Request options
     * @return Flux of stream events
     */
    default Flux<StreamEvent> completeStreamReactive(
            List<ConversationMessage> messages, LLMOptions options) {
        return Flux.create(sink -> {
            System.out.println("[FLUX-BRIDGE] Flux.create lambda executing, calling completeStream...");
            completeStream(messages, options, new StreamEventHandler() {
                @Override
                public void onTextDelta(String delta) {
                    System.out.println("[FLUX-BRIDGE] onTextDelta len=" + (delta != null ? delta.length() : 0));
                    sink.next(StreamEvent.textDelta(delta));
                }

                @Override
                public void onToolCallDelta(ToolCall toolCall) {
                    System.out.println("[FLUX-BRIDGE] onToolCallDelta tool=" + toolCall.getName());
                    sink.next(StreamEvent.toolCallStart(toolCall));
                }

                @Override
                public void onComplete(LLMResponse response) {
                    System.out.println("[FLUX-BRIDGE] onComplete hasToolCalls=" +
                        (response != null && response.hasToolCalls()));
                    sink.next(StreamEvent.llmComplete(response));
                    sink.complete();
                    System.out.println("[FLUX-BRIDGE] sink.complete() called");
                }

                @Override
                public void onError(Throwable error) {
                    System.out.println("[FLUX-BRIDGE] onError: " + error.getMessage());
                    sink.error(error);
                }
            });
            System.out.println("[FLUX-BRIDGE] completeStream returned (async task started)");
        });
    }

    /**
     * Event handler for streaming responses
     */
    interface StreamEventHandler {

        /**
         * Called when streaming starts
         */
        default void onStart() {}

        /**
         * Called when a text delta is received
         *
         * @param delta Text chunk
         */
        default void onTextDelta(String delta) {}

        /**
         * Called when a tool call is being streamed
         *
         * @param toolCall Tool call information
         */
        default void onToolCallDelta(ToolCall toolCall) {}

        /**
         * Called when streaming completes successfully
         *
         * @param response Final complete response
         */
        default void onComplete(LLMResponse response) {}

        /**
         * Called when an error occurs
         *
         * @param error The error
         */
        default void onError(Throwable error) {}
    }
}
