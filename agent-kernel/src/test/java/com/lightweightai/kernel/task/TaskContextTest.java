package com.lightweightai.kernel.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskContextTest {

    @Test
    void testBuilder() {
        TaskContext ctx = TaskContext.builder()
                .sessionId("s1")
                .requestId("r1")
                .userInput("hello")
                .attribute("key", "value")
                .build();

        assertEquals("s1", ctx.getSessionId());
        assertEquals("r1", ctx.getRequestId());
        assertEquals("hello", ctx.getUserInput());
        assertEquals("value", ctx.getAttribute("key", String.class).orElse(null));
    }

    @Test
    void testResultStorage() {
        TaskContext ctx = TaskContext.builder().userInput("test").build();

        assertTrue(ctx.getResult("task1").isEmpty());

        ctx.putResult("task1", TaskResult.success("done"));
        assertTrue(ctx.getResult("task1").isPresent());
        assertTrue(ctx.getResult("task1").get().isSuccess());
    }

    @Test
    void testGetAllResults() {
        TaskContext ctx = TaskContext.builder().userInput("test").build();
        ctx.putResult("a", TaskResult.success("a done"));
        ctx.putResult("b", TaskResult.error("b failed"));

        assertEquals(2, ctx.getAllResults().size());
    }

    @Test
    void testAttributeTypeSafety() {
        TaskContext ctx = TaskContext.builder().userInput("test").build();
        ctx.setAttribute("count", 42);

        assertTrue(ctx.getAttribute("count", Integer.class).isPresent());
        assertEquals(42, ctx.getAttribute("count", Integer.class).get());
        assertTrue(ctx.getAttribute("count", String.class).isEmpty()); // wrong type
    }
}
