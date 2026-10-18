package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.gateway.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.orchestrator.*;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.web.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.web.socket.config.annotation.*;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real Spring WebSocket transport and native runtime; controlled downstream LLM with captured payloads. */
class AuthenticatedWebSocketAcceptanceTest {
    static final ObjectMapper JSON = new ObjectMapper();

    @Configuration
    @EnableAutoConfiguration
    @EnableWebSocket
    static class Server implements WebSocketConfigurer {
        @Bean AuthSessionService auth() { return new AuthSessionService(); }
        @Bean MinionVoiceTurnWebSocketTest.RemoteLlm remote() { return new MinionVoiceTurnWebSocketTest.RemoteLlm(); }
        @Bean InMemoryProvider memory() { return new InMemoryProvider(); }
        @Bean ToolRegistry tools() { return new ToolRegistry(); }
        @Bean AgentRegistry agents() {
            var registry = new AgentRegistry();
            registry.register(AgentProfile.builder().agentId("default").build()); registry.setDefault("default");
            return registry;
        }
        @Bean(destroyMethod = "shutdown") Orchestrator orchestrator() {
            return new Orchestrator(agents(), new AgentFactory(remote().provider, memory(), tools()), new MetadataAgentRouter(agents()));
        }
        @Bean Gateway gateway() {
            return Gateway.builder().chatHandler(orchestrator())
                    .sessionManager(new NativeSessionManager(memory(), agents(), orchestrator())).build();
        }
        @Bean SpringChatWebSocketHandler handler() {
            return new SpringChatWebSocketHandler(gateway(), new CrisisDetector(), tools(), null, null, null,
                    null, null, null, new SessionAwareClientToolDispatcher(), null, null, null, auth());
        }
        @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(handler(), "/ws/chat").setAllowedOrigins("*");
        }
    }

    @Test void authenticatedConnectionsIsolateExecutionHistoryAndObservation() throws Exception {
        try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(Server.class)
                .properties("server.port=0", "spring.main.banner-mode=off", "logging.level.root=WARN",
                        "spring.config.location=optional:classpath:/identity-test-unused.properties").run()) {
            String url = "ws://127.0.0.1:" + context.getWebServer().getPort() + "/ws/chat";
            var auth = context.getBean(AuthSessionService.class);
            String aToken = auth.createSessionToken("alice");
            String bToken = auth.createSessionToken("bob");
            var remote = context.getBean(MinionVoiceTurnWebSocketTest.RemoteLlm.class);
            try (var a = new Peer(url + "?token=" + aToken); var b = new Peer(url + "?token=" + bToken);
                 var viewer = new Peer(url + "?token=" + bToken)) {
                viewer.send(Map.of("type", "observe", "sessionId", "same"));
                viewer.until("observing");
                a.send(Map.of("type", "chat", "sessionId", "same", "message", "alice-secret work"));
                var aRequest = remote.next();
                b.send(Map.of("type", "chat", "sessionId", "same", "message", "bob work", "userId", "alice"));
                var bRequest = remote.next();
                String actual = bRequest.messages().stream().map(m -> m.getTextContent()).toList().toString();
                assertFalse(actual.contains("alice-secret"), actual);
                assertFalse(aRequest.sink().isCancelled(), "Bob must not cancel Alice");
                bRequest.finish("bob-answer");
                b.until("stream_end");
                b.send(Map.of("type", "interrupt", "sessionId", "same"));
                b.send(Map.of("type", "get_history", "sessionId", "same"));
                var history = b.until("history");
                assertTrue(history.toString().contains("bob work"), history.toString());
                assertFalse(history.toString().contains("alice-secret"), history.toString());
                assertFalse(aRequest.sink().isCancelled());
                aRequest.finish("alice-private-answer");
                a.until("stream_end");
                viewer.send(Map.of("type", "get_history", "sessionId", "same"));
                viewer.until("history");
                assertFalse(viewer.received.toString().contains("alice-private-answer"), viewer.received.toString());
                assertTrue(viewer.received.toString().contains("bob-answer"));
                auth.invalidate(bToken);
                b.send(Map.of("type", "get_history", "sessionId", "same"));
                assertEquals(1008, b.closed.get(5, TimeUnit.SECONDS));
            }
            try (var anonymous = new Peer(url)) { assertEquals(1008, anonymous.closed.get(5, TimeUnit.SECONDS)); }
        }
    }

    @Test void vertxUsesTheSameAuthenticatedNamespaceAndClosesInvalidTokens() throws Exception {
        var auth = new AuthSessionService();
        var remote = new MinionVoiceTurnWebSocketTest.RemoteLlm();
        var registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("default").build()); registry.setDefault("default");
        var memory = new InMemoryProvider();
        var tools = new ToolRegistry();
        var orchestrator = new Orchestrator(registry, new AgentFactory(remote.provider, memory, tools), new MetadataAgentRouter(registry));
        var gateway = Gateway.builder().chatHandler(orchestrator)
                .sessionManager(new NativeSessionManager(memory, registry, orchestrator)).build();
        var config = new com.lightweightai.web.config.VertxWebSocketConfig(auth);
        var handler = config.vertxChatWebSocketHandler(gateway, new CrisisDetector(), tools, null, null,
                null, null, null, remote.provider, null);
        var vertx = io.vertx.core.Vertx.vertx();
        var server = new VertxWebSocketServer(vertx, handler, 0);
        try {
            server.start();
            String url = "ws://127.0.0.1:" + server.getActualPort() + "/ws/chat";
            String aToken = auth.createSessionToken("alice");
            String bToken = auth.createSessionToken("bob");
            try (var a = new Peer(url + "?token=" + aToken); var b = new Peer(url + "?token=" + bToken)) {
                a.send(Map.of("type", "chat", "sessionId", "same", "message", "alice-secret work"));
                var aRequest = remote.next();
                b.send(Map.of("type", "chat", "sessionId", "same", "message", "bob work", "userId", "alice"));
                var bRequest = remote.next();
                assertFalse(bRequest.messages().stream().anyMatch(m -> m.getTextContent().contains("alice-secret")));
                assertFalse(aRequest.sink().isCancelled());
                bRequest.finish("bob-answer"); b.until("stream_end");
                b.send(Map.of("type", "interrupt", "sessionId", "same"));
                b.send(Map.of("type", "get_history", "sessionId", "same"));
                var history = b.until("history");
                assertTrue(history.toString().contains("bob work"), history.toString());
                assertFalse(history.toString().contains("alice-secret"));
                assertFalse(aRequest.sink().isCancelled());
                aRequest.finish("alice-answer"); a.until("stream_end");
                auth.invalidate(bToken);
                b.send(Map.of("type", "get_history", "sessionId", "same"));
                assertEquals(1008, b.closed.get(5, TimeUnit.SECONDS));
            }
            try (var anonymous = HttpClient.newHttpClient()) {
                var error = assertThrows(ExecutionException.class, () -> anonymous.newWebSocketBuilder()
                        .buildAsync(URI.create(url), new WebSocket.Listener() {}).get(5, TimeUnit.SECONDS));
                var handshake = assertInstanceOf(WebSocketHandshakeException.class, error.getCause());
                assertEquals(401, handshake.getResponse().statusCode());
            }
        } finally {
            server.stop(); vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            orchestrator.shutdown();
        }
    }

    static class Peer implements WebSocket.Listener, AutoCloseable {
        final HttpClient client = HttpClient.newHttpClient();
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final List<String> received = new CopyOnWriteArrayList<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder partial = new StringBuilder();
        final WebSocket socket;
        Peer(String url) {
            try { socket = client.newWebSocketBuilder().buildAsync(URI.create(url), this).join(); }
            catch (CompletionException failure) { client.close(); throw new AssertionError("WebSocket handshake failed", failure); }
        }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            partial.append(text);
            if (last) { String frame = partial.toString(); frames.add(frame); received.add(frame); partial.setLength(0); }
            socket.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) { closed.complete(status); return null; }
        void send(Map<String, Object> payload) throws Exception { socket.sendText(JSON.writeValueAsString(payload), true).join(); }
        JsonNode until(String type) throws Exception {
            while (true) {
                String frame = frames.poll(5, TimeUnit.SECONDS);
                assertNotNull(frame, "Missing " + type + ": " + received);
                JsonNode value = JSON.readTree(frame);
                if (type.equals(value.path("type").asText())) return value;
                assertNotEquals("error", value.path("type").asText(), frame);
            }
        }
        @Override public void close() {
            socket.abort(); client.close();
        }
    }
}
