package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class InterleavedSpawnScopeAcceptanceTest {
    record Pending(List<ConversationMessage> messages, LLMOptions options, FluxSink<StreamEvent> sink) {
        void reply(List<ToolCall> calls) {
            sink.next(StreamEvent.llmComplete(LLMResponse.builder()
                    .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(calls.isEmpty() ? "done" : "").build())
                    .toolCalls(calls).stopReason(calls.isEmpty() ? "end_turn" : "tool_use").build()));
            sink.complete();
        }
    }

    @Test
    void overlappingParentsSpawnIntoTheirOwnSessionEvenWhenBExecutesFirst() throws Exception {
        BlockingQueue<Pending> requests = new LinkedBlockingQueue<>();
        CapturingLLMProvider rootLlm = CapturingLLMProvider.reactive((messages, options) ->
                Flux.create(sink -> requests.add(new Pending(messages, options, sink))));
        CapturingLLMProvider childLlm = CapturingLLMProvider.endTurn("child done");
        ToolRegistry global = new ToolRegistry();
        AgentRegistry agents = new AgentRegistry();
        agents.register(AgentProfile.builder().agentId("parent").maxSpawnDepth(2).build());
        agents.setDefault("parent");
        InMemoryProvider memory = new InMemoryProvider();
        SubagentRuntime runtime = new SubagentRuntime(new AgentFactory(childLlm, memory, global), agents, 2);
        Orchestrator orchestrator = new Orchestrator(agents, new AgentFactory(rootLlm, memory, global),
                new MetadataAgentRouter(agents), runtime);
        Disposable a = null;
        Disposable b = null;
        try {
            a = orchestrator.chatStreamReactive(GatewayRequest.builder().sessionId("A").message("parent-A").build()).subscribe();
            Pending aRequest = next(requests);
            b = orchestrator.chatStreamReactive(GatewayRequest.builder().sessionId("B").message("parent-B").build()).subscribe();
            Pending bRequest = next(requests);
            for (Pending request : List.of(aRequest, bRequest)) {
                assertTrue(request.options().getToolDefinitions().stream().anyMatch(d -> "spawn_subagent".equals(d.get("name"))));
            }
            assertFalse(global.has("spawn_subagent"));

            bRequest.reply(List.of(new ToolCall("spawn-B", "spawn_subagent", Map.of("task", "child-B"))));
            Pending bResult = next(requests);
            assertChild(runtime, bResult, "B", "child-B");
            bResult.reply(List.of());

            aRequest.reply(List.of(new ToolCall("spawn-A", "spawn_subagent", Map.of("task", "child-A"))));
            Pending aResult = next(requests);
            assertChild(runtime, aResult, "A", "child-A");
            aResult.reply(List.of());
            assertTrue(childLlm.messagesHistory().stream().anyMatch(ms -> ms.stream().anyMatch(m -> "child-A".equals(m.getTextContent()))));
            assertTrue(childLlm.messagesHistory().stream().anyMatch(ms -> ms.stream().anyMatch(m -> "child-B".equals(m.getTextContent()))));
        } finally {
            if (a != null) a.dispose();
            if (b != null) b.dispose();
            orchestrator.shutdown();
            runtime.shutdown();
        }
    }

    private static Pending next(BlockingQueue<Pending> requests) throws InterruptedException {
        Pending request = requests.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "actual root LLM must receive the tool result");
        return request;
    }

    private static void assertChild(SubagentRuntime runtime, Pending result, String parent, String task) throws Exception {
        String toolMessage = result.messages().stream().filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                .map(ConversationMessage::getTextContent).findFirst().orElseThrow();
        var matcher = Pattern.compile("Run ID: ([a-zA-Z0-9-]+)").matcher(toolMessage);
        assertTrue(matcher.find(), toolMessage);
        String runId = matcher.group(1);
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        SubagentRun child = runtime.getRunOrCompleted(runId);
        assertEquals("agent:parent:main:" + parent, child.getRequest().getParentSessionKey());
        assertEquals(task, child.getRequest().getTask());
        assertEquals("agent:parent:main:" + parent + ":subagent:" + runId, child.getSessionKey());
    }
}
