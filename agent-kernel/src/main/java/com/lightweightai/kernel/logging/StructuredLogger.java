package com.lightweightai.kernel.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * 结构化日志工具 (Harness Engineering - Context Engineering)。
 *
 * 包装 SLF4J Logger，自动从 MDC 读取 trace 上下文。
 * 提供领域便捷方法，确保关键操作有统一的日志格式。
 *
 * MDC 中自动包含: traceId, spanId, sessionId, requestId
 * （由 MdcTraceHook 从 Reactor Context 同步）
 */
public class StructuredLogger {

    private final Logger delegate;

    public StructuredLogger(Class<?> clazz) {
        this.delegate = LoggerFactory.getLogger(clazz);
    }

    public StructuredLogger(String name) {
        this.delegate = LoggerFactory.getLogger(name);
    }

    public static StructuredLogger getLogger(Class<?> clazz) {
        return new StructuredLogger(clazz);
    }

    // ==================== 通用方法 ====================

    public void event(String eventName, Map<String, Object> fields) {
        if (!delegate.isInfoEnabled()) return;
        delegate.info("[{}] {}", eventName, formatFields(fields));
    }

    public void warn(String eventName, Map<String, Object> fields) {
        delegate.warn("[{}] {}", eventName, formatFields(fields));
    }

    public void error(String eventName, Throwable t, Map<String, Object> fields) {
        delegate.error("[{}] {}", eventName, formatFields(fields), t);
    }

    // ==================== 领域便捷方法 ====================

    /**
     * 记录 LLM 调用
     */
    public void llmCall(String provider, String model, int inputTokens, int outputTokens, long latencyMs) {
        if (!delegate.isInfoEnabled()) return;
        delegate.info("[llm.call] provider={} model={} inputTokens={} outputTokens={} latencyMs={}",
            provider, model, inputTokens, outputTokens, latencyMs);
    }

    /**
     * 记录工具执行
     */
    public void toolExec(String toolName, long latencyMs, boolean success) {
        if (!delegate.isInfoEnabled()) return;
        delegate.info("[tool.exec] tool={} latencyMs={} success={}", toolName, latencyMs, success);
    }

    /**
     * 记录任务提交
     */
    public void taskSubmit(String taskName, String taskId) {
        if (!delegate.isInfoEnabled()) return;
        delegate.info("[task.submit] task={} taskId={}", taskName, taskId);
    }

    /**
     * 记录任务完成
     */
    public void taskComplete(String taskName, String taskId, long durationMs, boolean success) {
        if (!delegate.isInfoEnabled()) return;
        delegate.info("[task.complete] task={} taskId={} durationMs={} success={}",
            taskName, taskId, durationMs, success);
    }

    /**
     * 记录约束检查
     */
    public void constraintCheck(String constraintName, String level, String message) {
        if ("BLOCK".equals(level)) {
            delegate.warn("[constraint.check] constraint={} level={} message={}",
                constraintName, level, message);
        } else {
            delegate.info("[constraint.check] constraint={} level={}", constraintName, level);
        }
    }

    // ==================== 内部辅助 ====================

    private String formatFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        fields.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(" ");
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }

    /**
     * 获取当前 MDC 中的 traceId（便于手动使用）
     */
    public static String currentTraceId() {
        return MDC.get(MdcTraceHook.MDC_TRACE_ID);
    }
}
