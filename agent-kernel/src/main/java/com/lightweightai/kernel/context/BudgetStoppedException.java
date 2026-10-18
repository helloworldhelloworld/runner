package com.lightweightai.kernel.context;

import java.util.Map;

/** Admission refusal before an actual model attempt. Never retry this within the same budget. */
public final class BudgetStoppedException extends RuntimeException {
    private final Map<String,Object> usage;
    public BudgetStoppedException(Map<String,Object> usage) {
        super("budget_exhausted");
        this.usage = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(usage));
    }
    public Map<String,Object> getUsage() { return usage; }
}
