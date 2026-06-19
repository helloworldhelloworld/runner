package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Trajectory - agent execution trace record")
class TrajectoryTest {

    @Nested
    @DisplayName("Record construction")
    class Construction {

        @Test
        @DisplayName("stores all fields correctly")
        void storesAllFields() {
            var step = new Trajectory.Step("weather", Map.of("city", "Beijing"), ToolResult.success("sunny"));
            var trajectory = new Trajectory("查天气", "session-1", List.of(step),
                "北京今天晴天", "end_turn", false);

            assertEquals("查天气", trajectory.userInput());
            assertEquals("session-1", trajectory.sessionId());
            assertEquals(1, trajectory.steps().size());
            assertEquals("北京今天晴天", trajectory.finalText());
            assertEquals("end_turn", trajectory.stopReason());
            assertFalse(trajectory.errored());
        }

        @Test
        @DisplayName("null steps becomes empty immutable list")
        void nullStepsBecomesEmptyList() {
            var trajectory = new Trajectory("input", "s1", null, "text", "end_turn", false);
            assertNotNull(trajectory.steps());
            assertTrue(trajectory.steps().isEmpty());
        }

        @Test
        @DisplayName("steps list is defensively copied (immutable)")
        void stepsListIsImmutable() {
            var step = new Trajectory.Step("echo", Map.of("msg", "hi"), ToolResult.success("hi"));
            var trajectory = new Trajectory("input", "s1", List.of(step), "text", "end_turn", false);

            assertThrows(UnsupportedOperationException.class,
                () -> trajectory.steps().add(new Trajectory.Step("x", Map.of(), ToolResult.success("y"))));
        }
    }

    @Nested
    @DisplayName("Step record")
    class StepTests {

        @Test
        @DisplayName("stores tool name, arguments, and result")
        void storesFields() {
            var step = new Trajectory.Step("search",
                Map.of("query", "java"), ToolResult.success("found 10 results"));

            assertEquals("search", step.toolName());
            assertEquals("java", step.arguments().get("query"));
            assertEquals("found 10 results", step.result().getContent());
            assertFalse(step.isError());
        }

        @Test
        @DisplayName("null arguments becomes empty immutable map")
        void nullArgumentsBecomesEmptyMap() {
            var step = new Trajectory.Step("noop", null, ToolResult.success("ok"));
            assertNotNull(step.arguments());
            assertTrue(step.arguments().isEmpty());
        }

        @Test
        @DisplayName("arguments map is defensively copied (immutable)")
        void argumentsMapIsImmutable() {
            var step = new Trajectory.Step("echo", Map.of("x", "1"), ToolResult.success("ok"));
            assertThrows(UnsupportedOperationException.class,
                () -> step.arguments().put("new", "value"));
        }

        @Test
        @DisplayName("isError returns true for error result")
        void isErrorForErrorResult() {
            var step = new Trajectory.Step("fail", Map.of(), ToolResult.error("boom"));
            assertTrue(step.isError());
        }

        @Test
        @DisplayName("isError returns true for null result")
        void isErrorForNullResult() {
            var step = new Trajectory.Step("broken", Map.of(), null);
            assertTrue(step.isError());
        }

        @Test
        @DisplayName("isError returns false for success result")
        void isErrorForSuccessResult() {
            var step = new Trajectory.Step("ok", Map.of(), ToolResult.success("done"));
            assertFalse(step.isError());
        }
    }
}
