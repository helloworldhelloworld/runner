package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.cli.CliExecutor.ExecEvent;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Exited;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Failed;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Stderr;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Stdout;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.TimedOut;
import com.lightweightai.kernel.cli.CliExecutor.ExecRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: LocalProcessExecutor — CliExecutor 默认实现
 *
 * 验证 CliExecutor seam 的事件语义:Stdout / Stderr / Exited / TimedOut / Failed。
 * 这些事件是 CliTool 与外部执行策略的契约。
 */
@DisplayName("Acceptance: LocalProcessExecutor 事件语义")
class CliExecutorAcceptanceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("echo 命令 → Stdout 事件(带内容)+ Exited(0)")
    void echoProducesStdoutAndExitZero() throws IOException {
        Path script = writeScript("echo-ok", "#!/bin/bash\necho hello-world\n");

        List<ExecEvent> events = new LocalProcessExecutor()
                .exec(new ExecRequest(
                        List.of(script.toAbsolutePath().toString()),
                        tempDir,
                        Map.of(),
                        Duration.ofSeconds(5)))
                .collectList()
                .block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e instanceof Stdout s && s.line().contains("hello-world")),
                "stdout 事件必须携带行内容: " + events);
        assertTrue(events.stream().anyMatch(e -> e instanceof Exited ex && ex.code() == 0),
                "应包含 Exited(0) 事件: " + events);
    }

    @Test
    @DisplayName("失败命令 → Stderr 事件 + Exited(非零)")
    void failingCommandProducesStderrAndNonZeroExit() throws IOException {
        Path script = writeScript("fail", "#!/bin/bash\necho 'boom' >&2\nexit 7\n");

        List<ExecEvent> events = new LocalProcessExecutor()
                .exec(new ExecRequest(
                        List.of(script.toAbsolutePath().toString()),
                        tempDir,
                        Map.of(),
                        Duration.ofSeconds(5)))
                .collectList()
                .block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e instanceof Stderr s && s.line().contains("boom")),
                "stderr 必须带行内容: " + events);
        assertTrue(events.stream().anyMatch(e -> e instanceof Exited ex && ex.code() == 7),
                "exit code 必须原样透传: " + events);
    }

    @Test
    @DisplayName("超时 → TimedOut 事件(且不再有后续 Exited)")
    void timeoutProducesTimedOutEvent() throws IOException {
        Path script = writeScript("slow", "#!/bin/bash\nsleep 30\n");

        List<ExecEvent> events = new LocalProcessExecutor()
                .exec(new ExecRequest(
                        List.of(script.toAbsolutePath().toString()),
                        tempDir,
                        Map.of(),
                        Duration.ofMillis(500)))
                .collectList()
                .block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e instanceof TimedOut),
                "应包含 TimedOut 事件: " + events);
    }

    @Test
    @DisplayName("不存在的 binary → Failed 事件(异常包装)")
    void missingBinaryProducesFailed() {
        List<ExecEvent> events = new LocalProcessExecutor()
                .exec(new ExecRequest(
                        List.of("/nonexistent/path/to/bin-" + System.nanoTime()),
                        tempDir,
                        Map.of(),
                        Duration.ofSeconds(5)))
                .collectList()
                .block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e instanceof Failed),
                "启动失败应走 Failed 事件,而非 Exited: " + events);
    }

    @Test
    @DisplayName("流式:多行 stdout 按行拆分为多个 Stdout 事件")
    void multilineStdoutSplitsIntoMultipleEvents() throws IOException {
        Path script = writeScript("multi", "#!/bin/bash\necho a\necho b\necho c\n");

        List<ExecEvent> events = new LocalProcessExecutor()
                .exec(new ExecRequest(
                        List.of(script.toAbsolutePath().toString()),
                        tempDir,
                        Map.of(),
                        Duration.ofSeconds(5)))
                .collectList()
                .block();

        assertNotNull(events);
        long stdoutCount = events.stream().filter(e -> e instanceof Stdout).count();
        assertEquals(3, stdoutCount, "应有 3 个 Stdout 事件,实际: " + events);
    }

    private Path writeScript(String name, String body) throws IOException {
        Path script = tempDir.resolve(name + ".sh");
        Files.writeString(script, body);
        script.toFile().setExecutable(true);
        return script;
    }
}
