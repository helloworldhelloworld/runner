package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Observer 异常隔离测试
 *
 * AgentLoop.notifyObservers() 用 try-catch 包裹每个 observer 调用。
 * 一个 observer 抛异常不应阻断：
 * - 其他 observer 的执行
 * - 主链路的正常完成
 *
 * 这类 bug 在生产环境很难发现，因为测试通常只用一个 observer。
 */
@DisplayName("AgentLoop - Observer 异常隔离")
class AgentLoopObserverIsolationTest {

    private MemoryProvider memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
    }

    @Test
    @DisplayName("sync: 第一个 observer 抛异常不影响第二个 observer 和主链路")
    void syncObserverExceptionDoesNotBreakChain() {
        AtomicBoolean secondObserverCalled = new AtomicBoolean(false);
        AtomicReference<String> capturedInput = new AtomicReference<>();

        AgentObserver throwingObserver = new AgentObserver() {
            @Override
            public void onAgentStart(String input, String sessionId) {
                throw new RuntimeException("Observer 1 exploded!");
            }
        };

        AgentObserver capturingObserver = new AgentObserver() {
            @Override
            public void onAgentStart(String input, String sessionId) {
                secondObserverCalled.set(true);
                capturedInput.set(input);
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("hello");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .systemPrompt("test")
                .addObserver(throwingObserver)
                .addObserver(capturingObserver)
                .build();

        AgentResponse response = loop.run("user input", "sess-1");

        assertNotNull(response, "Main chain must complete despite observer failure");
        assertEquals("hello", response.getText());
        assertTrue(secondObserverCalled.get(),
                "Second observer must still be called after first one throws");
        assertEquals("user input", capturedInput.get());
    }

    @Test
    @DisplayName("sync: onLLMResponse observer 抛异常不影响结果返回")
    void syncLLMResponseObserverExceptionDoesNotBreakResult() {
        AtomicBoolean completeCalled = new AtomicBoolean(false);

        AgentObserver throwingOnResponse = new AgentObserver() {
            @Override
            public void onLLMResponse(LLMResponse response) {
                throw new RuntimeException("Response observer exploded!");
            }
        };

        AgentObserver completionTracker = new AgentObserver() {
            @Override
            public void onAgentComplete(AgentResponse response) {
                completeCalled.set(true);
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("result");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .systemPrompt("test")
                .addObserver(throwingOnResponse)
                .addObserver(completionTracker)
                .build();

        AgentResponse response = loop.run("hi", "sess-2");

        assertEquals("result", response.getText());
        assertTrue(completeCalled.get(),
                "onAgentComplete must fire even when onLLMResponse throws");
    }

    @Test
    @DisplayName("reactive: observer 抛异常不影响流式输出")
    void reactiveObserverExceptionDoesNotBreakStream() {
        AtomicBoolean completeCalled = new AtomicBoolean(false);

        AgentObserver throwingObserver = new AgentObserver() {
            @Override
            public void onAgentStart(String input, String sessionId) {
                throw new RuntimeException("Reactive observer exploded!");
            }

            @Override
            public void onLLMRequest(List<ConversationMessage> messages) {
                throw new RuntimeException("LLM request observer exploded!");
            }
        };

        AgentObserver completionTracker = new AgentObserver() {
            @Override
            public void onAgentComplete(AgentResponse response) {
                completeCalled.set(true);
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("reactive-ok");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .systemPrompt("test")
                .addObserver(throwingObserver)
                .addObserver(completionTracker)
                .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(loop.runReactive("test", "sess-3"))
                .recordWith(() -> events)
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        assertFalse(events.isEmpty(), "Stream should produce events despite observer failures");
        boolean hasComplete = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE);
        assertTrue(hasComplete, "Should reach LLM_COMPLETE despite observer exceptions");
        assertTrue(completeCalled.get(),
                "onAgentComplete must fire even when other observers throw");
    }

    @Test
    @DisplayName("sync: onError observer 抛异常不吞没原始异常")
    void onErrorObserverExceptionDoesNotSwallowOriginal() {
        AgentObserver throwingOnError = new AgentObserver() {
            @Override
            public void onError(Throwable error) {
                throw new RuntimeException("onError observer itself failed!");
            }
        };

        LLMProvider failingProvider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                throw new RuntimeException("LLM failed intentionally");
            }
            @Override
            public java.util.concurrent.CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("LLM failed"));
            }
            @Override
            public java.util.concurrent.CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                throw new UnsupportedOperationException();
            }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "failing-mock"; }
        };

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(failingProvider)
                .memoryProvider(memory)
                .systemPrompt("test")
                .addObserver(throwingOnError)
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loop.run("hi", "sess-4"));
        assertTrue(ex.getMessage().contains("LLM failed intentionally"),
                "Original exception must propagate, not the observer's exception. Got: " + ex.getMessage());
    }
}
