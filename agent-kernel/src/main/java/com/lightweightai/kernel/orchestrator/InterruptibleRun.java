package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.core.ToolExecutionContext;
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
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

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

    public enum RunPhase { NEW, RUNNING, INTERRUPTED, COMPLETED, FAILED, CANCELLED }

    private final String runId;
    private final String sessionId;
    private final AgentLoop agent;
    private final MemoryProvider memoryProvider;

    // === 可恢复状态 ===
    private final List<ConversationMessage> messages = Collections.synchronizedList(new ArrayList<>());
    private final StringBuilder accumulatedText = new StringBuilder();
    private volatile RunPhase phase = RunPhase.NEW;
    private volatile RunTermination termination;

    // === 打断控制 ===
    private volatile CancellationToken cancellationToken;
    private volatile Disposable activeSubscription;
    private volatile FluxSink<StreamEvent> activeSink;
    private volatile Disposable.Composite activeResources = Disposables.composite();

    public InterruptibleRun(String runId, String sessionId, AgentLoop agent, MemoryProvider memoryProvider) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.agent = agent;
        this.memoryProvider = memoryProvider;
        this.createdAt = System.currentTimeMillis();
    }

    private final long createdAt;

    /**
     * 开始或接续执行
     */
    public synchronized Flux<StreamEvent> execute(String input) {
        return execute(input, null);
    }

    public synchronized Flux<StreamEvent> execute(String input, ToolExecutionContext context) {
        if (phase == RunPhase.NEW || phase == RunPhase.COMPLETED || phase == RunPhase.FAILED) {
            return startFresh(input, context);
        } else if (phase == RunPhase.INTERRUPTED) {
            return resume(input, context);
        } else {
            // 已在运行中，不应再次 execute
            return Flux.error(new IllegalStateException("Run " + runId + " is already running"));
        }
    }

    /**
     * 打断 — 保存当前状态，cancel 底层 Flux，返回 AGENT_INTERRUPT 事件
     */
    public StreamEvent interrupt() {
        return interrupt(cancellationToken, CancellationCause.USER_INTERRUPT);
    }

    private StreamEvent interrupt(CancellationToken expected) {
        return interrupt(expected, CancellationCause.USER_INTERRUPT);
    }

    /** Idempotent transport/system cancellation that does not create a resumable interrupt event. */
    StreamEvent cancel(CancellationCause cause) {
        return interrupt(cancellationToken, cause);
    }

    private StreamEvent interrupt(CancellationToken expected, CancellationCause cause) {
        Disposable subscription;
        FluxSink<StreamEvent> sink;
        Disposable.Composite resources;
        StreamEvent event;
        synchronized (this) {
        if (expected != cancellationToken) return null;
        if (phase != RunPhase.RUNNING) {
            logger.debug("Run {} not running (phase={}), skip interrupt", runId, phase);
            return null;
        }

        logger.info("Interrupting run {}, accumulatedText.length={}", runId, accumulatedText.length());

        // Mark terminal before disposal: synchronous callbacks cannot complete a newer run.
        phase = cause == CancellationCause.USER_INTERRUPT ? RunPhase.INTERRUPTED : RunPhase.CANCELLED;
        termination = new RunTermination(cause, System.currentTimeMillis());

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

        String phaseStr = accumulatedText.isEmpty() ? "BEFORE_OUTPUT" : "STREAMING";
        event = StreamEvent.agentInterrupt(runId, phaseStr, accumulatedText.length());
        subscription = activeSubscription;
        sink = activeSink;
        resources = activeResources;
        }
        if (expected != null) expected.cancel();
        resources.dispose();
        if (subscription != null) subscription.dispose();
        if (sink != null) sink.complete();
        return event;
    }

    /**
     * 首次执行
     */
    private Flux<StreamEvent> startFresh(String input, ToolExecutionContext context) {
        phase = RunPhase.RUNNING;
        accumulatedText.setLength(0);
        cancellationToken = new CancellationToken();
        activeResources = Disposables.composite();
        activeSink = null;
        activeSubscription = null;
        return startAgent(input, cancellationToken, context);
    }

    /**
     * 从打断处接续执行新输入
     */
    private Flux<StreamEvent> resume(String newInput, ToolExecutionContext context) {
        logger.info("Resuming run {} with new input, context size={}", runId, messages.size());

        // 发出 AGENT_RESUME 事件
        StreamEvent resumeEvent = StreamEvent.agentResume(runId, newInput, messages.size());

        // AgentLoop owns the user-memory commit; a second write here duplicates input.
        Flux<StreamEvent> agentFlux = startFresh(newInput, context);
        CancellationToken token = cancellationToken;
        return Flux.concat(Flux.just(resumeEvent), agentFlux).doOnCancel(() -> interrupt(token));
    }

    private Flux<StreamEvent> startAgent(String input, CancellationToken token, ToolExecutionContext context) {
        return Flux.<StreamEvent>create(sink -> {
            Disposable.Swap subscription = Disposables.swap();
            synchronized (this) {
                if (token != cancellationToken || phase != RunPhase.RUNNING) {
                    sink.complete();
                    return;
                }
                activeSink = sink;
                activeSubscription = subscription;
            }
            sink.onCancel(() -> interrupt(token));
            sink.onDispose(subscription);
            if (subscription.isDisposed()) return;
            ToolExecutionContext executionContext = context == null
                    ? ToolExecutionContext.serverOnly()
                    : context;
            executionContext = executionContext.withResourceRegistrar(activeResources::add);
            Flux<StreamEvent> execution = agent.runReactive(input, sessionId, token, executionContext);
            subscription.update(execution.subscribe(
                    event -> {
                        synchronized (this) {
                            if (token != cancellationToken || phase != RunPhase.RUNNING) return;
                            trackEvent(event);
                            sink.next(event);
                        }
                    },
                    error -> {
                        synchronized (this) {
                            if (token != cancellationToken || phase != RunPhase.RUNNING) return;
                            phase = RunPhase.FAILED;
                        }
                        token.cancel();
                        sink.error(error);
                    },
                    () -> {
                        synchronized (this) {
                            if (token != cancellationToken || phase != RunPhase.RUNNING) return;
                            phase = RunPhase.COMPLETED;
                        }
                        sink.complete();
                    }));
        }).doOnCancel(() -> interrupt(token));
    }

    private void trackEvent(StreamEvent event) {
        if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
            accumulatedText.append(event.getTextDelta());
        }
    }

    // ==================== 状态查询 ====================

    public boolean isRunning() { return phase == RunPhase.RUNNING; }
    public RunPhase getPhase() { return phase; }
    public long getCreatedAt() { return createdAt; }
    public String getRunId() { return runId; }
    RunTermination getTermination() { return termination; }
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
