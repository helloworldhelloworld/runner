package com.lightweightai.web.config;

import com.fasterxml.jackson.databind.*;
import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.gateway.*;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import com.lightweightai.kernel.memory.*;
import com.lightweightai.web.controller.ChatController;
import com.lightweightai.web.service.AuthSessionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spring configuration -> authenticated HTTP SSE -> actual native model HTTP -> budget stop payload. */
class ExecutionBudgetWebAcceptanceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"/api/chat/stream/reactive", "/api/chat/stream"})
    void springLimitsReachModelAndBudgetStopReachesAuthenticatedSse(String endpoint) throws Exception {
        var json = new ObjectMapper();
        var requests = new CopyOnWriteArrayList<JsonNode>();
        var first = new CompletableFuture<JsonNode>();
        var remote = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        remote.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                var received = json.readTree(exchange.getRequestBody()); requests.add(received); first.complete(received);
                String body = """
                        {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[
                        {"id":"echo-call","type":"function","function":{"name":"echo","arguments":"{}"}}]},
                        "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":4923,"completion_tokens":77}}
                        """;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
            }
        }); remote.start();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("budget-test", Map.of(
                    "app.orchestrator.max-output-tokens", "77", "app.orchestrator.max-budget-tokens", "5000")));
            context.registerBean(DynamicLLMProvider.class, () -> new DynamicLLMProvider(new OpenRouterProvider(
                    "controlled-key", "controlled-model", "http://127.0.0.1:"+remote.getAddress().getPort()+"/v1")));
            context.registerBean(MemoryProvider.class, InMemoryProvider::new);
            context.registerBean(ToolRegistry.class, () -> {
                var tools = new ToolRegistry();
                tools.register(new Tool() {
                    public String getName() { return "echo"; }
                    public String getDescription() { return "Read a result"; }
                    public ToolSchema getSchema() { return ToolSchema.empty(); }
                    public ToolResult execute(Map<String,Object> args) { return ToolResult.success("actual tool result"); }
                }); return tools;
            });
            context.register(OrchestratorConfig.class); context.refresh();
            var gateway = Gateway.builder().chatHandler(context.getBean(ChatHandler.class))
                    .sessionManager(context.getBean(SessionManager.class)).build();
            var auth = new AuthSessionService();
            try (var controller = new ChatController(null, gateway, context.getBean(DynamicLLMProvider.class), json)) {
                var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new AuthInterceptor(auth)).build();
                var result = mvc.perform(post(endpoint)
                        .header("Authorization", "Bearer " + auth.createSessionToken("alice"))
                        .contentType("application/json").content("{\"sessionId\":\"budget\",\"message\":\"use echo\"}"))
                        .andExpect(request().asyncStarted()).andReturn();
                assertEquals(77, first.get(30, TimeUnit.SECONDS).path("max_tokens").asInt());
                result.getAsyncResult(30_000);
                String body = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString();
                var stop = body.lines().filter(line -> line.startsWith("data:"))
                        .map(line -> { try { return json.readTree(line.substring(5)); }
                            catch (Exception e) { throw new IllegalStateException(e); } })
                        .filter(event -> "budget_stopped".equals(event.path("type").asText())).findFirst().orElseThrow();
                assertEquals("budget_exhausted", stop.path("data").path("reason").asText());
                assertEquals(5000, stop.path("data").path("knownTokens").asLong());
                assertEquals(0, stop.path("data").path("unknownAttempts").asLong());
                assertEquals(1, requests.size());
            }
        } finally { remote.stop(0); org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
    }
}
