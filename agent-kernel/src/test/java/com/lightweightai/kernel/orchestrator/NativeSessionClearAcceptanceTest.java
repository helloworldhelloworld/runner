package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.gateway.SessionKeys;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeSessionClearAcceptanceTest {
    @Test void clearingACompletedForegroundStopsItsIndependentChildrenAndRemovesTheirDerivedMemory() throws Exception {
        var received = new LinkedBlockingQueue<String>();
        var release = new CompletableFuture<LLMResponse>();
        var provider = CapturingLLMProvider.responding((messages, options) -> {
            received.add(messages.get(messages.size() - 1).getTextContent());
            return release.join();
        });
        var memory = new InMemoryProvider();
        var agents = new AgentRegistry();
        agents.register(AgentProfile.builder().agentId("default").build());
        agents.register(AgentProfile.builder().agentId("worker").maxSpawnDepth(1).build());
        agents.setDefault("worker");
        var factory = new AgentFactory(provider, memory, new ToolRegistry());
        var children = new SubagentRuntime(factory, agents, 3);
        var orchestrator = new Orchestrator(agents, factory, new MetadataAgentRouter(agents), children);
        try {
            String a = spawn(children, "alice", "alice task");
            assertEquals("alice task", received.poll(30, TimeUnit.SECONDS), () -> String.valueOf(children.getRunOrCompleted(a).getErrorMessage()));
            String b = spawn(children, "bob", "bob task");
            assertEquals("bob task", received.poll(30, TimeUnit.SECONDS), () -> String.valueOf(children.getRunOrCompleted(b).getErrorMessage()));
            String childSession = children.getRunOrCompleted(a).getSessionKey();
            memory.forScope("alice", "worker").forSession(childSession).writeEphemeral("private-child-summary");
            new NativeSessionManager(memory, agents, orchestrator).forScope("alice", "default").clearSession("same");
            assertEquals(SubagentRun.Status.CANCELLED, children.getRunOrCompleted(a).getStatus());
            assertTrue(children.getRunOrCompleted(b).isRunning());
            assertTrue(memory.forScope("alice", "worker").getHistory(childSession, 10).isEmpty());
            assertTrue(memory.forScope("alice", "worker").search("private-child-summary").isEmpty());
        } finally {
            release.complete(LLMResponse.builder().message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT).textContent("late reply").build()).build());
            children.shutdown(); orchestrator.shutdown();
        }
    }
    private static String spawn(SubagentRuntime runtime, String owner, String task) {
        String root = SessionKeys.main(owner, "default", "same");
        return runtime.spawn(SpawnRequest.builder().parentSessionKey(root).agentId("worker").task(task)
                .executionContext(ToolExecutionContext.serverOnly().withPrincipalId(owner))
                .ownerScope(TaskScope.root(root)).inheritedPolicy(ToolPolicy.allowAll()).remainingSpawnDepth(0).build(), null);
    }
}
