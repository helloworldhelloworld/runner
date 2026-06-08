package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionAwareClientToolDispatcher - 会话感知工具分发")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Nested
    @DisplayName("无 delegate 时")
    class NoDelegateTests {

        @Test
        @DisplayName("hasDelegate 返回 false")
        void hasDelegateReturnsFalse() {
            assertFalse(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("dispatch 返回失败的 Future")
        void dispatchFailsWithoutDelegate() {
            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "tool", null);

            assertTrue(future.isCompletedExceptionally());
            assertThrows(ExecutionException.class, future::get);
        }

        @Test
        @DisplayName("失败 Future 的异常是 IllegalStateException")
        void failedFutureContainsIllegalStateException() {
            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "myTool", null);

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("myTool"));
        }
    }

    @Nested
    @DisplayName("有 delegate 时")
    class WithDelegateTests {

        @Test
        @DisplayName("setDelegate 后 hasDelegate 返回 true")
        void hasDelegateReturnsTrue() {
            dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));

            assertTrue(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("dispatch 委托给实际 dispatcher")
        void dispatchDelegatesToReal() throws Exception {
            dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("delegated:" + toolName)));

            ToolResult result = dispatcher.dispatch("call-1", "search", null).get();
            assertEquals("delegated:search", result.getContent());
        }

        @Test
        @DisplayName("clearDelegate 后 hasDelegate 返回 false")
        void clearDelegateReverts() {
            dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));

            dispatcher.clearDelegate();
            assertFalse(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("clearDelegate 后 dispatch 失败")
        void clearDelegateMakesDispatchFail() {
            dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));

            dispatcher.clearDelegate();

            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "tool", null);
            assertTrue(future.isCompletedExceptionally());
        }
    }
}
