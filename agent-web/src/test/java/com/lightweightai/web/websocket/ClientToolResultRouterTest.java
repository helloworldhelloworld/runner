package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientToolResultRouterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void clientToolResultWithoutCallIdShouldRejectInsteadOfGuessing() {
        VertxWebSocketClientToolDispatcher dispatcher = mock(VertxWebSocketClientToolDispatcher.class);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("content", "ok");
        payload.put("isError", false);

        ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
        assertFalse(router.routeClientToolResult(dispatcher, payload));

        verify(dispatcher, never()).completeLatestCall(any(ToolResult.class));
        verify(dispatcher, never()).completeCall(any(), any());
    }

    @Test
    void directiveResultWithIdShouldUseCompleteCall() {
        VertxWebSocketClientToolDispatcher dispatcher = mock(VertxWebSocketClientToolDispatcher.class);
        when(dispatcher.completeCall(eq("d-1"), any(ToolResult.class))).thenReturn(true);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("directiveId", "d-1");
        payload.put("success", true);
        payload.put("content", "done");

        ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
        assertTrue(router.routeDirectiveResult(dispatcher, payload));

        verify(dispatcher).completeCall(eq("d-1"), any(ToolResult.class));
    }

    @Test
    void directiveResultWithoutIdShouldRejectInsteadOfGuessing() {
        VertxWebSocketClientToolDispatcher dispatcher = mock(VertxWebSocketClientToolDispatcher.class);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("success", true);
        payload.put("content", "done");

        ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
        assertFalse(router.routeDirectiveResult(dispatcher, payload));

        verify(dispatcher, never()).completeLatestCall(any(ToolResult.class), any());
        verify(dispatcher, never()).completeCall(any(), any());
    }

}
