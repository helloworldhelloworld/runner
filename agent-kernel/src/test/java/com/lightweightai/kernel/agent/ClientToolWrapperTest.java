package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolWrapper - 客户端工具桥接")
class ClientToolWrapperTest {

    private final ClientToolDispatcher noOpDispatcher =
        (callId, toolName, directive) -> CompletableFuture.completedFuture(ToolResult.success("ok"));

    // ==================== Constructor ====================

    @Nested
    @DisplayName("构造函数")
    class ConstructorTests {

        @Test
        @DisplayName("正常构造")
        void normalConstruction() {
            ClientToolWrapper wrapper = new ClientToolWrapper(
                "camera", "Take a photo", ToolSchema.empty(), noOpDispatcher, 30_000);

            assertEquals("camera", wrapper.getName());
            assertEquals("Take a photo", wrapper.getDescription());
            assertNotNull(wrapper.getSchema());
            assertEquals(30_000, wrapper.getTimeoutMs());
        }

        @Test
        @DisplayName("默认超时构造")
        void defaultTimeoutConstruction() {
            ClientToolWrapper wrapper = new ClientToolWrapper(
                "camera", "desc", ToolSchema.empty(), noOpDispatcher);

            assertEquals(60_000, wrapper.getTimeoutMs());
        }

        @Test
        @DisplayName("null name 抛异常")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ClientToolWrapper(null, "desc", ToolSchema.empty(), noOpDispatcher));
        }

        @Test
        @DisplayName("空 name 抛异常")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ClientToolWrapper("", "desc", ToolSchema.empty(), noOpDispatcher));
            assertThrows(IllegalArgumentException.class,
                () -> new ClientToolWrapper("  ", "desc", ToolSchema.empty(), noOpDispatcher));
        }

        @Test
        @DisplayName("null dispatcher 抛异常")
        void nullDispatcherThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ClientToolWrapper("tool", "desc", ToolSchema.empty(), null));
        }

        @Test
        @DisplayName("null description 默认为空字符串")
        void nullDescriptionDefaultsToEmpty() {
            ClientToolWrapper wrapper = new ClientToolWrapper(
                "tool", null, ToolSchema.empty(), noOpDispatcher);
            assertEquals("", wrapper.getDescription());
        }

        @Test
        @DisplayName("null schema 默认为 empty")
        void nullSchemaDefaultsToEmpty() {
            ClientToolWrapper wrapper = new ClientToolWrapper(
                "tool", "desc", null, noOpDispatcher);
            assertNotNull(wrapper.getSchema());
        }

        @Test
        @DisplayName("非正数超时使用默认值")
        void invalidTimeoutUsesDefault() {
            ClientToolWrapper zero = new ClientToolWrapper(
                "tool", "desc", ToolSchema.empty(), noOpDispatcher, 0);
            assertEquals(60_000, zero.getTimeoutMs());

            ClientToolWrapper negative = new ClientToolWrapper(
                "tool", "desc", ToolSchema.empty(), noOpDispatcher, -100);
            assertEquals(60_000, negative.getTimeoutMs());
        }
    }

    // ==================== Execute ====================

    @Nested
    @DisplayName("execute 方法")
    class ExecuteTests {

        @Test
        @DisplayName("成功执行返回结果")
        void successfulExecution() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("photo_url.jpg"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                "camera", "Take photo", ToolSchema.empty(), dispatcher, 5000);

            ToolResult result = wrapper.execute(Map.of("resolution", "1080p"));

            assertFalse(result.isError());
            assertEquals("photo_url.jpg", result.getContent());
        }

        @Test
        @DisplayName("超时返回错误结果")
        void timeoutReturnsError() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
                CompletableFuture<ToolResult> future = new CompletableFuture<>();
                // Never completes — will timeout
                return future;
            };

            ClientToolWrapper wrapper = new ClientToolWrapper(
                "camera", "Take photo", ToolSchema.empty(), dispatcher, 100);

            ToolResult result = wrapper.execute(Map.of());

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("超时"));
        }

        @Test
        @DisplayName("执行异常返回错误结果")
        void exceptionReturnsError() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
                CompletableFuture<ToolResult> future = new CompletableFuture<>();
                future.completeExceptionally(new RuntimeException("connection lost"));
                return future;
            };

            ClientToolWrapper wrapper = new ClientToolWrapper(
                "camera", "desc", ToolSchema.empty(), dispatcher, 5000);

            ToolResult result = wrapper.execute(Map.of());

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("失败"));
        }

        @Test
        @DisplayName("dispatcher 返回带错误标记的结果")
        void dispatcherReturnsErrorResult() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.error("permission denied"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                "camera", "desc", ToolSchema.empty(), dispatcher);

            ToolResult result = wrapper.execute(Map.of());

            assertTrue(result.isError());
            assertEquals("permission denied", result.getContent());
        }
    }

    // ==================== ToolMetadata ====================

    @Nested
    @DisplayName("ToolMetadata 接口")
    class MetadataTests {

        @Test
        @DisplayName("metadata 属性正确")
        void metadataProperties() {
            ClientToolWrapper wrapper = new ClientToolWrapper(
                "camera", "desc", ToolSchema.empty(), noOpDispatcher);

            assertEquals("client", wrapper.getCategory());
            assertEquals(3, wrapper.getTags().size());
            assertTrue(wrapper.getTags().contains("client"));
            assertTrue(wrapper.getTags().contains("remote"));
            assertTrue(wrapper.getTags().contains("async"));
            assertEquals("client-side", wrapper.getAuthor());
            assertTrue(wrapper.isOpenWorld());
            assertTrue(wrapper.isClientSide());
        }

        @Test
        @DisplayName("不自动执行")
        void notAutoExecute() {
            ClientToolWrapper wrapper = new ClientToolWrapper(
                "camera", "desc", ToolSchema.empty(), noOpDispatcher);
            assertFalse(wrapper.isAutoExecute());
        }
    }

    @Test
    @DisplayName("toString 包含名称和超时")
    void toStringFormat() {
        ClientToolWrapper wrapper = new ClientToolWrapper(
            "camera", "desc", ToolSchema.empty(), noOpDispatcher, 30_000);

        String str = wrapper.toString();
        assertTrue(str.contains("camera"));
        assertTrue(str.contains("30000"));
    }
}
