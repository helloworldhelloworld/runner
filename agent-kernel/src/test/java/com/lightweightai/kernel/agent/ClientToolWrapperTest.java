package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClientToolWrapper}.
 *
 * Covers key paths:
 * - Happy path: dispatch -> success result
 * - Error path: dispatcher returns error result
 * - Timeout path: dispatcher future never completes
 * - Exception path: dispatcher future fails with exception
 * - Interruption path
 * - Constructor validation
 * - Metadata / client-side flags
 */
class ClientToolWrapperTest {

    /** Test dispatcher that captures calls and returns a preset future. */
    private static class RecordingDispatcher implements ClientToolDispatcher {
        final List<String> receivedCallIds = new ArrayList<>();
        final List<String> receivedToolNames = new ArrayList<>();
        final List<Directive> receivedDirectives = new ArrayList<>();
        CompletableFuture<ToolResult> response = new CompletableFuture<>();

        @Override
        public CompletableFuture<ToolResult> dispatch(String callId, String toolName,
                                                       Directive directive) {
            receivedCallIds.add(callId);
            receivedToolNames.add(toolName);
            receivedDirectives.add(directive);
            return response;
        }
    }

    // ==================== Constructor validation ====================

    @Test
    void constructor_nullName_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClientToolWrapper(null, "desc", ToolSchema.empty(), new RecordingDispatcher()));
    }

    @Test
    void constructor_emptyName_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClientToolWrapper("", "desc", ToolSchema.empty(), new RecordingDispatcher()));
    }

    @Test
    void constructor_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClientToolWrapper("   ", "desc", ToolSchema.empty(), new RecordingDispatcher()));
    }

    @Test
    void constructor_nullDispatcher_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClientToolWrapper("take_photo", "desc", ToolSchema.empty(), null));
    }

    @Test
    void constructor_nullDescription_usesEmptyString() {
        ClientToolWrapper tool = new ClientToolWrapper(
            "take_photo", null, ToolSchema.empty(), new RecordingDispatcher());
        assertEquals("", tool.getDescription());
    }

    @Test
    void constructor_nullSchema_usesEmptySchema() {
        ClientToolWrapper tool = new ClientToolWrapper(
            "take_photo", "desc", null, new RecordingDispatcher());
        assertNotNull(tool.getSchema());
    }

    @Test
    void constructor_zeroOrNegativeTimeout_usesDefault() {
        ClientToolWrapper withZero = new ClientToolWrapper(
            "t", "desc", ToolSchema.empty(), new RecordingDispatcher(), 0);
        ClientToolWrapper withNegative = new ClientToolWrapper(
            "t", "desc", ToolSchema.empty(), new RecordingDispatcher(), -5);

        assertEquals(60_000L, withZero.getTimeoutMs());
        assertEquals(60_000L, withNegative.getTimeoutMs());
    }

    @Test
    void constructor_positiveTimeout_isRetained() {
        ClientToolWrapper tool = new ClientToolWrapper(
            "t", "desc", ToolSchema.empty(), new RecordingDispatcher(), 5_000);
        assertEquals(5_000L, tool.getTimeoutMs());
    }

    @Test
    void constructor_defaultTimeoutConstructor_usesDefault() {
        ClientToolWrapper tool = new ClientToolWrapper(
            "t", "desc", ToolSchema.empty(), new RecordingDispatcher());
        assertEquals(60_000L, tool.getTimeoutMs());
    }

    // ==================== execute() happy path ====================

    @Test
    void execute_success_returnsResultFromDispatcher() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ClientToolWrapper tool = new ClientToolWrapper(
            "Camera.TakePhoto", "Take a photo", ToolSchema.empty(), dispatcher, 5_000);

        // Pre-complete the future so execute() returns immediately
        dispatcher.response.complete(ToolResult.success("photo.jpg"));

        ToolResult result = tool.execute(Map.of("resolution", "1080p"));

        assertFalse(result.isError());
        assertEquals("photo.jpg", result.getContent());

        // Verify dispatcher was called with correctly constructed directive
        assertEquals(1, dispatcher.receivedCallIds.size());
        assertEquals("Camera.TakePhoto", dispatcher.receivedToolNames.get(0));
        Directive d = dispatcher.receivedDirectives.get(0);
        assertEquals("Camera", d.getNamespace());
        assertEquals("TakePhoto", d.getName());
        assertEquals("1080p", d.getPayload().get("resolution"));
        assertEquals(5_000, d.getTimeoutMs());
        // callId must be propagated as directiveId
        assertEquals(dispatcher.receivedCallIds.get(0), d.getDirectiveId());
    }

    @Test
    void execute_callIdIsUnique() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ClientToolWrapper tool = new ClientToolWrapper(
            "t", "d", ToolSchema.empty(), dispatcher, 1_000);

        // First call
        dispatcher.response.complete(ToolResult.success("a"));
        tool.execute(Map.of());

        // Second call with fresh future
        dispatcher.response = CompletableFuture.completedFuture(ToolResult.success("b"));
        tool.execute(Map.of());

        assertEquals(2, dispatcher.receivedCallIds.size());
        assertNotEquals(dispatcher.receivedCallIds.get(0), dispatcher.receivedCallIds.get(1));
    }

    @Test
    void execute_errorResultFromDispatcher_isPreserved() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ClientToolWrapper tool = new ClientToolWrapper(
            "t", "d", ToolSchema.empty(), dispatcher, 1_000);
        dispatcher.response.complete(ToolResult.error("Permission denied"));

        ToolResult result = tool.execute(Map.of());

        assertTrue(result.isError());
        assertEquals("Permission denied", result.getContent());
    }

    // ==================== execute() timeout path ====================

    @Test
    void execute_timeout_returnsErrorResult() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        // Future never completes — trigger timeout
        ClientToolWrapper tool = new ClientToolWrapper(
            "slow_tool", "desc", ToolSchema.empty(), dispatcher, 50);

        ToolResult result = tool.execute(Map.of());

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("超时"),
            "Expected timeout error, got: " + result.getContent());
        assertTrue(result.getContent().contains("slow_tool"));
    }

    // ==================== execute() exception path ====================

    @Test
    void execute_dispatcherFutureFails_returnsError() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        dispatcher.response.completeExceptionally(new RuntimeException("boom"));

        ClientToolWrapper tool = new ClientToolWrapper(
            "t", "d", ToolSchema.empty(), dispatcher, 1_000);
        ToolResult result = tool.execute(Map.of());

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("失败"));
    }

    @Test
    void execute_withNullArgs_stillDispatches() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        dispatcher.response.complete(ToolResult.success("ok"));

        ClientToolWrapper tool = new ClientToolWrapper(
            "t", "d", ToolSchema.empty(), dispatcher, 1_000);
        ToolResult result = tool.execute(null);

        assertFalse(result.isError());
        assertNotNull(dispatcher.receivedDirectives.get(0));
    }

    // ==================== Tool interface ====================

    @Test
    void getters_returnConstructorValues() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ToolSchema schema = ToolSchema.empty();
        ClientToolWrapper tool = new ClientToolWrapper(
            "my_tool", "my description", schema, dispatcher, 2_500);

        assertEquals("my_tool", tool.getName());
        assertEquals("my description", tool.getDescription());
        assertSame(schema, tool.getSchema());
        assertEquals(2_500L, tool.getTimeoutMs());
    }

    @Test
    void isAutoExecute_returnsFalse() {
        ClientToolWrapper tool = new ClientToolWrapper(
            "t", "d", ToolSchema.empty(), new RecordingDispatcher());
        // Client tools always require confirmation
        assertFalse(tool.isAutoExecute());
    }

    // ==================== ToolMetadata ====================

    @Test
    void metadata_reportsClientSide() {
        ClientToolWrapper tool = new ClientToolWrapper(
            "t", "d", ToolSchema.empty(), new RecordingDispatcher());

        assertEquals("client", tool.getCategory());
        assertTrue(tool.getTags().contains("client"));
        assertTrue(tool.getTags().contains("remote"));
        assertTrue(tool.getTags().contains("async"));
        assertEquals("client-side", tool.getAuthor());
        assertTrue(tool.isOpenWorld());
        assertTrue(tool.isClientSide());
    }

    @Test
    void toString_includesNameAndTimeout() {
        ClientToolWrapper tool = new ClientToolWrapper(
            "Camera.TakePhoto", "d", ToolSchema.empty(), new RecordingDispatcher(), 30_000);
        String str = tool.toString();
        assertTrue(str.contains("Camera.TakePhoto"));
        assertTrue(str.contains("30000"));
    }
}
