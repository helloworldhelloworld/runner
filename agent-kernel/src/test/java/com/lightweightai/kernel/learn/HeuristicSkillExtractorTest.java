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

@DisplayName("HeuristicSkillExtractor -- heuristic skill extraction from trajectories")
class HeuristicSkillExtractorTest {

    private HeuristicSkillExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new HeuristicSkillExtractor();
    }

    // ==================== Skip conditions ====================

    @Nested
    @DisplayName("Given a trajectory that should be skipped")
    class SkipConditions {

        @Test
        @DisplayName("returns empty when trajectory errored")
        void returnsEmptyWhenErrored() {
            Trajectory t = new Trajectory(
                    "do something", "sess-1",
                    List.of(successStep("tool1", "result1")),
                    "final text", "end_turn", true);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty(), "errored trajectory should produce no Skill");
        }

        @Test
        @DisplayName("returns empty when no steps")
        void returnsEmptyWhenNoSteps() {
            Trajectory t = new Trajectory(
                    "hi there", "sess-2",
                    List.of(),
                    "just chatting", "end_turn", false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty(), "trajectory without tool calls should produce no Skill");
        }

        @Test
        @DisplayName("returns empty when any step has error (ToolResult.isError)")
        void returnsEmptyWhenAnyStepHasError() {
            Trajectory t = new Trajectory(
                    "multi-step task", "sess-3",
                    List.of(
                            successStep("tool1", "ok"),
                            new Trajectory.Step("tool2", Map.of(), ToolResult.error("boom")),
                            successStep("tool3", "also ok")),
                    "partial result", "end_turn", false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty(), "trajectory with any errored step should produce no Skill");
        }

        @Test
        @DisplayName("returns empty when any step has null result")
        void returnsEmptyWhenAnyStepHasNullResult() {
            Trajectory t = new Trajectory(
                    "null result task", "sess-4",
                    List.of(
                            successStep("tool1", "ok"),
                            new Trajectory.Step("tool2", Map.of(), null)),
                    "done", "end_turn", false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty(), "step with null result counts as error -> skip");
        }

        @Test
        @DisplayName("returns empty when stopReason is not end_turn")
        void returnsEmptyWhenStopReasonNotEndTurn() {
            Trajectory t = new Trajectory(
                    "timed out task", "sess-5",
                    List.of(successStep("tool1", "partial")),
                    "incomplete", "max_iterations", false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty(), "stopReason != end_turn should produce no Skill");
        }

        @Test
        @DisplayName("returns empty when stopReason is tool_use")
        void returnsEmptyWhenStopReasonIsToolUse() {
            Trajectory t = new Trajectory(
                    "tool_use stop", "sess-6",
                    List.of(successStep("tool1", "ok")),
                    "more to do", "tool_use", false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty(), "stopReason=tool_use should produce no Skill");
        }

        @Test
        @DisplayName("returns empty when stopReason is null")
        void returnsEmptyWhenStopReasonIsNull() {
            Trajectory t = new Trajectory(
                    "null stop", "sess-7",
                    List.of(successStep("tool1", "ok")),
                    "done", null, false);

            Optional<Skill> result = extractor.extract(t);

            assertTrue(result.isEmpty(), "null stopReason should produce no Skill");
        }
    }

    // ==================== Successful extraction ====================

    @Nested
    @DisplayName("Given a successful trajectory (all conditions met)")
    class SuccessfulExtraction {

        @Test
        @DisplayName("extracts Skill with auto-<hash> name pattern")
        void extractsSkillWithAutoHashName() {
            Trajectory t = successfulTrajectory("Check the weather in Paris");

            Skill skill = extractor.extract(t).orElseThrow();

            assertTrue(skill.getName().startsWith("auto-"),
                    "name should start with auto-: " + skill.getName());
            // Verify the hash part is valid hex
            String hashPart = skill.getName().substring("auto-".length());
            assertDoesNotThrow(() -> Integer.parseUnsignedInt(hashPart, 16),
                    "hash part should be valid hex: " + hashPart);
        }

        @Test
        @DisplayName("name hash is deterministic for same userInput")
        void nameHashIsDeterministicForSameInput() {
            String input = "Deploy the application";
            Trajectory t1 = successfulTrajectory(input);
            Trajectory t2 = successfulTrajectory(input);

            String name1 = extractor.extract(t1).orElseThrow().getName();
            String name2 = extractor.extract(t2).orElseThrow().getName();

            assertEquals(name1, name2, "same userInput should produce same name");
        }

        @Test
        @DisplayName("different userInput produces different name")
        void differentInputProducesDifferentName() {
            Trajectory t1 = successfulTrajectory("task alpha");
            Trajectory t2 = successfulTrajectory("task beta");

            String name1 = extractor.extract(t1).orElseThrow().getName();
            String name2 = extractor.extract(t2).orElseThrow().getName();

            assertNotEquals(name1, name2, "different inputs should produce different names");
        }

        @Test
        @DisplayName("description contains userInput text")
        void descriptionContainsUserInput() {
            String userInput = "Run the test suite";
            Trajectory t = successfulTrajectory(userInput);

            Skill skill = extractor.extract(t).orElseThrow();

            assertTrue(skill.getDescription().contains(userInput),
                    "description should contain userInput: " + skill.getDescription());
            assertTrue(skill.getDescription().startsWith("auto-extracted from successful run:"),
                    "description should have standard prefix: " + skill.getDescription());
        }

        @Test
        @DisplayName("description truncates long userInput at 60 chars")
        void descriptionTruncatesLongInput() {
            String longInput = "A".repeat(100);
            Trajectory t = successfulTrajectory(longInput);

            Skill skill = extractor.extract(t).orElseThrow();

            // The truncated part should be 60 chars + "..."
            assertTrue(skill.getDescription().contains("..."),
                    "long input in description should be truncated with '...'");
            assertFalse(skill.getDescription().contains(longInput),
                    "full long input should not appear in description");
        }

        @Test
        @DisplayName("triggers list contains the original userInput")
        void triggersContainUserInput() {
            String userInput = "Format the code with prettier";
            Trajectory t = successfulTrajectory(userInput);

            Skill skill = extractor.extract(t).orElseThrow();

            List<String> triggers = skill.getTriggers();
            assertEquals(1, triggers.size(), "should have exactly one trigger");
            assertEquals(userInput, triggers.get(0), "trigger should be the exact userInput");
        }

        @Test
        @DisplayName("priority is 200 (lower than default)")
        void priorityIs200() {
            Trajectory t = successfulTrajectory("any task");

            Skill skill = extractor.extract(t).orElseThrow();

            assertEquals(200, skill.getPriority(),
                    "auto-extracted skill priority should be 200 (lower than manual skills)");
        }

        @Test
        @DisplayName("lazy body renders properly with tool sequence")
        void lazyBodyRendersWithToolSequence() {
            Trajectory t = new Trajectory(
                    "Build the project", "sess-body",
                    List.of(
                            new Trajectory.Step("compile", Map.of("target", "all"),
                                    ToolResult.success("compiled ok")),
                            new Trajectory.Step("test", Map.of("suite", "unit"),
                                    ToolResult.success("42 tests passed"))),
                    "Build succeeded!", "end_turn", false);

            Skill skill = extractor.extract(t).orElseThrow();
            String body = skill.getSystemPrompt();

            // Verify body structure
            assertTrue(body.contains("## Past successful approach"), "body should have header");
            assertTrue(body.contains("Build the project"), "body should contain original request");
            assertTrue(body.contains("2 tool calls"), "body should state step count");
            assertTrue(body.contains("`compile`"), "body should contain first tool name");
            assertTrue(body.contains("`test`"), "body should contain second tool name");
            assertTrue(body.contains("target=all"), "body should contain tool args");
            assertTrue(body.contains("suite=unit"), "body should contain second tool args");
            assertTrue(body.contains("compiled ok"), "body should contain tool results");
            assertTrue(body.contains("42 tests passed"), "body should contain second result");
            assertTrue(body.contains("Build succeeded!"), "body should contain final answer");
            assertTrue(body.contains("prefer this exact tool sequence"),
                    "body should include recommendation");
        }

        @Test
        @DisplayName("lazy body handles single step (no plural 's')")
        void lazyBodyHandlesSingleStep() {
            Trajectory t = new Trajectory(
                    "Quick lookup", "sess-single",
                    List.of(successStep("search", "found it")),
                    "Here it is", "end_turn", false);

            Skill skill = extractor.extract(t).orElseThrow();
            String body = skill.getSystemPrompt();

            assertTrue(body.contains("1 tool call)"), "single step should say 'tool call' (no s)");
            assertFalse(body.contains("1 tool calls"), "should not have plural for single step");
        }

        @Test
        @DisplayName("lazy body omits final answer when finalText is null")
        void lazyBodyOmitsFinalAnswerWhenNull() {
            Trajectory t = new Trajectory(
                    "Silent finish", "sess-null",
                    List.of(successStep("exec", "done")),
                    null, "end_turn", false);

            Skill skill = extractor.extract(t).orElseThrow();
            String body = skill.getSystemPrompt();

            assertFalse(body.contains("**Final answer**"),
                    "body should not contain final answer section when finalText is null");
        }

        @Test
        @DisplayName("lazy body omits final answer when finalText is blank")
        void lazyBodyOmitsFinalAnswerWhenBlank() {
            Trajectory t = new Trajectory(
                    "Blank finish", "sess-blank",
                    List.of(successStep("exec", "done")),
                    "   ", "end_turn", false);

            Skill skill = extractor.extract(t).orElseThrow();
            String body = skill.getSystemPrompt();

            assertFalse(body.contains("**Final answer**"),
                    "body should not contain final answer section when finalText is blank");
        }

        @Test
        @DisplayName("lazy body truncates long tool result content")
        void lazyBodyTruncatesLongContent() {
            String longResult = "X".repeat(200);
            Trajectory t = new Trajectory(
                    "Long output", "sess-long",
                    List.of(new Trajectory.Step("verbose_tool", Map.of(),
                            ToolResult.success(longResult))),
                    "done", "end_turn", false);

            Skill skill = extractor.extract(t).orElseThrow();
            String body = skill.getSystemPrompt();

            assertFalse(body.contains(longResult),
                    "full long content should not appear in body");
            assertTrue(body.contains("..."),
                    "truncated content should end with '...'");
        }

        @Test
        @DisplayName("lazy body handles step with empty arguments")
        void lazyBodyHandlesEmptyArgs() {
            Trajectory t = new Trajectory(
                    "No args task", "sess-noargs",
                    List.of(new Trajectory.Step("simple_tool", Map.of(),
                            ToolResult.success("result"))),
                    "done", "end_turn", false);

            Skill skill = extractor.extract(t).orElseThrow();
            String body = skill.getSystemPrompt();

            assertTrue(body.contains("`simple_tool`()"),
                    "empty args should render as empty parens: " + body);
        }

        @Test
        @DisplayName("lazy body is only computed on first getSystemPrompt call (caching)")
        void lazyBodyIsCachedAfterFirstCall() {
            Trajectory t = successfulTrajectory("cached body test");

            Skill skill = extractor.extract(t).orElseThrow();

            String body1 = skill.getSystemPrompt();
            String body2 = skill.getSystemPrompt();

            assertSame(body1, body2,
                    "second call should return cached (same) String instance");
        }
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString returns descriptive lowercase string")
    void toStringReturnsDescriptiveString() {
        String str = extractor.toString();

        assertTrue(str.contains("heuristicskillextractor"),
                "toString should contain class name (lowercase): " + str);
        assertTrue(str.contains("end_turn"), "toString should mention end_turn: " + str);
    }

    // ==================== Helpers ====================

    private Trajectory.Step successStep(String toolName, String resultContent) {
        return new Trajectory.Step(toolName, Map.of(), ToolResult.success(resultContent));
    }

    private Trajectory successfulTrajectory(String userInput) {
        return new Trajectory(
                userInput, "test-session",
                List.of(successStep("default_tool", "success")),
                "Final answer", "end_turn", false);
    }
}
