package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HeuristicSkillExtractor - rule-based skill extraction from trajectories")
class HeuristicSkillExtractorTest {

    private HeuristicSkillExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new HeuristicSkillExtractor();
    }

    @Nested
    @DisplayName("Skip conditions")
    class SkipConditions {

        @Test
        @DisplayName("skips errored trajectory")
        void skipsErroredTrajectory() {
            var trajectory = new Trajectory("input", "s1",
                List.of(successStep("echo", "ok")),
                "text", "end_turn", true);

            Optional<Skill> result = extractor.extract(trajectory);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("skips trajectory with no tool calls")
        void skipsNoToolCalls() {
            var trajectory = new Trajectory("input", "s1",
                List.of(), "text", "end_turn", false);

            Optional<Skill> result = extractor.extract(trajectory);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("skips trajectory with any error step")
        void skipsAnyErrorStep() {
            var steps = List.of(
                successStep("echo", "ok"),
                new Trajectory.Step("fail", Map.of(), ToolResult.error("boom"))
            );
            var trajectory = new Trajectory("input", "s1",
                steps, "text", "end_turn", false);

            Optional<Skill> result = extractor.extract(trajectory);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("skips trajectory with non-end_turn stop reason")
        void skipsNonEndTurn() {
            var trajectory = new Trajectory("input", "s1",
                List.of(successStep("echo", "ok")),
                "text", "max_iterations", false);

            Optional<Skill> result = extractor.extract(trajectory);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Successful extraction")
    class SuccessfulExtraction {

        @Test
        @DisplayName("extracts skill from clean single-step trajectory")
        void extractsSingleStep() {
            var trajectory = new Trajectory("查北京天气", "s1",
                List.of(successStep("weather", "sunny 25C")),
                "北京今天晴天", "end_turn", false);

            Optional<Skill> result = extractor.extract(trajectory);
            assertTrue(result.isPresent());

            Skill skill = result.get();
            assertTrue(skill.getName().startsWith("auto-"));
            assertTrue(skill.getDescription().contains("查北京天气"));
            assertEquals(200, skill.getPriority());
        }

        @Test
        @DisplayName("extracts skill from multi-step trajectory")
        void extractsMultiStep() {
            var steps = List.of(
                successStep("search", "found results"),
                successStep("summarize", "summary done")
            );
            var trajectory = new Trajectory("search and summarize topic", "s1",
                steps, "Here is the summary", "end_turn", false);

            Optional<Skill> result = extractor.extract(trajectory);
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("skill name is deterministic based on input hash")
        void skillNameIsDeterministic() {
            var trajectory = new Trajectory("same input", "s1",
                List.of(successStep("echo", "ok")),
                "text", "end_turn", false);

            String name1 = extractor.extract(trajectory).get().getName();
            String name2 = extractor.extract(trajectory).get().getName();
            assertEquals(name1, name2);
        }

        @Test
        @DisplayName("skill trigger matches user input")
        void skillTriggerMatchesInput() {
            var trajectory = new Trajectory("navigate to Beijing", "s1",
                List.of(successStep("navigate", "ok")),
                "done", "end_turn", false);

            Skill skill = extractor.extract(trajectory).get();
            assertTrue(skill.getTriggers().contains("navigate to Beijing"));
        }

        @Test
        @DisplayName("lazy body renders step sequence as markdown")
        void lazyBodyRendersMarkdown() {
            var steps = List.of(
                new Trajectory.Step("weather", Map.of("city", "Beijing"),
                    ToolResult.success("sunny 25C"))
            );
            var trajectory = new Trajectory("查天气", "s1",
                steps, "北京晴天", "end_turn", false);

            Skill skill = extractor.extract(trajectory).get();
            String body = skill.getSystemPrompt();

            assertTrue(body.contains("Past successful approach"));
            assertTrue(body.contains("`weather`"));
            assertTrue(body.contains("city=Beijing"));
            assertTrue(body.contains("Final answer"));
        }

        @Test
        @DisplayName("lazy body omits final answer when blank")
        void lazyBodyOmitsFinalAnswerWhenBlank() {
            var trajectory = new Trajectory("do something", "s1",
                List.of(successStep("action", "done")),
                "", "end_turn", false);

            Skill skill = extractor.extract(trajectory).get();
            String body = skill.getSystemPrompt();
            assertFalse(body.contains("Final answer"));
        }
    }

    @Test
    @DisplayName("toString returns descriptive string")
    void toStringDescriptive() {
        String str = extractor.toString();
        assertTrue(str.contains("heuristic"));
        assertTrue(str.contains("end_turn"));
    }

    private Trajectory.Step successStep(String toolName, String result) {
        return new Trajectory.Step(toolName, Map.of(), ToolResult.success(result));
    }
}
