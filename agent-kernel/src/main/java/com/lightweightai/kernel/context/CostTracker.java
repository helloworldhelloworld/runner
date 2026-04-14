package com.lightweightai.kernel.context;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token 预算追踪
 *
 * 跟踪一次 Agent 执行链路中累计的 input/output token 消耗，
 * 当总消耗超过 maxBudgetTokens 时标记超预算。
 *
 * 用于：
 * - AgentLoop / ToolCallingLoop 每轮 LLM 调用后记录
 * - 超预算时触发 ContextCompactor 或优雅终止
 * - ResilientLLMProvider 重试前检查余额
 *
 * maxBudgetTokens = 0 表示无限预算（不检查）。
 */
public class CostTracker {

    private final int maxBudgetTokens;
    private final AtomicInteger consumedInput = new AtomicInteger(0);
    private final AtomicInteger consumedOutput = new AtomicInteger(0);

    public CostTracker(int maxBudgetTokens) {
        this.maxBudgetTokens = maxBudgetTokens;
    }

    /**
     * 记录一次 LLM 调用的 token 消耗
     */
    public void record(int inputTokens, int outputTokens) {
        consumedInput.addAndGet(inputTokens);
        consumedOutput.addAndGet(outputTokens);
    }

    /**
     * 是否超预算。maxBudgetTokens = 0 时永远返回 false。
     */
    public boolean isOverBudget() {
        if (maxBudgetTokens <= 0) return false;
        return getTotalConsumed() > maxBudgetTokens;
    }

    /**
     * 剩余可用 token 数。可为负数（已超预算）。
     */
    public int remainingTokens() {
        if (maxBudgetTokens <= 0) return Integer.MAX_VALUE;
        return maxBudgetTokens - getTotalConsumed();
    }

    public int getConsumedInputTokens() { return consumedInput.get(); }
    public int getConsumedOutputTokens() { return consumedOutput.get(); }
    public int getTotalConsumed() { return consumedInput.get() + consumedOutput.get(); }
    public int getMaxBudgetTokens() { return maxBudgetTokens; }
}
