package com.lightweightai.kernel.task.config;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.lightweightai.kernel.task.core.AbstractTask;
import com.lightweightai.kernel.task.core.Task;
import com.lightweightai.kernel.task.core.TaskContext;
import com.lightweightai.kernel.task.core.TaskRegistry;
import com.lightweightai.kernel.task.core.TaskResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TaskGraphLoaderTest {

    private static Task taskNamed(String n) {
        return new AbstractTask(n) {
            @Override
            protected Mono<TaskResult> doExecute(TaskContext ctx) {
                return Mono.just(TaskResult.success(n));
            }
        };
    }

    private TaskRegistry registry() {
        TaskRegistry r = new TaskRegistry();
        r.register(taskNamed("intent.classifier"));
        r.register(taskNamed("rag.retrieval"));
        r.register(taskNamed("safety.filter"));
        return r;
    }

    @Test
    void loadsDagWithNeedsAndJoin() throws IOException {
        String yaml = """
            version: 1
            tasks:
              - name: classify
                ref: intent.classifier
              - name: retrieve
                ref: rag.retrieval
                needs: [intent.classifier]
                join: ALL_SUCCESS
              - name: filter
                ref: safety.filter
                needs: [rag.retrieval]
            """;
        TaskGraphBundle bundle = new TaskGraphLoader(registry()).load(yaml);

        // Payload assertion: dependency edges must be the exact set the YAML described.
        var deps = bundle.getGraph().getDependencies();
        assertEquals(Set.of(), deps.get("intent.classifier"));
        assertEquals(Set.of("intent.classifier"), deps.get("rag.retrieval"));
        assertEquals(Set.of("rag.retrieval"), deps.get("safety.filter"));
    }

    @Test
    void unknownTopLevelFieldFailsLoudly() {
        String yaml = """
            version: 1
            taskz: []
            """;
        var ex = assertThrows(UnrecognizedPropertyException.class,
            () -> new TaskGraphLoader(registry()).load(yaml));
        assertTrue(ex.getMessage().contains("taskz"));
    }

    @Test
    void unknownNestedFieldFailsLoudly() {
        String yaml = """
            version: 1
            tasks:
              - name: x
                ref: intent.classifier
                neeeds: [foo]
            """;
        assertThrows(UnrecognizedPropertyException.class,
            () -> new TaskGraphLoader(registry()).load(yaml));
    }

    @Test
    void unknownRefFailsAtLoadTime() {
        String yaml = """
            version: 1
            tasks:
              - name: x
                ref: nonexistent.task
            """;
        var ex = assertThrows(IllegalArgumentException.class,
            () -> new TaskGraphLoader(registry()).load(yaml));
        assertTrue(ex.getMessage().contains("nonexistent.task"));
    }

    @Test
    void quorumJoinSyntaxAccepted() throws IOException {
        String yaml = """
            version: 1
            tasks:
              - name: a
                ref: intent.classifier
              - name: b
                ref: rag.retrieval
              - name: c
                ref: safety.filter
                needs: [intent.classifier, rag.retrieval]
                join: "quorum:1"
            """;
        TaskGraphBundle bundle = new TaskGraphLoader(registry()).load(yaml);
        assertEquals(Set.of("intent.classifier", "rag.retrieval"),
            bundle.getGraph().getDependencies().get("safety.filter"));
    }

    @Test
    void exposeAsToolsParsed() throws IOException {
        String yaml = """
            version: 1
            tasks:
              - name: a
                ref: intent.classifier
            expose-as-tools:
              - name: my_pipeline
                description: end-to-end intent classification
            """;
        TaskGraphBundle bundle = new TaskGraphLoader(registry()).load(yaml);
        assertEquals(1, bundle.getExposedTools().size());
        assertEquals("my_pipeline", bundle.getExposedTools().get(0).name);
    }
}
