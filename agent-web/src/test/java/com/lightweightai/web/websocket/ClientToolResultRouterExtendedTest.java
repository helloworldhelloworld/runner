package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

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

    @Nested
    @DisplayName("routeClientToolResult — payload assertions")
    class RouteClientToolResult {

        @Test
        @DisplayName("success result with callId delegates with correct content and isError=false")
        void successWithCallIdCapturesPayload() {
            AtomicReference<ToolResult> captured = new AtomicReference<>();
            when(dispatcher.completeCall(eq("call-1"), any(ToolResult.class))).thenAnswer(inv -> {
                captured.set(inv.getArgument(1));
                return true;
            });

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "call-1");
            payload.put("content", "42 degrees");
            payload.put("isError", false);

            assertTrue(router.routeClientToolResult(dispatcher, payload));

            ToolResult result = captured.get();
            assertNotNull(result, "ToolResult must be captured");
            assertEquals("42 degrees", result.getContent());
            assertFalse(result.isError());
        }

        @Test
        @DisplayName("error result with callId delegates with isError=true")
        void errorWithCallIdCapturesPayload() {
            AtomicReference<ToolResult> captured = new AtomicReference<>();
            when(dispatcher.completeCall(eq("call-2"), any(ToolResult.class))).thenAnswer(inv -> {
                captured.set(inv.getArgument(1));
                return true;
            });

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "call-2");
            payload.put("content", "GPS unavailable");
            payload.put("isError", true);

            assertTrue(router.routeClientToolResult(dispatcher, payload));

            ToolResult result = captured.get();
            assertTrue(result.isError());
            assertEquals("GPS unavailable", result.getContent());
        }

        @Test
        @DisplayName("missing content defaults to empty string")
        void missingContentDefaultsToEmpty() {
            AtomicReference<ToolResult> captured = new AtomicReference<>();
            when(dispatcher.completeCall(eq("call-3"), any(ToolResult.class))).thenAnswer(inv -> {
                captured.set(inv.getArgument(1));
                return true;
            });

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "call-3");

            router.routeClientToolResult(dispatcher, payload);

            assertEquals("", captured.get().getContent());
        }

        @Test
        @DisplayName("null dispatcher throws NullPointerException")
        void nullDispatcherThrows() {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "c1");
            assertThrows(NullPointerException.class,
                    () -> router.routeClientToolResult(null, payload));
        }

        @Test
        @DisplayName("null payload throws NullPointerException")
        void nullPayloadThrows() {
            assertThrows(NullPointerException.class,
                    () -> router.routeClientToolResult(dispatcher, null));
        }
    }

    @Nested
    @DisplayName("routeDirectiveResult — JSON content parsing")
    class RouteDirectiveResult {

        @Test
        @DisplayName("success directive with JSON content parses structuredContent")
        void successDirectiveWithJsonContent() {
            AtomicReference<ToolResult> captured = new AtomicReference<>();
            when(dispatcher.completeCall(eq("d-1"), any(ToolResult.class))).thenAnswer(inv -> {
                captured.set(inv.getArgument(1));
                return true;
            });

            String jsonContent = "{\"header\":{\"namespace\":\"audio\"},\"payload\":{\"volume\":80}}";
            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-1");
            payload.put("success", true);
            payload.put("content", jsonContent);

            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ToolResult result = captured.get();
            assertFalse(result.isError());
            assertTrue(result.hasStructuredContent(),
                    "JSON content should be parsed to structuredContent");
        }

        @Test
        @DisplayName("success directive with plain text content has no structuredContent")
        void successDirectiveWithPlainText() {
            AtomicReference<ToolResult> captured = new AtomicReference<>();
            when(dispatcher.completeCall(eq("d-2"), any(ToolResult.class))).thenAnswer(inv -> {
                captured.set(inv.getArgument(1));
                return true;
            });

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-2");
            payload.put("success", true);
            payload.put("content", "plain text result");

            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ToolResult result = captured.get();
            assertFalse(result.isError());
            assertFalse(result.hasStructuredContent());
            assertEquals("plain text result", result.getContent());
        }

        @Test
        @DisplayName("failed directive produces error ToolResult")
        void failedDirectiveProducesError() {
            AtomicReference<ToolResult> captured = new AtomicReference<>();
            when(dispatcher.completeCall(eq("d-3"), any(ToolResult.class))).thenAnswer(inv -> {
                captured.set(inv.getArgument(1));
                return true;
            });

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-3");
            payload.put("success", false);
            payload.put("content", "permission denied");

            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ToolResult result = captured.get();
            assertTrue(result.isError());
            assertEquals("permission denied", result.getContent());
        }

        @Test
        @DisplayName("malformed JSON in content falls back to plain text")
        void malformedJsonFallsBackToPlainText() {
            AtomicReference<ToolResult> captured = new AtomicReference<>();
            when(dispatcher.completeCall(eq("d-4"), any(ToolResult.class))).thenAnswer(inv -> {
                captured.set(inv.getArgument(1));
                return true;
            });

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-4");
            payload.put("success", true);
            payload.put("content", "{broken json");

            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ToolResult result = captured.get();
            assertFalse(result.hasStructuredContent());
            assertEquals("{broken json", result.getContent());
        }

        @Test
        @DisplayName("blank content is not parsed as JSON")
        void blankContentNotParsedAsJson() {
            AtomicReference<ToolResult> captured = new AtomicReference<>();
            when(dispatcher.completeCall(eq("d-5"), any(ToolResult.class))).thenAnswer(inv -> {
                captured.set(inv.getArgument(1));
                return true;
            });

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-5");
            payload.put("success", true);
            payload.put("content", "   ");

            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ToolResult result = captured.get();
            assertFalse(result.hasStructuredContent());
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("null objectMapper throws NullPointerException")
        void nullObjectMapperThrows() {
            assertThrows(NullPointerException.class,
                    () -> new ClientToolResultRouter(null, null));
        }

        @Test
        @DisplayName("null toolRegistry is allowed")
        void nullToolRegistryIsAllowed() {
            assertDoesNotThrow(() -> new ClientToolResultRouter(mapper, null));
        }
    }
}
