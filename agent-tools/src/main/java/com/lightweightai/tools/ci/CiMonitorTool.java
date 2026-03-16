package com.lightweightai.tools.ci;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * CI 构建监控工具
 *
 * 读取 ci-results/ 目录中的构建结果，提供状态查询、日志查看和摘要生成。
 */
public class CiMonitorTool {

    private static final int DEFAULT_LOG_LINES = 50;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path ciResultsDir;

    public CiMonitorTool() {
        this(Path.of("ci-results"));
    }

    public CiMonitorTool(Path ciResultsDir) {
        this.ciResultsDir = ciResultsDir;
    }

    @ToolFunction(
        name = "ci_monitor",
        description = "Monitor CI build status. Use action 'status' to get the latest build result, "
            + "'logs' to get the tail of the build log, or 'summary' for a formatted summary with pass/fail analysis.",
        category = "ci",
        tags = {"ci", "build", "devops"},
        readOnly = true,
        idempotent = true
    )
    public ToolResult ciMonitor(
        @ToolParam(name = "action", description = "Action: status, logs, or summary", required = true) String action,
        @ToolParam(name = "lines", description = "Number of log lines to return for 'logs' action (default 50)", type = "integer") int lines
    ) {
        return switch (action) {
            case "status" -> getStatus();
            case "logs" -> getLogs(lines > 0 ? lines : DEFAULT_LOG_LINES);
            case "summary" -> getSummary();
            default -> ToolResult.error("Unknown action: " + action + ". Use 'status', 'logs', or 'summary'.");
        };
    }

    private ToolResult getStatus() {
        try {
            Map<String, Object> data = readLatestJson();
            return ToolResult.success(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (IOException e) {
            return ToolResult.error("No CI results found: " + e.getMessage());
        }
    }

    private ToolResult getLogs(int lineCount) {
        Path logFile = ciResultsDir.resolve("build.log");
        if (!Files.exists(logFile)) {
            return ToolResult.error("Build log not found at " + logFile);
        }
        try {
            List<String> allLines = Files.readAllLines(logFile);
            int start = Math.max(0, allLines.size() - lineCount);
            List<String> tail = allLines.subList(start, allLines.size());
            return ToolResult.success(String.join("\n", tail));
        } catch (IOException e) {
            return ToolResult.error("Failed to read build log: " + e.getMessage());
        }
    }

    private ToolResult getSummary() {
        Map<String, Object> data;
        try {
            data = readLatestJson();
        } catch (IOException e) {
            return ToolResult.error("No CI results found: " + e.getMessage());
        }

        int exitCode = ((Number) data.getOrDefault("exit_code", -1)).intValue();
        String status = exitCode == 0 ? "PASSED" : "FAILED";
        String branch = String.valueOf(data.getOrDefault("branch", "unknown"));
        String sha = String.valueOf(data.getOrDefault("trigger_sha", "unknown"));
        String timestamp = String.valueOf(data.getOrDefault("timestamp", "unknown"));
        String runId = String.valueOf(data.getOrDefault("run_id", "unknown"));
        String testSummary = String.valueOf(data.getOrDefault("test_summary", "N/A"));

        StringBuilder sb = new StringBuilder();
        sb.append("CI Build Summary\n");
        sb.append("================\n");
        sb.append("Branch:    ").append(branch).append('\n');
        sb.append("Commit:    ").append(sha.length() > 7 ? sha.substring(0, 7) : sha).append('\n');
        sb.append("Status:    ").append(status).append(" (exit_code: ").append(exitCode).append(")\n");
        sb.append("Timestamp: ").append(timestamp).append('\n');
        sb.append("Run ID:    ").append(runId).append('\n');
        sb.append("Tests:     ").append(formatTestSummary(testSummary)).append('\n');

        if (exitCode != 0) {
            sb.append("\n--- Failure Context (last 20 lines) ---\n");
            Path logFile = ciResultsDir.resolve("build.log");
            if (Files.exists(logFile)) {
                try {
                    List<String> allLines = Files.readAllLines(logFile);
                    int start = Math.max(0, allLines.size() - 20);
                    allLines.subList(start, allLines.size()).forEach(line ->
                        sb.append(line).append('\n'));
                } catch (IOException e) {
                    sb.append("[Could not read build log: ").append(e.getMessage()).append("]\n");
                }
            } else {
                sb.append("[Build log not available]\n");
            }
        }

        return ToolResult.success(sb.toString());
    }

    private Map<String, Object> readLatestJson() throws IOException {
        Path jsonFile = ciResultsDir.resolve("latest.json");
        byte[] bytes = Files.readAllBytes(jsonFile);
        return MAPPER.readValue(bytes, new TypeReference<>() {});
    }

    private String formatTestSummary(String raw) {
        if (raw == null || raw.equals("N/A")) return "N/A";
        // Replace pipe separators from CI with newlines for readability
        return raw.replace("|", "\n         ");
    }
}
