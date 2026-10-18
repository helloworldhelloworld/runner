package com.lightweightai.web.config;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.ExecutionScopedLLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A delegating LLMProvider that supports hot-swapping the underlying provider at runtime.
 * Used to allow model configuration changes without restarting the application.
 */
public class DynamicLLMProvider implements ExecutionScopedLLMProvider {

    private final AtomicReference<LLMProvider> delegate;

    public DynamicLLMProvider(LLMProvider initial) {
        this.delegate = new AtomicReference<>(Objects.requireNonNull(initial, "initial provider required"));
    }

    public void setDelegate(LLMProvider newProvider) {
        this.delegate.set(Objects.requireNonNull(newProvider, "provider required"));
    }

    @Override
    public LLMProvider bindToExecution() {
        return ExecutionScopedLLMProvider.bind(delegate.get());
    }

    public LLMProvider getDelegate() {
        return delegate.get();
    }

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        return delegate.get().complete(messages, options);
    }

    @Override
    public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
        return delegate.get().completeAsync(messages, options);
    }

    @Override
    public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
        return delegate.get().completeStream(messages, options, handler);
    }

    @Override
    public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
        return delegate.get().completeStreamReactive(messages, options);
    }

    @Override
    public ModelCapability getModelCapability() {
        return delegate.get().getModelCapability();
    }

    @Override
    public String getProviderName() {
        return delegate.get().getProviderName();
    }
}
