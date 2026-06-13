package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionAwareClientToolDispatcher - 会话感知工具分发器")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Nested
    @DisplayName("无delegate时的行为")
    class NoDelegate {

        @Test
        @DisplayName("hasDelegate返回false")
        void shouldNotHaveDelegate() {
            assertFalse(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("dispatch返回失败Future")
        void shouldReturnFailedFutureWithoutDelegate() {
            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "testTool", null);

            assertTrue(future.isCompletedExceptionally());
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("testTool"));
        }
    }

    @Nested
    @DisplayName("有delegate时的行为")
    class WithDelegate {

        @Test
        @DisplayName("hasDelegate返回true")
        void shouldHaveDelegate() {
            dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
            assertTrue(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("dispatch委托给实际的delegate")
        void shouldDelegateDispatch() throws Exception {
            dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("delegated:" + toolName)));

            ToolResult result = dispatcher.dispatch("call-1", "search", null).get();
            assertEquals("delegated:search", result.getContent());
        }
    }

    @Nested
    @DisplayName("delegate生命周期")
    class DelegateLifecycle {

        @Test
        @DisplayName("设置后可分发，清除后失败")
        void shouldFollowLifecycle() {
            assertFalse(dispatcher.hasDelegate());

            dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
            assertTrue(dispatcher.hasDelegate());

            dispatcher.clearDelegate();
            assertFalse(dispatcher.hasDelegate());

            CompletableFuture<ToolResult> future = dispatcher.dispatch("c1", "tool", null);
            assertTrue(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("可以替换delegate")
        void shouldAllowDelegateReplacement() throws Exception {
            ClientToolDispatcher first = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("from-first"));
            ClientToolDispatcher second = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("from-second"));

            dispatcher.setDelegate(first);
            dispatcher.setDelegate(second);

            ToolResult result = dispatcher.dispatch("c1", "tool", null).get();
            assertEquals("from-second", result.getContent());
        }

        @Test
        @DisplayName("clearDelegate后dispatch失败")
        void clearDelegateMakesDispatchFail() {
            dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));

            dispatcher.clearDelegate();

            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "tool", null);
            assertTrue(future.isCompletedExceptionally());
        }
    }
}
