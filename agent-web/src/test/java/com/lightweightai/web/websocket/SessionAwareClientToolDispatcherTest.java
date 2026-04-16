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

@DisplayName("SessionAwareClientToolDispatcher - 会话感知客户端工具调度")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("初始状态无 delegate")
    void shouldStartWithNoDelegate() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("无 delegate 时 dispatch 返回失败 Future")
    void shouldFailWhenNoDelegate() {
        Directive directive = Directive.fromToolName("call-1", "get_position", Map.of(), 5000);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", directive);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("No client connected"));
    }

    @Test
    @DisplayName("设置 delegate 后 hasDelegate 返回 true")
    void shouldHaveDelegateAfterSet() {
        dispatcher.setDelegate(mockDispatcher(ToolResult.success("id", "ok")));
        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("设置 delegate 后 dispatch 委托执行")
    void shouldDelegateToActualDispatcher() throws Exception {
        ToolResult expected = ToolResult.success("tc-1", "position: 37.7,-122.4");
        dispatcher.setDelegate(mockDispatcher(expected));

        Directive directive = Directive.fromToolName("call-1", "get_position", Map.of(), 5000);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", directive);

        ToolResult result = future.get();
        assertEquals("position: 37.7,-122.4", result.getContent());
        assertFalse(result.isError());
    }

    @Test
    @DisplayName("clearDelegate 后回到无连接状态")
    void shouldClearDelegate() {
        dispatcher.setDelegate(mockDispatcher(ToolResult.success("id", "ok")));
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("clearDelegate 后 dispatch 再次失败")
    void shouldFailAfterClear() {
        dispatcher.setDelegate(mockDispatcher(ToolResult.success("id", "ok")));
        dispatcher.clearDelegate();

        Directive directive = Directive.fromToolName("call-1", "get_position", Map.of(), 5000);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", directive);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("可以替换 delegate")
    void shouldReplaceDelegate() throws Exception {
        dispatcher.setDelegate(mockDispatcher(ToolResult.success("id", "old")));
        dispatcher.setDelegate(mockDispatcher(ToolResult.success("id", "new")));

        Directive directive = Directive.fromToolName("call-1", "tool", Map.of(), 5000);
        ToolResult result = dispatcher.dispatch("call-1", "tool", directive).get();
        assertEquals("new", result.getContent());
    }

    private ClientToolDispatcher mockDispatcher(ToolResult result) {
        return (callId, toolName, directive) -> CompletableFuture.completedFuture(result);
    }
}
