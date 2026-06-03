package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;
    private Directive testDirective;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
        testDirective = new Directive("directive-1", "Default", "TestTool", Map.of("key", "value"), 30000);
    }

    @Test
    void hasDelegateReturnsFalseInitially() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    void hasDelegateReturnsTrueAfterSet() {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));

        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    void hasDelegateReturnsFalseAfterClear() {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
        dispatcher.clearDelegate();

        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    void dispatchWithNoDelegateReturnsFailedFuture() {
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "SomeTool", testDirective);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void dispatchWithNoDelegateIncludesToolNameInError() {
        String toolName = "Camera.TakePhoto";
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", toolName, testDirective);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        String message = ex.getCause().getMessage();
        assertTrue(message.contains(toolName),
                "Error message should contain tool name '" + toolName + "', but was: " + message);
    }

    @Test
    void dispatchDelegatesToSetDelegate() throws Exception {
        ToolResult expected = ToolResult.success("result text");
        ClientToolDispatcher delegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(expected);

        dispatcher.setDelegate(delegate);

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "TestTool", testDirective);
        ToolResult result = future.get();

        assertSame(expected, result);
    }

    @Test
    void dispatchPassesCallIdAndToolNameAndDirective() throws Exception {
        AtomicReference<String> capturedCallId = new AtomicReference<>();
        AtomicReference<String> capturedToolName = new AtomicReference<>();
        AtomicReference<Directive> capturedDirective = new AtomicReference<>();

        ClientToolDispatcher delegate = (callId, toolName, directive) -> {
            capturedCallId.set(callId);
            capturedToolName.set(toolName);
            capturedDirective.set(directive);
            return CompletableFuture.completedFuture(ToolResult.success("ok"));
        };

        dispatcher.setDelegate(delegate);
        dispatcher.dispatch("call-42", "MyTool", testDirective).get();

        assertEquals("call-42", capturedCallId.get());
        assertEquals("MyTool", capturedToolName.get());
        assertSame(testDirective, capturedDirective.get());
    }

    @Test
    void clearDelegatePreventsSubsequentDispatches() {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
        dispatcher.clearDelegate();

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "TestTool", testDirective);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void setDelegateOverridesPrevious() throws Exception {
        ToolResult firstResult = ToolResult.success("first");
        ToolResult secondResult = ToolResult.success("second");

        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(firstResult));
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(secondResult));

        ToolResult result = dispatcher.dispatch("call-1", "TestTool", testDirective).get();

        assertSame(secondResult, result, "Should use the second (most recently set) delegate");
    }

    @Test
    void implementsClientToolDispatcher() {
        assertInstanceOf(ClientToolDispatcher.class, dispatcher);
    }
}
