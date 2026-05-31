package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.agent.StreamingTool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Exited;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Failed;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Stderr;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Stdout;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.TimedOut;
import com.lightweightai.kernel.cli.CliExecutor.ExecRequest;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import reactor.core.publisher.FluxSink;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLI 工具 — 将外部命令行工具包装为 Tool 接口。
 *
 * 执行策略通过 {@link CliExecutor} 注入(默认 {@link LocalProcessExecutor}),
 * 本类只负责:参数 → ExecRequest 构造、ExecEvent → ToolResultChunk 映射、
 * exit code → ToolResult(success/error) 语义适配。
 */
public class CliTool extends StreamingTool {

    private final CliManifest manifest;
    private final Path cliDirectory;
    private final CliExecutor executor;
    private int timeoutSeconds = 30;

    public CliTool(CliManifest manifest, Path cliDirectory) {
        this(manifest, cliDirectory, new LocalProcessExecutor());
    }

    public CliTool(CliManifest manifest, Path cliDirectory, CliExecutor executor) {
        this.manifest = manifest;
        this.cliDirectory = cliDirectory;
        this.executor = executor;
    }

    @Override
    public String getName() { return manifest.getName(); }

    @Override
    public String getDescription() { return manifest.getDescription(); }

    @Override
    public ToolSchema getSchema() {
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

        // Fail-fast pre-flight check: don't spawn a process for a missing entry point.
        // Restored after the CliExecutor seam extraction dropped it — gives a clear,
        // platform-independent "not found" error instead of relying on ProcessBuilder's
        // OS-specific launch-failure message.
        if (!entryPoint.toFile().exists()) {
            emitter.next(ToolResultChunk.error(getName(), "CLI entry point not found: " + entryPoint));
            emitter.complete();
            return;
        }

        ExecRequest request = new ExecRequest(
                buildCommand(entryPoint, args),
                cliDirectory,
                Map.of(),
                Duration.ofSeconds(timeoutSeconds)
        );

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        executor.exec(request).subscribe(
                event -> handleEvent(event, emitter, stdout, stderr),
                throwable -> {
                    emitter.next(ToolResultChunk.error(getName(), throwable.getMessage()));
                    emitter.complete();
                }
        );
    }

    private void handleEvent(ExecEvent event,
                              FluxSink<ToolResultChunk> emitter,
                              StringBuilder stdoutBuf,
                              StringBuilder stderrBuf) {
        switch (event) {
            case Stdout(String line) -> {
                stdoutBuf.append(line).append("\n");
                emitter.next(ToolResultChunk.progress(getName(), line, 0, 0));
            }
            case Stderr(String line) -> stderrBuf.append(line).append("\n");
            case Exited(int code) -> {
                if (code == 0) {
                    emitter.next(ToolResultChunk.complete(getName(), ToolResult.success(stdoutBuf.toString().trim())));
                } else {
                    String stderrStr = stderrBuf.toString().trim();
                    String msg = stderrStr.isEmpty() ? "CLI exited with code " + code : stderrStr;
                    emitter.next(ToolResultChunk.error(getName(), msg));
                }
                emitter.complete();
            }
            case TimedOut ignored -> {
                emitter.next(ToolResultChunk.error(getName(), "CLI timed out after " + timeoutSeconds + "s"));
                emitter.complete();
            }
            case Failed(Throwable cause) -> {
                emitter.next(ToolResultChunk.error(getName(), cause.getMessage() != null ? cause.getMessage() : cause.toString()));
                emitter.complete();
            }
        }
    }

    private List<String> buildCommand(Path entryPoint, Map<String, Object> args) {
        List<String> command = new ArrayList<>();
        command.add(entryPoint.toAbsolutePath().toString());

        Object argsObj = args.get("args");
        if (argsObj instanceof String argsStr && !argsStr.isBlank()) {
            for (String arg : argsStr.split("\\s+")) {
                command.add(arg);
            }
        }

        args.forEach((key, value) -> {
            if (!"args".equals(key) && value != null) {
                command.add("--" + key);
                command.add(value.toString());
            }
        });

        return command;
    }
}
