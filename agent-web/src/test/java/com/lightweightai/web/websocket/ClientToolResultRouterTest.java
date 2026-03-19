package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ClientTool;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientToolResultRouterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void clientToolResultWithoutCallIdShouldFallbackToLatest() {
        VertxWebSocketClientToolDispatcher dispatcher = mock(VertxWebSocketClientToolDispatcher.class);
        when(dispatcher.completeLatestCall(any(ToolResult.class))).thenReturn(true);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("content", "ok");
        payload.put("isError", false);

        ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
        assertTrue(router.routeClientToolResult(dispatcher, payload));

        verify(dispatcher).completeLatestCall(any(ToolResult.class));
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
    void directiveResultWithoutIdShouldMatchByUpActionToDownAction() {
        VertxWebSocketClientToolDispatcher dispatcher = mock(VertxWebSocketClientToolDispatcher.class);
        when(dispatcher.completeLatestCall(any(ToolResult.class), eq("GeoInformation.GetPosition")))
            .thenReturn(true);

        ToolRegistry registry = new ToolRegistry();
        registry.register(new DummyGetPositionTool());

        ObjectNode payload = mapper.createObjectNode();
        payload.put("success", true);
        payload.put("content", "done");
        ObjectNode header = payload.putObject("header");
        header.put("namespace", "GeoInformation");
        header.put("name", "PositionInfo");

        ClientToolResultRouter router = new ClientToolResultRouter(mapper, registry);
        assertTrue(router.routeDirectiveResult(dispatcher, payload));

        verify(dispatcher).completeLatestCall(any(ToolResult.class), eq("GeoInformation.GetPosition"));
    }

    @ClientTool(
        toolName = "GetPosition",
        downAction = "GetPosition",
        upAction = "PositionInfo",
        namespace = "GeoInformation"
    )
    private static class DummyGetPositionTool implements com.lightweightai.kernel.agent.Tool {

        @Override
        public String getName() {
            return "GeoInformation.GetPosition";
        }

        @Override
        public String getDescription() {
            return "dummy";
        }

        @Override
        public com.lightweightai.kernel.agent.ToolSchema getSchema() {
            return com.lightweightai.kernel.agent.ToolSchema.empty();
        }

        @Override
        public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("ok");
        }
    }
}
