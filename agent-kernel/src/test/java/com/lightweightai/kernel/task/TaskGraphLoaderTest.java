package com.lightweightai.kernel.task;

import com.lightweightai.kernel.task.config.TaskGraphBundle;
import com.lightweightai.kernel.task.config.TaskGraphLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskGraphLoaderTest {

    private TaskRegistry registry;
    private TaskGraphLoader loader;

    @BeforeEach
    void setUp() {
        registry = new TaskRegistry();
        loader = new TaskGraphLoader(registry);

        // 注册测试 Task
        registry.register(new AbstractTask("intent-classifier", "classify intent") {
            @Override
            protected Mono<TaskResult> doExecute(TaskContext ctx) {
                return Mono.just(TaskResult.success("booking",
                        Map.of("intent", "booking", "confidence", 0.95)));
            }
        });
        registry.register(new AbstractTask("safety-filter", "check safety") {
            @Override
            protected Mono<TaskResult> doExecute(TaskContext ctx) {
                return Mono.just(TaskResult.success("safe"));
            }
        });
        registry.register(new AbstractTask("rag-retrieval", "retrieve docs") {
            @Override
            protected Mono<TaskResult> doExecute(TaskContext ctx) {
                return Mono.just(TaskResult.success("found 3 docs"));
            }
        });
        registry.register(new AbstractTask("analytics-logger", "log analytics") {
            @Override
            protected Mono<TaskResult> doExecute(TaskContext ctx) {
                return Mono.just(TaskResult.success("logged"));
            }
        });
    }

    @Test
    void testLoadSequentialSteps() throws IOException {
        String yaml = """
            task-graphs:
              pipeline:
                steps:
                  - task: intent-classifier
                  - task: rag-retrieval
                  - task: analytics-logger
            """;

        TaskGraphBundle bundle = loader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        TaskGraph graph = bundle.getGraph("pipeline").orElseThrow();
        assertEquals(3, graph.getTasks().size());

        TaskContext ctx = TaskContext.builder().userInput("hello").build();
        graph.execute(ctx).blockLast();

        assertEquals(3, ctx.getAllResults().size());
        assertTrue(ctx.getResult("intent-classifier").get().isSuccess());
        assertTrue(ctx.getResult("rag-retrieval").get().isSuccess());
        assertTrue(ctx.getResult("analytics-logger").get().isSuccess());
    }

    @Test
    void testLoadParallelSteps() throws IOException {
        String yaml = """
            task-graphs:
              parallel-test:
                steps:
                  - parallel:
                      tasks: [intent-classifier, safety-filter]
                      join: ALL_SUCCESS
                  - task: rag-retrieval
            """;

        TaskGraphBundle bundle = loader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        TaskGraph graph = bundle.getGraph("parallel-test").orElseThrow();
        assertEquals(3, graph.getTasks().size());

        TaskContext ctx = TaskContext.builder().userInput("hello").build();
        graph.execute(ctx).blockLast();

        assertEquals(3, ctx.getAllResults().size());
    }

    @Test
    void testLoadWithCondition() throws IOException {
        String yaml = """
            task-graphs:
              cond-test:
                steps:
                  - task: safety-filter
                  - task: rag-retrieval
                    condition:
                      task: safety-filter
                      status: SUCCESS
            """;

        TaskGraphBundle bundle = loader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        TaskGraph graph = bundle.getGraph("cond-test").orElseThrow();
        TaskContext ctx = TaskContext.builder().userInput("hello").build();
        graph.execute(ctx).blockLast();

        assertTrue(ctx.getResult("rag-retrieval").get().isSuccess());
    }

    @Test
    void testLoadWithOrchestration() throws IOException {
        String yaml = """
            task-graphs:
              pre:
                steps:
                  - task: intent-classifier
              post:
                steps:
                  - task: analytics-logger
            orchestration:
              pre-process: pre
              post-process: post
            """;

        TaskGraphBundle bundle = loader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertTrue(bundle.getPreProcessGraph().isPresent());
        assertTrue(bundle.getPostProcessGraph().isPresent());
        assertEquals("pre", bundle.getPreProcessGraph().get().getName());
        assertEquals("post", bundle.getPostProcessGraph().get().getName());
    }

    @Test
    void testLoadWithExposeAsTool() throws IOException {
        String yaml = """
            task-graphs:
              search:
                expose-as-tool:
                  name: search_knowledge
                  description: Search KB
                steps:
                  - parallel:
                      tasks: [intent-classifier, safety-filter]
                      join: ALL_COMPLETE
                  - task: rag-retrieval
            """;

        TaskGraphBundle bundle = loader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, bundle.getExposedTools().size());
        assertEquals("search_knowledge", bundle.getExposedTools().get(0).getName());
    }

    @Test
    void testLoadWithConfig() throws IOException {
        String yaml = """
            task-graphs:
              configured:
                steps:
                  - task: rag-retrieval
                config:
                  rag-retrieval:
                    max-results: 10
                    threshold: 0.8
            """;

        TaskGraphBundle bundle = loader.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        TaskGraph graph = bundle.getGraph("configured").orElseThrow();
        TaskContext ctx = TaskContext.builder().userInput("test").build();
        graph.execute(ctx).blockLast();

        // ConfiguredTask 将配置注入到 context attributes
        assertEquals(10, ctx.getAttribute("config:rag-retrieval:max-results", Integer.class).orElse(0));
        assertEquals(0.8, ctx.getAttribute("config:rag-retrieval:threshold", Double.class).orElse(0.0));
    }

    @Test
    void testMissingTaskThrows() {
        String yaml = """
            task-graphs:
              missing:
                steps:
                  - task: nonexistent-task
            """;

        assertThrows(IllegalArgumentException.class, () ->
                loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))));
    }
}
