package com.lightweightai.web.postprocess;

import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 风控处理器 - 按 token 累积触发风控检查
 *
 * 检查时机：
 * - 每 N 个 token（可配置）
 * - 流结束时
 *
 * 检查结果：
 * - safe: 正常透传
 * - warning: 注入警告事件，继续流
 * - blocked: 注入错误事件，终止流
 */
public class RiskControlProcessor implements StreamPostProcessor {

    private final RiskChecker checker;
    private final int checkIntervalTokens;

    public RiskControlProcessor(RiskChecker checker, int checkIntervalTokens) {
        this.checker = checker;
        this.checkIntervalTokens = checkIntervalTokens;
    }

    @Override
    public String getName() {
        return "risk-control";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
        StringBuilder accumulated = new StringBuilder();
        AtomicInteger tokenCount = new AtomicInteger(0);

        return source.concatMap(event -> {
            if (event.getType() != StreamEvent.EventType.TEXT_DELTA) {
                return Flux.just(event);
            }

            accumulated.append(event.getTextDelta());
            int tokens = tokenCount.addAndGet(estimateTokens(event.getTextDelta()));

            if (tokens >= checkIntervalTokens) {
                tokenCount.set(0);
                RiskResult risk = checker.check(accumulated.toString());

                if (risk.isBlocked()) {
                    return Flux.just(
                            StreamEvent.postProcessData("risk_control", Map.of(
                                    "action", "blocked",
                                    "reason", risk.getReason()
                            )),
                            StreamEvent.error(new RiskControlException(risk.getReason()))
                    );
                }

                if (risk.hasWarning()) {
                    return Flux.just(
                            event,
                            StreamEvent.postProcessData("risk_control", Map.of(
                                    "action", "warning",
                                    "reason", risk.getReason()
                            ))
                    );
                }
            }

            return Flux.just(event);
        });
    }

    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 粗略估算：英文约 4 字符/token，中文约 1.5 字符/token
        // 简化为字符数 / 3
        return Math.max(1, text.length() / 3);
    }

    /**
     * 风控检查接口
     */
    @FunctionalInterface
    public interface RiskChecker {
        RiskResult check(String accumulatedText);
    }

    /**
     * 风控检查结果
     */
    public static class RiskResult {
        private final Action action;
        private final String reason;

        public enum Action { SAFE, WARNING, BLOCKED }

        private RiskResult(Action action, String reason) {
            this.action = action;
            this.reason = reason;
        }

        public static RiskResult safe() {
            return new RiskResult(Action.SAFE, null);
        }

        public static RiskResult warning(String reason) {
            return new RiskResult(Action.WARNING, reason);
        }

        public static RiskResult blocked(String reason) {
            return new RiskResult(Action.BLOCKED, reason);
        }

        public boolean isBlocked() { return action == Action.BLOCKED; }
        public boolean hasWarning() { return action == Action.WARNING; }
        public boolean isSafe() { return action == Action.SAFE; }
        public String getReason() { return reason; }
    }

    /**
     * 风控异常
     */
    public static class RiskControlException extends RuntimeException {
        public RiskControlException(String message) {
            super(message);
        }
    }
}
