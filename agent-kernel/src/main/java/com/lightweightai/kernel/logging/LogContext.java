package com.lightweightai.kernel.logging;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志上下文工具类。
 *
 * 提供 MDC 的便捷操作，支持 try-with-resources 自动清理。
 *
 * 使用方式:
 * <pre>
 *   try (var ctx = LogContext.withFields(Map.of("toolName", "weather"))) {
 *       // MDC 中自动包含 toolName=weather
 *       logger.info("executing tool");
 *   }
 *   // toolName 自动从 MDC 移除
 * </pre>
 */
public final class LogContext implements AutoCloseable {

    private final Map<String, String> previousValues;
    private final Map<String, String> keys;

    private LogContext(Map<String, String> fields) {
        this.keys = new HashMap<>(fields);
        this.previousValues = new HashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            previousValues.put(entry.getKey(), MDC.get(entry.getKey()));
            MDC.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 设置多个 MDC 字段，返回可 close 的上下文
     */
    public static LogContext withFields(Map<String, String> fields) {
        return new LogContext(fields);
    }

    /**
     * 设置单个 MDC 字段
     */
    public static LogContext withField(String key, String value) {
        return new LogContext(Map.of(key, value));
    }

    @Override
    public void close() {
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            String previousValue = previousValues.get(entry.getKey());
            if (previousValue != null) {
                MDC.put(entry.getKey(), previousValue);
            } else {
                MDC.remove(entry.getKey());
            }
        }
    }
}
