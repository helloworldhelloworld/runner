package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.directive.ClientCapability;
import com.lightweightai.kernel.agent.directive.ClientManifest;
import com.lightweightai.kernel.agent.directive.DefaultDirectiveManager;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VertxWebSocketClientToolDispatcherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldRejectSecondDispatchWhenSingleFlightPending() {
        ServerWebSocket ws = mock(ServerWebSocket.class);
        when(ws.isClosed()).thenReturn(false);
        when(ws.writeTextMessage(anyString())).thenReturn(Future.succeededFuture());

        VertxWebSocketClientToolDispatcher dispatcher = new VertxWebSocketClientToolDispatcher(ws);

        var first = dispatcher.dispatch("c1", "GetPosition",
            Directive.fromToolName("c1", "GetPosition", Map.of("precision", 3), 0));
        var second = dispatcher.dispatch("c2", "GetPosition",
            Directive.fromToolName("c2", "GetPosition", Map.of("precision", 3), 0));

        CompletionException ex = assertThrows(CompletionException.class, second::join);
        assertTrue(ex.getCause().getMessage().contains("busy"));

        assertTrue(dispatcher.completeCall("c1", ToolResult.success("ok")));
        assertEquals(0, dispatcher.pendingCount());
        assertFalse(first.isCompletedExceptionally());
    }

    @Test
    void shouldFallbackCompleteLatestWhenIdMissing() {
        ServerWebSocket ws = mock(ServerWebSocket.class);
        when(ws.isClosed()).thenReturn(false);
        when(ws.writeTextMessage(anyString())).thenReturn(Future.succeededFuture());

        VertxWebSocketClientToolDispatcher dispatcher = new VertxWebSocketClientToolDispatcher(ws);

        var pending = dispatcher.dispatch("c1", "GetPosition",
            Directive.fromToolName("c1", "GetPosition", Map.of("precision", 3), 0));

        assertTrue(dispatcher.completeLatestCall(ToolResult.success("ok")));
        assertEquals("ok", pending.join().getContent());
        assertEquals(0, dispatcher.pendingCount());
    }

    @Test
    void shouldSerializeDirectiveWithHeaderAndDirectivesArray() throws Exception {
        ServerWebSocket ws = mock(ServerWebSocket.class);
        when(ws.isClosed()).thenReturn(false);
        when(ws.writeTextMessage(anyString())).thenReturn(Future.succeededFuture());

        VertxWebSocketClientToolDispatcher dispatcher = new VertxWebSocketClientToolDispatcher(ws);
        dispatcher.setManifest(new ClientManifest(
            "android", "1.0.0",
            List.of(new ClientCapability("GeoInformation", "GetPosition", "desc", Map.of(), 3000L))
        ));

        dispatcher.dispatch("c1", "GetPosition",
            new Directive("c1", "GeoInformation", "GetPosition", Map.of("precision", 3), 0));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(ws).writeTextMessage(jsonCaptor.capture());
        JsonNode root = MAPPER.readTree(jsonCaptor.getValue());

        assertEquals("DIRECTIVE", root.path("type").asText());
        assertEquals("c1", root.path("request_id").asText());
        assertEquals("c1", root.path("data").path("directive_id").asText());
        assertTrue(root.path("data").path("directives").isArray());
        assertEquals(1, root.path("data").path("directives").size());
        assertEquals("GeoInformation",
            root.path("data").path("directives").get(0).path("header").path("namespace").asText());
        assertEquals("GetPosition",
            root.path("data").path("directives").get(0).path("header").path("name").asText());
        assertEquals(3,
            root.path("data").path("directives").get(0).path("payload").path("precision").asInt());
    }

    @Test
    void shouldAppendCommonDirectiveViaCustomDirectiveManager() throws Exception {
        ServerWebSocket ws = mock(ServerWebSocket.class);
        when(ws.isClosed()).thenReturn(false);
        when(ws.writeTextMessage(anyString())).thenReturn(Future.succeededFuture());

        DefaultDirectiveManager customManager = new DefaultDirectiveManager();
        customManager.addCommonProvider(primary ->
            new Directive(primary.getDirectiveId(), "UserInteraction", "DisplayLoadingCard",
                Map.of("state", "open"), 0)
        );

        VertxWebSocketClientToolDispatcher dispatcher =
            new VertxWebSocketClientToolDispatcher(ws, customManager);
        dispatcher.setManifest(new ClientManifest(
            "android", "1.0.0",
            List.of(new ClientCapability("GeoInformation", "GetPosition", "desc", Map.of(), 3000L))
        ));

        dispatcher.dispatch("c1", "GetPosition",
            new Directive("c1", "GeoInformation", "GetPosition", Map.of("precision", 3), 0));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(ws).writeTextMessage(jsonCaptor.capture());
        JsonNode root = MAPPER.readTree(jsonCaptor.getValue());
        JsonNode directives = root.path("data").path("directives");

        assertEquals(2, directives.size());
        assertEquals("GeoInformation", directives.get(0).path("header").path("namespace").asText());
        assertEquals("UserInteraction", directives.get(1).path("header").path("namespace").asText());
        assertEquals("DisplayLoadingCard", directives.get(1).path("header").path("name").asText());
    }

    @Test
    void shouldUseExternalWrappedDirectiveWhenManifestEnabled() throws Exception {
        ServerWebSocket ws = mock(ServerWebSocket.class);
        when(ws.isClosed()).thenReturn(false);
        when(ws.writeTextMessage(anyString())).thenReturn(Future.succeededFuture());

        VertxWebSocketClientToolDispatcher dispatcher = new VertxWebSocketClientToolDispatcher(ws);
        dispatcher.setManifest(new ClientManifest(
            "android", "1.0.0",
            List.of(new ClientCapability("GeoInformation", "GetPosition", "desc", Map.of(), 3000L))
        ));

        Directive wrapped = new Directive("external-id", "UserInteraction", "DisplayLoadingCard",
            Map.of("state", "open"), 5000);
        dispatcher.dispatch("c1", "GetPosition", wrapped);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(ws).writeTextMessage(jsonCaptor.capture());
        JsonNode root = MAPPER.readTree(jsonCaptor.getValue());

        assertEquals("external-id", root.path("data").path("directive_id").asText());
        assertEquals("UserInteraction",
            root.path("data").path("directives").get(0).path("header").path("namespace").asText());
        assertEquals("DisplayLoadingCard",
            root.path("data").path("directives").get(0).path("header").path("name").asText());
        assertEquals("open",
            root.path("data").path("directives").get(0).path("payload").path("state").asText());
    }
}
