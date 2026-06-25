package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.Skill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HeuristicSkillExtractor — 纯规则启发式 Skill 萃取")
class HeuristicSkillExtractorTest {

    private final HeuristicSkillExtractor extractor = new HeuristicSkillExtractor();

    private static Trajectory.Step successStep(String toolName, Map<String, Object> args, String content) {
        return new Trajectory.Step(toolName, args, ToolResult.success(content));
    }

    private static Trajectory validTrajectory(String userInput, List<Trajectory.Step> steps,
                                               String finalText) {
        return new Trajectory(userInput, "sess-1", steps, finalText, "end_turn", false);
    }

    @Nested
    @DisplayName("跳过条件 — 应返回 empty")
    class SkipConditions {

        @Test
        @DisplayName("errored 轨迹 → empty")
        void erroredTrajectoryReturnsEmpty() {
            Trajectory t = new Trajectory(
                    "do something", "s", List.of(successStep("tool1", Map.of(), "ok")),
                    "done", "end_turn", true);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空 steps → empty")
        void emptyStepsReturnsEmpty() {
            Trajectory t = new Trajectory(
                    "do something", "s", List.of(),
                    "done", "end_turn", false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("step 含 error result → empty")
        void stepWithErrorResultReturnsEmpty() {
            Trajectory.Step errorStep = new Trajectory.Step("bad-tool", Map.of(), ToolResult.error("boom"));
            Trajectory t = new Trajectory(
                    "do something", "s", List.of(errorStep),
                    "done", "end_turn", false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("stopReason 不是 end_turn → empty")
        void nonEndTurnStopReasonReturnsEmpty() {
            Trajectory t = new Trajectory(
                    "do something", "s", List.of(successStep("tool1", Map.of(), "ok")),
                    "done", "max_iterations", false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("step 含 null result (isError=true) → empty")
        void stepWithNullResultReturnsEmpty() {
            Trajectory.Step nullResultStep = new Trajectory.Step("tool1", Map.of(), null);
            assertTrue(nullResultStep.isError());

            Trajectory t = new Trajectory(
                    "do something", "s", List.of(nullResultStep),
                    "done", "end_turn", false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("成功萃取 — Skill 属性验证")
    class SuccessfulExtraction {

        @Test
        @DisplayName("有效轨迹 → present 且 name 符合 auto-<hex> 模式")
        void validTrajectoryProducesSkillWithAutoHexName() {
            String userInput = "帮我查天气";
            Trajectory t = validTrajectory(userInput,
                    List.of(successStep("weather", Map.of("city", "Tokyo"), "Sunny 25C")),
                    "Tokyo 现在晴天");

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isPresent());
            Skill skill = result.get();
            String expectedHex = Integer.toHexString(userInput.hashCode() & 0x7fffffff);
            assertEquals("auto-" + expectedHex, skill.getName());
        }

        @Test
        @DisplayName("description 含截断后的 userInput")
        void descriptionContainsTruncatedUserInput() {
            String shortInput = "短输入";
            Trajectory t = validTrajectory(shortInput,
                    List.of(successStep("tool", Map.of(), "ok")),
                    "done");

            Skill skill = extractor.extract(t).orElseThrow();

            assertTrue(skill.getDescription().contains(shortInput));
            assertTrue(skill.getDescription().startsWith("auto-extracted from successful run: "));
        }

        @Test
        @DisplayName("description 超过 60 字符时截断并加 ...")
        void descriptionTruncatesLongUserInput() {
            String longInput = "a".repeat(100);
            Trajectory t = validTrajectory(longInput,
                    List.of(successStep("tool", Map.of(), "ok")),
                    "done");

            Skill skill = extractor.extract(t).orElseThrow();

            assertFalse(skill.getDescription().contains(longInput));
            assertTrue(skill.getDescription().contains("a".repeat(60) + "..."));
        }

        @Test
        @DisplayName("triggers 含原始 userInput")
        void triggersContainOriginalUserInput() {
            String userInput = "帮我查 Tokyo 天气";
            Trajectory t = validTrajectory(userInput,
                    List.of(successStep("weather", Map.of("city", "Tokyo"), "Sunny")),
                    "done");

            Skill skill = extractor.extract(t).orElseThrow();

            assertEquals(1, skill.getTriggers().size());
            assertEquals(userInput, skill.getTriggers().get(0));
        }

        @Test
        @DisplayName("priority 固定为 200")
        void priorityIs200() {
            Trajectory t = validTrajectory("input",
                    List.of(successStep("tool", Map.of(), "ok")),
                    "done");

            Skill skill = extractor.extract(t).orElseThrow();

            assertEquals(200, skill.getPriority());
        }

        @Test
        @DisplayName("getSystemPrompt (lazy body) 含工具名和 userInput")
        void lazyBodyContainsToolNamesAndUserInput() {
            String userInput = "帮我部署服务";
            Trajectory t = validTrajectory(userInput,
                    List.of(
                            successStep("build", Map.of("target", "prod"), "build ok"),
                            successStep("deploy", Map.of("env", "staging"), "deployed")
                    ),
                    "部署完成");

            Skill skill = extractor.extract(t).orElseThrow();

            String body = skill.getSystemPrompt();
            assertTrue(body.contains("build"), "body 应含工具名 build");
            assertTrue(body.contains("deploy"), "body 应含工具名 deploy");
            assertTrue(body.contains(userInput), "body 应含原始 userInput");
            assertTrue(body.contains("target=prod"), "body 应含步骤参数");
        }

        @Test
        @DisplayName("renderBody 包含 finalText(非空时)")
        void renderBodyIncludesFinalAnswerWhenPresent() {
            String finalText = "这是最终回答";
            Trajectory t = validTrajectory("input",
                    List.of(successStep("tool", Map.of(), "ok")),
                    finalText);

            Skill skill = extractor.extract(t).orElseThrow();

            String body = skill.getSystemPrompt();
            assertTrue(body.contains("Final answer"), "body 应含 Final answer 标题");
            assertTrue(body.contains(finalText), "body 应含 finalText 内容");
        }

        @Test
        @DisplayName("renderBody 省略 finalText(空白时)")
        void renderBodyOmitsFinalAnswerWhenBlank() {
            Trajectory t = validTrajectory("input",
                    List.of(successStep("tool", Map.of(), "ok")),
                    "   ");

            Skill skill = extractor.extract(t).orElseThrow();

            String body = skill.getSystemPrompt();
            assertFalse(body.contains("Final answer"), "finalText 空白时不应含 Final answer");
        }

        @Test
        @DisplayName("renderBody 省略 finalText(null 时)")
        void renderBodyOmitsFinalAnswerWhenNull() {
            Trajectory t = validTrajectory("input",
                    List.of(successStep("tool", Map.of(), "ok")),
                    null);

            Skill skill = extractor.extract(t).orElseThrow();

            String body = skill.getSystemPrompt();
            assertFalse(body.contains("Final answer"), "finalText 为 null 时不应含 Final answer");
        }
    }
}
