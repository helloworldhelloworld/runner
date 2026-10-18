package com.lightweightai.web.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.orchestrator.SubagentRuntime;
import com.lightweightai.web.controller.ModelConfigController;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring assembly, MVC configuration updates and Provider HTTP/SSE round trips. */
class ModelBindingAssemblyAcceptanceTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hotSwapKeepsActiveLoopAndLaterChildOnOriginalModel(boolean reactive) throws Exception {
        try (Fixture f = new Fixture()) {
            CompletableFuture<?> root = f.start("A", "root work", reactive);
            assertEquals(Map.of("marker", "root work"), f.gate.received.get(5, TimeUnit.SECONDS));
            assertEquals("model-v1", f.requests.get(0).path("model").asText());
            f.update("openrouter", "model-v2");
            // A concurrent new root must see v2 while A is still waiting on its tool.
            f.start("B", "new work", reactive).get(5, TimeUnit.SECONDS);
            f.gate.release.complete(ToolResult.success("gate result"));
            root.get(5, TimeUnit.SECONDS);

            JsonNode parentReply = f.forInput("root work").stream()
                    .filter(r -> r.path("messages").toString().contains("Run ID:")).findFirst().orElseThrow();
            var matcher = Pattern.compile("Run ID: ([a-zA-Z0-9-]+)").matcher(parentReply.toString());
            assertTrue(matcher.find(), "the parent must receive the actual spawned run ID");
            SubagentRuntime runtime = f.context.getBean(SubagentRuntime.class);
            assertTrue(runtime.waitForCompletion(matcher.group(1), 5, TimeUnit.SECONDS));
            assertEquals("child result", runtime.getRunOrCompleted(matcher.group(1)).getResult().getText());
            List<JsonNode> parentRequests = f.forInput("root work");
            assertEquals(3, parentRequests.size());
            assertTrue(parentRequests.get(0).path("tools").toString().contains("spawn_subagent"));
            assertTrue(parentRequests.get(1).path("messages").toString().contains("gate result"));
            assertEquals(List.of("model-v1", "model-v1", "model-v1"), models(parentRequests));
            assertEquals(List.of("model-v1"), models(f.forInput("child work")));
            assertFalse(f.forInput("child work").get(0).path("tools").toString().contains("spawn_subagent"));
            assertEquals(List.of("model-v2"), models(f.forInput("new work")));
            f.export(reactive ? "model-binding-reactive" : "model-binding-sync",
                    runtime.getRunOrCompleted(matcher.group(1)).getResult().getText());
        }
    }

    @Test void implicitResumeBindsTheNewModel() throws Exception {
        try (Fixture f = new Fixture()) {
            CompletableFuture<?> old = f.start("A", "root work", true);
            assertEquals(Map.of("marker", "root work"), f.gate.received.get(5, TimeUnit.SECONDS));
            f.update("openrouter", "model-v2");
            f.start("A", "resumed work", true).get(5, TimeUnit.SECONDS);
            old.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("model-v1"), models(f.forInput("root work")));
            assertEquals(List.of("model-v2"), models(f.forInput("resumed work")));
        }
    }

    @Test void reactiveBindingHappensOnSubscription() throws Exception {
        try (Fixture f = new Fixture()) {
            var pending = f.handler.chatStreamReactive(request("A", "new work"));
            assertTrue(f.requests.isEmpty());
            f.update("openrouter", "model-v2");
            pending.collectList().toFuture().get(5, TimeUnit.SECONDS);
            assertEquals(List.of("model-v2"), models(f.forInput("new work")));
        }
    }

    @Test void failedConfigurationLeavesThePreviousProviderUsable() throws Exception {
        try (Fixture f = new Fixture()) {
            ServletException error = assertThrows(ServletException.class, () -> f.update("unsupported", "bad"));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
            f.start("A", "new work", true).get(5, TimeUnit.SECONDS);
            assertEquals(List.of("model-v1"), models(f.forInput("new work")));
        }
    }

    private static List<String> models(List<JsonNode> requests) {
        return requests.stream().map(r -> r.path("model").asText()).toList();
    }

    private static GatewayRequest request(String session, String input) {
        return GatewayRequest.builder().sessionId(session).message(input).build();
    }

    private static String input(JsonNode request) {
        String input = "";
        for (JsonNode message : request.path("messages")) {
            if (message.path("role").asText().equals("user")) input = message.path("content").asText();
        }
        return input;
    }

    private static class Gate implements Tool {
        final CompletableFuture<Map<String, Object>> received = new CompletableFuture<>();
        final CompletableFuture<ToolResult> release = new CompletableFuture<>();
        public String getName() { return "gate"; }
        public String getDescription() { return "Controlled actuator"; }
        public ToolSchema getSchema() { return ToolSchema.withRequired(Map.of("marker", Map.of("type", "string")), "marker"); }
        public ToolResult execute(Map<String, Object> args) {
            received.complete(Map.copyOf(args));
            return release.join();
        }
    }

    static class Fixture implements AutoCloseable {
        final ObjectMapper json = new ObjectMapper();
        final List<JsonNode> requests = new CopyOnWriteArrayList<>();
        final Gate gate = new Gate();
        final HttpServer server;
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final ChatHandler handler;
        final MockMvc mvc;
        final String baseUrl;

        Fixture() throws Exception { this(null, null); }

        Fixture(com.lightweightai.kernel.prompt.PromptEngine prompts) throws Exception { this(prompts, null); }

        Fixture(com.lightweightai.kernel.prompt.PromptEngine prompts,
                com.lightweightai.kernel.agent.AgentObserver observer) throws Exception { this(prompts, observer, null); }

        Fixture(com.lightweightai.kernel.prompt.PromptEngine prompts,
                com.lightweightai.kernel.agent.AgentObserver observer,
                com.lightweightai.kernel.prompt.PromptSource publishedPrompts) throws Exception {
            this(prompts, observer, publishedPrompts, null);
        }

        Fixture(com.lightweightai.kernel.prompt.PromptEngine prompts,
                com.lightweightai.kernel.agent.AgentObserver observer,
                com.lightweightai.kernel.prompt.PromptSource publishedPrompts, MemoryProvider memory) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                try (exchange) {
                    JsonNode request = json.readTree(exchange.getRequestBody());
                    requests.add(request);
                    String body = response(request);
                    exchange.getResponseHeaders().set("Content-Type", request.path("stream").asBoolean()
                            ? "text/event-stream" : "application/json");
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
            baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            AgentConfig builder = new AgentConfig();
            DynamicLLMProvider provider = new DynamicLLMProvider(
                    builder.buildProvider("openrouter", "test-key", "model-v1", baseUrl));
            context.registerBean(DynamicLLMProvider.class, () -> provider);
            context.registerBean(MemoryProvider.class, () -> memory == null ? new InMemoryProvider() : memory);
            context.registerBean(ToolRegistry.class, () -> {
                ToolRegistry registry = new ToolRegistry();
                registry.register(gate);
                return registry;
            });
            if (prompts != null) context.registerBean(com.lightweightai.kernel.prompt.PromptEngine.class, () -> prompts);
            if (publishedPrompts != null) context.registerBean("publishedPrompts", com.lightweightai.kernel.prompt.PromptSource.class,
                    () -> publishedPrompts, definition -> definition.setPrimary(true));
            if (observer != null) context.registerBean(com.lightweightai.kernel.agent.AgentObserver.class, () -> observer);
            context.register(OrchestratorConfig.class);
            context.refresh();
            handler = context.getBean(ChatHandler.class);
            mvc = MockMvcBuilders.standaloneSetup(new ModelConfigController(provider, builder)).build();
        }

        Map<String, Object> awaitActuator() throws Exception { return gate.received.get(5, TimeUnit.SECONDS); }
        void releaseActuator() { gate.release.complete(ToolResult.success("gate result")); }

        CompletableFuture<?> start(String session, String input, boolean reactive) {
            return reactive ? handler.chatStreamReactive(request(session, input)).collectList().toFuture()
                    : CompletableFuture.supplyAsync(() -> handler.chat(request(session, input)));
        }

        void update(String type, String model) throws Exception {
            var result = mvc.perform(put("/api/model-config").requestAttr("userRole", "ADMIN")
                    .contentType("application/json").content(json.writeValueAsString(Map.of(
                            "providerType", type, "apiKey", "test-key", "model", model, "baseUrl", baseUrl))))
                    .andExpect(status().isOk()).andReturn();
            assertEquals(model, json.readTree(result.getResponse().getContentAsString()).path("model").asText());
        }

        List<JsonNode> forInput(String input) {
            return requests.stream().filter(r -> input.equals(input(r))).toList();
        }

        String response(JsonNode request) throws java.io.IOException {
            String input = input(request);
            boolean gateDone = false, spawnDone = false;
            for (JsonNode message : request.path("messages")) {
                gateDone |= message.path("tool_call_id").asText().equals("gate-call");
                spawnDone |= message.path("tool_call_id").asText().equals("spawn-call");
            }
            Map<String, Object> message;
            String finish;
            boolean memoryDone = request.path("messages").toString().contains("memory-call");
            if (input.equals("remember work") && !memoryDone) {
                message = Map.of("role", "assistant", "tool_calls", List.of(Map.of("index", 0,
                        "id", "memory-call", "type", "function", "function", Map.of("name", "write_memory",
                                "arguments", json.writeValueAsString(Map.of("content", "alice-durable-secret", "type", "durable"))))));
                finish = "tool_calls";
            } else if (input.equals("root work") && !spawnDone) {
                String name = gateDone ? "spawn_subagent" : "gate";
                String id = gateDone ? "spawn-call" : "gate-call";
                Map<String, Object> args = gateDone ? Map.of("task", "child work", "agentId", "worker")
                        : Map.of("marker", "root work");
                message = Map.of("role", "assistant", "tool_calls", List.of(Map.of("index", 0,
                        "id", id, "type", "function", "function", Map.of("name", name,
                                "arguments", json.writeValueAsString(args)))));
                finish = "tool_calls";
            } else {
                message = Map.of("role", "assistant", "content", input.equals("child work") ? "child result" : "done");
                finish = "stop";
            }
            if (request.path("stream").asBoolean()) {
                return "data: " + json.writeValueAsString(Map.of("choices", List.of(Map.of("index", 0, "delta", message))))
                        + "\n\ndata: " + json.writeValueAsString(Map.of("choices", List.of(Map.of("index", 0,
                        "delta", Map.of(), "finish_reason", finish)))) + "\n\ndata: [DONE]\n\n";
            }
            return json.writeValueAsString(Map.of("id", "test-response", "choices", List.of(
                    Map.of("index", 0, "message", message, "finish_reason", finish))));
        }

        void export(String caseId, String childResult) throws Exception {
            String directory = System.getProperty("runner.evidence.dir");
            if (directory == null) return;
            String candidate = System.getProperty("runner.candidate.digest", "");
            String evaluation = System.getProperty("runner.evaluation.digest", "");
            assertTrue(candidate.matches("[a-f0-9]{64}"), "candidate digest required for export");
            assertTrue(evaluation.matches("[a-f0-9]{64}"), "evaluation digest required for export");
            Map<String, Object> evidence = Map.of("schemaVersion", 1, "caseId", caseId,
                    "candidateDigest", candidate, "evaluationDigest", evaluation,
                    "observations", Map.of("httpRequests", List.copyOf(requests),
                            "actuatorArguments", gate.received.get(5, TimeUnit.SECONDS),
                            "childResult", childResult));
            Path root = Path.of(directory);
            Files.createDirectories(root);
            Files.writeString(root.resolve(caseId + ".json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }

        public void close() {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            gate.release.complete(ToolResult.success("gate result"));
            context.close();
            server.stop(0);
        }
    }

}
