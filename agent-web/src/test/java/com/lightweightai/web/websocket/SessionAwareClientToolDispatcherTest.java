package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionAwareClientToolDispatcher — 会话感知的 ClientToolDispatcher 代理")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Nested
    @DisplayName("无 delegate 时")
    class WithoutDelegate {

        @Test
        @DisplayName("hasDelegate 返回 false")
        void hasDelegateReturnsFalse() {
            assertFalse(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("dispatch 返回 failed CompletableFuture（IllegalStateException）")
        void dispatchReturnsFailedFuture() {
            Directive directive = new Directive("call-1", "Default", "TestTool",
                    Map.of("arg1", "value1"), 30000);

            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "TestTool", directive);

            assertTrue(future.isCompletedExceptionally());
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("No client connected"),
                    "错误信息应提及无客户端连接: " + ex.getCause().getMessage());
            assertTrue(ex.getCause().getMessage().contains("TestTool"),
                    "错误信息应包含工具名: " + ex.getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("有 delegate 时")
    class WithDelegate {

        @Test
        @DisplayName("setDelegate 后 hasDelegate 返回 true")
        void hasDelegateReturnsTrue() {
            dispatcher.setDelegate((callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok")));

            assertTrue(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("dispatch 委托给实际 delegate 并传递正确的 callId、toolName、directive")
        void dispatchDelegatesToActual() throws Exception {
            AtomicReference<String> capturedCallId = new AtomicReference<>();
            AtomicReference<String> capturedToolName = new AtomicReference<>();
            AtomicReference<Directive> capturedDirective = new AtomicReference<>();

            ClientToolDispatcher mockDelegate = (callId, toolName, directive) -> {
                capturedCallId.set(callId);
                capturedToolName.set(toolName);
                capturedDirective.set(directive);
                return CompletableFuture.completedFuture(ToolResult.success("delegate-result"));
            };

            dispatcher.setDelegate(mockDelegate);

            Directive directive = new Directive("call-42", "Camera", "TakePhoto",
                    Map.of("resolution", "1080p"), 5000);

            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-42", "Camera.TakePhoto", directive);
            ToolResult result = future.get();

            assertEquals("call-42", capturedCallId.get());
            assertEquals("Camera.TakePhoto", capturedToolName.get());
            assertSame(directive, capturedDirective.get());
            assertEquals("delegate-result", result.getContent());
            assertFalse(result.isError());
        }
    }

    @Nested
    @DisplayName("delegate 生命周期")
    class DelegateLifecycle {

        @Test
        @DisplayName("clearDelegate 后 hasDelegate 返回 false")
        void clearDelegateResetsState() {
            dispatcher.setDelegate((c, t, d) -> CompletableFuture.completedFuture(ToolResult.success("ok")));
            assertTrue(dispatcher.hasDelegate());

            dispatcher.clearDelegate();
            assertFalse(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("clearDelegate 后 dispatch 再次返回 failed Future")
        void clearDelegateCausesDispatchFailure() {
            dispatcher.setDelegate((c, t, d) -> CompletableFuture.completedFuture(ToolResult.success("ok")));
            dispatcher.clearDelegate();

            Directive directive = new Directive("call-1", "Default", "Tool",
                    Map.of(), 30000);
            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "Tool", directive);

            assertTrue(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("setDelegate 可以替换已有 delegate")
        void setDelegateReplacesExisting() throws Exception {
            dispatcher.setDelegate((c, t, d) -> CompletableFuture.completedFuture(ToolResult.success("first")));

            dispatcher.setDelegate((c, t, d) -> CompletableFuture.completedFuture(ToolResult.success("second")));

            Directive directive = new Directive("call-1", "Default", "Tool",
                    Map.of(), 30000);
            ToolResult result = dispatcher.dispatch("call-1", "Tool", directive).get();

            assertEquals("second", result.getContent());
        }
    }
}
