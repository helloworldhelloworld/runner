package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionAwareClientToolDispatcher — lazy-binding dispatcher proxy")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("dispatch without delegate returns failed future")
    void noDelegate_failsFuture() {
        Directive directive = Directive.fromToolName("c-1", "take_photo", Map.of(), 5000);

        CompletableFuture<ToolResult> future = dispatcher.dispatch("c-1", "take_photo", directive);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("No client connected"));
    }

    @Test
    @DisplayName("dispatch with delegate forwards to delegate")
    void withDelegate_forwards() throws Exception {
        ClientToolDispatcher mockDelegate = (callId, toolName, dir) ->
                CompletableFuture.completedFuture(ToolResult.success("photo.jpg"));

        dispatcher.setDelegate(mockDelegate);

        Directive directive = Directive.fromToolName("c-1", "take_photo", Map.of(), 5000);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("c-1", "take_photo", directive);

        ToolResult result = future.get();
        assertFalse(result.isError());
        assertEquals("photo.jpg", result.getContent());
    }

    @Test
    @DisplayName("clearDelegate makes subsequent dispatch fail")
    void clearDelegate_failsAfter() {
        ClientToolDispatcher mockDelegate = (callId, toolName, dir) ->
                CompletableFuture.completedFuture(ToolResult.success("ok"));

        dispatcher.setDelegate(mockDelegate);
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("c-1", "tool",
                Directive.fromToolName("c-1", "tool", Map.of(), 1000));
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("hasDelegate returns false initially")
    void initiallyNoDelegate() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("hasDelegate returns true after setDelegate")
    void hasDelegateAfterSet() {
        dispatcher.setDelegate((callId, toolName, dir) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("dispatch arguments are forwarded to delegate correctly")
    void argumentsForwarded() throws Exception {
        String[] capturedCallId = {null};
        String[] capturedToolName = {null};
        Directive[] capturedDirective = {null};

        ClientToolDispatcher capturing = (callId, toolName, dir) -> {
            capturedCallId[0] = callId;
            capturedToolName[0] = toolName;
            capturedDirective[0] = dir;
            return CompletableFuture.completedFuture(ToolResult.success("ok"));
        };

        dispatcher.setDelegate(capturing);

        Directive directive = Directive.fromToolName("c-99", "gps.getPosition", Map.of("accuracy", "high"), 3000);
        dispatcher.dispatch("c-99", "gps.getPosition", directive).get();

        assertEquals("c-99", capturedCallId[0]);
        assertEquals("gps.getPosition", capturedToolName[0]);
        assertEquals("gps", capturedDirective[0].getNamespace());
    }
}
