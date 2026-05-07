package com.lightweightai.kernel.task.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskResultTest {

    @Test
    void successHasContentAndStatus() {
        TaskResult r = TaskResult.success("hello");
        assertEquals(TaskResult.Status.SUCCESS, r.getStatus());
        assertEquals("hello", r.getContent());
        assertTrue(r.isSuccess());
        assertFalse(r.isError());
    }

    @Test
    void errorWithCausePreservesTypeAndStackHead() {
        IllegalStateException ex = new IllegalStateException("boom");
        TaskResult r = TaskResult.error("wrapped", ex);

        assertEquals(TaskResult.Status.ERROR, r.getStatus());
        assertEquals("wrapped", r.getContent());
        assertEquals("java.lang.IllegalStateException", r.getErrorType());
        assertNotNull(r.getErrorStackHead());
        // stack head must contain at least one frame from this test class
        assertTrue(r.getErrorStackHead().contains("TaskResultTest"),
            "stack head should contain calling class, got: " + r.getErrorStackHead());
    }

    @Test
    void dataIsImmutable() {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("k", "v");
        TaskResult r = TaskResult.success("ok", data);
        // mutating the source after construction must not affect the result
        data.put("k2", "v2");
        assertFalse(r.getData().containsKey("k2"));
        assertThrows(UnsupportedOperationException.class, () -> r.getData().put("x", "y"));
    }
}
