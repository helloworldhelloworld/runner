package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Exited;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Failed;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Stderr;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Stdout;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.TimedOut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CliExecutor 默认实现 — 本地 ProcessBuilder 启动子进程。
 *
 * 职责(从 CliTool 旧实现搬来):
 * - 启动 Process,继承/追加环境变量
 * - 两个后台线程按行读取 stdout/stderr,各自发出对应事件
 * - 主线程 waitFor(timeout);超时则 destroyForcibly 并发 TimedOut
 * - 任何启动/执行异常包装成 Failed
 *
 * 无状态,可被多个 CliTool 共享。
 */
public final class LocalProcessExecutor implements CliExecutor {

    private static final Logger logger = LoggerFactory.getLogger(LocalProcessExecutor.class);

    @Override
    public Flux<ExecEvent> exec(ExecRequest request) {
        return Flux.create(sink -> runProcess(request, sink));
    }

    private void runProcess(ExecRequest request, FluxSink<ExecEvent> sink) {
        Process process = null;
        Thread stdoutReader = null;
        Thread stderrReader = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(request.command())
                    .directory(request.workingDir().toFile())
                    .redirectErrorStream(false);
            if (request.env() != null && !request.env().isEmpty()) {
                Map<String, String> pbEnv = pb.environment();
                pbEnv.putAll(request.env());
            }

            process = pb.start();
            final Process proc = process;

            // dispose 时兜底:杀掉可能还活着的进程
            sink.onDispose(() -> {
                if (proc.isAlive()) {
                    proc.destroyForcibly();
                }
            });

            stdoutReader = new Thread(() -> pumpLines(proc.getInputStream(), Stdout::new, sink),
                    "cli-stdout-" + request.command().get(0));
            stdoutReader.setDaemon(true);
            stdoutReader.start();

            stderrReader = new Thread(() -> pumpLines(proc.getErrorStream(), Stderr::new, sink),
                    "cli-stderr-" + request.command().get(0));
            stderrReader.setDaemon(true);
            stderrReader.start();

            long timeoutMs = Math.max(1, request.timeout().toMillis());
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                stdoutReader.interrupt();
                stderrReader.interrupt();
                sink.next(new TimedOut());
                sink.complete();
                return;
            }

            // 等 reader 把缓冲清空
            stdoutReader.join(1000);
            stderrReader.join(1000);

            sink.next(new Exited(process.exitValue()));
            sink.complete();

        } catch (Exception e) {
            logger.debug("CLI exec failed: {}", request.command(), e);
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (stdoutReader != null) stdoutReader.interrupt();
            if (stderrReader != null) stderrReader.interrupt();
            sink.next(new Failed(e));
            sink.complete();
        }
    }

    private static void pumpLines(java.io.InputStream in,
                                   java.util.function.Function<String, ExecEvent> toEvent,
                                   FluxSink<ExecEvent> sink) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sink.next(toEvent.apply(line));
            }
        } catch (IOException ignored) {
            // reader 被 interrupt 或 stream 关闭,静默返回
        }
    }
}
