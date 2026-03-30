package com.lightweightai.kernel.task;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskResultTest {

    @Test
    void testSuccessWithContent() {
        TaskResult result = TaskResult.success("ok");
        assertTrue(result.isSuccess());
        assertFalse(result.isError());
        assertFalse(result.isSkipped());
        assertEquals("ok", result.getContent());
        assertTrue(result.getData().isEmpty());
        assertNull(result.getError());
    }

    @Test
    void testSuccessWithData() {
        TaskResult result = TaskResult.success("classified",
                Map.of("intent", "booking", "confidence", 0.95));
        assertTrue(result.isSuccess());
        assertEquals("classified", result.getContent());
        assertEquals("booking", result.getData().get("intent"));
        assertEquals(0.95, result.getData().get("confidence"));
    }

    @Test
    void testError() {
        TaskResult result = TaskResult.error("timeout");
        assertTrue(result.isError());
        assertEquals("timeout", result.getContent());
        assertNotNull(result.getError());
    }

    @Test
    void testErrorFromThrowable() {
        TaskResult result = TaskResult.error(new RuntimeException("connection lost"));
        assertTrue(result.isError());
        assertEquals("connection lost", result.getContent());
    }

    @Test
    void testSkipped() {
        TaskResult result = TaskResult.skipped("condition not met");
        assertTrue(result.isSkipped());
        assertEquals("condition not met", result.getContent());
    }

    @Test
    void testDataIsImmutable() {
        TaskResult result = TaskResult.success("ok", Map.of("key", "value"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getData().put("new", "val"));
    }
}
