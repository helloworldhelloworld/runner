package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.cli.CliExecutor.ExecEvent;
import com.lightweightai.kernel.cli.CliExecutor.ExecRequest;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用 CliExecutor — 不启子进程,按预设脚本发 ExecEvent。
 *
 * 用途:让 CliTool 的映射逻辑能在不依赖真实 shell 的情况下被测。
 */
final class FakeCliExecutor implements CliExecutor {

    private final List<ExecEvent> scripted;
    private final List<ExecRequest> received = new ArrayList<>();

    FakeCliExecutor(List<ExecEvent> scripted) {
        this.scripted = List.copyOf(scripted);
    }

    static FakeCliExecutor of(ExecEvent... events) {
        return new FakeCliExecutor(List.of(events));
    }

    @Override
    public Flux<ExecEvent> exec(ExecRequest request) {
        received.add(request);
        return Flux.fromIterable(scripted);
    }

    List<ExecRequest> received() {
        return List.copyOf(received);
    }
}
