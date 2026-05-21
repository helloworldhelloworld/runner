package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionAwareClientToolDispatcher - lazy-binding singleton dispatcher")
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

    @Test
    @DisplayName("dispatch without delegate returns failed future")
    void dispatchWithoutDelegateReturnsFailed() {
        CompletableFuture<ToolResult> result = dispatcher.dispatch("call1", "get_position", directive);

        assertNotNull(result);
        assertTrue(result.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("No client connected"));
        assertTrue(ex.getCause().getMessage().contains("get_position"));
    }

    @Test
    @DisplayName("dispatch with delegate forwards to delegate")
    void dispatchWithDelegateForwards() {
        ToolResult expectedResult = ToolResult.success("position data");
        when(delegate.dispatch("call1", "get_position", directive))
            .thenReturn(CompletableFuture.completedFuture(expectedResult));

        dispatcher.setDelegate(delegate);
        CompletableFuture<ToolResult> result = dispatcher.dispatch("call1", "get_position", directive);

        assertNotNull(result);
        assertFalse(result.isCompletedExceptionally());
        assertEquals(expectedResult, result.join());
        verify(delegate).dispatch("call1", "get_position", directive);
    }

    @Test
    @DisplayName("hasDelegate returns false initially")
    void hasDelegateReturnsFalseInitially() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("hasDelegate returns true after setDelegate")
    void hasDelegateReturnsTrueAfterSet() {
        dispatcher.setDelegate(delegate);
        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("clearDelegate removes delegate")
    void clearDelegateRemovesDelegate() {
        dispatcher.setDelegate(delegate);
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("dispatch after clearDelegate returns failed future")
    void dispatchAfterClearDelegateFails() {
        dispatcher.setDelegate(delegate);
        dispatcher.clearDelegate();

        CompletableFuture<ToolResult> result = dispatcher.dispatch("call1", "tool1", directive);
        assertTrue(result.isCompletedExceptionally());
    }
}
