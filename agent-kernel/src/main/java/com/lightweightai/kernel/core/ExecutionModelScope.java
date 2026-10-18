package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ExecutionScopedLLMProvider;
import com.lightweightai.kernel.llm.LLMProvider;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** One execution family's model selections, keyed by configuration source identity. */
public final class ExecutionModelScope {
    private final Map<LLMProvider, LLMProvider> bindings = new IdentityHashMap<>();

    /** Concurrent children using the same source must receive the same selection. */
    public synchronized LLMProvider resolve(LLMProvider source) {
        Objects.requireNonNull(source, "provider required");
        return bindings.computeIfAbsent(source, ExecutionScopedLLMProvider::bind);
    }
}
