package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.orchestrator.SubagentRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Spring drives the production bean assembly; only the remote LLM is simulated. */
class SubagentScopeAssemblyTest {
    @Test void nativeAssemblyDelegatesToWorkerAndKeepsTaskControlsLocal() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(OrchestratorConfig.class, Peers.class);
            context.refresh();
            ToolRegistry global = context.getBean(ToolRegistry.class);
            assertTrue(global.getToolDefinitions().isEmpty(), "native assembly published unscoped task controls");
            ChatHandler handler = context.getBean(ChatHandler.class);
            RemoteLlm remote = context.getBean(RemoteLlm.class);
            handler.chat(request("A", "delegate"));
            String id = remote.runId.get();
            assertNotNull(id, "root LLM must receive the worker's run ID");
            assertTrue(context.getBean(SubagentRuntime.class).waitForCompletion(id, 5, TimeUnit.SECONDS));
            Request worker = remote.requests.stream().filter(r -> "work".equals(r.input())).findFirst().orElseThrow();
            assertTrue(worker.options().getToolDefinitions().stream().noneMatch(d -> "spawn_subagent".equals(d.get("name"))));
            assertTrue(worker.options().getToolDefinitions().stream().anyMatch(d -> "wait_subagent".equals(d.get("name"))));
            handler.chat(request("B", "collect"));
            assertTrue(remote.lastToolResult.get().contains("Not found"));
            handler.chat(request("A", "collect"));
            assertTrue(remote.lastToolResult.get().contains("worker result"));
            assertTrue(global.getToolDefinitions().isEmpty());
        }
    }

    static GatewayRequest request(String session, String input) {
        return GatewayRequest.builder().sessionId(session).message(input).build();
    }

    record Request(List<ConversationMessage> messages, LLMOptions options) {
        String input() {
            return messages.stream().filter(m -> m.getRole() == ConversationMessage.MessageRole.USER)
                    .map(ConversationMessage::getTextContent).reduce((a, b) -> b).orElseThrow();
        }
        String toolResult() {
            return messages.stream().filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                    .map(ConversationMessage::getTextContent).reduce("", (a, b) -> a + b);
        }
    }

    static class RemoteLlm {
        final List<Request> requests = new CopyOnWriteArrayList<>();
        final AtomicReference<String> runId = new AtomicReference<>();
        final AtomicReference<String> lastToolResult = new AtomicReference<>();
        final LLMProvider provider = mock(LLMProvider.class);
        RemoteLlm() {
            when(provider.complete(any(), any())).thenAnswer(invocation -> {
                Request request = new Request(List.copyOf(invocation.getArgument(0)), invocation.getArgument(1));
                requests.add(request);
                if (!request.toolResult().isEmpty()) {
                    lastToolResult.set(request.toolResult());
                    var matcher = Pattern.compile("Run ID: ([a-zA-Z0-9-]+)").matcher(request.toolResult());
                    if (matcher.find()) runId.set(matcher.group(1));
                    return response("done", null);
                }
                return switch (request.input()) {
                    case "delegate" -> response("", new ToolCall("spawn", "spawn_subagent", Map.of("task", "work", "agentId", "worker")));
                    case "collect" -> response("", new ToolCall("wait", "wait_subagent", Map.of("runIds", runId.get())));
                    default -> response("worker result", null);
                };
            });
        }
    }

    static LLMResponse response(String text, ToolCall call) {
        return LLMResponse.builder().message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text).build()).toolCalls(call == null ? List.of() : List.of(call))
                .stopReason(call == null ? "end_turn" : "tool_use").build();
    }

    @Configuration(proxyBeanMethods = false)
    static class Peers {
        @Bean RemoteLlm remoteLlm() { return new RemoteLlm(); }
        @Bean LLMProvider llmProvider(RemoteLlm remote) { return remote.provider; }
        @Bean MemoryProvider memoryProvider() { return new InMemoryProvider(); }
        @Bean ToolRegistry toolRegistry() { return new ToolRegistry(); }
    }
}
