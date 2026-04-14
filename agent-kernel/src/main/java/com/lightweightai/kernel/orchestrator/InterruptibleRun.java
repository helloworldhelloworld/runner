package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.CancellationToken;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可打断可接续的 Agent 执行容器
 *
 * 解决 AgentLoop.runReactive() 的局限：它是一个闭合 Flux pipeline，
 * 内部的 fullResponse、messages、ToolCallingLoop 状态都是局部变量，
 * 一旦 dispose 全部丢失。
 *
 * InterruptibleRun 将这些状态提升为成员变量：
 * - messages: 当前对话上下文（持续更新）
 * - accumulatedText: 已生成的部分文本
 * - phase: 当前执行阶段
 *
 * 支持三个操作：
 * - execute(input): 首次执行或接续执行
 * - interrupt(): 打断当前执行，保存断点状态
 * - isRunning(): 检查是否正在执行
 */
public class InterruptibleRun {

    private static final Logger logger = LoggerFactory.getLogger(InterruptibleRun.class);

    public enum RunPhase { NEW, RUNNING, INTERRUPTED, COMPLETED }

    private final String runId;
    private final String sessionId;
    private final AgentLoop agent;
    private final MemoryProvider memoryProvider;

    // === 可恢复状态 ===
    private final List<ConversationMessage> messages = Collections.synchronizedList(new ArrayList<>());
    private final StringBuilder accumulatedText = new StringBuilder();
    private volatile RunPhase phase = RunPhase.NEW;

    // === 打断控制 ===
    private volatile CancellationToken cancellationToken;
    private volatile Disposable activeSubscription;

    public InterruptibleRun(String runId, String sessionId, AgentLoop agent, MemoryProvider memoryProvider) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.agent = agent;
        this.memoryProvider = memoryProvider;
    }

    /**
     * 开始或接续执行
     */
    public Flux<StreamEvent> execute(String input) {
        if (phase == RunPhase.NEW || phase == RunPhase.COMPLETED) {
            return startFresh(input);
        } else if (phase == RunPhase.INTERRUPTED) {
            return resume(input);
        } else {
            // 已在运行中，不应再次 execute
            return Flux.error(new IllegalStateException("Run " + runId + " is already running"));
        }
    }

    /**
     * 打断 — 保存当前状态，cancel 底层 Flux，返回 AGENT_INTERRUPT 事件
     */
    public StreamEvent interrupt() {
        if (phase != RunPhase.RUNNING) {
            logger.debug("Run {} not running (phase={}), skip interrupt", runId, phase);
            return null;
        }

        logger.info("Interrupting run {}, accumulatedText.length={}", runId, accumulatedText.length());

        // 1. 触发 CancellationToken，ToolCallingLoop 在下一个检查点退出
        if (cancellationToken != null) {
            cancellationToken.cancel();
        }

        // 2. 保存部分文本到 messages
        if (!accumulatedText.isEmpty()) {
            String partialText = accumulatedText.toString() + "\n[被用户打断]";
            messages.add(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT)
                    .textContent(partialText)
                    .build());

            // 写入 memory
            try {
                memoryProvider.addMessage(sessionId, Message.assistant(partialText));
            } catch (Exception e) {
                logger.warn("Failed to save partial response to memory", e);
            }
        }

        // 3. Dispose 底层 Flux subscription
        if (activeSubscription != null && !activeSubscription.isDisposed()) {
            activeSubscription.dispose();
        }

        phase = RunPhase.INTERRUPTED;
        String phaseStr = accumulatedText.isEmpty() ? "BEFORE_OUTPUT" : "STREAMING";

        return StreamEvent.agentInterrupt(runId, phaseStr, accumulatedText.length());
    }

    /**
     * 首次执行
     */
    private Flux<StreamEvent> startFresh(String input) {
        phase = RunPhase.RUNNING;
        accumulatedText.setLength(0);
        cancellationToken = new CancellationToken();

        // 委托 AgentLoop.runReactive 执行完整流程（含 memory search, prompt build 等）
        // 传入 cancellationToken 使 ToolCallingLoop 可被打断
        return Flux.create(sink -> {
            Disposable d = agent.runReactive(input, sessionId, cancellationToken)
                    .doOnNext(this::trackEvent)
                    .subscribe(
                            sink::next,
                            sink::error,
                            () -> { phase = RunPhase.COMPLETED; sink.complete(); }
                    );
            activeSubscription = d;
            sink.onDispose(d);
        });
    }

    /**
     * 从打断处接续执行新输入
     */
    private Flux<StreamEvent> resume(String newInput) {
        logger.info("Resuming run {} with new input, context size={}", runId, messages.size());

        // 发出 AGENT_RESUME 事件
        StreamEvent resumeEvent = StreamEvent.agentResume(runId, newInput, messages.size());

        phase = RunPhase.RUNNING;
        accumulatedText.setLength(0);
        cancellationToken = new CancellationToken();

        // 添加新用户消息到 memory
        memoryProvider.addMessage(sessionId, Message.user(newInput));

        // 使用 AgentLoop.runReactive 重新执行（它会从 memory 加载完整上下文）
        // 因为 interrupt 时已将部分回答保存到 memory，所以 LLM 能看到完整上下文
        Flux<StreamEvent> agentFlux = Flux.create(sink -> {
            Disposable d = agent.runReactive(newInput, sessionId, cancellationToken)
                    .doOnNext(this::trackEvent)
                    .subscribe(
                            sink::next,
                            sink::error,
                            () -> { phase = RunPhase.COMPLETED; sink.complete(); }
                    );
            activeSubscription = d;
            sink.onDispose(d);
        });

        return Flux.concat(Flux.just(resumeEvent), agentFlux);
    }

    private void trackEvent(StreamEvent event) {
        if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
            accumulatedText.append(event.getTextDelta());
        }
    }

    // ==================== 状态查询 ====================

    public boolean isRunning() { return phase == RunPhase.RUNNING; }
    public RunPhase getPhase() { return phase; }
    public String getRunId() { return runId; }
    public List<ConversationMessage> getMessages() { return Collections.unmodifiableList(messages); }

    /**
     * 测试辅助：强制设置为 INTERRUPTED 状态，模拟打断后的断点
     */
    void forceInterruptedState(String partialText) {
        if (!partialText.isEmpty()) {
            String text = partialText + "\n[被用户打断]";
            memoryProvider.addMessage(sessionId, Message.assistant(text));
        }
        phase = RunPhase.INTERRUPTED;
        accumulatedText.setLength(0);
    }
}
