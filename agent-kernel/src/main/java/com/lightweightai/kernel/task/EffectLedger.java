package com.lightweightai.kernel.task;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** In-process idempotency ledger; callers must persist the outcome before acknowledging a task step. */
public final class EffectLedger {
    private final Map<String, Object> outcomes = new ConcurrentHashMap<>();

    public <T> T executeOnce(String idempotencyKey, Supplier<T> effect) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(effect, "effect");
        @SuppressWarnings("unchecked") T existing = (T) outcomes.get(idempotencyKey);
        if (existing != null) return existing;
        T result = effect.get();
        Object raced = outcomes.putIfAbsent(idempotencyKey, result);
        @SuppressWarnings("unchecked") T winner = raced == null ? result : (T) raced;
        return winner;
    }
}
