package com.lightweightai.kernel.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lightweightai.kernel.llm.ToolCall;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.lightweightai.kernel.orchestrator.ForegroundSubagentCancellationAcceptanceTest.*;
import static com.lightweightai.kernel.orchestrator.SubagentScopeAcceptanceTest.end;
import static com.lightweightai.kernel.orchestrator.SubagentScopeAcceptanceTest.input;
import static com.lightweightai.kernel.orchestrator.SubagentScopeAcceptanceTest.response;
import static com.lightweightai.kernel.orchestrator.SubagentScopeAcceptanceTest.spawnedId;
import static com.lightweightai.kernel.orchestrator.SubagentScopeAcceptanceTest.toolText;
import static org.junit.jupiter.api.Assertions.*;

/** Real native execution; export only controlled peer observations, never live user data. */
class HarnessEvidenceAcceptanceTest {
    @Test void cancelledDescendantCannotReachActuator() throws Exception {
        Map<String, Object> observed = new LinkedHashMap<>();
        try (Fixture f = new Fixture()) {
            Disposable foreground = f.start("A");
            f.nextRoot().respond(spawn("old child"));
            String childId = spawnedId(toolText(f.nextRoot().messages()));
            ChildRequest child = f.nextChild();
            observed.put("childInput", input(child.messages()));
            observed.put("providerToolDefinitions", f.child.lastOptions().getToolDefinitions());
            assertTrue(f.child.lastOptions().getToolDefinitions().stream().anyMatch(t -> "move".equals(t.get("name"))));
            foreground.dispose();
            observed.put("childStatus", f.runtime.getRunOrCompleted(childId).getStatus().name());
            assertEquals("CANCELLED", observed.get("childStatus"));
            child.respond(response(new ToolCall("late", "move", Map.of("request", "old A"))));
            var terminal = f.cancelledResponses.poll(5, TimeUnit.SECONDS);
            assertNotNull(terminal, "The actual child execution must finish before inspecting actuator effects");
            observed.put("executionStopReason", terminal.getStopReason());
            observed.put("actuatorRequests", List.copyOf(f.actuator));
            observed.put("nextModelRequestCountAfterCancellation", f.childRequests.size());
            assertEquals("cancelled", terminal.getStopReason());
            assertTrue(f.childRequests.isEmpty(), "Cancelled work must not spend another model request");
            assertEquals(List.of(), observed.get("actuatorRequests"));
            assertEquals("old child", observed.get("childInput"));
        } finally {
            export("cancelled-descendant", observed);
        }
    }

    @Test void independentChildDeliversActualPayloadAfterForegroundCompletes() throws Exception {
        Map<String, Object> observed = new LinkedHashMap<>();
        try (Fixture f = new Fixture()) {
            java.util.concurrent.CompletableFuture<Void> completed = new java.util.concurrent.CompletableFuture<>();
            f.gateway.handleStreamReactive(request("background request")).subscribe(e -> {},
                    completed::completeExceptionally, () -> completed.complete(null));
            f.nextRoot().respond(spawn("independent"));
            RootRequest waiting = f.nextRoot();
            String childId = spawnedId(toolText(waiting.messages()));
            ChildRequest child = f.nextChild();
            waiting.respond(end("delegated"));
            completed.get(5, TimeUnit.SECONDS);
            observed.put("statusAfterForegroundCompletion", f.runtime.getRunOrCompleted(childId).getStatus().name());
            observed.put("childInput", input(child.messages()));
            observed.put("providerToolDefinitions", f.child.lastOptions().getToolDefinitions());
            assertTrue(f.child.lastOptions().getToolDefinitions().stream().anyMatch(t -> "move".equals(t.get("name"))));
            assertEquals("RUNNING", observed.get("statusAfterForegroundCompletion"));
            child.respond(response(new ToolCall("move", "move", Map.of("request", "background"))));
            ChildRequest delivered = f.nextChild();
            Map<String, Object> actual = f.actuator.poll(5, TimeUnit.SECONDS);
            observed.put("actuatorRequests", actual == null ? List.of() : List.of(actual));
            observed.put("toolResultInNextModelRequest", toolText(delivered.messages()));
            observed.put("nextModelMessages", delivered.messages());
            delivered.respond(end("done"));
            assertTrue(f.runtime.waitForCompletion(childId, 5, TimeUnit.SECONDS));
            observed.put("childStatus", f.runtime.getRunOrCompleted(childId).getStatus().name());
            assertEquals(Map.of("request", "background"), actual);
            assertTrue(toolText(delivered.messages()).contains("moved background"));
            assertEquals("COMPLETED", observed.get("childStatus"));
        } finally {
            export("independent-child", observed);
        }
    }

    private static void export(String caseId, Map<String, Object> observed) throws Exception {
        String directory = System.getProperty("runner.evidence.dir");
        if (directory == null) return;
        String candidate = System.getProperty("runner.candidate.digest", "");
        String evaluation = System.getProperty("runner.evaluation.digest", "");
        assertTrue(candidate.matches("[a-f0-9]{64}"), "candidate digest required for evidence export");
        assertTrue(evaluation.matches("[a-f0-9]{64}"), "evaluation digest required for evidence export");
        Map<String, Object> evidence = Map.of("schemaVersion", 1, "caseId", caseId,
                "candidateDigest", candidate, "evaluationDigest", evaluation,
                "observations", observed);
        Path root = Path.of(directory);
        Files.createDirectories(root);
        Files.writeString(root.resolve(caseId + ".json"),
                new ObjectMapper().registerModule(new JavaTimeModule())
                        .writerWithDefaultPrettyPrinter().writeValueAsString(evidence),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
