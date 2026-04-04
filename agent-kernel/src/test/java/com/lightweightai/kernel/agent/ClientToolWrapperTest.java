package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolWrapper")
class ClientToolWrapperTest {

    private static final String TOOL_NAME = "take_photo";
    private static final String TOOL_DESC = "Take a photo with the camera";

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        void shouldCreateWithAllParameters() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher, 5000);

            assertEquals(TOOL_NAME, wrapper.getName());
            assertEquals(TOOL_DESC, wrapper.getDescription());
            assertNotNull(wrapper.getSchema());
            assertEquals(5000, wrapper.getTimeoutMs());
        }

        @Test
        void shouldUseDefaultTimeout() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher);

            assertEquals(60_000, wrapper.getTimeoutMs());
        }

        @Test
        void shouldRejectNullName() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok"));

            assertThrows(IllegalArgumentException.class, () ->
                    new ClientToolWrapper(null, TOOL_DESC, ToolSchema.empty(), dispatcher));
        }

        @Test
        void shouldRejectEmptyName() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok"));

            assertThrows(IllegalArgumentException.class, () ->
                    new ClientToolWrapper("  ", TOOL_DESC, ToolSchema.empty(), dispatcher));
        }

        @Test
        void shouldRejectNullDispatcher() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ClientToolWrapper(TOOL_NAME, TOOL_DESC, ToolSchema.empty(), null));
        }

        @Test
        void shouldDefaultDescriptionToEmptyWhenNull() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, null, ToolSchema.empty(), dispatcher);

            assertEquals("", wrapper.getDescription());
        }

        @Test
        void shouldDefaultSchemaWhenNull() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, null, dispatcher);

            assertNotNull(wrapper.getSchema());
        }

        @Test
        void shouldUseDefaultTimeoutForNegativeValue() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher, -1);

            assertEquals(60_000, wrapper.getTimeoutMs());
        }
    }

    @Nested
    @DisplayName("Execution")
    class ExecutionTests {

        @Test
        void shouldExecuteSuccessfully() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("photo_url.jpg"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher, 5000);

            ToolResult result = wrapper.execute(Map.of("resolution", "1080p"));

            assertFalse(result.isError());
            assertEquals("photo_url.jpg", result.getContent());
        }

        @Test
        void shouldPassToolNameToDispatcher() {
            String[] capturedToolName = new String[1];
            ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
                capturedToolName[0] = toolName;
                return CompletableFuture.completedFuture(ToolResult.success("ok"));
            };

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher, 5000);
            wrapper.execute(Map.of());

            assertEquals(TOOL_NAME, capturedToolName[0]);
        }

        @Test
        void shouldGenerateUniqueCallIds() {
            String[] capturedIds = new String[2];
            int[] callCount = {0};
            ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
                capturedIds[callCount[0]++] = callId;
                return CompletableFuture.completedFuture(ToolResult.success("ok"));
            };

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher, 5000);
            wrapper.execute(Map.of());
            wrapper.execute(Map.of());

            assertNotNull(capturedIds[0]);
            assertNotNull(capturedIds[1]);
            assertNotEquals(capturedIds[0], capturedIds[1]);
        }

        @Test
        void shouldReturnErrorOnTimeout() {
            // Dispatcher that never completes
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    new CompletableFuture<>();

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher, 100);

            ToolResult result = wrapper.execute(Map.of());

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("超时"));
        }

        @Test
        void shouldReturnErrorOnException() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.failedFuture(new RuntimeException("connection lost"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher, 5000);

            ToolResult result = wrapper.execute(Map.of());

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("connection lost"));
        }

        @Test
        void shouldHandleDispatcherReturningErrorResult() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.error("camera not available"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher, 5000);

            ToolResult result = wrapper.execute(Map.of());

            assertTrue(result.isError());
            assertEquals("camera not available", result.getContent());
        }
    }

    @Nested
    @DisplayName("ToolMetadata")
    class MetadataTests {

        private ClientToolWrapper wrapper;

        @BeforeEach
        void setUp() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok"));
            wrapper = new ClientToolWrapper(TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher);
        }

        @Test
        void shouldReturnClientCategory() {
            assertEquals("client", wrapper.getCategory());
        }

        @Test
        void shouldReturnClientTags() {
            assertTrue(wrapper.getTags().contains("client"));
            assertTrue(wrapper.getTags().contains("remote"));
            assertTrue(wrapper.getTags().contains("async"));
        }

        @Test
        void shouldBeClientSide() {
            assertTrue(wrapper.isClientSide());
        }

        @Test
        void shouldBeOpenWorld() {
            assertTrue(wrapper.isOpenWorld());
        }

        @Test
        void shouldNotBeAutoExecute() {
            assertFalse(wrapper.isAutoExecute());
        }

        @Test
        void shouldReturnClientSideAuthor() {
            assertEquals("client-side", wrapper.getAuthor());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        void shouldIncludeNameAndTimeout() {
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                    CompletableFuture.completedFuture(ToolResult.success("ok"));

            ClientToolWrapper wrapper = new ClientToolWrapper(
                    TOOL_NAME, TOOL_DESC, ToolSchema.empty(), dispatcher, 30_000);

            String str = wrapper.toString();
            assertTrue(str.contains(TOOL_NAME));
            assertTrue(str.contains("30000"));
        }
    }
}
