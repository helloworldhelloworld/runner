package com.lightweightai.kernel.trace;

import java.util.HashMap;
import java.util.Map;

/**
 * W3C Trace Context 传播器。
 *
 * 实现 traceparent / tracestate 注入与提取，用于跨服务追踪：
 * - Gateway: 从 HTTP headers extract → 接续外部 trace
 * - OkHttp Interceptor: inject → LLM API 请求
 * - McpToolWrapper: inject → MCP HTTP 请求
 *
 * traceparent 格式: 00-{traceId32hex}-{spanId16hex}-{flags2hex}
 *
 * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
 */
public final class TracePropagator {

    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";
    public static final String BAGGAGE_HEADER = "baggage";

    private static final String VERSION = "00";
    private static final String FLAGS_SAMPLED = "01";
    private static final String FLAGS_NOT_SAMPLED = "00";

    private TracePropagator() {}

    // ==================== Inject ====================

    /**
     * 将 SpanContext 注入为 HTTP headers (traceparent + baggage)
     */
    public static Map<String, String> inject(SpanContext span) {
        if (span == null) {
            return Map.of();
        }
        Map<String, String> headers = new HashMap<>();
        headers.put(TRACEPARENT_HEADER, toTraceparent(span));

        // 注入 baggage
        Map<String, String> baggage = span.getAllBaggage();
        if (!baggage.isEmpty()) {
            headers.put(BAGGAGE_HEADER, encodeBaggage(baggage));
        }
        return headers;
    }

    /**
     * 生成 traceparent 字符串
     * 格式: 00-{traceId}-{spanId}-01
     */
    public static String toTraceparent(SpanContext span) {
        return VERSION + "-"
            + padTo32Hex(span.getTraceId()) + "-"
            + padTo16Hex(span.getSpanId()) + "-"
            + FLAGS_SAMPLED;
    }

    // ==================== Extract ====================

    /**
     * 从 HTTP headers 中提取 SpanContext（作为新 span 的 parent）
     *
     * @param headers HTTP headers
     * @param spanName 新 span 的名称
     * @return 提取的 SpanContext，headers 无效则创建新 root
     */
    public static SpanContext extract(Map<String, String> headers, String spanName) {
        if (headers == null) {
            return SpanContext.createRoot(spanName, SpanKind.SERVER);
        }
        String traceparent = headers.get(TRACEPARENT_HEADER);
        if (traceparent == null) {
            // 尝试 case-insensitive
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (TRACEPARENT_HEADER.equalsIgnoreCase(entry.getKey())) {
                    traceparent = entry.getValue();
                    break;
                }
            }
        }
        if (traceparent == null || traceparent.isEmpty()) {
            return SpanContext.createRoot(spanName, SpanKind.SERVER);
        }

        SpanContext ctx = fromTraceparent(traceparent, spanName);

        // 提取 baggage
        String baggageHeader = headers.getOrDefault(BAGGAGE_HEADER, "");
        if (!baggageHeader.isEmpty()) {
            Map<String, String> baggage = decodeBaggage(baggageHeader);
            baggage.forEach(ctx::setBaggage);
        }

        return ctx;
    }

    /**
     * 解析 traceparent 字符串为 SpanContext
     */
    public static SpanContext fromTraceparent(String traceparent, String spanName) {
        if (traceparent == null || traceparent.isEmpty()) {
            return SpanContext.createRoot(spanName, SpanKind.SERVER);
        }
        String[] parts = traceparent.split("-");
        if (parts.length < 4) {
            return SpanContext.createRoot(spanName, SpanKind.SERVER);
        }
        String traceId = parts[1];
        String parentSpanId = parts[2];
        return SpanContext.createFromRemote(traceId, parentSpanId, spanName, SpanKind.SERVER);
    }

    // ==================== Baggage 编解码 ====================

    /**
     * 编码 baggage 为 W3C Baggage 格式: key1=value1,key2=value2
     */
    static String encodeBaggage(Map<String, String> baggage) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : baggage.entrySet()) {
            if (!first) sb.append(",");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    /**
     * 解码 W3C Baggage 格式
     */
    static Map<String, String> decodeBaggage(String header) {
        Map<String, String> result = new HashMap<>();
        if (header == null || header.isEmpty()) return result;
        for (String pair : header.split(",")) {
            String trimmed = pair.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0 && eq < trimmed.length() - 1) {
                String key = trimmed.substring(0, eq).trim();
                // 取 value 时忽略 properties (;xxx)
                String valueAndProps = trimmed.substring(eq + 1).trim();
                int semicolon = valueAndProps.indexOf(';');
                String value = semicolon > 0 ? valueAndProps.substring(0, semicolon).trim() : valueAndProps;
                result.put(key, value);
            }
        }
        return result;
    }

    // ==================== 内部辅助 ====================

    private static String padTo32Hex(String id) {
        if (id.length() >= 32) return id.substring(0, 32);
        return "0".repeat(32 - id.length()) + id;
    }

    private static String padTo16Hex(String id) {
        if (id.length() >= 16) return id.substring(0, 16);
        return "0".repeat(16 - id.length()) + id;
    }
}
