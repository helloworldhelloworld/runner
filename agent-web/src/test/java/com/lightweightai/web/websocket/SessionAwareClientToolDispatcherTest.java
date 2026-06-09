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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("SessionAwareClientToolDispatcher — lazy-binding singleton dispatcher")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Nested
    @DisplayName("No delegate connected")
    class NoDelegate {

        @Test
        void dispatchReturnsFailedFuture() {
            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "test_tool", null);
            assertTrue(future.isCompletedExceptionally());
        }

        @Test
        void failedFutureContainsToolName() {
            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", null);
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("get_position"));
        }

        @Test
        void hasDelegateReturnsFalse() {
            assertFalse(dispatcher.hasDelegate());
        }
    }

    @Nested
    @DisplayName("Delegate lifecycle")
    class DelegateLifecycle {

        @Test
        void setDelegateEnablesDispatch() {
            ClientToolDispatcher delegate = mock(ClientToolDispatcher.class);
            CompletableFuture<ToolResult> expected = CompletableFuture.completedFuture(ToolResult.success("ok"));
            when(delegate.dispatch(any(), any(), any())).thenReturn(expected);

            dispatcher.setDelegate(delegate);
            assertTrue(dispatcher.hasDelegate());

            CompletableFuture<ToolResult> result = dispatcher.dispatch("call-1", "tool", null);
            assertSame(expected, result);
            verify(delegate).dispatch("call-1", "tool", null);
        }

        @Test
        void clearDelegateDisablesDispatch() {
            ClientToolDispatcher delegate = mock(ClientToolDispatcher.class);
            dispatcher.setDelegate(delegate);
            assertTrue(dispatcher.hasDelegate());

            dispatcher.clearDelegate();
            assertFalse(dispatcher.hasDelegate());

            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "tool", null);
            assertTrue(future.isCompletedExceptionally());
            verifyNoInteractions(delegate);
        }

        @Test
        void replaceDelegateUsesNewOne() {
            ClientToolDispatcher first = mock(ClientToolDispatcher.class);
            ClientToolDispatcher second = mock(ClientToolDispatcher.class);

            CompletableFuture<ToolResult> firstResult = CompletableFuture.completedFuture(ToolResult.success("first"));
            CompletableFuture<ToolResult> secondResult = CompletableFuture.completedFuture(ToolResult.success("second"));
            when(first.dispatch(any(), any(), any())).thenReturn(firstResult);
            when(second.dispatch(any(), any(), any())).thenReturn(secondResult);

            dispatcher.setDelegate(first);
            assertSame(firstResult, dispatcher.dispatch("c1", "t", null));

            dispatcher.setDelegate(second);
            assertSame(secondResult, dispatcher.dispatch("c2", "t", null));
        }
    }

    @Nested
    @DisplayName("Dispatch delegation — payload verification")
    class PayloadVerification {

        @Test
        void passesAllParametersToDelegate() {
            ClientToolDispatcher delegate = mock(ClientToolDispatcher.class);
            when(delegate.dispatch(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(ToolResult.success("ok")));

            Directive directive = new Directive("d-1", "ns", "action", Map.of("key", "val"), 5000);
            dispatcher.setDelegate(delegate);
            dispatcher.dispatch("call-42", "my_tool", directive);

            verify(delegate).dispatch(eq("call-42"), eq("my_tool"), eq(directive));
        }
    }
}
