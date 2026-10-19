package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Trajectory -- immutable agent run record with defensive copies")
class TrajectoryTest {

    // ==================== Trajectory record ====================

    @Nested
    @DisplayName("Given a Trajectory record")
    class TrajectoryRecord {

        @Test
        @DisplayName("stores all fields correctly")
        void storesAllFields() {
            List<Trajectory.Step> steps = List.of(
                    new Trajectory.Step("tool1", Map.of("k", "v"), ToolResult.success("ok")));

            Trajectory t = new Trajectory(
                    "user input", "session-1", steps, "final text", "end_turn", false);

            assertEquals("user input", t.userInput());
            assertEquals("session-1", t.sessionId());
            assertEquals(1, t.steps().size());
            assertEquals("final text", t.finalText());
            assertEquals("end_turn", t.stopReason());
            assertFalse(t.errored());
        }

        @Test
        @DisplayName("defensive copy of steps -- mutations to original list do not affect Trajectory")
        void defensiveCopyOfSteps() {
            List<Trajectory.Step> mutableSteps = new ArrayList<>();
            mutableSteps.add(new Trajectory.Step("tool1", Map.of(), ToolResult.success("r1")));

            Trajectory t = new Trajectory("input", "s", mutableSteps, "text", "end_turn", false);

            // Mutate original list after construction
            mutableSteps.add(new Trajectory.Step("tool2", Map.of(), ToolResult.success("r2")));

            assertEquals(1, t.steps().size(),
                    "Trajectory should not be affected by mutations to the original list");
        }

        @Test
        @DisplayName("steps list returned by accessor is unmodifiable")
        void stepsListIsUnmodifiable() {
            Trajectory t = new Trajectory(
                    "input", "s",
                    List.of(new Trajectory.Step("t", Map.of(), ToolResult.success("r"))),
                    "text", "end_turn", false);

            List<Trajectory.Step> steps = t.steps();

            assertThrows(UnsupportedOperationException.class,
                    () -> steps.add(new Trajectory.Step("x", Map.of(), ToolResult.success("y"))),
                    "returned steps list should be unmodifiable");
        }

        @Test
        @DisplayName("null steps becomes empty list")
        void nullStepsBecomesEmptyList() {
            Trajectory t = new Trajectory("input", "s", null, "text", "end_turn", false);

            assertNotNull(t.steps());
            assertTrue(t.steps().isEmpty(), "null steps should become empty list");
        }

        @Test
        @DisplayName("errored=true is preserved")
        void erroredFlagPreserved() {
            Trajectory t = new Trajectory("input", "s", List.of(), "text", "end_turn", true);

            assertTrue(t.errored());
        }
    }

    // ==================== Step record ====================

    @Nested
    @DisplayName("Given a Trajectory.Step record")
    class StepRecord {

        @Test
        @DisplayName("stores toolName, arguments, and result")
        void storesFields() {
            Map<String, Object> args = Map.of("key", "value", "count", 42);
            ToolResult result = ToolResult.success("output");

            Trajectory.Step step = new Trajectory.Step("my_tool", args, result);

            assertEquals("my_tool", step.toolName());
            assertEquals("value", step.arguments().get("key"));
            assertEquals(42, step.arguments().get("count"));
            assertSame(result, step.result());
        }

        @Test
        @DisplayName("defensive copy of arguments -- mutations to original map do not affect Step")
        void defensiveCopyOfArguments() {
            Map<String, Object> mutableArgs = new HashMap<>();
            mutableArgs.put("key1", "val1");

            Trajectory.Step step = new Trajectory.Step("tool", mutableArgs, ToolResult.success("r"));

            // Mutate original map after construction
            mutableArgs.put("key2", "val2");

            assertEquals(1, step.arguments().size(),
                    "Step should not be affected by mutations to the original map");
            assertFalse(step.arguments().containsKey("key2"),
                    "newly added key should not appear in Step's arguments");
        }

        @Test
        @DisplayName("arguments map returned by accessor is unmodifiable")
        void argumentsMapIsUnmodifiable() {
            Trajectory.Step step = new Trajectory.Step(
                    "tool", Map.of("k", "v"), ToolResult.success("r"));

            Map<String, Object> args = step.arguments();

            assertThrows(UnsupportedOperationException.class,
                    () -> args.put("new", "value"),
                    "returned arguments map should be unmodifiable");
        }

        @Test
        @DisplayName("null arguments becomes empty map")
        void nullArgumentsBecomesEmptyMap() {
            Trajectory.Step step = new Trajectory.Step("tool", null, ToolResult.success("r"));

            assertNotNull(step.arguments());
            assertTrue(step.arguments().isEmpty(), "null arguments should become empty map");
        }
    }

    // ==================== Step.isError ====================

    @Nested
    @DisplayName("Step.isError()")
    class StepIsError {

        @Test
        @DisplayName("returns true when result is null")
        void returnsTrueWhenResultIsNull() {
            Trajectory.Step step = new Trajectory.Step("tool", Map.of(), null);

            assertTrue(step.isError(), "null result should be treated as error");
        }

        @Test
        @DisplayName("returns true when result.isError() is true")
        void returnsTrueWhenResultIsError() {
            Trajectory.Step step = new Trajectory.Step(
                    "tool", Map.of(), ToolResult.error("something failed"));

            assertTrue(step.isError(), "error ToolResult should make step an error");
        }

        @Test
        @DisplayName("returns false when result is a successful ToolResult")
        void returnsFalseWhenResultIsSuccess() {
            Trajectory.Step step = new Trajectory.Step(
                    "tool", Map.of(), ToolResult.success("all good"));

            assertFalse(step.isError(), "successful ToolResult should not be an error");
        }
    }
}
