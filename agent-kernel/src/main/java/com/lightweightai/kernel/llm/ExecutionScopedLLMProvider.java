package com.lightweightai.kernel.llm;

import java.util.Objects;

/** Optional SPI for providers whose configuration can change between executions. */
public interface ExecutionScopedLLMProvider extends LLMProvider {
    /**
     * Select a stable provider for an execution, retaining transport/retry behavior.
     * The returned instance must not follow later configuration changes.
     * Binding must be fast and must not perform remote I/O.
     */
    LLMProvider bindToExecution();

    /** Ordinary providers retain their existing instance and behavior. */
    static LLMProvider bind(LLMProvider source) {
        Objects.requireNonNull(source, "provider required");
        return source instanceof ExecutionScopedLLMProvider scoped
                ? Objects.requireNonNull(scoped.bindToExecution(), "bound provider required") : source;
    }
}
