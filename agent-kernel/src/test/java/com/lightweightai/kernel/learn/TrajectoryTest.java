package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Trajectory — immutable run trace record")
class TrajectoryTest {

    @Test
    @DisplayName("record stores all fields correctly")
    void storesAllFields() {
        Trajectory.Step step = new Trajectory.Step("tool1", Map.of("k", "v"), ToolResult.success("ok"));
        Trajectory t = new Trajectory("input", "sess-1", List.of(step), "final", "end_turn", false);

        assertEquals("input", t.userInput());
        assertEquals("sess-1", t.sessionId());
        assertEquals(1, t.steps().size());
        assertEquals("final", t.finalText());
        assertEquals("end_turn", t.stopReason());
        assertFalse(t.errored());
    }

    @Test
    @DisplayName("steps list is immutable copy")
    void stepsImmutable() {
        Trajectory.Step step = new Trajectory.Step("tool1", Map.of(), ToolResult.success("ok"));
        Trajectory t = new Trajectory("input", "s", List.of(step), "done", "end_turn", false);

        assertThrows(UnsupportedOperationException.class,
                () -> t.steps().add(new Trajectory.Step("injected", Map.of(), ToolResult.success("bad"))));
    }

    @Test
    @DisplayName("null steps becomes empty list")
    void nullStepsBecomesEmpty() {
        Trajectory t = new Trajectory("input", "s", null, "done", "end_turn", false);
        assertNotNull(t.steps());
        assertTrue(t.steps().isEmpty());
    }

    @Test
    @DisplayName("Step arguments are immutable copy")
    void stepArgumentsImmutable() {
        Trajectory.Step step = new Trajectory.Step("tool", Map.of("k", "v"), ToolResult.success("ok"));
        assertThrows(UnsupportedOperationException.class,
                () -> step.arguments().put("injected", "bad"));
    }

    @Test
    @DisplayName("null arguments becomes empty map")
    void nullArgumentsBecomesEmpty() {
        Trajectory.Step step = new Trajectory.Step("tool", null, ToolResult.success("ok"));
        assertNotNull(step.arguments());
        assertTrue(step.arguments().isEmpty());
    }

    @Test
    @DisplayName("Step.isError returns true when result is error")
    void stepIsErrorTrue() {
        Trajectory.Step step = new Trajectory.Step("tool", Map.of(), ToolResult.error("boom"));
        assertTrue(step.isError());
    }

    @Test
    @DisplayName("Step.isError returns false when result is success")
    void stepIsErrorFalse() {
        Trajectory.Step step = new Trajectory.Step("tool", Map.of(), ToolResult.success("ok"));
        assertFalse(step.isError());
    }

    @Test
    @DisplayName("Step.isError returns true when result is null")
    void stepIsErrorNullResult() {
        Trajectory.Step step = new Trajectory.Step("tool", Map.of(), null);
        assertTrue(step.isError());
    }

    @Test
    @DisplayName("multiple steps preserve order")
    void multipleStepsPreserveOrder() {
        Trajectory.Step s1 = new Trajectory.Step("first", Map.of(), ToolResult.success("1"));
        Trajectory.Step s2 = new Trajectory.Step("second", Map.of(), ToolResult.success("2"));
        Trajectory.Step s3 = new Trajectory.Step("third", Map.of(), ToolResult.success("3"));

        Trajectory t = new Trajectory("input", "s", List.of(s1, s2, s3), "done", "end_turn", false);

        assertEquals("first", t.steps().get(0).toolName());
        assertEquals("second", t.steps().get(1).toolName());
        assertEquals("third", t.steps().get(2).toolName());
    }

    @Test
    @DisplayName("errored=true flags the trajectory as failed")
    void erroredFlag() {
        Trajectory t = new Trajectory("input", "s", List.of(), "done", "end_turn", true);
        assertTrue(t.errored());
    }

    @Test
    @DisplayName("Step preserves complex arguments")
    void stepComplexArguments() {
        Map<String, Object> args = Map.of(
                "text", "hello",
                "count", 5,
                "tags", List.of("a", "b")
        );
        Trajectory.Step step = new Trajectory.Step("tool", args, ToolResult.success("ok"));

        assertEquals("hello", step.arguments().get("text"));
        assertEquals(5, step.arguments().get("count"));
        assertEquals(List.of("a", "b"), step.arguments().get("tags"));
    }
}
