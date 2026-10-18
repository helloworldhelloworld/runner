package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.lightweightai.kernel.orchestrator.SubagentScopeAcceptanceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real foreground and child loops; only the remote LLM and actuator are controlled. */
class ForegroundSubagentCancellationAcceptanceTest {
    @ParameterizedTest
    @ValueSource(strings = {"subscription", "interrupt", "failure"})
    void cancellingForegroundReachesDescendantsThroughCompletedParents(String mode) throws Exception {
        try (Fixture f = new Fixture()) {
            Disposable a = f.start("A");
            RootRequest first = f.nextRoot();
            first.respond(spawn("branch"));
            RootRequest waiting = f.nextRoot();
            String branch = spawnedId(toolText(waiting.messages()));
            ChildRequest branchRequest = f.nextChild();
            branchRequest.respond(spawn("leaf"));
            ChildRequest second = f.nextChild();
            ChildRequest third = f.nextChild();
            ChildRequest leaf = "leaf".equals(input(second.messages())) ? second : third;
            ChildRequest branchResult = leaf == second ? third : second;
            String leafId = spawnedId(toolText(branchResult.messages()));
            branchResult.respond(end("branch complete"));
            assertTrue(f.runtime.waitForCompletion(branch, 5, TimeUnit.SECONDS));
            switch (mode) {
                case "subscription" -> a.dispose();
                case "interrupt" -> assertNotNull(f.gateway.interrupt("shared-session"));
                case "failure" -> waiting.sink().error(new IllegalStateException("upstream failed"));
                default -> fail(mode);
            }
            assertEquals(SubagentRun.Status.CANCELLED, f.runtime.getRunOrCompleted(leafId).getStatus(),
                    "foreground cancellation left the grandchild alive");
            assertEquals(SubagentRun.Status.COMPLETED, f.runtime.getRunOrCompleted(branch).getStatus());
            leaf.respond(response(new ToolCall("late", "move", Map.of("request", "old A"))));
            AgentResponse terminal = f.cancelledResponses.poll(5, TimeUnit.SECONDS);
            assertNotNull(terminal, "The actual child execution did not finish after its late response");
            assertEquals("cancelled", terminal.getStopReason());
            assertTrue(f.actuator.isEmpty(), "a cancelled foreground task reached the actuator");
            assertTrue(f.childRequests.isEmpty(), "Cancellation must not spend another model call on a denied tool result");
        }
    }

    @Test void resumedExecutionOwnsNewChildrenAndOldCleanupCannotStopThem() throws Exception {
        try (Fixture f = new Fixture()) {
            Disposable a = f.start("A");
            f.nextRoot().respond(spawn("old-child"));
            String oldId = spawnedId(toolText(f.nextRoot().messages()));
            ChildRequest old = f.nextChild();
            Disposable b = f.start("B"); // implicit interrupt and resume of the same run container
            f.nextRoot().respond(spawn("new-child"));
            String newId = spawnedId(toolText(f.nextRoot().messages()));
            ChildRequest current = f.nextChild();
            a.dispose(); // late cleanup of the replaced subscription
            assertEquals(SubagentRun.Status.CANCELLED, f.runtime.getRunOrCompleted(oldId).getStatus());
            assertTrue(f.runtime.getRunOrCompleted(newId).isRunning());
            current.respond(response(new ToolCall("current", "move", Map.of("request", "new B"))));
            ChildRequest delivered = f.nextChild();
            assertEquals(Map.of("request", "new B"), f.actuator.poll(5, TimeUnit.SECONDS));
            assertTrue(toolText(delivered.messages()).contains("moved new B"));
            delivered.respond(end("B child finished"));
            assertTrue(f.runtime.waitForCompletion(newId, 5, TimeUnit.SECONDS));
            old.respond(end("late A"));
            b.dispose();
        }
    }

    @Test void normalForegroundCompletionDoesNotCancelAnIndependentChild() throws Exception {
        try (Fixture f = new Fixture()) {
            CompletableFuture<Void> completed = new CompletableFuture<>();
            f.gateway.handleStreamReactive(request("A")).subscribe(e -> {}, completed::completeExceptionally,
                    () -> completed.complete(null));
            f.nextRoot().respond(spawn("independent"));
            RootRequest waiting = f.nextRoot();
            String id = spawnedId(toolText(waiting.messages()));
            ChildRequest child = f.nextChild();
            waiting.respond(end("delegated"));
            completed.get(5, TimeUnit.SECONDS);
            assertTrue(f.runtime.getRunOrCompleted(id).isRunning());
            child.respond(response(new ToolCall("independent", "move", Map.of("request", "background"))));
            ChildRequest result = f.nextChild();
            assertEquals(Map.of("request", "background"), f.actuator.poll(5, TimeUnit.SECONDS));
            assertTrue(toolText(result.messages()).contains("moved background"));
            result.respond(end("done"));
            assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
        }
    }

    static LLMResponse spawn(String task) {
        return response(new ToolCall("spawn-" + task, "spawn_subagent", Map.of("agentId", "child", "task", task)));
    }

    static GatewayRequest request(String input) {
        return GatewayRequest.builder().sessionId("shared-session").message(input).build();
    }

    record RootRequest(List<ConversationMessage> messages, FluxSink<StreamEvent> sink) {
        void respond(LLMResponse response) { sink.next(StreamEvent.llmComplete(response)); sink.complete(); }
    }
    record ChildRequest(List<ConversationMessage> messages, CompletableFuture<LLMResponse> response) {
        void respond(LLMResponse value) { response.complete(value); }
    }

    static class Fixture implements AutoCloseable {
        final BlockingQueue<RootRequest> rootRequests = new LinkedBlockingQueue<>();
        final BlockingQueue<ChildRequest> childRequests = new LinkedBlockingQueue<>();
        final List<CompletableFuture<LLMResponse>> pendingChildren = new CopyOnWriteArrayList<>();
        final BlockingQueue<Map<String, Object>> actuator = new LinkedBlockingQueue<>();
        final BlockingQueue<AgentResponse> cancelledResponses = new LinkedBlockingQueue<>();
        final CapturingLLMProvider root = CapturingLLMProvider.reactive((messages, options) ->
                Flux.create(sink -> rootRequests.add(new RootRequest(messages, sink))));
        final CapturingLLMProvider child = CapturingLLMProvider.responding((messages, options) -> {
            CompletableFuture<LLMResponse> response = new CompletableFuture<>();
            pendingChildren.add(response);
            childRequests.add(new ChildRequest(messages, response));
            return response.join(); // remote peer that can return after local interruption
        });
        final SubagentRuntime runtime;
        final Orchestrator orchestrator;
        final Gateway gateway;

        Fixture() {
            AgentRegistry agents = new AgentRegistry();
            agents.register(AgentProfile.builder().agentId("parent").maxSpawnDepth(3).build());
            agents.register(AgentProfile.builder().agentId("child").maxSpawnDepth(3).build());
            agents.setDefault("parent");
            ToolRegistry tools = new ToolRegistry();
            tools.register(tool("move", args -> {
                actuator.add(Map.copyOf(args));
                return ToolResult.success("moved " + args.get("request"));
            }));
            InMemoryProvider memory = new InMemoryProvider();
            runtime = new SubagentRuntime(new AgentFactory(child, memory, tools, null, List.of(new AgentObserver() {
                @Override public void onAgentComplete(AgentResponse response) {
                    if ("cancelled".equals(response.getStopReason())) cancelledResponses.add(response);
                }
            })), agents, 4);
            orchestrator = new Orchestrator(agents, new AgentFactory(root, memory, tools),
                    new MetadataAgentRouter(agents), runtime);
            gateway = Gateway.builder().chatHandler(orchestrator).build();
        }
        Disposable start(String input) { return gateway.handleStreamReactive(request(input)).subscribe(e -> {}, error -> {}); }
        RootRequest nextRoot() throws Exception {
            RootRequest request = rootRequests.poll(5, TimeUnit.SECONDS);
            assertNotNull(request, "root LLM did not receive the next request");
            return request;
        }
        ChildRequest nextChild() throws Exception {
            ChildRequest request = childRequests.poll(5, TimeUnit.SECONDS);
            assertNotNull(request, "child LLM did not receive the next request");
            return request;
        }
        public void close() {
            orchestrator.shutdown();
            runtime.shutdown();
            pendingChildren.forEach(f -> f.complete(end("cleanup")));
        }
    }
}
