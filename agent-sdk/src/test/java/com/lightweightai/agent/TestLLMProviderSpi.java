package com.lightweightai.agent;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test-only LLM provider SPI registered under the name "test".
 *
 * Used by DefaultAgentChatWiringTest to exercise the full
 * DefaultAgent.chat() → ToolCallingLoop → LLMProvider path without HTTP calls.
 * Captures all messages and options for assertion.
 */
public class TestLLMProviderSpi implements LLMProviderSpi {

    static final AtomicReference<TestProvider> LAST_INSTANCE = new AtomicReference<>();

    @Override
    public String[] supportedNames() {
        return new String[]{"test"};
    }

    @Override
    public LLMProvider create(String apiKey, String model) {
        TestProvider p = new TestProvider(apiKey, model);
        LAST_INSTANCE.set(p);
        return p;
    }

    public static class TestProvider implements LLMProvider {
        final String apiKey;
        final String model;
        final CopyOnWriteArrayList<LLMOptions> capturedOptions = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<List<ConversationMessage>> capturedMessages = new CopyOnWriteArrayList<>();
        String responseText = "test response";

        TestProvider(String apiKey, String model) {
            this.apiKey = apiKey;
            this.model = model;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            capturedMessages.add(messages);
            capturedOptions.add(options);
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(responseText)
                            .build())
                    .stopReason("end_turn")
                    .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages,
                                                              LLMOptions options, StreamEventHandler handler) {
            LLMResponse r = complete(messages, options);
            handler.onComplete(r);
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            return Flux.just(StreamEvent.llmComplete(complete(messages, options)));
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "test"; }
    }
}
