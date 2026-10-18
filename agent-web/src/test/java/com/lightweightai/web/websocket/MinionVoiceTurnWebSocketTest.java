package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.postprocess.SpeakableChunkProcessor;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.orchestrator.*;
import com.lightweightai.safety.CrisisDetector;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.web.socket.config.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real Tomcat/Spring/Gateway/Orchestrator/AgentLoop; fake only the remote LLM. */
class MinionVoiceTurnWebSocketTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static ServletWebServerApplicationContext context;
    static String url;
    static RemoteLlm remote;

    record Request(List<ConversationMessage> messages, FluxSink<StreamEvent> sink) {
        void callTool(String token) {
            sink.next(StreamEvent.llmComplete(LLMResponse.builder()
                    .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("").build())
                    .toolCalls(List.of(new ToolCall("call-" + token, "device_probe", Map.of("token", token))))
                    .stopReason("tool_use").build()));
            sink.complete();
        }
        void finish(String text) {
            sink.next(StreamEvent.textDelta(text));
            sink.next(StreamEvent.llmComplete(LLMResponse.builder()
                    .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(text).build()).stopReason("end_turn").build()));
            sink.complete();
        }
    }

    static class RemoteLlm {
        final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
        final BlockingQueue<ChildRequest> children = new LinkedBlockingQueue<>();
        final LLMProvider provider = mock(LLMProvider.class);
        RemoteLlm() {
            when(provider.complete(any(), any())).thenAnswer(invocation -> {
                var reply = new CompletableFuture<LLMResponse>();
                children.add(new ChildRequest(List.copyOf(invocation.getArgument(0)), reply));
                return reply.join();
            });
            when(provider.completeStreamReactive(any(), any())).thenAnswer(invocation -> {
                List<ConversationMessage> messages = List.copyOf(invocation.getArgument(0));
                return Flux.<StreamEvent>create(sink -> requests.add(new Request(messages, sink)));
            });
        }
        Request next() throws Exception {
            Request request = requests.poll(5, TimeUnit.SECONDS);
            assertNotNull(request, "The actual LLM consumer must receive this input");
            return request;
        }
        ChildRequest nextChild() throws Exception {
            ChildRequest request = children.poll(5, TimeUnit.SECONDS);
            assertNotNull(request, "Child LLM must receive the actual task or tool result");
            return request;
        }
    }

    record ChildRequest(List<ConversationMessage> messages, CompletableFuture<LLMResponse> reply) {}

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableWebSocket
    static class Server implements WebSocketConfigurer {
        @Bean RemoteLlm remoteLlm() { return new RemoteLlm(); }
        @Bean SessionAwareClientToolDispatcher legacyDispatcher() { return new SessionAwareClientToolDispatcher(); }
        @Bean ToolRegistry tools(SessionAwareClientToolDispatcher legacy) {
            ToolRegistry tools = new ToolRegistry();
            tools.register(new ProbeTool(legacy));
            return tools;
        }
        @Bean AgentRegistry agents() {
            AgentRegistry registry = new AgentRegistry();
            registry.register(AgentProfile.builder().agentId("minion").systemPrompt("Assistant").maxSpawnDepth(2).build());
            registry.setDefault("minion");
            return registry;
        }
        @Bean AgentFactory factory(RemoteLlm llm, ToolRegistry tools) {
            return new AgentFactory(llm.provider, new InMemoryProvider(), tools);
        }
        @Bean(destroyMethod = "shutdown") SubagentRuntime subagents(AgentFactory factory, AgentRegistry agents) {
            return new SubagentRuntime(factory, agents, 4);
        }
        @Bean(destroyMethod = "shutdown") Orchestrator orchestrator(AgentFactory factory, AgentRegistry agents, SubagentRuntime subagents) {
            return new Orchestrator(agents, factory, new MetadataAgentRouter(agents), subagents);
        }
        @Bean SpringChatWebSocketHandler handler(Orchestrator orchestrator, ToolRegistry tools, SessionAwareClientToolDispatcher legacy) {
            Gateway gateway = Gateway.builder().chatHandler(orchestrator)
                    .addPostProcessor(new SpeakableChunkProcessor()).build();
            return new SpringChatWebSocketHandler(gateway, new CrisisDetector(), tools,
                    null, null, null, null, null, null, legacy,
                    null, null, null, null);
        }
        @org.springframework.beans.factory.annotation.Autowired org.springframework.beans.factory.ObjectProvider<SpringChatWebSocketHandler> handler;
        @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(handler.getObject(), "/ws/chat").setAllowedOrigins("*");
        }
    }

    @com.lightweightai.kernel.agent.annotation.ClientTool(toolName = "Probe", aliases = {"device_probe"},
            namespace = "Device", downAction = "Read", upAction = "Report", timeoutMs = 5000)
    static class ProbeTool extends ClientTool<Map<String, Object>> {
        ProbeTool(ClientToolDispatcher dispatcher) { super(dispatcher); }
        protected Map<String, Object> buildPayload(Map<String, Object> args) {
            return Map.of("requestToken", args.get("token"));
        }
        protected Map<String, Object> parseResult(ToolResult result) {
            if (result.isError()) throw new IllegalStateException(result.getContent());
            return result.getStructuredContent();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void foregroundInterruptOrDisconnectStopsItsChild(boolean disconnect) throws Exception {
        ChildRequest child = null;
        try (Peer peer = new Peer()) {
            String session = "child-owner-" + disconnect;
            peer.send(JSON.writeValueAsString(Map.of("type", "chat", "sessionId", session, "turnId", "A", "message", "delegate")));
            Request root = remote.next();
            root.sink().next(StreamEvent.llmComplete(LLMResponse.builder()
                    .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT).textContent("").build())
                    .toolCalls(List.of(new ToolCall("spawn", "spawn_subagent", Map.of("task", "child work"))))
                    .stopReason("tool_use").build()));
            root.sink().complete();
            Request waiting = remote.next();
            CompletableFuture<Void> parentCancelled = new CompletableFuture<>();
            waiting.sink().onCancel(() -> parentCancelled.complete(null));
            String result = waiting.messages().stream().filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                    .map(ConversationMessage::getTextContent).findFirst().orElseThrow();
            var matcher = java.util.regex.Pattern.compile("Run ID: ([a-zA-Z0-9-]+)").matcher(result);
            assertTrue(matcher.find(), result);
            String id = matcher.group(1);
            child = remote.nextChild();
            assertTrue(child.messages().stream().anyMatch(m -> "child work".equals(m.getTextContent())));
            if (disconnect) {
                peer.socket.sendClose(WebSocket.NORMAL_CLOSURE, "leave").join();
            } else {
                peer.send(JSON.writeValueAsString(Map.of("type", "interrupt", "sessionId", session, "turnId", "A")));
                assertEquals("A", peer.until("speech_interrupted").path("turnId").asText());
            }
            SubagentRuntime runtime = context.getBean(SubagentRuntime.class);
            parentCancelled.get(5, TimeUnit.SECONDS);
            assertTrue(runtime.waitForCompletion(id, 5, TimeUnit.SECONDS), "child did not receive transport cancellation");
            assertEquals(SubagentRun.Status.CANCELLED, runtime.getRunOrCompleted(id).getStatus());
            assertTrue(waiting.sink().isCancelled());
        } finally {
            if (child != null) child.reply().complete(LLMResponse.builder()
                    .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT).textContent("late child").build())
                    .stopReason("end_turn").build());
        }
    }

    @Test void interleavedConnectionsReceiveTheirOwnTransformedDirectiveAndResult() throws Exception {
        try (Peer a = new Peer()) {
            a.send("{\"type\":\"chat\",\"sessionId\":\"device-A\",\"message\":\"read A\"}");
            Request aRequest = remote.next();
            try (Peer b = new Peer()) {
                b.send("{\"type\":\"chat\",\"sessionId\":\"device-B\",\"message\":\"read B\"}");
                Request bRequest = remote.next();
                aRequest.callTool("A");
                JsonNode aDirective = a.directive();
                assertEquals("A", aDirective.path("payload").path("requestToken").asText());
                assertEquals("Device", aDirective.path("header").path("namespace").asText());
                assertEquals("Read", aDirective.path("header").path("name").asText());
                bRequest.callTool("B");
                JsonNode bDirective = b.directive();
                assertEquals("B", bDirective.path("payload").path("requestToken").asText());
                b.reply(bDirective, "result-B");
                Request bResult = remote.next();
                assertTrue(bResult.messages().stream().anyMatch(m -> m.getTextContent().contains("result-B")));
                assertFalse(bResult.messages().stream().anyMatch(m -> m.getTextContent().contains("result-A")));
                bResult.finish("B done");
                b.until("stream_end");
                a.reply(aDirective, "result-A");
                Request aResult = remote.next();
                assertTrue(aResult.messages().stream().anyMatch(m -> m.getTextContent().contains("result-A")));
                assertFalse(aResult.messages().stream().anyMatch(m -> m.getTextContent().contains("result-B")));
                aResult.finish("A done");
                a.until("stream_end");
            }
        }
    }

    @Test void cancellingPendingClientToolFreesConnectionForTheNextTurn() throws Exception {
        try (Peer peer = new Peer()) {
            peer.send("{\"type\":\"chat\",\"sessionId\":\"pending-tool\",\"turnId\":\"tool-A\",\"message\":\"A\"}");
            remote.next().callTool("A");
            JsonNode aDirective = peer.directive();
            peer.send("{\"type\":\"interrupt\",\"sessionId\":\"pending-tool\",\"turnId\":\"tool-A\"}");
            peer.until("speech_interrupted");
            peer.send("{\"type\":\"chat\",\"sessionId\":\"pending-tool\",\"turnId\":\"tool-B\",\"message\":\"B\"}");
            remote.next().callTool("B");
            JsonNode bDirective = peer.directive();
            assertEquals("B", bDirective.path("payload").path("requestToken").asText());
            assertNotEquals(aDirective.path("directiveId"), bDirective.path("directiveId"));
            peer.reply(aDirective, "stale-result-A");
            peer.reply(bDirective, "fresh-result-B");
            Request result = remote.next();
            assertTrue(result.messages().stream().anyMatch(m -> m.getTextContent().contains("fresh-result-B")));
            assertFalse(result.messages().stream().anyMatch(m -> m.getTextContent().contains("stale-result-A")));
            result.finish("B done");
            assertEquals("tool-B", peer.until("stream_end").path("meta").path("turnId").asText());
        }
    }

    @Test void implicitResumeUsesTheNewRequestsConnection() throws Exception {
        try (Peer a = new Peer()) {
            a.send("{\"type\":\"chat\",\"sessionId\":\"handoff\",\"message\":\"A\"}");
            Request aRequest = remote.next();
            try (Peer b = new Peer()) {
                b.send("{\"type\":\"chat\",\"sessionId\":\"handoff\",\"message\":\"B\"}");
                Request bRequest = remote.next();
                assertTrue(aRequest.sink().isCancelled());
                bRequest.callTool("B");
                JsonNode directive = b.directive();
                assertEquals("B", directive.path("payload").path("requestToken").asText());
                b.reply(directive, "new-connection-result");
                Request result = remote.next();
                assertTrue(result.messages().stream().anyMatch(m -> m.getTextContent().contains("new-connection-result")));
                result.finish("done");
                b.until("stream_end");
            }
        }
    }

    @BeforeAll static void startServer() {
        context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(Server.class)
                .properties("server.port=0", "spring.main.banner-mode=off",
                        "spring.config.location=optional:classpath:/voice-turn-test-unused.properties",
                        "logging.level.root=WARN")
                .run();
        remote = context.getBean(RemoteLlm.class);
        url = "ws://127.0.0.1:" + context.getWebServer().getPort() + "/ws/chat";
    }
    @AfterAll static void stopServer() { if (context != null) context.close(); }

    static class Peer implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final StringBuilder text = new StringBuilder();
        final HttpClient client = HttpClient.newHttpClient();
        final WebSocket socket = client.newWebSocketBuilder().buildAsync(URI.create(url), this).join();
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            text.append(data);
            if (last) { frames.add(text.toString()); text.setLength(0); }
            ws.request(1);
            return null;
        }
        void send(String message) { socket.sendText(message, true).join(); }
        JsonNode directive() throws Exception {
            JsonNode envelope = until("DIRECTIVE").path("data");
            com.fasterxml.jackson.databind.node.ObjectNode primary = envelope.path("directives").get(0).deepCopy();
            primary.put("directiveId", envelope.path("directive_id").asText());
            return primary;
        }
        void reply(JsonNode directive, String value) throws Exception {
            String content = JSON.writeValueAsString(Map.of(
                    "header", Map.of("namespace", "Device", "name", "Report"),
                    "payload", Map.of("value", value)));
            send(JSON.writeValueAsString(Map.of("type", "directive_result", "success", true,
                    "directiveId", directive.path("directiveId").asText(), "content", content)));
        }
        JsonNode until(String type) throws Exception {
            List<String> observed = new java.util.ArrayList<>();
            while (true) {
                String frame = frames.poll(5, TimeUnit.SECONDS);
                assertNotNull(frame, "Missing frame: " + type + "; received: " + observed);
                observed.add(frame);
                JsonNode json = JSON.readTree(frame);
                if (type.equals(json.path("type").asText())) return json;
                assertNotEquals("error", json.path("type").asText(), frame);
            }
        }
        @Override public void close() {
            if (!socket.isOutputClosed()) socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            client.close();
        }
    }

    @Test void interruptBeforeAudioThenCompleteBAndIgnoreStaleInterrupt() throws Exception {
        try (Peer peer = new Peer()) {
            peer.send("""
                    {"type":"chat","sessionId":"wire","turnId":"A","message":"A"}
                    """);
            Request a = remote.next();
            peer.send("""
                    {"type":"interrupt","sessionId":"wire","turnId":"A"}
                    """);
            assertEquals("A", peer.until("speech_interrupted").path("turnId").asText());
            assertTrue(a.sink().isCancelled());
            peer.send("""
                    {"type":"chat","sessionId":"wire","turnId":"B","message":"先停一下。再说第二件事。"}
                    """);
            Request b = remote.next();
            assertEquals(1, b.messages().stream()
                    .filter(m -> "先停一下。再说第二件事。".equals(m.getTextContent())).count());
            peer.send("""
                    {"type":"interrupt","sessionId":"wire","turnId":"A"}
                    """);
            assertEquals("A", peer.until("speech_interrupted").path("turnId").asText());
            assertFalse(b.sink().isCancelled(), "Late A interruption must not cancel B");
            a.finish("A late。");
            b.finish("B reply。");
            JsonNode chunk = peer.until("speakable_chunk");
            assertEquals("B", chunk.path("turnId").asText());
            assertEquals("B reply。", chunk.path("data").path("text").asText());
            assertEquals("B", peer.until("stream_end").path("meta").path("turnId").asText());
        }
    }

    @Test void actualPythonGatewayRoundTrip() throws Exception {
        String root = System.getProperty("voice.gateway.root");
        Assumptions.assumeTrue(root != null, "Set voice.gateway.root to run the cross-repository Python client");
        ProcessBuilder builder = new ProcessBuilder(System.getProperty("voice.gateway.python", "python3"),
                Path.of(root, "tests", "probe_runner_turn.py").toString(), url);
        builder.environment().put("PYTHONPATH", Path.of(root, "src").toString());
        builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");
        builder.environment().put("NO_PROXY", "127.0.0.1,localhost");
        builder.environment().put("no_proxy", "127.0.0.1,localhost");
        Process process = builder.redirectErrorStream(true).start();
        try {
            Request a = remote.next();
            a.sink().next(StreamEvent.textDelta("A first。"));
            Request b = remote.next();
            assertEquals(1, b.messages().stream()
                    .filter(m -> "先停一下。再说第二件事。".equals(m.getTextContent())).count());
            assertTrue(a.sink().isCancelled());
            b.finish("B reply。");
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
        } finally {
            process.destroyForcibly();
        }
    }

    @Test void failureBeforeAudioEndsTheCorrectTurnAndAllowsTheNextInput() throws Exception {
        try (Peer peer = new Peer()) {
            peer.send("""
                    {"type":"chat","sessionId":"failure","turnId":"failed-A","message":"A"}
                    """);
            remote.next().sink().error(new IllegalStateException("remote disconnected"));
            JsonNode error = peer.until("error");
            assertEquals("failed-A", error.path("turnId").asText());
            assertTrue(error.path("message").asText().contains("remote disconnected"));

            peer.send("""
                    {"type":"chat","sessionId":"failure","turnId":"recovered-B","message":"完整的新问题。"}
                    """);
            Request b = remote.next();
            assertEquals(1, b.messages().stream()
                    .filter(m -> "完整的新问题。".equals(m.getTextContent())).count());
            b.finish("Recovered reply。");
            JsonNode chunk = peer.until("speakable_chunk");
            assertEquals("recovered-B", chunk.path("turnId").asText());
            assertEquals("Recovered reply。", chunk.path("data").path("text").asText());
            assertEquals("recovered-B", peer.until("stream_end").path("meta").path("turnId").asText());
        }
    }

    @Test void interruptAfterGenerationStillAcknowledgesLocalPlaybackCancellation() throws Exception {
        try (Peer peer = new Peer()) {
            peer.send("""
                    {"type":"chat","sessionId":"playback","turnId":"generated-A","message":"A"}
                    """);
            remote.next().finish("Already generated。");
            assertEquals("generated-A", peer.until("stream_end").path("meta").path("turnId").asText());

            peer.send("""
                    {"type":"interrupt","sessionId":"playback","turnId":"generated-A"}
                    """);
            JsonNode receipt = peer.until("speech_interrupted");
            assertEquals("generated-A", receipt.path("turnId").asText());
            assertEquals("barge-in", receipt.path("data").path("reason").asText());
            assertEquals("", receipt.path("data").path("runId").asText(),
                    "Completed generation has no active brain run to cancel");

            peer.send("""
                    {"type":"chat","sessionId":"playback","turnId":"playback-B","message":"下一件事。"}
                    """);
            Request b = remote.next();
            assertEquals(1, b.messages().stream()
                    .filter(m -> "下一件事。".equals(m.getTextContent())).count());
            b.finish("Next reply。");
            JsonNode chunk = peer.until("speakable_chunk");
            assertEquals("playback-B", chunk.path("turnId").asText());
            assertEquals("Next reply。", chunk.path("data").path("text").asText());
            assertEquals("playback-B", peer.until("stream_end").path("meta").path("turnId").asText());
        }
    }
}
