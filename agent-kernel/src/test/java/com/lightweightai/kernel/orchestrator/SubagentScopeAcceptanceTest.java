package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SubagentScopeAcceptanceTest {
    @ParameterizedTest
    @EnumSource(value = RiskLevel.class, names = {"SAFE", "WRITE"})
    void restrictedRiskPolicyCannotCreateBackgroundTasks(RiskLevel ceiling) {
        var child = CapturingLLMProvider.responding((messages, options) -> end("unexpected child"));
        try (Fixture f = new Fixture(ToolPolicy.byRiskLevel(ceiling), ToolPolicy.allowAll(), child, 1)) {
            String result = f.call("A", "spawn_subagent", Map.of("task", "private-task", "agentId", "child"));
            assertTrue(result.contains("denied"), "a policy below SYSTEM allowed a background spawn");
            assertTrue(child.messagesHistory().isEmpty());
            assertTrue(f.root.optionsHistory().get(0).getToolDefinitions().stream()
                    .noneMatch(d -> "spawn_subagent".equals(d.get("name"))));
        }
    }

    @Test void cascadeReachesActiveDescendantsThroughCompletedIntermediateTasks() throws Exception {
        CompletableFuture<LLMResponse> finish = new CompletableFuture<>();
        CompletableFuture<Void> leafStarted = new CompletableFuture<>();
        var child = CapturingLLMProvider.responding((messages, options) -> {
            if ("leaf".equals(input(messages))) {
                leafStarted.complete(null);
                return finish.join();
            }
            if (hasToolResult(messages)) return end("ancestor done");
            return response(new ToolCall("nested", "spawn_subagent", Map.of("agentId", "child", "task",
                    "private-task".equals(input(messages)) ? "middle" : "leaf")));
        });
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 3, 3)) {
            try {
                String id = f.spawn("A", null, true);
                assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
                leafStarted.get(5, TimeUnit.SECONDS);
                String middle = spawnedId(toolText(child.messagesHistory().stream()
                        .filter(ms -> "private-task".equals(input(ms)) && hasToolResult(ms)).findFirst().orElseThrow()));
                assertTrue(f.runtime.waitForCompletion(middle, 5, TimeUnit.SECONDS));
                String leaf = spawnedId(toolText(child.messagesHistory().stream()
                        .filter(ms -> "middle".equals(input(ms)) && hasToolResult(ms)).findFirst().orElseThrow()));
                assertTrue(f.call("A", "stop_subagent", Map.of("runId", id)).contains("Stop requested"));
                assertEquals(SubagentRun.Status.CANCELLED, f.runtime.getRunOrCompleted(leaf).getStatus());
                assertEquals(SubagentRun.Status.COMPLETED, f.runtime.getRunOrCompleted(middle).getStatus());
            } finally {
                finish.complete(end("cleanup"));
            }
        }
    }

    @Test void stoppingAChildDoesNotCancelTasksOwnedByAMatchingRootSessionString() throws Exception {
        CompletableFuture<LLMResponse> finish = new CompletableFuture<>();
        CompletableFuture<Void> unrelatedStarted = new CompletableFuture<>();
        var child = CapturingLLMProvider.responding((messages, options) -> {
            if ("unrelated-task".equals(input(messages))) {
                unrelatedStarted.complete(null);
                return finish.join();
            }
            return end("completed child");
        });
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 2)) {
            try {
                String id = f.spawn("A", null, true);
                assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
                String other = f.spawnTask("A:subagent:" + id, "unrelated-task", null, true);
                unrelatedStarted.get(5, TimeUnit.SECONDS);
                assertTrue(f.call("A", "stop_subagent", Map.of("runId", id)).contains("Stop requested"));
                assertTrue(f.runtime.getRunOrCompleted(other).isRunning(), "cascade crossed into a different root scope");
                finish.complete(end("unrelated result"));
                assertTrue(f.runtime.waitForCompletion(other, 5, TimeUnit.SECONDS));
                assertTrue(f.call("A:subagent:" + id, "wait_subagent", Map.of("runIds", other)).contains("unrelated result"));
            } finally {
                finish.complete(end("cleanup"));
            }
        }
    }

    @Test void craftedRootSessionCannotImpersonateAnExistingChildScope() throws Exception {
        var child = CapturingLLMProvider.responding((messages, options) -> {
            if (hasToolResult(messages)) return end("child finished");
            if ("grandchild".equals(input(messages))) return end("secret grandchild result");
            return response(new ToolCall("nested", "spawn_subagent", Map.of("task", "grandchild", "agentId", "child")));
        });
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 2)) {
            String id = f.spawn("A", null, true);
            assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            String nested = spawnedId(toolText(child.messagesHistory().stream()
                    .filter(ms -> "private-task".equals(input(ms)) && hasToolResult(ms)).findFirst().orElseThrow()));
            assertTrue(f.runtime.waitForCompletion(nested, 5, TimeUnit.SECONDS));
            String forged = f.call("A:subagent:" + id, "wait_subagent", Map.of("runIds", nested));
            assertFalse(forged.contains("secret grandchild result"), "a root session impersonated a nested task owner");
            assertTrue(forged.contains("Not found"));
        }
    }
    @Test void independentGrandchildCanUseToolsAfterItsParentCompletes() throws Exception {
        CompletableFuture<LLMResponse> release = new CompletableFuture<>();
        AtomicReference<Map<String, Object>> executed = new AtomicReference<>();
        var child = CapturingLLMProvider.responding((messages, options) -> {
            if (hasToolResult(messages)) return end("done");
            if ("grandchild".equals(input(messages))) return release.join();
            return response(new ToolCall("spawn", "spawn_subagent", Map.of("task", "grandchild", "agentId", "child")));
        });
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 2)) {
            try {
                f.tools.register(tool("move", args -> { executed.set(args); return ToolResult.success("moved"); }));
                String id = f.spawn("A", null, true);
                assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
                String nested = spawnedId(toolText(child.messagesHistory().stream()
                        .filter(ms -> "private-task".equals(input(ms)) && hasToolResult(ms)).findFirst().orElseThrow()));
                release.complete(response(new ToolCall("move", "move", Map.of("distance", 1))));
                assertTrue(f.runtime.waitForCompletion(nested, 5, TimeUnit.SECONDS));
                assertEquals(Map.of("distance", 1), executed.get());
                assertEquals(SubagentRun.Status.COMPLETED, f.runtime.getRunOrCompleted(nested).getStatus());
            } finally {
                release.complete(end("cleanup"));
            }
        }
    }

    @Test void childCannotListWaitForOrStopItsSibling() throws Exception {
        CompletableFuture<LLMResponse> finish = new CompletableFuture<>();
        CompletableFuture<Void> siblingStarted = new CompletableFuture<>();
        AtomicReference<String> siblingId = new AtomicReference<>();
        var child = CapturingLLMProvider.responding((messages, options) -> {
            if ("held-sibling".equals(input(messages))) {
                siblingStarted.complete(null);
                return finish.join();
            }
            if (hasToolResult(messages)) return end("reader done");
            return LLMResponse.builder().message(end("").getMessage()).stopReason("tool_use")
                    .toolCalls(List.of(new ToolCall("list", "list_subagents", Map.of()),
                            new ToolCall("wait", "wait_subagent", Map.of("runIds", siblingId.get(), "timeoutSeconds", 0)),
                            new ToolCall("stop", "stop_subagent", Map.of("runId", siblingId.get())))).build();
        });
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 1)) {
            try {
                siblingId.set(f.spawnTask("A", "held-sibling", null, true));
                siblingStarted.get(5, TimeUnit.SECONDS);
                String reader = f.spawnTask("A", "reader", null, true);
                assertTrue(f.runtime.waitForCompletion(reader, 5, TimeUnit.SECONDS));
                String result = toolText(child.messagesHistory().stream()
                        .filter(ms -> "reader".equals(input(ms)) && hasToolResult(ms)).findFirst().orElseThrow());
                assertTrue(result.contains("No active sub-agents"));
                assertEquals(2, result.split("Not found", -1).length - 1);
                assertFalse(result.contains("held-sibling"));
                assertTrue(f.runtime.getRunOrCompleted(siblingId.get()).isRunning());
            } finally {
                finish.complete(end("private sibling result"));
            }
        }
    }
    @Test void lateModelToolCallAfterStopCannotReachTheActuator() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        CompletableFuture<LLMResponse> releaseModel = new CompletableFuture<>();
        CompletableFuture<List<ConversationMessage>> receivedResult = new CompletableFuture<>();
        AtomicReference<Map<String, Object>> executed = new AtomicReference<>();
        var child = CapturingLLMProvider.responding((messages, options) -> {
            if (hasToolResult(messages)) {
                receivedResult.complete(messages);
                return end("late completion");
            }
            started.complete(null);
            return releaseModel.join(); // remote that ignores a thread interrupt
        });
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 1)) {
            try {
                f.tools.register(tool("move", args -> { executed.set(args); return ToolResult.success("moved"); }));
                String id = f.spawn("A", null, true);
                started.get(5, TimeUnit.SECONDS);
                assertTrue(f.call("A", "stop_subagent", Map.of("runId", id)).contains("Stop requested"));
                releaseModel.complete(response(new ToolCall("late", "move", Map.of("distance", 1))));
                List<ConversationMessage> actual = receivedResult.get(5, TimeUnit.SECONDS);
                assertNull(executed.get(), "a cancelled child reached the actuator");
                assertTrue(toolText(actual).contains("denied"));
                assertEquals(SubagentRun.Status.CANCELLED, f.runtime.getRunOrCompleted(id).getStatus());
            } finally {
                releaseModel.complete(end("cleanup"));
            }
        }
    }
    @Test void childPolicyStillRestrictsAnUnrestrictedParent() throws Exception {
        AtomicReference<Map<String, Object>> executed = new AtomicReference<>();
        var child = CapturingLLMProvider.toolThenEnd("move", Map.of("distance", 1), "done");
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.denyList(java.util.Set.of("move")), child, 1)) {
            f.tools.register(tool("move", args -> { executed.set(args); return ToolResult.success("moved"); }));
            String id = f.spawn("A", null, true);
            assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            assertNull(executed.get());
            assertTrue(toolText(child.lastMessages()).contains("denied"));
            assertTrue(child.optionsHistory().get(0).getToolDefinitions().stream().noneMatch(d -> "move".equals(d.get("name"))));
        }
    }

    @Test void grandchildKeepsAncestorPolicyAndItsOriginalProfile() throws Exception {
        AtomicReference<Map<String, Object>> executed = new AtomicReference<>();
        ToolPolicy parentPolicy = req -> "parent".equals(req.profile().getAgentId()) && "move".equals(req.toolName())
                && req.args() != null ? ToolPolicy.Decision.DENY : ToolPolicy.Decision.ABSTAIN;
        var child = CapturingLLMProvider.responding((messages, options) -> {
            if (hasToolResult(messages)) return end("done");
            return response("private-task".equals(input(messages))
                    ? new ToolCall("nested", "spawn_subagent", Map.of("task", "grandchild", "agentId", "child"))
                    : new ToolCall("move", "move", Map.of("distance", 100)));
        });
        try (Fixture f = new Fixture(parentPolicy, ToolPolicy.allowAll(), child, 5)) {
            f.tools.register(tool("move", args -> { executed.set(args); return ToolResult.success("moved"); }));
            String id = f.spawn("A", null, true);
            assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            String nested = spawnedId(toolText(child.messagesHistory().stream()
                    .filter(ms -> "private-task".equals(input(ms)) && hasToolResult(ms)).findFirst().orElseThrow()));
            assertTrue(f.runtime.waitForCompletion(nested, 5, TimeUnit.SECONDS));
            assertEquals(SubagentRun.Status.COMPLETED, f.runtime.getRunOrCompleted(nested).getStatus(),
                    f.runtime.getRunOrCompleted(nested).getErrorMessage());
            assertNull(executed.get());
            assertTrue(child.messagesHistory().stream().filter(ms -> "grandchild".equals(input(ms)))
                    .anyMatch(ms -> toolText(ms).contains("denied")),
                    () -> child.messagesHistory().stream().map(ms -> input(ms) + ": " + toolText(ms)).toList().toString());
        }
    }

    @Test void choosingAGenerousProfileCannotResetTheRootDepthLimit() throws Exception {
        var child = CapturingLLMProvider.responding((messages, options) -> hasToolResult(messages) ? end("done")
                : response(new ToolCall("nested", "spawn_subagent", Map.of("agentId", "child", "task",
                        "private-task".equals(input(messages)) ? "level-two" : "level-three"))));
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 99)) {
            String id = f.spawn("A", null, true);
            assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            String nested = spawnedId(toolText(child.messagesHistory().stream()
                    .filter(ms -> "private-task".equals(input(ms)) && hasToolResult(ms)).findFirst().orElseThrow()));
            assertTrue(f.runtime.waitForCompletion(nested, 5, TimeUnit.SECONDS));
            assertTrue(child.messagesHistory().stream().filter(ms -> "level-two".equals(input(ms)))
                    .anyMatch(ms -> toolText(ms).contains("denied")));
            assertTrue(child.messagesHistory().stream().noneMatch(ms -> "level-three".equals(input(ms))));
        }
    }

    @Test void ownerCanStopDescendantsAfterItsDirectChildHasCompleted() throws Exception {
        CompletableFuture<Void> grandchildStarted = new CompletableFuture<>();
        CompletableFuture<LLMResponse> finish = new CompletableFuture<>();
        var child = CapturingLLMProvider.responding((messages, options) -> {
            if ("held-grandchild".equals(input(messages))) {
                grandchildStarted.complete(null);
                return finish.join();
            }
            return hasToolResult(messages) ? end("parent child finished")
                    : response(new ToolCall("nested", "spawn_subagent", Map.of("task", "held-grandchild", "agentId", "child")));
        });
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 2)) {
            try {
                String id = f.spawn("A", null, true);
                assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
                grandchildStarted.get(5, TimeUnit.SECONDS);
                String nested = spawnedId(toolText(child.messagesHistory().stream()
                        .filter(ms -> "private-task".equals(input(ms)) && hasToolResult(ms)).findFirst().orElseThrow()));
                assertTrue(f.call("B", "stop_subagent", Map.of("runId", id)).contains("Not found"));
                assertTrue(f.runtime.getRunOrCompleted(nested).isRunning());
                assertTrue(f.call("A", "stop_subagent", Map.of("runId", id)).contains("Stop requested"));
                assertEquals(SubagentRun.Status.CANCELLED, f.runtime.getRunOrCompleted(nested).getStatus());
                assertEquals(SubagentRun.Status.COMPLETED, f.runtime.getRunOrCompleted(id).getStatus());
                assertTrue(f.call("A", "wait_subagent", Map.of("runIds", id)).contains("parent child finished"));
            } finally {
                finish.complete(end("late output"));
            }
        }
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void childCannotRelaxParentArgumentPolicy(boolean reactive) throws Exception {
        AtomicReference<Map<String, Object>> executed = new AtomicReference<>();
        ToolPolicy parent = req -> "move".equals(req.toolName()) && req.args() != null
                && ((Number) req.args().get("distance")).intValue() > 10
                ? ToolPolicy.Decision.DENY : ToolPolicy.Decision.ALLOW;
        var child = CapturingLLMProvider.toolThenEnd("move", Map.of("distance", 100), "child done");
        try (Fixture f = new Fixture(parent, ToolPolicy.allowAll(), child, 1)) {
            f.tools.register(tool("move", args -> { executed.set(args); return ToolResult.success("moved"); }));
            String id = f.spawn("A", null, reactive);
            assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            assertNull(executed.get(), "child arguments must obey the parent ceiling");
            assertTrue(toolText(child.lastMessages()).contains("denied"));
            assertTrue(child.optionsHistory().get(0).getToolDefinitions().stream().anyMatch(d -> "move".equals(d.get("name"))));
        }
    }

    @Test void childReceivesTheParentsDeviceAndDispatcher() throws Exception {
        AtomicReference<Map<String, Object>> devicePayload = new AtomicReference<>();
        var child = CapturingLLMProvider.toolThenEnd("probe", Map.of("precision", 7), "child done");
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 1)) {
            f.tools.register(tool("probe", args -> ToolResult.success("wrong generic device")));
            f.tools.register(new DeviceProbe(), "phone", VersionRange.exact("2.0"));
            ToolExecutionContext context = new ToolExecutionContext((id, name, directive) -> {
                assertEquals(1234, directive.getTimeoutMs());
                devicePayload.set(directive.getPayload());
                return CompletableFuture.completedFuture(ToolResult.success("phone result"));
            }, 1234, DeviceContext.of("phone", "2.0"));
            String id = f.spawn("A", context, true);
            assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            assertEquals(Map.of("precision", 7), devicePayload.get());
            assertTrue(toolText(child.lastMessages()).contains("phone result"));
        }
    }

    @Test void leafProfileCanWorkWithoutBeingAllowedToSpawn() throws Exception {
        var child = CapturingLLMProvider.endTurn("leaf result");
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 0)) {
            String id = f.spawn("A", null, true);
            assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            assertEquals("leaf result", f.runtime.getRunOrCompleted(id).getResult().getText());
            assertTrue(child.lastOptions().getToolDefinitions().stream().noneMatch(d -> "spawn_subagent".equals(d.get("name"))));
        }
    }

    @Test void anotherSessionCannotWaitForACompletedPrivateResult() throws Exception {
        var child = CapturingLLMProvider.endTurn("private-child-result");
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 1)) {
            String id = f.spawn("A", null, true);
            assertTrue(f.runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            String foreign = f.call("A:subagent:spoof", "wait_subagent", Map.of("runIds", id, "timeoutSeconds", 1));
            assertFalse(foreign.contains("private-child-result"), "foreign result must not reach this LLM");
            assertTrue(foreign.contains("Not found"));
            assertTrue(f.call("A", "wait_subagent", Map.of("runIds", id)).contains("private-child-result"));
        }
    }

    @Test void anotherSessionCannotListAnActivePrivateTask() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        CompletableFuture<LLMResponse> finish = new CompletableFuture<>();
        var child = CapturingLLMProvider.responding((messages, options) -> {
            started.complete(null);
            return finish.join();
        });
        try (Fixture f = new Fixture(ToolPolicy.allowAll(), ToolPolicy.allowAll(), child, 1)) {
            try {
                String id = f.spawn("A", null, true);
                started.get(5, TimeUnit.SECONDS);
                String foreign = f.call("A:subagent:spoof", "list_subagents", Map.of());
                assertFalse(foreign.contains(id), "list leaked another session's run ID");
                assertFalse(foreign.contains("private-task"));
                assertTrue(f.call("A", "list_subagents", Map.of()).contains(id));
            } finally {
                finish.complete(end("done"));
            }
        }
    }

    static class Fixture implements AutoCloseable {
        final ToolRegistry tools = new ToolRegistry();
        final AtomicReference<ToolCall> command = new AtomicReference<>();
        final CapturingLLMProvider root = CapturingLLMProvider.responding((messages, options) ->
                messages.stream().anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                        ? end("root done") : response(command.get()));
        final SubagentRuntime runtime;
        final Orchestrator orchestrator;

        Fixture(ToolPolicy parentPolicy, ToolPolicy childPolicy, CapturingLLMProvider child, int childDepth) {
            this(parentPolicy, childPolicy, child, childDepth, 2);
        }

        Fixture(ToolPolicy parentPolicy, ToolPolicy childPolicy, CapturingLLMProvider child, int childDepth, int rootDepth) {
            AgentRegistry agents = new AgentRegistry();
            agents.register(AgentProfile.builder().agentId("parent").toolPolicy(parentPolicy).maxSpawnDepth(rootDepth).build());
            agents.register(AgentProfile.builder().agentId("child").toolPolicy(childPolicy).maxSpawnDepth(childDepth).build());
            agents.setDefault("parent");
            InMemoryProvider memory = new InMemoryProvider();
            runtime = new SubagentRuntime(new AgentFactory(child, memory, tools), agents, 4);
            tools.register(new WaitSubagentTool(runtime));
            tools.register(new ListSubagentsTool(runtime));
            orchestrator = new Orchestrator(agents, new AgentFactory(root, memory, tools), new MetadataAgentRouter(agents), runtime);
        }

        String spawn(String session, ToolExecutionContext context, boolean reactive) {
            return spawnTask(session, "private-task", context, reactive);
        }

        String spawnTask(String session, String task, ToolExecutionContext context, boolean reactive) {
            command.set(new ToolCall("spawn", "spawn_subagent", Map.of("task", task, "agentId", "child")));
            GatewayRequest request = GatewayRequest.builder().sessionId(session).message("delegate")
                    .toolExecutionContext(context).build();
            if (reactive) orchestrator.chatStreamReactive(request).collectList().block(Duration.ofSeconds(5));
            else orchestrator.chat(request);
            var matcher = Pattern.compile("Run ID: ([a-zA-Z0-9-]+)").matcher(toolText(root.lastMessages()));
            assertTrue(matcher.find(), "expected successful spawn, actual LLM received " + toolText(root.lastMessages()));
            return matcher.group(1);
        }

        String call(String session, String name, Map<String, Object> args) {
            command.set(new ToolCall("control", name, args));
            orchestrator.chatStreamReactive(GatewayRequest.builder().sessionId(session).message(name).build())
                    .collectList().block(Duration.ofSeconds(5));
            return toolText(root.lastMessages());
        }

        public void close() { orchestrator.shutdown(); runtime.shutdown(); }
    }

    static String toolText(List<ConversationMessage> messages) {
        return messages.stream().filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                .map(ConversationMessage::getTextContent).reduce("", (a, b) -> a + b);
    }

    static boolean hasToolResult(List<ConversationMessage> messages) {
        return messages.stream().anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.TOOL);
    }

    static String input(List<ConversationMessage> messages) {
        return messages.stream().filter(m -> m.getRole() == ConversationMessage.MessageRole.USER)
                .map(ConversationMessage::getTextContent).reduce((a, b) -> b).orElseThrow();
    }

    static String spawnedId(String toolText) {
        var matcher = Pattern.compile("Run ID: ([a-zA-Z0-9-]+)").matcher(toolText);
        assertTrue(matcher.find(), toolText);
        return matcher.group(1);
    }

    static LLMResponse end(String text) {
        return LLMResponse.builder().message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text).build()).stopReason("end_turn").build();
    }

    static LLMResponse response(ToolCall call) {
        return LLMResponse.builder().message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("").build()).toolCalls(List.of(call)).stopReason("tool_use").build();
    }

    static Tool tool(String name, java.util.function.Function<Map<String, Object>, ToolResult> execution) {
        return new Tool() {
            public String getName() { return name; }
            public String getDescription() { return name; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) { return execution.apply(args); }
        };
    }

    static class DeviceProbe implements Tool, ToolMetadata {
        public String getName() { return "probe"; }
        public String getDescription() { return "phone probe"; }
        public ToolSchema getSchema() { return ToolSchema.empty(); }
        public boolean isClientSide() { return true; }
        public ToolResult execute(Map<String, Object> args) { return ToolResult.success("wrong server fallback"); }
    }
}
