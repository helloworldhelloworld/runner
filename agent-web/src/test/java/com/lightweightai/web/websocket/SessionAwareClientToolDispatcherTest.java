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

@DisplayName("SessionAwareClientToolDispatcher - lazy-binding client tool dispatch")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Nested
    @DisplayName("No delegate (no client connection)")
    class NoDelegate {

        @Test
        @DisplayName("dispatch returns failed future with IllegalStateException")
        void dispatchWithoutDelegateFailsFuture() {
            Directive directive = Directive.fromToolName("call-1", "Camera.TakePhoto", Map.of(), 30000);

            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "Camera.TakePhoto", directive);

            assertTrue(future.isCompletedExceptionally());
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("Camera.TakePhoto"));
        }

        @Test
        @DisplayName("hasDelegate returns false when no delegate set")
        void hasDelegateReturnsFalse() {
            assertFalse(dispatcher.hasDelegate());
        }
    }

    @Nested
    @DisplayName("With delegate (client connected)")
    class WithDelegate {

        @Test
        @DisplayName("dispatch delegates to actual dispatcher and captures arguments")
        void dispatchDelegatesToActual() throws Exception {
            AtomicReference<String> capturedCallId = new AtomicReference<>();
            AtomicReference<String> capturedToolName = new AtomicReference<>();
            AtomicReference<Directive> capturedDirective = new AtomicReference<>();

            ClientToolDispatcher mockDelegate = (callId, toolName, directive) -> {
                capturedCallId.set(callId);
                capturedToolName.set(toolName);
                capturedDirective.set(directive);
                return CompletableFuture.completedFuture(ToolResult.success("photo taken"));
            };

            dispatcher.setDelegate(mockDelegate);
            Directive directive = Directive.fromToolName("call-42", "Camera.TakePhoto",
                Map.of("resolution", "1080p"), 30000);

            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-42", "Camera.TakePhoto", directive);

            ToolResult result = future.get();
            assertFalse(result.isError());
            assertEquals("photo taken", result.getContent());
            assertEquals("call-42", capturedCallId.get());
            assertEquals("Camera.TakePhoto", capturedToolName.get());
            assertNotNull(capturedDirective.get());
        }

        @Test
        @DisplayName("hasDelegate returns true when delegate is set")
        void hasDelegateReturnsTrue() {
            dispatcher.setDelegate((id, name, dir) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
            assertTrue(dispatcher.hasDelegate());
        }
    }

    @Nested
    @DisplayName("Delegate lifecycle (connect/disconnect)")
    class DelegateLifecycle {

        @Test
        @DisplayName("clearDelegate makes subsequent dispatch fail")
        void clearDelegateMakesDispatchFail() {
            dispatcher.setDelegate((id, name, dir) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
            assertTrue(dispatcher.hasDelegate());

            dispatcher.clearDelegate();
            assertFalse(dispatcher.hasDelegate());

            Directive directive = Directive.fromToolName("call-1", "GPS.GetPos", Map.of(), 5000);
            CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "GPS.GetPos", directive);
            assertTrue(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("delegate can be replaced with new connection")
        void delegateReplacement() throws Exception {
            dispatcher.setDelegate((id, name, dir) ->
                CompletableFuture.completedFuture(ToolResult.success("from-client-1")));

            dispatcher.setDelegate((id, name, dir) ->
                CompletableFuture.completedFuture(ToolResult.success("from-client-2")));

            Directive directive = Directive.fromToolName("c1", "tool", Map.of(), 5000);
            ToolResult result = dispatcher.dispatch("c1", "tool", directive).get();
            assertEquals("from-client-2", result.getContent());
        }
    }
}
