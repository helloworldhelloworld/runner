package com.lightweightai.kernel.trace.export;

import com.lightweightai.kernel.trace.Span;
import com.lightweightai.kernel.trace.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 可选 OTLP Span 导出器 — 批量导出到 OpenTelemetry Collector / Jaeger / Zipkin。
 *
 * 使用简化的 JSON 格式（非 protobuf），适合轻量集成。
 * 批量缓冲 + 定时刷新，减少 HTTP 开销。
 */
public class OtlpSpanExporter implements SpanExporter, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OtlpSpanExporter.class);

    private final String endpoint;
    private final HttpClient httpClient;
    private final List<Span> buffer = new CopyOnWriteArrayList<>();
    private final int batchSize;
    private final ScheduledExecutorService flusher;

    /**
     * @param endpoint OTLP HTTP endpoint (e.g. http://localhost:4318/v1/traces)
     * @param batchSize 批量大小（达到此数量立即发送）
     * @param flushIntervalMs 定时刷新间隔（毫秒）
     */
    public OtlpSpanExporter(String endpoint, int batchSize, long flushIntervalMs) {
        this.endpoint = endpoint;
        this.batchSize = batchSize;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "otlp-flusher");
            t.setDaemon(true);
            return t;
        });
        this.flusher.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    public OtlpSpanExporter(String endpoint) {
        this(endpoint, 50, 5000);
    }

    @Override
    public void export(Span span) {
        buffer.add(span);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    /**
     * 刷新缓冲区，发送所有待发送的 spans
     */
    public synchronized void flush() {
        if (buffer.isEmpty()) return;

        List<Span> toSend = new ArrayList<>(buffer);
        buffer.clear();

        try {
            String json = toOtlpJson(toSend);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                logger.warn("OTLP export failed: status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("OTLP export error: {}", e.getMessage());
            // 导出失败不重试，避免内存累积
        }
    }

    private String toOtlpJson(List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"resourceSpans\":[{\"scopeSpans\":[{\"spans\":[");
        boolean first = true;
        for (Span span : spans) {
            if (!first) sb.append(",");
            sb.append("{\"traceId\":\"").append(span.getTraceId()).append("\"");
            sb.append(",\"spanId\":\"").append(span.getSpanId()).append("\"");
            if (span.getParentSpanId() != null) {
                sb.append(",\"parentSpanId\":\"").append(span.getParentSpanId()).append("\"");
            }
            sb.append(",\"name\":\"").append(escape(span.getName())).append("\"");
            sb.append(",\"kind\":\"").append(span.getKind()).append("\"");
            sb.append(",\"startTimeUnixNano\":").append(span.getStartTimeMs() * 1_000_000L);
            sb.append(",\"endTimeUnixNano\":").append(span.getEndTimeMs() * 1_000_000L);
            sb.append(",\"status\":{\"code\":\"").append(span.getStatus()).append("\"}");
            // attributes
            sb.append(",\"attributes\":[");
            boolean attrFirst = true;
            for (Map.Entry<String, Object> attr : span.getAttributes().entrySet()) {
                if (!attrFirst) sb.append(",");
                sb.append("{\"key\":\"").append(escape(attr.getKey())).append("\",\"value\":{\"stringValue\":\"")
                  .append(escape(String.valueOf(attr.getValue()))).append("\"}}");
                attrFirst = false;
            }
            sb.append("]");
            sb.append("}");
            first = false;
        }
        sb.append("]}]}]}");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void close() {
        flush();
        flusher.shutdown();
    }

    @Override
    public String getName() {
        return "OtlpSpanExporter(" + endpoint + ")";
    }
}
