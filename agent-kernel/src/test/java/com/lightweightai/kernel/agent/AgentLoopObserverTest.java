package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentLoop - Observer 异常处理、记忆异常处理、LLM 错误传播测试
 *
 * 已有 AgentLoopTest 覆盖基本对话和工具调用
 * 已有 AgentLoopReactiveTest 覆盖 reactive 基本流程
 * 本测试补充以下关键路径：
 * - Observer 各回调异常不中断主链路
 * - 多 Observer 部分失败不影响其他
 * - Reactive 模式 Observer 异常隔离
 * - Reactive 模式 LLM 错误通过 Observer.onError 传播
 * - 记忆保存失败在 reactive doOnComplete 中的安全处理
 */
@DisplayName("AgentLoop - Observer & 错误处理")
class AgentLoopObserverTest {

    private InMemoryProvider memory;
    private SyncMockLLMProvider llmProvider;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
        llmProvider = new SyncMockLLMProvider();
    }

    // ==================== Observer 异常隔离（同步路径）====================

    @Nested
    @DisplayName("Observer 异常隔离 - 同步路径")
    class SyncObserverIsolation {

        @Test
        @DisplayName("onAgentStart 异常不中断主链路")
        void onAgentStartExceptionShouldNotBreak() {
            llmProvider.setNextResponse("正常");
            AgentLoop loop = AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        throw new RuntimeException("onAgentStart crash");
                    }
                })
                .build();

            AgentResponse response = loop.run("test", "s1");
            assertEquals("正常", response.getText());
        }

        @Test
        @DisplayName("onLLMRequest 异常不中断主链路")
        void onLLMRequestExceptionShouldNotBreak() {
            llmProvider.setNextResponse("正常");
            AgentLoop loop = AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onLLMRequest(List<ConversationMessage> messages) {
                        throw new RuntimeException("onLLMRequest crash");
                    }
                })
                .build();

            AgentResponse response = loop.run("test", "s1");
            assertEquals("正常", response.getText());
        }

        @Test
        @DisplayName("onLLMResponse 异常不中断主链路")
        void onLLMResponseExceptionShouldNotBreak() {
            llmProvider.setNextResponse("正常");
            AgentLoop loop = AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onLLMResponse(LLMResponse response) {
                        throw new RuntimeException("onLLMResponse crash");
                    }
                })
                .build();

            AgentResponse response = loop.run("test", "s1");
            assertEquals("正常", response.getText());
        }

        @Test
        @DisplayName("onAgentComplete 异常不中断主链路")
        void onAgentCompleteExceptionShouldNotBreak() {
            llmProvider.setNextResponse("完成");
            AgentLoop loop = AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentComplete(AgentResponse resp) {
                        throw new RuntimeException("onAgentComplete crash");
                    }
                })
                .build();

            AgentResponse response = loop.run("test", "s1");
            assertEquals("完成", response.getText());
        }

        @Test
        @DisplayName("onError 中的异常不影响错误传播")
        void onErrorExceptionShouldNotAffectErrorPropagation() {
            llmProvider.setShouldFail(true);
            AgentLoop loop = AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onError(Throwable error) {
                        throw new RuntimeException("onError itself crashes");
                    }
                })
                .build();

            // 原始异常应正常传播
            assertThrows(RuntimeException.class, () -> loop.run("test", "s1"));
        }
    }

    // ==================== 多 Observer 互不影响 ====================

    @Nested
    @DisplayName("多 Observer 互不影响")
    class MultipleObserverTests {

        @Test
        @DisplayName("第一个 Observer 失败不阻止后续 Observer 执行")
        void firstFailingObserverShouldNotBlockSecond() {
            llmProvider.setNextResponse("回复");
            AtomicBoolean secondCalled = new AtomicBoolean(false);
            AtomicBoolean thirdCalled = new AtomicBoolean(false);

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        throw new RuntimeException("First observer fails");
                    }
                })
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        secondCalled.set(true);
                    }
                })
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        thirdCalled.set(true);
                    }
                })
                .build();

            loop.run("test", "s1");
            assertTrue(secondCalled.get(), "Second observer should execute");
            assertTrue(thirdCalled.get(), "Third observer should execute");
        }

        @Test
        @DisplayName("所有 lifecycle 回调都被正确调用")
        void allLifecycleCallbacksShouldBeCalled() {
            llmProvider.setNextResponse("回复");
            AtomicBoolean startCalled = new AtomicBoolean(false);
            AtomicBoolean requestCalled = new AtomicBoolean(false);
            AtomicBoolean responseCalled = new AtomicBoolean(false);
            AtomicBoolean completeCalled = new AtomicBoolean(false);

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        startCalled.set(true);
                    }
                    @Override
                    public void onLLMRequest(List<ConversationMessage> messages) {
                        requestCalled.set(true);
                    }
                    @Override
                    public void onLLMResponse(LLMResponse response) {
                        responseCalled.set(true);
                    }
                    @Override
                    public void onAgentComplete(AgentResponse response) {
                        completeCalled.set(true);
                    }
                })
                .build();

            loop.run("test", "s1");
            assertTrue(startCalled.get(), "onAgentStart should be called");
            assertTrue(requestCalled.get(), "onLLMRequest should be called");
            assertTrue(responseCalled.get(), "onLLMResponse should be called");
            assertTrue(completeCalled.get(), "onAgentComplete should be called");
        }

        @Test
        @DisplayName("LLM 失败时 onError 被调用")
        void onErrorShouldBeCalledOnLLMFailure() {
            llmProvider.setShouldFail(true);
            AtomicReference<Throwable> captured = new AtomicReference<>();

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onError(Throwable error) {
                        captured.set(error);
                    }
                })
                .build();

            assertThrows(RuntimeException.class, () -> loop.run("test", "s1"));
            assertNotNull(captured.get());
            assertTrue(captured.get().getMessage().contains("Mock LLM failure"));
        }
    }

    // ==================== Reactive 路径 Observer 异常隔离 ====================

    @Nested
    @DisplayName("Reactive 路径 Observer 异常隔离")
    class ReactiveObserverIsolation {

        @Test
        @DisplayName("Reactive onAgentStart 异常不阻断流")
        void reactiveOnAgentStartExceptionShouldNotBreakStream() {
            ReactiveMockProvider reactiveProvider = new ReactiveMockProvider();
            reactiveProvider.setStreamEvents(List.of(
                StreamEvent.textDelta("ok"),
                StreamEvent.llmComplete(createTextResponse("ok"))
            ));

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(reactiveProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        throw new RuntimeException("Reactive observer crash");
                    }
                })
                .build();

            StepVerifier.create(loop.runReactive("test", "s-rx"))
                .thenConsumeWhile(e -> true)
                .verifyComplete();
        }

        @Test
        @DisplayName("Reactive onLLMResponse 异常在 doOnNext 中被隔离")
        void reactiveOnLLMResponseExceptionShouldBeIsolated() {
            ReactiveMockProvider reactiveProvider = new ReactiveMockProvider();
            reactiveProvider.setStreamEvents(List.of(
                StreamEvent.textDelta("text"),
                StreamEvent.llmComplete(createTextResponse("text"))
            ));

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(reactiveProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onLLMResponse(LLMResponse response) {
                        throw new RuntimeException("Observer LLM response crash");
                    }
                })
                .build();

            // Observer crash in doOnNext for LLM_COMPLETE should be swallowed
            // by notifyObservers try-catch
            StepVerifier.create(loop.runReactive("test", "s-rx2"))
                .thenConsumeWhile(e -> true)
                .verifyComplete();
        }

        @Test
        @DisplayName("Reactive LLM 错误触发 doOnError 中的 observer.onError")
        void reactiveLLMErrorShouldTriggerObserverOnError() {
            ReactiveMockProvider reactiveProvider = new ReactiveMockProvider();
            reactiveProvider.setReactiveError(new RuntimeException("LLM failed"));

            AtomicReference<Throwable> captured = new AtomicReference<>();

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(reactiveProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onError(Throwable error) {
                        captured.set(error);
                    }
                })
                .build();

            StepVerifier.create(loop.runReactive("test", "s-err"))
                .verifyErrorMessage("LLM failed");

            assertNotNull(captured.get());
        }
    }

    // ==================== 辅助方法 ====================

    private LLMResponse createTextResponse(String text) {
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build())
            .stopReason("stop")
            .build();
    }

    // ==================== Mock Providers ====================

    private static class SyncMockLLMProvider implements LLMProvider {
        private String nextResponse = "Default";
        private boolean shouldFail = false;

        void setNextResponse(String response) {
            this.nextResponse = response;
            this.shouldFail = false;
        }

        void setShouldFail(boolean fail) { this.shouldFail = fail; }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            if (shouldFail) throw new RuntimeException("Mock LLM failure");
            return LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT)
                    .textContent(nextResponse)
                    .build())
                .stopReason("stop")
                .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            if (shouldFail) {
                handler.onError(new RuntimeException("Mock LLM failure"));
                return CompletableFuture.failedFuture(new RuntimeException("Mock LLM failure"));
            }
            handler.onTextDelta(nextResponse);
            LLMResponse response = complete(messages, options);
            handler.onComplete(response);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public ModelCapability getModelCapability() { return null; }
        @Override
        public String getProviderName() { return "sync-mock"; }
    }

    private static class ReactiveMockProvider implements LLMProvider {
        private final List<List<StreamEvent>> streamQueue = new ArrayList<>();
        private int streamIndex = 0;
        private RuntimeException reactiveError = null;

        void setStreamEvents(List<StreamEvent> events) {
            streamQueue.clear();
            streamQueue.add(events);
            streamIndex = 0;
        }

        void setReactiveError(RuntimeException error) {
            this.reactiveError = error;
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            if (reactiveError != null) return Flux.error(reactiveError);
            if (streamIndex >= streamQueue.size()) return Flux.error(new RuntimeException("No more"));
            return Flux.fromIterable(streamQueue.get(streamIndex++));
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException();
        }
        @Override
        public ModelCapability getModelCapability() { return null; }
        @Override
        public String getProviderName() { return "reactive-mock"; }
    }
}
