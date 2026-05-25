package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionAwareClientToolDispatcher — 延迟绑定 delegate 调度")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("初始无 delegate，hasDelegate 返回 false")
    void initiallyNoDelegate() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("无 delegate 时 dispatch 返回失败 Future 并包含工具名")
    void dispatchWithoutDelegateFails() {
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call_1", "GetPosition", null);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("GetPosition"));
    }

    @Test
    @DisplayName("setDelegate 后 hasDelegate 返回 true")
    void setDelegateUpdatesFlag() {
        ClientToolDispatcher mock = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok"));

        dispatcher.setDelegate(mock);
        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("有 delegate 时 dispatch 转发到 delegate 并返回正确结果")
    void dispatchForwardsToDelegate() throws Exception {
        ToolResult expected = ToolResult.success("{\"lat\":31.2}");
        ClientToolDispatcher mock = (callId, toolName, directive) -> {
            assertEquals("call_1", callId);
            assertEquals("GetPosition", toolName);
            return CompletableFuture.completedFuture(expected);
        };

        dispatcher.setDelegate(mock);

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call_1", "GetPosition", null);
        ToolResult result = future.get();
        assertEquals("{\"lat\":31.2}", result.getContent());
    }

    @Test
    @DisplayName("clearDelegate 后恢复无 delegate 状态")
    void clearDelegateResetsToNoDelegate() {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call_2", "SomeTool", null);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("delegate 替换后新请求路由到新 delegate")
    void replaceDelegateRoutesToNew() throws Exception {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("old")));

        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("new")));

        ToolResult result = dispatcher.dispatch("call_3", "Tool", null).get();
        assertEquals("new", result.getContent());
    }
}
