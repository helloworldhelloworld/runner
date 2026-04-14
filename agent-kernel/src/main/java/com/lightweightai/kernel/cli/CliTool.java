package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.agent.StreamingTool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CLI 工具 — 继承 StreamingTool，将外部命令行工具包装为 Tool 接口
 *
 * 执行模型：
 * - 同步 execute(): 运行进程，等待完成，返回 ToolResult
 * - 流式 executeReactive(): 逐行读取 stdout → 发出 PROGRESS 事件 → 最终 COMPLETE/ERROR
 *
 * 适配逻辑：
 * - exit code = 0 → ToolResult.success(stdout)
 * - exit code ≠ 0 → ToolResult.error(stderr)
 * - output_format = json → stdout 直接作为结构化内容
 */
public class CliTool extends StreamingTool {

    private static final Logger logger = LoggerFactory.getLogger(CliTool.class);

    private final CliManifest manifest;
    private final Path cliDirectory;  // CLI 包所在目录
    private int timeoutSeconds = 30;

    public CliTool(CliManifest manifest, Path cliDirectory) {
        this.manifest = manifest;
        this.cliDirectory = cliDirectory;
    }

    @Override
    public String getName() { return manifest.getName(); }

    @Override
    public String getDescription() { return manifest.getDescription(); }

    @Override
    public ToolSchema getSchema() {
        // CLI 的 schema 是动态的（通过 --help 发现）
        // 默认提供一个通用 schema：args 字符串参数
        return new ToolSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "args", Map.of("type", "string", "description", "Command line arguments")
                )
        ));
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
        Path entryPoint = cliDirectory.resolve(manifest.getEntryPoint());

        if (!entryPoint.toFile().exists()) {
            emitter.next(ToolResultChunk.error(getName(), "CLI entry point not found: " + entryPoint));
            emitter.complete();
            return;
        }

        try {
            List<String> command = buildCommand(entryPoint, args);
            logger.debug("Executing CLI: {} in {}", command, cliDirectory);

            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(cliDirectory.toFile())
                    .redirectErrorStream(false);

            Process process = pb.start();

            // 在独立线程中读取 stdout，避免 readLine 阻塞导致超时失效
            StringBuilder stdoutBuilder = new StringBuilder();
            Thread stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutBuilder.append(line).append("\n");
                        emitter.next(ToolResultChunk.progress(getName(), line, 0, 0));
                    }
                } catch (IOException ignored) {}
            }, "cli-stdout-" + getName());
            stdoutReader.setDaemon(true);
            stdoutReader.start();

            // 在独立线程中读取 stderr
            StringBuilder stderrBuilder = new StringBuilder();
            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrBuilder.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            }, "cli-stderr-" + getName());
            stderrReader.setDaemon(true);
            stderrReader.start();

            // 等待进程完成（带超时）
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                stdoutReader.interrupt();
                stderrReader.interrupt();
                emitter.next(ToolResultChunk.error(getName(), "CLI timed out after " + timeoutSeconds + "s"));
                emitter.complete();
                return;
            }

            // 等待读取线程完成
            stdoutReader.join(1000);
            stderrReader.join(1000);

            int exitCode = process.exitValue();
            String stdout = stdoutBuilder.toString().trim();
            String stderr = stderrBuilder.toString().trim();

            if (exitCode == 0) {
                emitter.next(ToolResultChunk.complete(getName(), ToolResult.success(stdout)));
            } else {
                String errorMsg = stderr.isEmpty() ? "CLI exited with code " + exitCode : stderr;
                emitter.next(ToolResultChunk.error(getName(), errorMsg));
            }

        } catch (Exception e) {
            logger.error("CLI execution failed: {}", getName(), e);
            emitter.next(ToolResultChunk.error(getName(), e.getMessage()));
        }

        emitter.complete();
    }

    private List<String> buildCommand(Path entryPoint, Map<String, Object> args) {
        List<String> command = new ArrayList<>();
        command.add(entryPoint.toAbsolutePath().toString());

        // 如果 args 包含 "args" 字符串，按空格拆分为参数
        Object argsObj = args.get("args");
        if (argsObj instanceof String argsStr && !argsStr.isBlank()) {
            for (String arg : argsStr.split("\\s+")) {
                command.add(arg);
            }
        }

        // 支持任意 key=value 形式的参数
        args.forEach((key, value) -> {
            if (!"args".equals(key) && value != null) {
                command.add("--" + key);
                command.add(value.toString());
            }
        });

        return command;
    }
}
