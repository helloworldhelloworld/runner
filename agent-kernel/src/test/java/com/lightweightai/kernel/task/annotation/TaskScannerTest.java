package com.lightweightai.kernel.task.annotation;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.task.core.AbstractTask;
import com.lightweightai.kernel.task.core.Task;
import com.lightweightai.kernel.task.core.TaskContext;
import com.lightweightai.kernel.task.core.TaskRegistry;
import com.lightweightai.kernel.task.core.TaskResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class TaskScannerTest {

    @TaskDef(name = "demo", description = "x", group = "default", condition = "status==SUCCESS")
    public static class DemoTask extends AbstractTask {
        public DemoTask() { super("demo"); }
        @Override
        protected Mono<TaskResult> doExecute(TaskContext ctx) {
            return Mono.just(TaskResult.success("demo"));
        }
    }

    @Test
    void parseConditionIsPublicAndUsable() {
        // PR #109 review B1：parseCondition 必须公开可见，否则跨包测试无法编译。
        Predicate<TaskContext> p = TaskScanner.parseCondition("status==SUCCESS");
        assertNotNull(p);

        TaskContext ctx = new TaskContext();
        assertFalse(p.test(ctx), "no result yet → false");
        ctx.putResult("a", TaskResult.success("ok"));
        assertTrue(p.test(ctx));
    }

    @Test
    void parseConditionEmptyAlwaysTrue() {
        Predicate<TaskContext> p = TaskScanner.parseCondition("");
        assertTrue(p.test(new TaskContext()));

        Predicate<TaskContext> p2 = TaskScanner.parseCondition(null);
        assertTrue(p2.test(new TaskContext()));
    }

    @Test
    void parseConditionRejectsUnknownExpression() {
        assertThrows(IllegalArgumentException.class,
            () -> TaskScanner.parseCondition("not-a-real-expr"));
    }

    @Test
    void registerObjectFromAnnotation() {
        TaskRegistry registry = new TaskRegistry();
        DemoTask task = new DemoTask();
        registry.register(task);

        // payload chain: registered task is callable end-to-end
        Task fetched = registry.get("demo").orElseThrow();
        Flux<StreamEvent> events = fetched.execute(new TaskContext());
        var list = events.collectList().block();
        assertNotNull(list);
        TaskResult r = (TaskResult) list.get(1).getData().get("taskResult");
        assertEquals("demo", r.getContent());
    }
}
