package com.lightweightai.kernel.trace.export;

import com.lightweightai.kernel.trace.Span;
import com.lightweightai.kernel.trace.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 文件落盘 Span 导出器 — JSONL 格式，支持离线分析。
 *
 * 按日期分文件: data/traces/spans-2026-04-03.jsonl
 * 每行一个 JSON 对象。
 */
public class FileSpanExporter implements SpanExporter {

    private static final Logger logger = LoggerFactory.getLogger(FileSpanExporter.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path baseDir;
    private volatile String currentDate;
    private volatile BufferedWriter currentWriter;

    public FileSpanExporter(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            logger.error("Failed to create trace directory: {}", baseDir, e);
        }
    }

    public FileSpanExporter(String baseDirPath) {
        this(Path.of(baseDirPath));
    }

    @Override
    public synchronized void export(Span span) {
        try {
            String today = LocalDate.now().format(DATE_FMT);
            if (!today.equals(currentDate)) {
                rotateWriter(today);
            }
            if (currentWriter != null) {
                currentWriter.write(toJsonLine(span));
                currentWriter.newLine();
                currentWriter.flush();
            }
        } catch (IOException e) {
            logger.error("Failed to write span to file", e);
        }
    }

    private void rotateWriter(String date) throws IOException {
        if (currentWriter != null) {
            currentWriter.close();
        }
        currentDate = date;
        Path file = baseDir.resolve("spans-" + date + ".jsonl");
        currentWriter = Files.newBufferedWriter(file,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String toJsonLine(Span span) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"").append(escape(span.getName())).append("\"");
        sb.append(",\"kind\":\"").append(span.getKind()).append("\"");
        sb.append(",\"traceId\":\"").append(span.getTraceId()).append("\"");
        sb.append(",\"spanId\":\"").append(span.getSpanId()).append("\"");
        if (span.getParentSpanId() != null) {
            sb.append(",\"parentSpanId\":\"").append(span.getParentSpanId()).append("\"");
        }
        sb.append(",\"startTimeMs\":").append(span.getStartTimeMs());
        sb.append(",\"endTimeMs\":").append(span.getEndTimeMs());
        sb.append(",\"durationMs\":").append(span.getDurationMs());
        sb.append(",\"status\":\"").append(span.getStatus()).append("\"");
        if (span.getErrorMessage() != null) {
            sb.append(",\"error\":\"").append(escape(span.getErrorMessage())).append("\"");
        }
        sb.append(",\"attributes\":").append(mapToJson(span.getAttributes()));
        if (!span.getBaggage().isEmpty()) {
            sb.append(",\"baggage\":").append(stringMapToJson(span.getBaggage()));
        }
        sb.append("}");
        return sb.toString();
    }

    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(entry.getKey())).append("\":");
            Object v = entry.getValue();
            if (v instanceof Number) {
                sb.append(v);
            } else if (v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("\"").append(escape(String.valueOf(v))).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String stringMapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(entry.getKey())).append("\":\"")
              .append(escape(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    @Override
    public String getName() {
        return "FileSpanExporter(" + baseDir + ")";
    }

    public synchronized void close() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException ignored) {}
        }
    }
}
