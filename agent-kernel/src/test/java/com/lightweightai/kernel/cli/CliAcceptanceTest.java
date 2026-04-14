package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: CLI 全链路
 *
 * 验证：cli-manifest.json 解析 → CliToolSourceProvider 注册 → CliTool 执行 → ToolResult 统一
 */
@DisplayName("Acceptance: CLI 工具全链路")
class CliAcceptanceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("场景：注册 CLI → 从 ToolRegistry 查到 → 执行 → 返回正确 ToolResult")
    void fullCliLifecycle() throws Exception {
        // 1. 准备：在临时目录创建一个 CLI 工具
        Path cliDir = createSampleCli("hello-cli",
                "Prints a greeting message",
                "#!/bin/bash\necho \"Hello, $1!\"");

        // 2. 注册：CliToolSourceProvider 扫描目录
        ToolRegistry registry = new ToolRegistry();
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        // 3. 验证：ToolRegistry 包含该 CLI
        assertTrue(registry.has("hello-cli"), "CLI should be registered");
        Tool tool = registry.get("hello-cli").orElseThrow();
        assertEquals("Prints a greeting message", tool.getDescription());

        // 4. 执行：调用 CLI 工具
        ToolResult result = tool.execute(Map.of("args", "World"));

        // 5. 验证：ToolResult 正确
        assertFalse(result.isError(), "Should succeed: " + result.getContent());
        assertTrue(result.getContent().contains("Hello"),
                "Output should contain greeting: " + result.getContent());
    }

    @Test
    @DisplayName("场景：CLI 执行失败 → ToolResult.isError() = true + stderr 内容")
    void cliFailureReturnsError() throws Exception {
        Path cliDir = createSampleCli("fail-cli",
                "Always fails",
                "#!/bin/bash\necho 'Something went wrong' >&2\nexit 1");

        ToolRegistry registry = new ToolRegistry();
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        Tool tool = registry.get("fail-cli").orElseThrow();
        ToolResult result = tool.execute(Map.of());

        assertTrue(result.isError(), "Failed CLI should return error");
        assertTrue(result.getContent().contains("Something went wrong"),
                "Error should contain stderr: " + result.getContent());
    }

    @Test
    @DisplayName("场景：CLI 输出 JSON（--json 模式）→ ToolResult 包含结构化数据")
    void cliJsonOutput() throws Exception {
        Path cliDir = createSampleCli("json-cli",
                "Outputs JSON data",
                "#!/bin/bash\necho '{\"status\":\"ok\",\"count\":42}'",
                "json");  // output_format = json

        ToolRegistry registry = new ToolRegistry();
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        Tool tool = registry.get("json-cli").orElseThrow();
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        // JSON 输出应被透传或包含在 content 中
        assertTrue(result.getContent().contains("ok") || result.getContent().contains("42"),
                "Should contain JSON data: " + result.getContent());
    }

    @Test
    @DisplayName("场景：多个 CLI 注册 → ToolRegistry.search(关键词) 能按 description 检索")
    void searchCliByDescription() throws Exception {
        createSampleCli("weather-cli", "Check weather forecast for a city", "#!/bin/bash\necho sunny");
        createSampleCli("translate-cli", "Translate text between languages", "#!/bin/bash\necho translated");
        createSampleCli("calc-cli", "Calculate math expressions", "#!/bin/bash\necho 42");

        ToolRegistry registry = new ToolRegistry();
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        // 搜索 "weather" 应该找到 weather-cli
        List<Tool> results = registry.search("weather");
        assertTrue(results.stream().anyMatch(t -> t.getName().equals("weather-cli")),
                "Should find weather-cli by description search");

        // 搜索 "translate" 应该找到 translate-cli
        results = registry.search("translate");
        assertTrue(results.stream().anyMatch(t -> t.getName().equals("translate-cli")),
                "Should find translate-cli by description search");
    }

    @Test
    @DisplayName("场景：CLI 执行超时 → ToolResult.isError() + timeout 消息")
    void cliTimeout() throws Exception {
        createSampleCli("slow-cli",
                "Extremely slow tool",
                "#!/bin/bash\nsleep 60\necho done");

        ToolRegistry registry = new ToolRegistry();
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        Tool tool = registry.get("slow-cli").orElseThrow();

        // CliTool 默认超时应该远小于 60s
        // 如果 CliTool 构造时可以设置 timeout，用短超时
        if (tool instanceof CliTool cliTool) {
            cliTool.setTimeoutSeconds(2);
        }

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError(), "Timed-out CLI should return error");
        assertTrue(result.getContent().toLowerCase().contains("timeout")
                || result.getContent().toLowerCase().contains("timed out"),
                "Error should mention timeout: " + result.getContent());
    }

    @Test
    @DisplayName("场景：StreamingTool 流式输出 — CLI stdout 逐行推送 PROGRESS 事件")
    void cliStreamingOutput() throws Exception {
        createSampleCli("stream-cli",
                "Outputs multiple lines",
                "#!/bin/bash\necho 'line1'\necho 'line2'\necho 'line3'");

        ToolRegistry registry = new ToolRegistry();
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        Tool tool = registry.get("stream-cli").orElseThrow();

        // 通过 executeReactive 获取流式输出
        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        // 应有 PROGRESS 事件（每行一个）和最终的 COMPLETE
        long progressCount = chunks.stream()
                .filter(c -> c.getType() == ToolResultChunk.ChunkType.PROGRESS)
                .count();
        assertTrue(progressCount >= 2, "Should have multiple PROGRESS chunks for multi-line output");

        boolean hasComplete = chunks.stream()
                .anyMatch(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE);
        assertTrue(hasComplete, "Should end with COMPLETE chunk");
    }

    // ==================== Helpers ====================

    private Path createSampleCli(String name, String description, String script) throws IOException {
        return createSampleCli(name, description, script, "text");
    }

    private Path createSampleCli(String name, String description, String script, String outputFormat) throws IOException {
        Path cliDir = tempDir.resolve(name);
        Files.createDirectories(cliDir.resolve("bin"));

        // cli-manifest.json
        String manifest = """
                {
                  "name": "%s",
                  "version": "1.0.0",
                  "description": "%s",
                  "entry_point": "bin/%s",
                  "output_format": "%s"
                }
                """.formatted(name, description, name, outputFormat);
        Files.writeString(cliDir.resolve("cli-manifest.json"), manifest);

        // Executable script
        Path bin = cliDir.resolve("bin").resolve(name);
        Files.writeString(bin, script);
        bin.toFile().setExecutable(true);

        return cliDir;
    }
}
