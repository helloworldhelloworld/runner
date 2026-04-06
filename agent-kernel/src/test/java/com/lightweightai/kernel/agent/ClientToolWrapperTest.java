package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolWrapper - 客户端工具包装器")
class ClientToolWrapperTest {

    // ==================== 构造器校验 ====================

    @Test
    @DisplayName("null name 应抛异常")
    void shouldThrowOnNullName() {
        assertThrows(IllegalArgumentException.class,
            () -> new ClientToolWrapper(null, "desc", ToolSchema.empty(), mockDispatcher()));
    }

    @Test
    @DisplayName("空 name 应抛异常")
    void shouldThrowOnEmptyName() {
        assertThrows(IllegalArgumentException.class,
            () -> new ClientToolWrapper("  ", "desc", ToolSchema.empty(), mockDispatcher()));
    }

    @Test
    @DisplayName("null dispatcher 应抛异常")
    void shouldThrowOnNullDispatcher() {
        assertThrows(IllegalArgumentException.class,
            () -> new ClientToolWrapper("tool", "desc", ToolSchema.empty(), null));
    }

    @Test
    @DisplayName("null description 用空字符串")
    void shouldDefaultNullDescription() {
        ClientToolWrapper wrapper = new ClientToolWrapper("tool", null, null, mockDispatcher());
        assertEquals("", wrapper.getDescription());
    }

    @Test
    @DisplayName("null schema 用 empty schema")
    void shouldDefaultNullSchema() {
        ClientToolWrapper wrapper = new ClientToolWrapper("tool", "desc", null, mockDispatcher());
        assertNotNull(wrapper.getSchema());
    }

    @Test
    @DisplayName("负超时使用默认值")
    void shouldUseDefaultTimeoutForNegative() {
        ClientToolWrapper wrapper = new ClientToolWrapper("tool", "desc", ToolSchema.empty(), mockDispatcher(), -1);
        assertEquals(60_000, wrapper.getTimeoutMs());
    }

    // ==================== 基本属性 ====================

    @Test
    @DisplayName("getter 方法返回正确值")
    void shouldReturnCorrectProperties() {
        ClientToolWrapper wrapper = new ClientToolWrapper("take_photo", "拍照", ToolSchema.empty(), mockDispatcher(), 30_000);

        assertEquals("take_photo", wrapper.getName());
        assertEquals("拍照", wrapper.getDescription());
        assertEquals(30_000, wrapper.getTimeoutMs());
    }

    // ==================== ToolMetadata ====================

    @Test
    @DisplayName("category 为 client")
    void shouldHaveClientCategory() {
        ClientToolWrapper wrapper = new ClientToolWrapper("tool", "desc", ToolSchema.empty(), mockDispatcher());
        assertEquals("client", wrapper.getCategory());
    }

    @Test
    @DisplayName("isClientSide 为 true")
    void shouldBeClientSide() {
        ClientToolWrapper wrapper = new ClientToolWrapper("tool", "desc", ToolSchema.empty(), mockDispatcher());
        assertTrue(wrapper.isClientSide());
    }

    @Test
    @DisplayName("isAutoExecute 为 false")
    void shouldNotAutoExecute() {
        ClientToolWrapper wrapper = new ClientToolWrapper("tool", "desc", ToolSchema.empty(), mockDispatcher());
        assertFalse(wrapper.isAutoExecute());
    }

    @Test
    @DisplayName("isOpenWorld 为 true")
    void shouldBeOpenWorld() {
        ClientToolWrapper wrapper = new ClientToolWrapper("tool", "desc", ToolSchema.empty(), mockDispatcher());
        assertTrue(wrapper.isOpenWorld());
    }

    @Test
    @DisplayName("tags 包含 client")
    void shouldHaveClientTag() {
        ClientToolWrapper wrapper = new ClientToolWrapper("tool", "desc", ToolSchema.empty(), mockDispatcher());
        assertTrue(wrapper.getTags().contains("client"));
    }

    // ==================== 执行成功路径 ====================

    @Test
    @DisplayName("执行成功返回结果")
    void shouldExecuteSuccessfully() {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
            CompletableFuture.completedFuture(ToolResult.success("photo_url.jpg"));

        ClientToolWrapper wrapper = new ClientToolWrapper("take_photo", "拍照", ToolSchema.empty(), dispatcher, 5000);
        ToolResult result = wrapper.execute(Map.of("resolution", "1080p"));

        assertNotNull(result);
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("photo_url.jpg"));
    }

    // ==================== 超时路径 ====================

    @Test
    @DisplayName("超时返回错误 ToolResult")
    void shouldReturnErrorOnTimeout() {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
            CompletableFuture<ToolResult> future = new CompletableFuture<>();
            // 不完成 future，让它超时
            return future;
        };

        ClientToolWrapper wrapper = new ClientToolWrapper("slow_tool", "慢工具", ToolSchema.empty(), dispatcher, 100);
        ToolResult result = wrapper.execute(Map.of());

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("超时"));
    }

    // ==================== 异常路径 ====================

    @Test
    @DisplayName("dispatcher 抛异常返回错误 ToolResult")
    void shouldReturnErrorOnException() {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
            throw new RuntimeException("connection lost");
        };

        ClientToolWrapper wrapper = new ClientToolWrapper("fail_tool", "失败工具", ToolSchema.empty(), dispatcher);
        ToolResult result = wrapper.execute(Map.of());

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("失败"));
    }

    @Test
    @DisplayName("dispatcher Future 异常返回错误 ToolResult")
    void shouldReturnErrorOnFutureException() {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
            CompletableFuture.failedFuture(new RuntimeException("remote error"));

        ClientToolWrapper wrapper = new ClientToolWrapper("err_tool", "异常工具", ToolSchema.empty(), dispatcher, 5000);
        ToolResult result = wrapper.execute(Map.of());

        assertTrue(result.isError());
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString 包含名称和超时")
    void shouldHaveMeaningfulToString() {
        ClientToolWrapper wrapper = new ClientToolWrapper("cam", "camera", ToolSchema.empty(), mockDispatcher(), 30_000);
        String str = wrapper.toString();
        assertTrue(str.contains("cam"));
        assertTrue(str.contains("30000"));
    }

    // ==================== 辅助方法 ====================

    private ClientToolDispatcher mockDispatcher() {
        return (callId, toolName, directive) -> CompletableFuture.completedFuture(ToolResult.success("ok"));
    }
}
