package com.lightweightai.tools.ci;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CI 监控工具测试")
class CiMonitorToolTest {

    @TempDir
    Path tempDir;

    private Tool ciTool;

    @BeforeEach
    void setUp() {
        CiMonitorTool ciMonitorTool = new CiMonitorTool(tempDir);
        List<Tool> tools = AnnotatedToolScanner.scan(ciMonitorTool);
        assertEquals(1, tools.size());
        ciTool = tools.get(0);
    }

    // ==================== status 操作 ====================

    @Test
    @DisplayName("status 操作返回最新 CI 状态")
    void shouldReturnStatusFromLatestJson() throws IOException {
        writeLatestJson(0, "main");

        ToolResult result = ciTool.execute(Map.of("action", "status", "lines", 0));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("main"));
        assertTrue(result.getContent().contains("abc123def456"));
    }

    @Test
    @DisplayName("结果文件不存在时返回错误")
    void shouldReturnErrorWhenNoResultsExist() {
        ToolResult result = ciTool.execute(Map.of("action", "status", "lines", 0));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("No CI results found"));
    }

    // ==================== logs 操作 ====================

    @Test
    @DisplayName("logs 操作返回构建日志尾部")
    void shouldReturnLogTail() throws IOException {
        writeBuildLog(100);

        ToolResult result = ciTool.execute(Map.of("action", "logs", "lines", 10));

        assertFalse(result.isError());
        String[] lines = result.getContent().split("\n");
        assertEquals(10, lines.length);
        assertTrue(result.getContent().contains("Build line 100"));
        assertTrue(result.getContent().contains("Build line 91"));
    }

    @Test
    @DisplayName("logs 操作默认返回50行")
    void shouldReturnDefaultLogLines() throws IOException {
        writeBuildLog(100);

        ToolResult result = ciTool.execute(Map.of("action", "logs", "lines", 0));

        assertFalse(result.isError());
        String[] lines = result.getContent().split("\n");
        assertEquals(50, lines.length);
    }

    @Test
    @DisplayName("日志文件不存在时返回错误")
    void shouldReturnErrorWhenLogMissing() {
        ToolResult result = ciTool.execute(Map.of("action", "logs", "lines", 10));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Build log not found"));
    }

    // ==================== summary 操作 ====================

    @Test
    @DisplayName("summary 操作返回格式化摘要")
    void shouldReturnFormattedSummary() throws IOException {
        writeLatestJson(0, "main");

        ToolResult result = ciTool.execute(Map.of("action", "summary", "lines", 0));

        assertFalse(result.isError());
        String content = result.getContent();
        assertTrue(content.contains("CI Build Summary"));
        assertTrue(content.contains("Branch:    main"));
        assertTrue(content.contains("PASSED"));
        assertTrue(content.contains("abc123d"));
    }

    @Test
    @DisplayName("失败构建的摘要包含日志上下文")
    void shouldIncludeFailureContextInSummary() throws IOException {
        writeLatestJson(1, "feature-branch");
        writeBuildLog(30);

        ToolResult result = ciTool.execute(Map.of("action", "summary", "lines", 0));

        assertFalse(result.isError());
        String content = result.getContent();
        assertTrue(content.contains("FAILED"));
        assertTrue(content.contains("Failure Context"));
        assertTrue(content.contains("Build line 30"));
    }

    @Test
    @DisplayName("日志缺失时仍返回摘要")
    void shouldReturnSummaryWithoutLogWhenLogMissing() throws IOException {
        writeLatestJson(1, "main");

        ToolResult result = ciTool.execute(Map.of("action", "summary", "lines", 0));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("FAILED"));
        assertTrue(result.getContent().contains("Build log not available"));
    }

    // ==================== 边界情况 ====================

    @Test
    @DisplayName("未知操作返回错误")
    void shouldReturnErrorForUnknownAction() {
        ToolResult result = ciTool.execute(Map.of("action", "unknown", "lines", 0));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Unknown action"));
    }

    @Test
    @DisplayName("工具可通过注解扫描发现")
    void shouldBeDiscoverableViaAnnotationScanner() {
        assertEquals("ci_monitor", ciTool.getName());
        assertTrue(ciTool.getDescription().contains("CI"));
    }

    // ==================== 辅助方法 ====================

    private void writeLatestJson(int exitCode, String branch) throws IOException {
        String json = """
            {
                "trigger_sha": "abc123def456",
                "exit_code": %d,
                "timestamp": "2026-03-16T10:30:00Z",
                "branch": "%s",
                "run_id": "12345",
                "test_summary": "Tests run: 42, Failures: 0"
            }
            """.formatted(exitCode, branch);
        Files.writeString(tempDir.resolve("latest.json"), json);
    }

    private void writeBuildLog(int lineCount) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lineCount; i++) {
            sb.append("[INFO] Build line ").append(i).append("\n");
        }
        Files.writeString(tempDir.resolve("build.log"), sb.toString());
    }
}
