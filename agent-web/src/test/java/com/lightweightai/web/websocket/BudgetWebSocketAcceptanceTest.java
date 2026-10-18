package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.gateway.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.orchestrator.*;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.web.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BudgetWebSocketAcceptanceTest {
    @Test void springDeliversBudgetStopBeforeAnyModelAttempt() throws Exception {
        try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(AuthenticatedWebSocketAcceptanceTest.Server.class)
                .properties("server.port=0", "spring.main.banner-mode=off", "logging.level.root=WARN",
                        "spring.config.location=optional:classpath:/budget-test-unused.properties").run()) {
            context.getBean(AgentRegistry.class).register(AgentProfile.builder().agentId("default").maxBudgetTokens(1).build());
            var token = context.getBean(AuthSessionService.class).createSessionToken("alice");
            String url = "ws://127.0.0.1:"+context.getWebServer().getPort()+"/ws/chat?token="+token;
            try (var peer = new AuthenticatedWebSocketAcceptanceTest.Peer(url)) {
                peer.send(Map.of("type", "chat", "sessionId", "budget", "message", "hello", "turnId", "budget-turn"));
                var stopped = peer.until("budget_stopped");
                assertEquals("budget-turn", stopped.path("turnId").asText());
                assertEquals("budget_exhausted", stopped.path("data").path("reason").asText());
                assertEquals(1, stopped.path("data").path("maxBudgetTokens").asInt());
                assertEquals(0, stopped.path("data").path("attemptCount").asInt());
                peer.until("stream_end");
                assertTrue(context.getBean(MinionVoiceTurnWebSocketTest.RemoteLlm.class).requests.isEmpty());
            }
        }
    }

    @Test void vertxDeliversTheSameBudgetStopPayload() throws Exception {
        var auth = new AuthSessionService();
        var remote = new MinionVoiceTurnWebSocketTest.RemoteLlm();
        var registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("default").maxBudgetTokens(1).build()); registry.setDefault("default");
        var memory = new InMemoryProvider(); var tools = new ToolRegistry();
        var orchestrator = new Orchestrator(registry, new AgentFactory(remote.provider, memory, tools), new MetadataAgentRouter(registry));
        var gateway = Gateway.builder().chatHandler(orchestrator).sessionManager(new NativeSessionManager(memory, registry, orchestrator)).build();
        var config = new com.lightweightai.web.config.VertxWebSocketConfig(auth);
        var handler = config.vertxChatWebSocketHandler(gateway, new CrisisDetector(), tools, null, null, null, null, null, remote.provider, null);
        var vertx = io.vertx.core.Vertx.vertx(); var server = new VertxWebSocketServer(vertx, handler, 0);
        try {
            server.start();
            String url = "ws://127.0.0.1:"+server.getActualPort()+"/ws/chat?token="+auth.createSessionToken("alice");
            try (var peer = new AuthenticatedWebSocketAcceptanceTest.Peer(url)) {
                peer.send(Map.of("type", "chat", "sessionId", "budget", "message", "hello"));
                var stopped = peer.until("budget_stopped");
                assertEquals("budget_exhausted", stopped.path("data").path("reason").asText());
                assertEquals(1, stopped.path("data").path("maxBudgetTokens").asInt());
                assertEquals(0, stopped.path("data").path("attemptCount").asInt());
                peer.until("stream_end"); assertTrue(remote.requests.isEmpty());
            }
        } finally {
            server.stop(); vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            orchestrator.shutdown();
        }
    }
}
