package com.lightweightai.kernel.task;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 重试策略 (Harness Engineering - Entropy Management)
 */
public class RetryPolicy {

    private final int maxRetries;
    private final Duration initialBackoff;
    private final double backoffMultiplier;
    private final Set<Class<? extends Throwable>> retryableExceptions;

    private RetryPolicy(int maxRetries, Duration initialBackoff, double backoffMultiplier,
                        Set<Class<? extends Throwable>> retryableExceptions) {
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
        this.backoffMultiplier = backoffMultiplier;
        this.retryableExceptions = Collections.unmodifiableSet(retryableExceptions);
    }

    public static RetryPolicy none() {
        return new RetryPolicy(0, Duration.ZERO, 1.0, Collections.emptySet());
    }

    public static RetryPolicy fixed(int maxRetries, Duration backoff) {
        return new RetryPolicy(maxRetries, backoff, 1.0, Collections.emptySet());
    }

    public static RetryPolicy exponential(int maxRetries, Duration initialBackoff) {
        return new RetryPolicy(maxRetries, initialBackoff, 2.0, Collections.emptySet());
    }

    public static RetryPolicy exponential(int maxRetries, Duration initialBackoff,
                                           Set<Class<? extends Throwable>> retryableExceptions) {
        return new RetryPolicy(maxRetries, initialBackoff, 2.0, new HashSet<>(retryableExceptions));
    }

    /**
     * 计算第 attempt 次重试的等待时间
     */
    public Duration getBackoffDuration(int attempt) {
        if (attempt <= 0) return Duration.ZERO;
        long millis = (long) (initialBackoff.toMillis() * Math.pow(backoffMultiplier, attempt - 1));
        return Duration.ofMillis(Math.min(millis, 60_000)); // 最大 60 秒
    }

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryable(Throwable t) {
        if (retryableExceptions.isEmpty()) return true; // 空集 = 所有异常都可重试
        for (Class<? extends Throwable> cls : retryableExceptions) {
            if (cls.isInstance(t)) return true;
        }
        return false;
    }

    public int getMaxRetries() { return maxRetries; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
}
