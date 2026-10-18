package com.lightweightai.web.config;

import com.lightweightai.kernel.gateway.*;
import com.lightweightai.web.gateway.GatewayController;
import com.lightweightai.web.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The legacy HTTP protocol must preserve the same authenticated native session boundary. */
class LegacyGatewayIdentityAcceptanceTest {
    @Test void gatewaySsePreservesBudgetTerminalAndFinalMetadata() throws Exception {
        try (var f = new Fixture()) {
            f.nativeRuntime.context.getBean(com.lightweightai.kernel.agent.AgentRegistry.class).register(
                    com.lightweightai.kernel.agent.AgentProfile.builder().agentId("default").maxBudgetTokens(1).build());
            var pending = f.mvc.perform(post("/gateway/chat/stream").header("Authorization", f.token("alice"))
                    .contentType("application/json").content("{\"session_id\":\"same\",\"message\":\"hello\"}"))
                    .andExpect(request().asyncStarted()).andReturn();
            pending.getAsyncResult(30_000);
            String actual = f.mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertTrue(actual.contains("budget_stopped"), actual);
            assertTrue(actual.contains("budget_exhausted"), actual);
            assertTrue(actual.contains("maxBudgetTokens"), actual);
            assertTrue(f.nativeRuntime.requests.isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothChatEntriesCarryServerIdentityToTheActualModel(boolean streaming) throws Exception {
        try (var f = new Fixture()) {
            f.chat("alice", "alice-private-work", false);
            f.chat("bob", "bob-work", streaming);
            var received = f.nativeRuntime.forInput("bob-work").get(0).path("messages").toString();
            assertFalse(received.contains("alice-private-work"), received);
            assertTrue(received.contains("bob-work"));
        }
    }

    @Test void historySummaryAndClearUseTheSamePrincipalScope() throws Exception {
        try (var f = new Fixture()) {
            f.chat("alice", "alice-private-work", false);
            assertFalse(f.history("bob").contains("alice-private-work"));
            assertTrue(f.history("alice").contains("alice-private-work"));
            String summary = f.mvc.perform(get("/gateway/session/same/summary").header("Authorization", f.token("bob")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(summary.contains("alice-private-work"));
            f.mvc.perform(delete("/gateway/session/same").header("Authorization", f.token("bob")))
                    .andExpect(status().isOk());
            assertTrue(f.history("alice").contains("alice-private-work"), "Bob cannot clear Alice's native history");
            f.mvc.perform(delete("/gateway/session/same").header("Authorization", f.token("alice")))
                    .andExpect(status().isOk());
            assertFalse(f.history("alice").contains("alice-private-work"));
        }
    }

    static class Fixture implements AutoCloseable {
        final ModelBindingAssemblyAcceptanceTest.Fixture nativeRuntime = new ModelBindingAssemblyAcceptanceTest.Fixture();
        final AuthSessionService auth = new AuthSessionService();
        final GatewayController controller;
        final MockMvc mvc;
        Fixture() throws Exception {
            var gateway = Gateway.builder().chatHandler(nativeRuntime.handler)
                    .sessionManager(nativeRuntime.context.getBean(SessionManager.class)).build();
            controller = new GatewayController(gateway, nativeRuntime.json);
            mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new AuthInterceptor(auth)).build();
        }
        String token(String principal) { return "Bearer " + auth.createSessionToken(principal); }
        void chat(String principal, String message, boolean streaming) throws Exception {
            String body = nativeRuntime.json.writeValueAsString(Map.of("message", message, "session_id", "same",
                    "extra", Map.of("userId", "alice", "principalId", "alice")));
            var result = mvc.perform(post(streaming ? "/gateway/chat/stream" : "/gateway/chat")
                    .header("Authorization", token(principal)).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andReturn();
            if (streaming) {
                result.getAsyncResult(30_000);
                mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
            }
        }
        String history(String principal) throws Exception {
            return mvc.perform(get("/gateway/session/same/history").header("Authorization", token(principal)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        }
        public void close() throws Exception {
            if ((Object) controller instanceof AutoCloseable owned) owned.close();
            nativeRuntime.close();
        }
    }
}
