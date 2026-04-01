package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolWrapper - 客户端工具包装器")
class ClientToolWrapperTest {

    private final ClientToolDispatcher mockDispatcher = (callId, toolName, directive) ->
            CompletableFuture.completedFuture(ToolResult.success("client result"));

    @Test
    @DisplayName("正常创建")
    void normalCreation() {
        ClientToolWrapper wrapper = new ClientToolWrapper(
                "take_photo", "拍照", ToolSchema.empty(), mockDispatcher, 30000);

        assertEquals("take_photo", wrapper.getName());
        assertEquals("拍照", wrapper.getDescription());
        assertNotNull(wrapper.getSchema());
        assertEquals(30000, wrapper.getTimeoutMs());
        assertFalse(wrapper.isAutoExecute());
    }

    @Test
    @DisplayName("默认超时 60 秒")
    void defaultTimeout() {
        ClientToolWrapper wrapper = new ClientToolWrapper(
                "gps", "获取位置", ToolSchema.empty(), mockDispatcher);
        assertEquals(60_000, wrapper.getTimeoutMs());
    }

    @Test
    @DisplayName("null name 抛异常")
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ClientToolWrapper(null, "desc", ToolSchema.empty(), mockDispatcher));
    }

    @Test
    @DisplayName("空 name 抛异常")
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ClientToolWrapper("  ", "desc", ToolSchema.empty(), mockDispatcher));
    }

    @Test
    @DisplayName("null dispatcher 抛异常")
    void nullDispatcherThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ClientToolWrapper("tool", "desc", ToolSchema.empty(), null));
    }

    @Test
    @DisplayName("null description 默认为空字符串")
    void nullDescription() {
        ClientToolWrapper wrapper = new ClientToolWrapper(
                "tool", null, ToolSchema.empty(), mockDispatcher);
        assertEquals("", wrapper.getDescription());
    }

    @Test
    @DisplayName("null schema 默认为 empty")
    void nullSchema() {
        ClientToolWrapper wrapper = new ClientToolWrapper(
                "tool", "desc", null, mockDispatcher);
        assertNotNull(wrapper.getSchema());
    }

    @Test
    @DisplayName("负超时使用默认值")
    void negativeTimeout() {
        ClientToolWrapper wrapper = new ClientToolWrapper(
                "tool", "desc", ToolSchema.empty(), mockDispatcher, -1);
        assertEquals(60_000, wrapper.getTimeoutMs());
    }

    @Test
    @DisplayName("execute 成功调用 dispatcher")
    void executeSuccess() {
        ClientToolWrapper wrapper = new ClientToolWrapper(
                "tool", "desc", ToolSchema.empty(), mockDispatcher);
        ToolResult result = wrapper.execute(Map.of("key", "value"));
        assertNotNull(result);
        assertFalse(result.isError());
    }

    @Test
    @DisplayName("ToolMetadata 属性")
    void toolMetadata() {
        ClientToolWrapper wrapper = new ClientToolWrapper(
                "tool", "desc", ToolSchema.empty(), mockDispatcher);
        assertEquals("client", wrapper.getCategory());
        assertTrue(wrapper.getTags().contains("client"));
        assertTrue(wrapper.isClientSide());
        assertTrue(wrapper.isOpenWorld());
        assertEquals("client-side", wrapper.getAuthor());
    }

    @Test
    @DisplayName("toString 包含名称和超时")
    void toStringOutput() {
        ClientToolWrapper wrapper = new ClientToolWrapper(
                "my_tool", "desc", ToolSchema.empty(), mockDispatcher, 5000);
        String str = wrapper.toString();
        assertTrue(str.contains("my_tool"));
        assertTrue(str.contains("5000"));
    }

    @Test
    @DisplayName("dispatcher 抛异常时返回 error ToolResult")
    void executeWithException() {
        ClientToolDispatcher failDispatcher = (callId, toolName, directive) -> {
            CompletableFuture<ToolResult> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("connection lost"));
            return f;
        };
        ClientToolWrapper wrapper = new ClientToolWrapper(
                "tool", "desc", ToolSchema.empty(), failDispatcher, 1000);
        ToolResult result = wrapper.execute(Map.of());
        assertTrue(result.isError());
    }
}
