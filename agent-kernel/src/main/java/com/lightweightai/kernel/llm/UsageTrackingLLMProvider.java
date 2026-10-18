package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.context.ExecutionUsageScope;
import com.lightweightai.kernel.context.ExecutionUsageScope.Status;
import com.lightweightai.kernel.core.CancellationToken;
import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Accounts actual attempts, inside retry decorators, without storing request content in the ledger. */
public final class UsageTrackingLLMProvider implements LLMProvider {
    private final LLMProvider delegate;
    private final ExecutionUsageScope scope;
    private final CancellationToken token;
    private UsageTrackingLLMProvider(LLMProvider delegate, ExecutionUsageScope scope, CancellationToken token) {
        this.delegate = delegate; this.scope = scope; this.token = token;
    }
    public static LLMProvider bind(LLMProvider provider, ExecutionUsageScope scope, CancellationToken token) {
        Objects.requireNonNull(scope);
        return provider instanceof ResilientLLMProvider resilient ? resilient.withUsageAccounting(scope, token)
                : new UsageTrackingLLMProvider(provider, scope, token);
    }
    private ExecutionUsageScope.Attempt begin(List<ConversationMessage> messages, LLMOptions options) {
        checkCancellation();
        var capability = getModelCapability();
        return scope.reserve(getProviderName(), capability == null ? null : capability.getModelId(), messages, options);
    }
    private void checkCancellation() {
        if (token != null && token.isCancelled()) throw new CancellationException("Execution cancelled");
    }
    private LLMOptions options(LLMOptions base, ExecutionUsageScope.Attempt attempt) {
        return LLMOptions.builder().from(base).maxTokens(attempt.maxTokens()).build().snapshot();
    }
    @Override public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        var attempt = begin(messages, options);
        var registration = token == null ? null : token.onCancel(() -> attempt.finish(null, Status.CANCELLED));
        try {
            checkCancellation();
            var result = delegate.complete(messages, options(options, attempt));
            attempt.finish(result, Status.SUCCEEDED);
            return result;
        } catch (RuntimeException | Error failure) {
            attempt.finish(null, failure instanceof CancellationException ? Status.CANCELLED : Status.FAILED);
            throw failure;
        } finally { if (registration != null) registration.close(); }
    }
    @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
        final ExecutionUsageScope.Attempt attempt;
        try { attempt = begin(messages, options); }
        catch (RuntimeException e) { return CompletableFuture.failedFuture(e); }
        final CompletableFuture<LLMResponse> upstream;
        try { upstream = delegate.completeAsync(messages, options(options, attempt)); }
        catch (RuntimeException e) { attempt.finish(null, Status.FAILED); return CompletableFuture.failedFuture(e); }
        var result = new CompletableFuture<LLMResponse>();
        var registration = token == null ? null : token.onCancel(() -> {
            attempt.finish(null, Status.CANCELLED); result.cancel(true); upstream.cancel(true);
        });
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) { attempt.finish(null, Status.CANCELLED); upstream.cancel(true); }
            if (registration != null) registration.close();
        });
        upstream.whenComplete((value, error) -> {
            if (error == null) { attempt.finish(value, Status.SUCCEEDED); result.complete(value); }
            else {
                attempt.finish(null, upstream.isCancelled() ? Status.CANCELLED : Status.FAILED);
                result.completeExceptionally(error);
            }
        });
        return result;
    }
    @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
        return Flux.defer(() -> {
            var attempt = begin(messages, options);
            var response = new AtomicReference<LLMResponse>();
            Flux<StreamEvent> stream = Flux.defer(() -> delegate.completeStreamReactive(messages, options(options, attempt)))
                    .doOnNext(event -> { if (event.getType() == StreamEvent.EventType.LLM_COMPLETE) response.set(event.getResponse()); })
                    .doOnComplete(() -> attempt.finish(response.get(), Status.SUCCEEDED))
                    .doOnError(error -> attempt.finish(response.get(), Status.FAILED))
                    .doOnCancel(() -> attempt.finish(null, Status.CANCELLED));
            if (token == null) return stream;
            Mono<Void> cancellation = Mono.create(sink -> {
                var registration = token.onCancel(() -> sink.error(new CancellationException("Execution cancelled")));
                sink.onDispose(registration::close);
            });
            return stream.takeUntilOther(cancellation);
        });
    }
    @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages,
            LLMOptions options, StreamEventHandler handler) {
        var response = new AtomicReference<LLMResponse>();
        handler.onStart();
        return completeStreamReactive(messages, options).doOnNext(event -> {
            switch (event.getType()) {
                case TEXT_DELTA -> handler.onTextDelta(event.getTextDelta());
                case TOOL_CALL_START -> handler.onToolCallDelta(event.getToolCall());
                case LLM_COMPLETE -> response.set(event.getResponse());
                default -> { }
            }
        }).then(Mono.fromSupplier(() -> Objects.requireNonNull(response.get(), "Missing model completion")))
                .doOnSuccess(handler::onComplete).doOnError(handler::onError).toFuture();
    }
    @Override public ModelCapability getModelCapability() { return delegate.getModelCapability(); }
    @Override public String getProviderName() { return delegate.getProviderName(); }
}
