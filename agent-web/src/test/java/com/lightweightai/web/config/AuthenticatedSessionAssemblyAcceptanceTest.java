package com.lightweightai.web.config;

import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.web.controller.ChatController;
import com.lightweightai.web.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.SessionManager;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real authentication, MVC, native assembly and Provider HTTP. Only the remote model is controlled. */
class AuthenticatedSessionAssemblyAcceptanceTest {
    @TempDir Path directory;

    @Test void nativeHistoryAndDerivedMemorySurviveRestartAndClearTogether() throws Exception {
        try (var files = new FileMemoryManager(directory, "legacy", new MockEmbeddingProvider(64));
             var f = new ModelBindingAssemblyAcceptanceTest.Fixture(null, null, null, new FileMemoryAdapter(files))) {
            files.appendDurable("legacy", "unassigned-private-data");
            f.handler.chat(request("alice", "same", "remembered-secret work"));
            var sessions = f.context.getBean(SessionManager.class).forScope("alice", "default");
            assertEquals("remembered-secret work", sessions.getSessionHistory("same").get(0).get("content"));
            assertFalse(f.requests.get(0).path("messages").toString().contains("unassigned-private-data"));
        }
        try (var files = new FileMemoryManager(directory, "legacy", new MockEmbeddingProvider(64));
             var f = new ModelBindingAssemblyAcceptanceTest.Fixture(null, null, null, new FileMemoryAdapter(files))) {
            var sessions = f.context.getBean(SessionManager.class).forScope("alice", "default");
            assertEquals(2, sessions.getSessionHistory("same").size());
            f.handler.chat(request("alice", "same", "resume work"));
            assertTrue(f.forInput("resume work").get(0).path("messages").toString().contains("remembered-secret"));
            String internal = com.lightweightai.kernel.gateway.SessionKeys.main("alice", "default", "same");
            f.handler.chat(request("bob", internal, "bob work"));
            assertFalse(f.forInput("bob work").get(0).path("messages").toString().contains("remembered-secret"));
            sessions.clearSession("same");
            assertTrue(sessions.getSessionHistory("same").isEmpty());
            f.handler.chat(request("alice", "same", "after clear work"));
            assertFalse(f.forInput("after clear work").get(0).path("messages").toString().contains("remembered-secret"));
        }
    }

    @Test void anotherUsersMessageAndInterruptCannotCancelAnActiveRun() throws Exception {
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture()) {
            var root = f.handler.chatStreamReactive(request("alice", "same", "root work")).collectList().toFuture();
            assertEquals("root work", f.awaitActuator().get("marker"));
            f.handler.chatStreamReactive(request("bob", "same", "bob work")).collectList().toFuture().get(5, TimeUnit.SECONDS);
            assertNull(f.handler.interrupt("bob", "same"));
            assertFalse(root.isDone(), "Bob must not interrupt Alice's actuator");
            f.releaseActuator();
            root.get(5, TimeUnit.SECONDS);
            assertEquals(3, f.forInput("root work").size());
            assertTrue(f.forInput("root work").get(1).path("messages").toString().contains("gate result"));
        }
    }

    private static GatewayRequest request(String principal, String session, String input) {
        return GatewayRequest.builder().principalId(principal).sessionId(session).message(input).build();
    }

    @Test void aClearedExecutionCannotRepopulateHistoryOrDerivedMemory() throws Exception {
        try (var files = new FileMemoryManager(directory, "legacy", new MockEmbeddingProvider(64))) {
            var root = new FileMemoryAdapter(files).forScope("alice", "default");
            String session = com.lightweightai.kernel.gateway.SessionKeys.main("alice", "default", "same");
            var old = root.forSession(session);
            old.addMessage(session, com.lightweightai.kernel.memory.Message.user("secret"));
            old.writeEphemeral("secret-summary");
            root.clearSession(session);
            assertThrows(IllegalStateException.class, () -> old.addMessage(session, com.lightweightai.kernel.memory.Message.assistant("late")));
            assertThrows(IllegalStateException.class, () -> old.writeEphemeral("late-summary"));
            assertTrue(root.getHistory(session, 10).isEmpty());
            assertTrue(root.search("secret").isEmpty());
            var fresh = root.forSession(session);
            fresh.addMessage(session, com.lightweightai.kernel.memory.Message.user("fresh"));
            assertEquals("fresh", root.getHistory(session, 10).get(0).getContent());
        }
    }

    @Test void theActualMemoryToolWritesOnlyTheRequestOwnersPersistentStore() throws Exception {
        try (var files = new FileMemoryManager(directory, "legacy", new MockEmbeddingProvider(64));
             var f = new ModelBindingAssemblyAcceptanceTest.Fixture(null, null, null, new FileMemoryAdapter(files))) {
            f.context.getBean(com.lightweightai.kernel.agent.ToolRegistry.class).register(
                    new MemoryWriteTool(com.lightweightai.kernel.memory.tools.MemoryToolkit.create(files)));
            f.handler.chat(request("alice", "same", "remember work"));
            assertTrue(f.forInput("remember work").get(1).path("messages").toString().contains("Memory saved"));
            f.handler.chat(request("alice", "other", "alice recall work"));
            f.handler.chat(request("bob", "same", "bob recall work"));
            assertTrue(f.forInput("alice recall work").get(0).path("messages").toString().contains("alice-durable-secret"));
            assertFalse(f.forInput("bob recall work").get(0).path("messages").toString().contains("alice-durable-secret"));
            assertFalse(files.readDurable().contains("alice-durable-secret"));
        }
    }

    @Test void twoAuthenticatedUsersChoosingTheSameSessionCannotShareHistoryOrSummaries() throws Exception {
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture()) {
            var auth = new AuthSessionService();
            String a = auth.createSessionToken("alice");
            String b = auth.createSessionToken("bob");
            var controller = new ChatController(null, Gateway.builder().chatHandler(f.handler)
                    .sessionManager(f.context.getBean(SessionManager.class)).build(),
                    f.context.getBean(DynamicLLMProvider.class), f.json);
            var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new AuthInterceptor(auth)).build();
            mvc.perform(post("/api/chat").header("Authorization", "Bearer " + a).contentType("application/json")
                    .content(f.json.writeValueAsString(Map.of("sessionId", "same", "message", "alice-secret work"))))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/chat").header("Authorization", "Bearer " + b).contentType("application/json")
                    .content(f.json.writeValueAsString(Map.of("sessionId", "same", "message", "bob work",
                            "options", Map.of("userId", "alice")))))
                    .andExpect(status().isOk());
            var actual = f.forInput("bob work").get(0).path("messages").toString();
            assertTrue(actual.contains("bob work"));
            assertFalse(actual.contains("alice-secret"), actual);
            String history = mvc.perform(get("/api/session/same/history").header("Authorization", "Bearer " + b))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(history.contains("bob work"), history);
            assertFalse(history.contains("alice-secret"), history);
            mvc.perform(delete("/api/session/same").header("Authorization", "Bearer " + a)).andExpect(status().isOk());
            mvc.perform(post("/api/chat").header("Authorization", "Bearer " + a).contentType("application/json")
                    .content(f.json.writeValueAsString(Map.of("sessionId", "same", "message", "alice cleared work"))))
                    .andExpect(status().isOk());
            assertFalse(f.forInput("alice cleared work").get(0).path("messages").toString().contains("alice-secret"));
            int before = f.requests.size();
            auth.invalidate(b);
            mvc.perform(post("/api/chat").header("Authorization", "Bearer " + b).contentType("application/json")
                    .content("{\"sessionId\":\"same\",\"message\":\"forbidden\"}"))
                    .andExpect(status().isUnauthorized());
            assertEquals(before, f.requests.size());
        }
    }
}
