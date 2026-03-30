package com.lightweightai.kernel.task;

import com.lightweightai.kernel.task.annotation.TaskDef;
import com.lightweightai.kernel.task.annotation.TaskScanner;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class TaskScannerTest {

    @TaskDef(name = "test-a", description = "task A", group = "test-group", order = 0)
    static class TaskA extends AbstractTask {
        TaskA() { super("test-a", "task A"); }
        @Override
        protected Mono<TaskResult> doExecute(TaskContext ctx) {
            return Mono.just(TaskResult.success("A done"));
        }
    }

    @TaskDef(name = "test-b", description = "task B", group = "test-group",
            dependsOn = "test-a", order = 1)
    static class TaskB extends AbstractTask {
        TaskB() { super("test-b", "task B"); }
        @Override
        protected Mono<TaskResult> doExecute(TaskContext ctx) {
            return Mono.just(TaskResult.success("B done"));
        }
    }

    @TaskDef(name = "test-c", description = "task C", group = "test-group",
            dependsOn = "test-a", condition = "test-a:SUCCESS", order = 2)
    static class TaskC extends AbstractTask {
        TaskC() { super("test-c", "task C"); }
        @Override
        protected Mono<TaskResult> doExecute(TaskContext ctx) {
            return Mono.just(TaskResult.success("C done"));
        }
    }

    @Test
    void testAssembleGraphs() {
        TaskRegistry registry = new TaskRegistry();
        registry.register(new TaskA());
        registry.register(new TaskB());
        registry.register(new TaskC());

        Map<String, TaskGraph> graphs = TaskScanner.assembleGraphs(registry);

        assertTrue(graphs.containsKey("test-group"));
        TaskGraph graph = graphs.get("test-group");
        assertEquals(3, graph.getTasks().size());

        TaskContext ctx = TaskContext.builder().userInput("test").build();
        graph.execute(ctx).blockLast();

        assertTrue(ctx.getResult("test-a").get().isSuccess());
        assertTrue(ctx.getResult("test-b").get().isSuccess());
        assertTrue(ctx.getResult("test-c").get().isSuccess());
    }

    @Test
    void testParseCondition() {
        Predicate<TaskContext> pred = TaskScanner.parseCondition("my-task:SUCCESS");

        TaskContext ctx = TaskContext.builder().userInput("test").build();
        assertFalse(pred.test(ctx)); // no result yet

        ctx.putResult("my-task", TaskResult.success("ok"));
        assertTrue(pred.test(ctx));

        TaskContext ctx2 = TaskContext.builder().userInput("test").build();
        ctx2.putResult("my-task", TaskResult.error("fail"));
        assertFalse(pred.test(ctx2));
    }

    @Test
    void testParseConditionInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> TaskScanner.parseCondition("invalid-format"));
    }

    @Test
    void testTasksWithoutAnnotationIgnored() {
        TaskRegistry registry = new TaskRegistry();
        // This task has no @TaskDef annotation
        registry.register(new AbstractTask("plain", "no annotation") {
            @Override
            protected Mono<TaskResult> doExecute(TaskContext ctx) {
                return Mono.just(TaskResult.success("done"));
            }
        });
        registry.register(new TaskA());

        Map<String, TaskGraph> graphs = TaskScanner.assembleGraphs(registry);
        assertEquals(1, graphs.size());
        assertEquals(1, graphs.get("test-group").getTasks().size());
    }
}
