package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.PromptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 成本追踪 Observer (Harness Engineering - Architectural Constraints)。
 *
 * 累计 token 用量和估算成本，支持按 session 查询。
 * 可配合 token-budget 约束实现成本控制。
 */
public class CostTrackingObserver implements AgentObserver {

    private static final Logger logger = LoggerFactory.getLogger(CostTrackingObserver.class);

    private final AtomicInteger totalInputTokens = new AtomicInteger(0);
    private final AtomicInteger totalOutputTokens = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final ConcurrentHashMap<String, SessionCost> sessionCosts = new ConcurrentHashMap<>();

    // 当前请求的 sessionId（由 onAgentStart 设置）
    private volatile String currentSessionId;

    @Override
    public void onAgentStart(String input, String sessionId) {
        currentSessionId = sessionId;
        totalRequests.incrementAndGet();
    }

    @Override
    public void onLLMResponse(LLMResponse response) {
        if (response != null && response.getUsage() != null) {
            int inTokens = response.getUsage().getInputTokens();
            int outTokens = response.getUsage().getOutputTokens();

            totalInputTokens.addAndGet(inTokens);
            totalOutputTokens.addAndGet(outTokens);

            String sessionId = currentSessionId;
            if (sessionId != null) {
                sessionCosts.computeIfAbsent(sessionId, k -> new SessionCost())
                    .add(inTokens, outTokens);
            }
        }
    }

    // ==================== 查询 API ====================

    /**
     * 获取全局累计 token 用量
     */
    public CostSummary getGlobalCost() {
        return new CostSummary(
            totalInputTokens.get(),
            totalOutputTokens.get(),
            totalRequests.get()
        );
    }

    /**
     * 获取指定 session 的 token 用量
     */
    public CostSummary getSessionCost(String sessionId) {
        SessionCost cost = sessionCosts.get(sessionId);
        if (cost == null) {
            return new CostSummary(0, 0, 0);
        }
        return new CostSummary(cost.inputTokens.get(), cost.outputTokens.get(), cost.requests.get());
    }

    /**
     * 重置统计
     */
    public void reset() {
        totalInputTokens.set(0);
        totalOutputTokens.set(0);
        totalRequests.set(0);
        sessionCosts.clear();
    }

    // ==================== 内部类 ====================

    private static class SessionCost {
        final AtomicInteger inputTokens = new AtomicInteger(0);
        final AtomicInteger outputTokens = new AtomicInteger(0);
        final AtomicLong requests = new AtomicLong(0);

        void add(int inTokens, int outTokens) {
            inputTokens.addAndGet(inTokens);
            outputTokens.addAndGet(outTokens);
            requests.incrementAndGet();
        }
    }

    /**
     * 成本摘要
     */
    public record CostSummary(int inputTokens, int outputTokens, long requests) {
        public int totalTokens() { return inputTokens + outputTokens; }
    }
}
