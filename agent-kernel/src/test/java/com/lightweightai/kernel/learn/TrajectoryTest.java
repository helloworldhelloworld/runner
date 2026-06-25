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

class TrajectoryTest {

    @Nested
    @DisplayName("Trajectory 记录")
    class TrajectoryRecordTests {

        @Test
        @DisplayName("null steps 变为空列表而非 null")
        void nullStepsBecomesEmptyList() {
            Trajectory trajectory = new Trajectory("input", "session-1", null, "final", "end_turn", false);

            assertNotNull(trajectory.steps());
            assertTrue(trajectory.steps().isEmpty());
        }

        @Test
        @DisplayName("steps 防御性拷贝——修改原始列表不影响 Trajectory")
        void stepsAreDefensivelyCopied() {
            Trajectory.Step step = new Trajectory.Step("tool1", Map.of("k", "v"), ToolResult.success("ok"));
            List<Trajectory.Step> original = new ArrayList<>();
            original.add(step);

            Trajectory trajectory = new Trajectory("input", "session-1", original, "final", "end_turn", false);
            original.add(new Trajectory.Step("tool2", Map.of(), ToolResult.success("ok2")));

            assertEquals(1, trajectory.steps().size());
            assertEquals("tool1", trajectory.steps().get(0).toolName());
        }

        @Test
        @DisplayName("记录访问器返回正确字段值")
        void fieldsAccessibleViaRecordAccessors() {
            Trajectory.Step step = new Trajectory.Step("tool1", Map.of(), ToolResult.success("ok"));
            Trajectory trajectory = new Trajectory("user-input", "sess-42", List.of(step), "done", "end_turn", true);

            assertEquals("user-input", trajectory.userInput());
            assertEquals("sess-42", trajectory.sessionId());
            assertEquals(1, trajectory.steps().size());
            assertEquals("done", trajectory.finalText());
            assertEquals("end_turn", trajectory.stopReason());
            assertTrue(trajectory.errored());
        }
    }

    @Nested
    @DisplayName("Step 记录")
    class StepRecordTests {

        @Test
        @DisplayName("null arguments 变为空 Map 而非 null")
        void nullArgumentsBecomesEmptyMap() {
            Trajectory.Step step = new Trajectory.Step("tool1", null, ToolResult.success("ok"));

            assertNotNull(step.arguments());
            assertTrue(step.arguments().isEmpty());
        }

        @Test
        @DisplayName("arguments 防御性拷贝——修改原始 Map 不影响 Step")
        void argumentsAreDefensivelyCopied() {
            Map<String, Object> original = new HashMap<>();
            original.put("key", "value");

            Trajectory.Step step = new Trajectory.Step("tool1", original, ToolResult.success("ok"));
            original.put("key2", "value2");

            assertEquals(1, step.arguments().size());
            assertTrue(step.arguments().containsKey("key"));
            assertFalse(step.arguments().containsKey("key2"));
        }

        @Test
        @DisplayName("result 为 null 时 isError 返回 true")
        void isErrorReturnsTrueWhenResultIsNull() {
            Trajectory.Step step = new Trajectory.Step("tool1", Map.of(), null);

            assertTrue(step.isError());
        }

        @Test
        @DisplayName("result.isError() 为 true 时 isError 返回 true")
        void isErrorReturnsTrueWhenResultIsError() {
            Trajectory.Step step = new Trajectory.Step("tool1", Map.of(), ToolResult.error("something went wrong"));

            assertTrue(step.isError());
        }

        @Test
        @DisplayName("result 为成功结果时 isError 返回 false")
        void isErrorReturnsFalseWhenResultIsSuccess() {
            Trajectory.Step step = new Trajectory.Step("tool1", Map.of(), ToolResult.success("ok"));

            assertFalse(step.isError());
        }

        @Test
        @DisplayName("记录访问器返回正确字段值")
        void fieldsAccessibleViaRecordAccessors() {
            ToolResult result = ToolResult.success("content");
            Map<String, Object> args = Map.of("param", "val");
            Trajectory.Step step = new Trajectory.Step("myTool", args, result);

            assertEquals("myTool", step.toolName());
            assertEquals(Map.of("param", "val"), step.arguments());
            assertSame(result, step.result());
        }
    }
}
