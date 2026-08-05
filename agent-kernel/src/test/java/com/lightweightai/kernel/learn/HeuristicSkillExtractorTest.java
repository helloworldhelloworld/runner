package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.Skill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HeuristicSkillExtractor — zero-LLM heuristic extraction")
class HeuristicSkillExtractorTest {

    private final HeuristicSkillExtractor extractor = new HeuristicSkillExtractor();

    @Test
    @DisplayName("successful single-tool trajectory produces Skill with auto- prefix name")
    void successfulSingleTool() {
        Trajectory t = new Trajectory(
                "check weather in Tokyo",
                "sess-1",
                List.of(new Trajectory.Step("weather", Map.of("city", "Tokyo"),
                        ToolResult.success("Sunny 25C"))),
                "Tokyo is sunny, 25 degrees",
                "end_turn",
                false
        );

        Optional<Skill> result = extractor.extract(t);

        assertTrue(result.isPresent());
        Skill skill = result.get();
        assertTrue(skill.getName().startsWith("auto-"), "name should have auto- prefix: " + skill.getName());
        assertTrue(skill.getDescription().contains("check weather"), "description should contain user input");
        assertEquals(1, skill.getTriggers().size(), "trigger should be the user input");
        assertEquals("check weather in Tokyo", skill.getTriggers().get(0));
        assertEquals(200, skill.getPriority(), "auto-extracted skills should have low priority 200");
    }

    @Test
    @DisplayName("lazy body contains tool sequence and user input")
    void lazyBodyContent() {
        Trajectory t = new Trajectory(
                "calculate 2+3",
                "s1",
                List.of(new Trajectory.Step("math", Map.of("expr", "2+3"),
                        ToolResult.success("5"))),
                "The answer is 5",
                "end_turn",
                false
        );

        Skill skill = extractor.extract(t).orElseThrow();
        String body = skill.getSystemPrompt();

        assertTrue(body.contains("math"), "body should contain tool name");
        assertTrue(body.contains("2+3"), "body should contain tool args");
        assertTrue(body.contains("calculate 2+3"), "body should contain original request");
        assertTrue(body.contains("The answer is 5"), "body should contain final answer");
        assertTrue(body.contains("1 tool call"), "body should show singular form for 1 step");
    }

    @Test
    @DisplayName("multi-step trajectory renders all steps in body")
    void multiStepTrajectory() {
        Trajectory t = new Trajectory(
                "plan a trip",
                "s1",
                List.of(
                        new Trajectory.Step("search", Map.of("q", "flights"), ToolResult.success("found 3 flights")),
                        new Trajectory.Step("book", Map.of("id", "FL123"), ToolResult.success("booked")),
                        new Trajectory.Step("notify", Map.of("email", "test@test.com"), ToolResult.success("sent"))
                ),
                "Trip planned!",
                "end_turn",
                false
        );

        Skill skill = extractor.extract(t).orElseThrow();
        String body = skill.getSystemPrompt();

        assertTrue(body.contains("search"), "body should contain first tool");
        assertTrue(body.contains("book"), "body should contain second tool");
        assertTrue(body.contains("notify"), "body should contain third tool");
        assertTrue(body.contains("3 tool calls"), "body should show plural form for 3 steps");
        assertTrue(body.contains("prefer this exact tool sequence"), "body should contain reuse hint");
    }

    @Test
    @DisplayName("errored trajectory returns empty")
    void erroredTrajectory() {
        Trajectory t = new Trajectory(
                "do something",
                "s1",
                List.of(new Trajectory.Step("tool", Map.of(), ToolResult.success("ok"))),
                "done",
                "end_turn",
                true
        );

        assertTrue(extractor.extract(t).isEmpty());
    }

    @Test
    @DisplayName("empty steps returns empty")
    void emptySteps() {
        Trajectory t = new Trajectory(
                "hello",
                "s1",
                List.of(),
                "hi",
                "end_turn",
                false
        );

        assertTrue(extractor.extract(t).isEmpty());
    }

    @Test
    @DisplayName("any step with error result returns empty")
    void stepWithErrorResult() {
        Trajectory t = new Trajectory(
                "do something",
                "s1",
                List.of(
                        new Trajectory.Step("ok_tool", Map.of(), ToolResult.success("ok")),
                        new Trajectory.Step("bad_tool", Map.of(), ToolResult.error("boom"))
                ),
                "partial",
                "end_turn",
                false
        );

        assertTrue(extractor.extract(t).isEmpty());
    }

    @Test
    @DisplayName("non end_turn stopReason returns empty")
    void nonEndTurnStopReason() {
        Trajectory t = new Trajectory(
                "do something",
                "s1",
                List.of(new Trajectory.Step("tool", Map.of(), ToolResult.success("ok"))),
                "ran out",
                "max_iterations",
                false
        );

        assertTrue(extractor.extract(t).isEmpty());
    }

    @Test
    @DisplayName("null stopReason returns empty")
    void nullStopReason() {
        Trajectory t = new Trajectory(
                "do something",
                "s1",
                List.of(new Trajectory.Step("tool", Map.of(), ToolResult.success("ok"))),
                "done",
                null,
                false
        );

        assertTrue(extractor.extract(t).isEmpty());
    }

    @Test
    @DisplayName("same userInput produces same deterministic name")
    void deterministicName() {
        Trajectory t1 = new Trajectory("hello world", "s1",
                List.of(new Trajectory.Step("t", Map.of(), ToolResult.success("ok"))),
                "done", "end_turn", false);
        Trajectory t2 = new Trajectory("hello world", "s2",
                List.of(new Trajectory.Step("other", Map.of(), ToolResult.success("ok2"))),
                "done2", "end_turn", false);

        String name1 = extractor.extract(t1).orElseThrow().getName();
        String name2 = extractor.extract(t2).orElseThrow().getName();
        assertEquals(name1, name2, "same userInput should produce same skill name");
    }

    @Test
    @DisplayName("long userInput gets truncated in description")
    void longUserInputTruncated() {
        String longInput = "a".repeat(200);
        Trajectory t = new Trajectory(longInput, "s1",
                List.of(new Trajectory.Step("t", Map.of(), ToolResult.success("ok"))),
                "done", "end_turn", false);

        Skill skill = extractor.extract(t).orElseThrow();
        assertTrue(skill.getDescription().length() < longInput.length(),
                "description should truncate long user input");
        assertTrue(skill.getDescription().contains("..."), "truncated text should end with ...");
    }

    @Test
    @DisplayName("null finalText in body doesn't crash")
    void nullFinalText() {
        Trajectory t = new Trajectory("test", "s1",
                List.of(new Trajectory.Step("t", Map.of(), ToolResult.success("ok"))),
                null, "end_turn", false);

        Skill skill = extractor.extract(t).orElseThrow();
        String body = skill.getSystemPrompt();
        assertNotNull(body);
        assertFalse(body.contains("Final answer"), "null finalText should not produce final answer section");
    }

    @Test
    @DisplayName("empty args formatted correctly")
    void emptyArgs() {
        Trajectory t = new Trajectory("test", "s1",
                List.of(new Trajectory.Step("no_args_tool", Map.of(), ToolResult.success("done"))),
                "done", "end_turn", false);

        Skill skill = extractor.extract(t).orElseThrow();
        String body = skill.getSystemPrompt();
        assertTrue(body.contains("no_args_tool"), "body should contain tool name");
    }

    @Test
    @DisplayName("toString returns descriptive string")
    void toStringDescriptive() {
        String str = extractor.toString();
        assertTrue(str.contains("heuristicskillextractor"), "toString should contain class name (lowercase)");
        assertTrue(str.contains("end_turn"), "toString should mention end_turn filter");
    }
}
