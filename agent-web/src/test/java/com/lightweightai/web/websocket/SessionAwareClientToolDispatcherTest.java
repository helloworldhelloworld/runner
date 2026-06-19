package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionAwareClientToolDispatcher - session-aware delegating dispatcher")
class SessionAwareClientToolDispatcherTest {

    @Mock
    private ClientToolDispatcher delegate;

    @Mock
    private Directive directive;

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    // ==================== Dispatch without delegate ====================

    @Nested
    @DisplayName("Dispatch without delegate")
    class DispatchWithoutDelegate {

        @Test
        @DisplayName("dispatch returns failed future with IllegalStateException when no delegate")
        void shouldReturnFailedFutureWhenNoDelegate() {
            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", directive);

            assertNotNull(future);
            assertTrue(future.isCompletedExceptionally());

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("get_position"));
            assertTrue(ex.getCause().getMessage().contains("No client connected"));
        }

        @Test
        @DisplayName("dispatch after clearDelegate returns failed future")
        void shouldReturnFailedFutureAfterClearDelegate() {
            dispatcher.setDelegate(delegate);
            dispatcher.clearDelegate();

            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-2", "some_tool", directive);

            assertTrue(future.isCompletedExceptionally());
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
        }
    }

    // ==================== Dispatch with delegate ====================

    @Nested
    @DisplayName("Dispatch with delegate")
    class DispatchWithDelegate {

        @Test
        @DisplayName("dispatch delegates to bound dispatcher with correct arguments")
        void shouldDelegateToCurrentDispatcher() {
            ToolResult expectedResult = ToolResult.success("position data");
            CompletableFuture<ToolResult> expectedFuture = CompletableFuture.completedFuture(expectedResult);
            when(delegate.dispatch("call-1", "get_position", directive)).thenReturn(expectedFuture);

            dispatcher.setDelegate(delegate);

            CompletableFuture<ToolResult> result = dispatcher.dispatch("call-1", "get_position", directive);

            assertSame(expectedFuture, result);
            verify(delegate).dispatch("call-1", "get_position", directive);
        }

        @Test
        @DisplayName("dispatch passes through delegate's failed future")
        void shouldPassThroughDelegateFailure() {
            CompletableFuture<ToolResult> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("connection lost"));
            when(delegate.dispatch("call-1", "tool", directive)).thenReturn(failedFuture);

            dispatcher.setDelegate(delegate);

            CompletableFuture<ToolResult> result = dispatcher.dispatch("call-1", "tool", directive);

            assertTrue(result.isCompletedExceptionally());
            ExecutionException ex = assertThrows(ExecutionException.class, result::get);
            assertEquals("connection lost", ex.getCause().getMessage());
        }

        @Test
        @DisplayName("dispatch after swapping delegate uses the new delegate")
        void shouldUseNewDelegateAfterSwap() {
            ClientToolDispatcher secondDelegate = mock(ClientToolDispatcher.class);

            ToolResult result1 = ToolResult.success("from first");
            ToolResult result2 = ToolResult.success("from second");

            when(delegate.dispatch("c1", "tool", directive))
                .thenReturn(CompletableFuture.completedFuture(result1));
            when(secondDelegate.dispatch("c2", "tool", directive))
                .thenReturn(CompletableFuture.completedFuture(result2));

            // Use first delegate
            dispatcher.setDelegate(delegate);
            CompletableFuture<ToolResult> future1 = dispatcher.dispatch("c1", "tool", directive);
            assertEquals("from first", future1.join().getContent());

            // Swap to second delegate
            dispatcher.setDelegate(secondDelegate);
            CompletableFuture<ToolResult> future2 = dispatcher.dispatch("c2", "tool", directive);
            assertEquals("from second", future2.join().getContent());

            verify(delegate).dispatch("c1", "tool", directive);
            verify(secondDelegate).dispatch("c2", "tool", directive);
        }
    }

    // ==================== Lifecycle methods ====================

    @Nested
    @DisplayName("Delegate lifecycle management")
    class DelegateLifecycle {

        @Test
        @DisplayName("hasDelegate returns false initially")
        void shouldReturnFalseInitially() {
            assertFalse(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("hasDelegate returns true after setDelegate")
        void shouldReturnTrueAfterSet() {
            dispatcher.setDelegate(delegate);

            assertTrue(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("hasDelegate returns false after clearDelegate")
        void shouldReturnFalseAfterClear() {
            dispatcher.setDelegate(delegate);
            dispatcher.clearDelegate();

            assertFalse(dispatcher.hasDelegate());
        }

        @Test
        @DisplayName("setDelegate then clearDelegate then setDelegate cycle works")
        void shouldHandleSetClearSetCycle() {
            dispatcher.setDelegate(delegate);
            assertTrue(dispatcher.hasDelegate());

            dispatcher.clearDelegate();
            assertFalse(dispatcher.hasDelegate());

            ClientToolDispatcher anotherDelegate = mock(ClientToolDispatcher.class);
            dispatcher.setDelegate(anotherDelegate);
            assertTrue(dispatcher.hasDelegate());

            // Verify the new delegate is actually used
            ToolResult expected = ToolResult.success("ok");
            when(anotherDelegate.dispatch("c1", "tool", directive))
                .thenReturn(CompletableFuture.completedFuture(expected));

            CompletableFuture<ToolResult> result = dispatcher.dispatch("c1", "tool", directive);
            assertEquals("ok", result.join().getContent());
            verify(anotherDelegate).dispatch("c1", "tool", directive);
            verifyNoInteractions(delegate);
        }
    }
}
