package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ClientToolResultRouter - extended coverage")
class ClientToolResultRouterExtendedTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private ClientToolResultRouter router;
    private ToolCallCompleter dispatcher;

    @BeforeEach
    void setUp() {
        router = new ClientToolResultRouter(mapper, null);
        dispatcher = mock(ToolCallCompleter.class);
    }

    // ==================== routeClientToolResult ====================

    @Nested
    @DisplayName("routeClientToolResult")
    class RouteClientToolResult {

        @Test
        @DisplayName("routes success result with callId — payload content reaches dispatcher")
        void routesSuccessWithCallId() {
            when(dispatcher.completeCall(eq("c-1"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "c-1");
            payload.put("content", "42");
            payload.put("isError", false);

            assertTrue(router.routeClientToolResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("c-1"), captor.capture());
            ToolResult captured = captor.getValue();
            assertEquals("42", captured.getContent());
            assertFalse(captured.isError());
        }

        @Test
        @DisplayName("routes error result with callId — isError flag propagated")
        void routesErrorWithCallId() {
            when(dispatcher.completeCall(eq("c-err"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "c-err");
            payload.put("content", "connection timeout");
            payload.put("isError", true);

            assertTrue(router.routeClientToolResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("c-err"), captor.capture());
            assertTrue(captor.getValue().isError());
        }

        @Test
        @DisplayName("missing callId returns false without calling dispatcher")
        void missingCallIdReturnsFalse() {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("content", "orphan");

            assertFalse(router.routeClientToolResult(dispatcher, payload));
            verify(dispatcher, never()).completeCall(any(), any());
            verify(dispatcher, never()).completeLatestCall(any());
        }

        @Test
        @DisplayName("empty callId string returns false")
        void emptyCallIdReturnsFalse() {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "");
            payload.put("content", "x");

            assertFalse(router.routeClientToolResult(dispatcher, payload));
            verify(dispatcher, never()).completeCall(any(), any());
        }

        @Test
        @DisplayName("isError defaults to false when absent")
        void isErrorDefaultsFalse() {
            when(dispatcher.completeCall(eq("c-2"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "c-2");
            payload.put("content", "ok");
            // no isError field

            router.routeClientToolResult(dispatcher, payload);

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("c-2"), captor.capture());
            assertFalse(captor.getValue().isError());
        }

        @Test
        @DisplayName("content defaults to empty string when absent")
        void contentDefaultsEmpty() {
            when(dispatcher.completeCall(eq("c-3"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "c-3");
            // no content field

            router.routeClientToolResult(dispatcher, payload);

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("c-3"), captor.capture());
            assertEquals("", captor.getValue().getContent());
        }

        @Test
        @DisplayName("null dispatcher throws NPE")
        void nullDispatcherThrows() {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "c-4");

            assertThrows(NullPointerException.class,
                    () -> router.routeClientToolResult(null, payload));
        }

        @Test
        @DisplayName("null payload throws NPE")
        void nullPayloadThrows() {
            assertThrows(NullPointerException.class,
                    () -> router.routeClientToolResult(dispatcher, null));
        }
    }

    // ==================== routeDirectiveResult ====================

    @Nested
    @DisplayName("routeDirectiveResult")
    class RouteDirectiveResult {

        @Test
        @DisplayName("successful directive with JSON content parses structuredContent")
        void successfulDirectiveWithJsonContent() {
            when(dispatcher.completeCall(eq("d-json"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-json");
            payload.put("success", true);
            payload.put("content", "{\"header\":{\"ns\":\"audio\"},\"payload\":{\"vol\":50}}");

            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-json"), captor.capture());
            ToolResult result = captor.getValue();
            assertFalse(result.isError());
            assertNotNull(result.getStructuredContent(), "JSON content should be parsed into structuredContent");
            assertTrue(result.getStructuredContent().containsKey("header"));
        }

        @Test
        @DisplayName("successful directive with plain text — no structuredContent")
        void successfulDirectiveWithPlainText() {
            when(dispatcher.completeCall(eq("d-plain"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-plain");
            payload.put("success", true);
            payload.put("content", "plain text response");

            router.routeDirectiveResult(dispatcher, payload);

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-plain"), captor.capture());
            assertNull(captor.getValue().getStructuredContent());
        }

        @Test
        @DisplayName("failed directive produces error ToolResult")
        void failedDirectiveProducesError() {
            when(dispatcher.completeCall(eq("d-fail"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-fail");
            payload.put("success", false);
            payload.put("content", "device not supported");

            router.routeDirectiveResult(dispatcher, payload);

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-fail"), captor.capture());
            assertTrue(captor.getValue().isError());
        }

        @Test
        @DisplayName("missing directiveId returns false without guessing")
        void missingDirectiveIdReturnsFalse() {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("success", true);
            payload.put("content", "done");

            assertFalse(router.routeDirectiveResult(dispatcher, payload));
            verify(dispatcher, never()).completeCall(any(), any());
        }

        @Test
        @DisplayName("malformed JSON in content does not produce structuredContent")
        void malformedJsonFallsBackToPlain() {
            when(dispatcher.completeCall(eq("d-bad"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-bad");
            payload.put("success", true);
            payload.put("content", "{broken json]]");

            router.routeDirectiveResult(dispatcher, payload);

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-bad"), captor.capture());
            assertNull(captor.getValue().getStructuredContent());
        }

        @Test
        @DisplayName("empty content with success true — result has empty string content")
        void emptyContentSuccess() {
            when(dispatcher.completeCall(eq("d-empty"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-empty");
            payload.put("success", true);
            payload.put("content", "");

            router.routeDirectiveResult(dispatcher, payload);

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-empty"), captor.capture());
            assertFalse(captor.getValue().isError());
        }
    }

    // ==================== Constructor validation ====================

    @Test
    @DisplayName("constructor rejects null objectMapper")
    void constructorRejectsNullObjectMapper() {
        assertThrows(NullPointerException.class,
                () -> new ClientToolResultRouter(null, null));
    }
}
