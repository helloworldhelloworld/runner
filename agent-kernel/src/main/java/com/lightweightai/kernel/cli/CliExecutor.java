package com.lightweightai.kernel.cli;

import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * CLI 执行策略 — CliTool 与具体子进程启动方式之间的抽象。
 *
 * 默认实现:{@link LocalProcessExecutor}(基于 ProcessBuilder)。
 * 未来扩展点:DockerExecutor、SshExecutor、RemoteAgentExecutor 等均可实现此接口,
 * CliTool 本体无需改动。
 */
public interface CliExecutor {

    /**
     * 启动并监听一次命令执行。返回的 Flux 在进程结束前持续推送事件;
     * 事件流以 {@link ExecEvent.Exited}、{@link ExecEvent.TimedOut}、
     * 或 {@link ExecEvent.Failed} 之一作为终止信号,随后 Flux complete。
     */
    Flux<ExecEvent> exec(ExecRequest request);

    /**
     * 一次执行的不可变请求。
     *
     * @param command    完整命令(argv[0] 为可执行文件路径)
     * @param workingDir 子进程工作目录
     * @param env        追加的环境变量(空 map 表示继承父环境)
     * @param timeout    整体超时时间,超过后发出 {@link ExecEvent.TimedOut}
     */
    record ExecRequest(
            List<String> command,
            Path workingDir,
            Map<String, String> env,
            Duration timeout
    ) {}

    /**
     * 执行过程事件。sealed 以便消费方用 pattern match 穷尽分支。
     */
    sealed interface ExecEvent {
        /** 一行 stdout(不含换行符)*/
        record Stdout(String line) implements ExecEvent {}

        /** 一行 stderr(不含换行符)*/
        record Stderr(String line) implements ExecEvent {}

        /** 进程正常结束,携带 exit code */
        record Exited(int code) implements ExecEvent {}

        /** 超过 timeout,进程已被强制中止 */
        record TimedOut() implements ExecEvent {}

        /** 启动或执行过程中抛出异常(如 binary 不存在)*/
        record Failed(Throwable cause) implements ExecEvent {}
    }
}
