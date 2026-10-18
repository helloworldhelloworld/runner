package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.core.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 弹性 LLM Provider 装饰器
 *
 * 在真实 Provider 外层包裹：
 * 1. 自动重试（指数退避，可配置次数）
 * 2. 运行时健康状态跟踪
 * 3. 友好的错误信息（不再裸抛 RuntimeException）
 *
 * 注意：不做 mock fallback —— 用户发消息得到假回复比报错更糟糕。
 * 应该让前端明确知道"模型暂时不可用"，而不是给一个 mock 回复误导用户。
 */
public class ResilientLLMProvider implements ExecutionScopedLLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(ResilientLLMProvider.class);

    private final LLMProvider delegate;
    private final int maxRetries;
    private final long initialBackoffMs;

    // 健康状态
    private final AtomicReference<HealthStatus> healthStatus;
    private final AtomicLong lastSuccessTime;
    private final AtomicLong lastFailureTime;
    private final AtomicInteger consecutiveFailures;
    private final AtomicInteger totalRequests;
    private final AtomicInteger totalFailures;
    private final AtomicReference<String> lastError;

    public enum HealthStatus {
        UNKNOWN,    // 尚未发起过请求
        HEALTHY,    // 最近一次请求成功
        DEGRADED,   // 有间歇性失败（连续失败 < 3）
        UNHEALTHY   // 连续失败 >= 3
    }

    public ResilientLLMProvider(LLMProvider delegate) {
        this(delegate, 2, 1000);
    }

    public ResilientLLMProvider(LLMProvider delegate, int maxRetries, long initialBackoffMs) {
        this(delegate, maxRetries, initialBackoffMs, null);
    }

    private ResilientLLMProvider(LLMProvider delegate, int maxRetries, long initialBackoffMs,
                                 ResilientLLMProvider sharedHealth) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate LLM provider cannot be null");
        }
        this.healthStatus = sharedHealth == null ? new AtomicReference<>(HealthStatus.UNKNOWN) : sharedHealth.healthStatus;
        this.lastSuccessTime = sharedHealth == null ? new AtomicLong(0) : sharedHealth.lastSuccessTime;
        this.lastFailureTime = sharedHealth == null ? new AtomicLong(0) : sharedHealth.lastFailureTime;
        this.consecutiveFailures = sharedHealth == null ? new AtomicInteger(0) : sharedHealth.consecutiveFailures;
        this.totalRequests = sharedHealth == null ? new AtomicInteger(0) : sharedHealth.totalRequests;
        this.totalFailures = sharedHealth == null ? new AtomicInteger(0) : sharedHealth.totalFailures;
        this.lastError = sharedHealth == null ? new AtomicReference<>("") : sharedHealth.lastError;
        this.delegate = delegate;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        logger.info("ResilientLLMProvider wrapping [{}] with maxRetries={}, initialBackoffMs={}",
            delegate.getProviderName(), maxRetries, initialBackoffMs);
    }

    @Override
    public LLMProvider bindToExecution() {
        LLMProvider selected = ExecutionScopedLLMProvider.bind(delegate);
        // Production's Dynamic -> Resilient -> fixed provider retains the same health state.
        return selected == delegate ? this : new ResilientLLMProvider(selected, maxRetries, initialBackoffMs, this);
    }

    /** Keep retry and health behavior while exposing each actual delegate attempt to the root ledger. */
    public LLMProvider withUsageAccounting(com.lightweightai.kernel.context.ExecutionUsageScope scope,
            com.lightweightai.kernel.core.CancellationToken token) {
        return new ResilientLLMProvider(UsageTrackingLLMProvider.bind(delegate, scope, token),
                maxRetries, initialBackoffMs, this);
    }

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        return executeWithRetry(() -> delegate.complete(messages, options), "complete");
    }

    @Override
    public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
        return CompletableFuture.supplyAsync(() -> complete(messages, options));
    }

    @Override
    public CompletableFuture<LLMResponse> completeStream(
            List<ConversationMessage> messages,
            LLMOptions options,
            StreamEventHandler handler) {
        AtomicReference<LLMResponse> response = new AtomicReference<>();
        handler.onStart();
        return completeStreamReactive(messages, options).doOnNext(event -> {
            switch (event.getType()) {
                case TEXT_DELTA -> handler.onTextDelta(event.getTextDelta());
                case TOOL_CALL_START -> handler.onToolCallDelta(event.getToolCall());
                case LLM_COMPLETE -> response.set(event.getResponse());
                default -> { }
            }
        }).then(reactor.core.publisher.Mono.fromSupplier(() ->
                java.util.Objects.requireNonNull(response.get(), "Missing model completion")))
                .doOnSuccess(handler::onComplete).doOnError(handler::onError).toFuture();
    }

    @Override
    public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
        return Flux.defer(() -> {
            java.util.concurrent.atomic.AtomicBoolean delivered = new java.util.concurrent.atomic.AtomicBoolean();
            return Flux.defer(() -> {
                totalRequests.incrementAndGet();
                return delegate.completeStreamReactive(messages, options);
            }).doOnNext(event -> {
                if (event.getType() == StreamEvent.EventType.TEXT_DELTA
                        || event.getType() == StreamEvent.EventType.TOOL_CALL_START
                        || event.getType() == StreamEvent.EventType.LLM_COMPLETE) delivered.set(true);
            }).doOnComplete(this::recordSuccess)
              .retryWhen(reactor.util.retry.Retry.backoff(maxRetries, java.time.Duration.ofMillis(initialBackoffMs))
                    .maxBackoff(java.time.Duration.ofSeconds(10))
                    .filter(error -> !delivered.get() && isRetryable(error)))
              .doOnError(error -> {
                  if (error instanceof com.lightweightai.kernel.context.BudgetStoppedException) totalRequests.decrementAndGet();
                  else if (!(error instanceof java.util.concurrent.CancellationException)) recordFailure(error);
              });
        });
    }

    @Override
    public ModelCapability getModelCapability() {
        return delegate.getModelCapability();
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    // ==================== Health Status ====================

    public HealthStatus getHealthStatus() {
        return healthStatus.get();
    }

    public HealthInfo getHealthInfo() {
        return new HealthInfo(
            healthStatus.get(),
            delegate.getProviderName(),
            totalRequests.get(),
            totalFailures.get(),
            consecutiveFailures.get(),
            lastSuccessTime.get(),
            lastFailureTime.get(),
            lastError.get()
        );
    }

    /**
     * 获取被装饰的原始 Provider
     */
    public LLMProvider getDelegate() {
        return delegate;
    }

    // ==================== Internal ====================

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private <T> T executeWithRetry(ThrowingSupplier<T> action, String methodName) {
        int attempt = 0;
        while (true) {
            try {
                totalRequests.incrementAndGet();
                T result = action.get();
                recordSuccess();
                return result;
            } catch (Exception e) {
                Throwable cause = unwrapException(e);
                if (cause instanceof com.lightweightai.kernel.context.BudgetStoppedException stopped) {
                    totalRequests.decrementAndGet();
                    throw stopped;
                }
                if (cause instanceof java.util.concurrent.CancellationException cancelled) throw cancelled;
                attempt++;
                if (attempt > maxRetries || !isRetryable(cause)) {
                    recordFailure(cause);
                    throw wrapException(cause, attempt);
                }
                logger.warn("[{}] {} attempt {}/{} failed: {}. Retrying in {}ms...",
                    delegate.getProviderName(), methodName, attempt, maxRetries + 1,
                    cause.getMessage(), backoffMs(attempt));
                sleep(backoffMs(attempt));
            }
        }
    }

    /**
     * 判断异常是否可重试
     *
     * 可重试：网络超时、5xx、429（限流）
     * 不可重试：4xx（除429）、认证失败、参数错误
     */
    private boolean isRetryable(Throwable e) {
        if (e instanceof com.lightweightai.kernel.context.BudgetStoppedException
                || e instanceof java.util.concurrent.CancellationException) return false;
        String msg = e.getMessage();
        if (msg == null) return true;

        // 明确不可重试的 HTTP 状态码
        if (msg.contains(": 400") || msg.contains(": 401") || msg.contains(": 403") || msg.contains(": 404")) {
            return false;
        }
        // 429 限流 → 可重试
        if (msg.contains(": 429")) {
            return true;
        }
        // 5xx → 可重试
        if (msg.contains(": 500") || msg.contains(": 502") || msg.contains(": 503") || msg.contains(": 504")) {
            return true;
        }
        // 网络异常 → 可重试
        if (e instanceof java.net.SocketTimeoutException || e instanceof java.net.ConnectException
            || e instanceof java.io.IOException) {
            return true;
        }
        // 默认重试
        return true;
    }

    private long backoffMs(int attempt) {
        return initialBackoffMs * (1L << (attempt - 1)); // 1s, 2s, 4s...
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordSuccess() {
        lastSuccessTime.set(System.currentTimeMillis());
        consecutiveFailures.set(0);
        healthStatus.set(HealthStatus.HEALTHY);
    }

    private void recordFailure(Throwable e) {
        lastFailureTime.set(System.currentTimeMillis());
        totalFailures.incrementAndGet();
        int failures = consecutiveFailures.incrementAndGet();
        lastError.set(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());

        if (failures >= 3) {
            healthStatus.set(HealthStatus.UNHEALTHY);
        } else {
            healthStatus.set(HealthStatus.DEGRADED);
        }
    }

    private Throwable unwrapException(Throwable e) {
        // Unwrap CompletionException / ExecutionException
        if ((e instanceof java.util.concurrent.CompletionException
            || e instanceof java.util.concurrent.ExecutionException)
            && e.getCause() != null) {
            return e.getCause();
        }
        return e;
    }

    private LLMProviderException wrapException(Throwable cause, int attempts) {
        String msg = String.format(
            "[%s] LLM 调用失败（已重试 %d 次）: %s",
            delegate.getProviderName(), attempts - 1, cause.getMessage());
        return new LLMProviderException(msg, cause, delegate.getProviderName(), attempts);
    }

    // ==================== HealthInfo ====================

    public record HealthInfo(
        HealthStatus status,
        String providerName,
        int totalRequests,
        int totalFailures,
        int consecutiveFailures,
        long lastSuccessTime,
        long lastFailureTime,
        String lastError
    ) {}
}
