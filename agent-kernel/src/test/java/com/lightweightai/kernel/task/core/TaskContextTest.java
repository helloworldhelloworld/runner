package com.lightweightai.kernel.task.core;

import com.lightweightai.kernel.core.CancellationToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskContextTest {

    @Test
    void getAttributesReturnsTrueSnapshot() {
        TaskContext ctx = new TaskContext();
        ctx.setAttribute("a", 1);
        var snapshot = ctx.getAttributes();
        ctx.setAttribute("b", 2);
        assertEquals(1, snapshot.size(), "snapshot must not reflect later writes");
        assertTrue(snapshot.containsKey("a"));
        assertFalse(snapshot.containsKey("b"));
    }

    @Test
    void getAttributeWithTypeThrowsOnMismatch() {
        TaskContext ctx = new TaskContext();
        ctx.setAttribute("count", "not a number");
        assertThrows(ClassCastException.class, () -> ctx.getAttribute("count", Integer.class));
    }

    @Test
    void cancellationTokenIsExposed() {
        CancellationToken token = new CancellationToken();
        TaskContext ctx = new TaskContext(token);
        assertFalse(ctx.isCancelled());
        token.cancel();
        assertTrue(ctx.isCancelled());
        assertSame(token, ctx.getCancellationToken());
    }

    @Test
    void resultsAreSnapshotted() {
        TaskContext ctx = new TaskContext();
        ctx.putResult("a", TaskResult.success("v"));
        var snap = ctx.getAllResults();
        ctx.putResult("b", TaskResult.success("v2"));
        assertEquals(1, snap.size());
        assertTrue(ctx.hasResult("b"));
    }
}
