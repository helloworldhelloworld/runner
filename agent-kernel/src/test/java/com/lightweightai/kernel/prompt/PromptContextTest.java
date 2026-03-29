package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptContext - observable prompt context")
class PromptContextTest {

    @Nested
    @DisplayName("Builder and getters")
    class BuilderTests {

        @Test
        void basicBuild_setsAllFields() {
            PromptContext ctx = PromptContext.builder()
                .systemPrompt("You are helpful")
                .userMessage("Hello")
                .memoryContext("previous context")
                .build();

            assertEquals("You are helpful", ctx.getSystemPrompt());
            assertEquals("Hello", ctx.getUserMessage());
            assertEquals("previous context", ctx.getMemoryContext());
            assertNotNull(ctx.getBuildTime());
        }

        @Test
        void defaultValues_areEmptyOrEmpty() {
            PromptContext ctx = PromptContext.builder().build();

            assertEquals("", ctx.getSystemPrompt());
            assertEquals("", ctx.getUserMessage());
            assertEquals("", ctx.getMemoryContext());
            assertTrue(ctx.getHistoryMessages().isEmpty());
            assertTrue(ctx.getTools().isEmpty());
            assertTrue(ctx.getActiveSkillNames().isEmpty());
            assertTrue(ctx.getBuildLog().isEmpty());
            assertTrue(ctx.getMetadata().isEmpty());
        }

        @Test
        void historyMessages_areCopied() {
            List<Message> history = List.of(
                Message.user("hi"),
                Message.assistant("hello")
            );
            PromptContext ctx = PromptContext.builder()
                .historyMessages(history)
                .build();

            assertEquals(2, ctx.getHistoryMessages().size());
            // returned list is a defensive copy
            ctx.getHistoryMessages().clear();
            assertEquals(2, ctx.getHistoryMessages().size());
        }

        @Test
        void addTool_accumulates() {
            Skill.ToolDefinition tool1 = new Skill.ToolDefinition("calc", "calculator", Map.of());
            Skill.ToolDefinition tool2 = new Skill.ToolDefinition("weather", "weather", Map.of());

            PromptContext ctx = PromptContext.builder()
                .addTool(tool1)
                .addTool(tool2)
                .build();

            assertEquals(2, ctx.getTools().size());
        }

        @Test
        void addActiveSkill_accumulates() {
            PromptContext ctx = PromptContext.builder()
                .addActiveSkill("weather")
                .addActiveSkill("calendar")
                .build();

            assertEquals(List.of("weather", "calendar"), ctx.getActiveSkillNames());
        }

        @Test
        void addBuildLog_accumulates() {
            PromptContext ctx = PromptContext.builder()
                .addBuildLog("loaded skills")
                .addBuildLog("injected memory")
                .build();

            assertEquals(2, ctx.getBuildLog().size());
        }

        @Test
        void metadata_canBeSet() {
            PromptContext ctx = PromptContext.builder()
                .metadata("version", "1.0")
                .metadata("debug", true)
                .build();

            assertEquals("1.0", ctx.getMetadata().get("version"));
            assertEquals(true, ctx.getMetadata().get("debug"));
        }
    }

    @Nested
    @DisplayName("Helper methods")
    class HelperMethods {

        @Test
        void hasTools_returnsTrueWhenToolsExist() {
            PromptContext ctx = PromptContext.builder()
                .addTool(new Skill.ToolDefinition("calc", "desc", Map.of()))
                .build();
            assertTrue(ctx.hasTools());
        }

        @Test
        void hasTools_returnsFalseWhenEmpty() {
            PromptContext ctx = PromptContext.builder().build();
            assertFalse(ctx.hasTools());
        }

        @Test
        void hasMemoryContext_trueWhenNonEmpty() {
            PromptContext ctx = PromptContext.builder()
                .memoryContext("some context")
                .build();
            assertTrue(ctx.hasMemoryContext());
        }

        @Test
        void hasMemoryContext_falseWhenEmpty() {
            PromptContext ctx = PromptContext.builder().build();
            assertFalse(ctx.hasMemoryContext());
        }

        @Test
        void hasMemoryContext_falseWhenNull() {
            PromptContext ctx = PromptContext.builder()
                .memoryContext(null)
                .build();
            assertFalse(ctx.hasMemoryContext());
        }
    }

    @Nested
    @DisplayName("Debug and serialization")
    class DebugTests {

        @Test
        void toDebugString_containsAllSections() {
            PromptContext ctx = PromptContext.builder()
                .systemPrompt("You are helpful")
                .userMessage("Hello")
                .addActiveSkill("weather")
                .addTool(new Skill.ToolDefinition("weather_tool", "get weather", Map.of()))
                .historyMessages(List.of(Message.user("hi"), Message.assistant("hello")))
                .memoryContext("some memory")
                .addBuildLog("step1")
                .build();

            String debug = ctx.toDebugString();
            assertTrue(debug.contains("Active Skills"));
            assertTrue(debug.contains("weather"));
            assertTrue(debug.contains("Tools"));
            assertTrue(debug.contains("weather_tool"));
            assertTrue(debug.contains("System Prompt"));
            assertTrue(debug.contains("Memory Context"));
            assertTrue(debug.contains("History"));
            assertTrue(debug.contains("User Message"));
            assertTrue(debug.contains("Build Log"));
        }

        @Test
        void toDebugString_truncatesLongSystemPrompt() {
            String longPrompt = "x".repeat(600);
            PromptContext ctx = PromptContext.builder()
                .systemPrompt(longPrompt)
                .userMessage("msg")
                .build();

            String debug = ctx.toDebugString();
            assertTrue(debug.contains("truncated"));
        }

        @Test
        void toDebugString_truncatesLongMemoryContext() {
            String longMem = "m".repeat(400);
            PromptContext ctx = PromptContext.builder()
                .memoryContext(longMem)
                .userMessage("msg")
                .build();

            String debug = ctx.toDebugString();
            assertTrue(debug.contains("truncated"));
        }

        @Test
        void toDebugString_showsMoreIndicatorForLargeHistory() {
            List<Message> history = List.of(
                Message.user("m1"), Message.assistant("r1"),
                Message.user("m2"), Message.assistant("r2")
            );
            PromptContext ctx = PromptContext.builder()
                .systemPrompt("sys")
                .userMessage("msg")
                .historyMessages(history)
                .build();

            String debug = ctx.toDebugString();
            assertTrue(debug.contains("and 1 more"));
        }

        @Test
        void toMap_containsRequiredKeys() {
            PromptContext ctx = PromptContext.builder()
                .systemPrompt("sys")
                .userMessage("msg")
                .build();

            Map<String, Object> map = ctx.toMap();
            assertTrue(map.containsKey("systemPrompt"));
            assertTrue(map.containsKey("userMessage"));
            assertTrue(map.containsKey("historyCount"));
            assertTrue(map.containsKey("toolCount"));
            assertTrue(map.containsKey("activeSkills"));
            assertTrue(map.containsKey("hasMemory"));
            assertTrue(map.containsKey("buildTime"));
        }

        @Test
        void toMap_reflectsCorrectValues() {
            PromptContext ctx = PromptContext.builder()
                .systemPrompt("sys")
                .userMessage("msg")
                .historyMessages(List.of(Message.user("hi")))
                .addTool(new Skill.ToolDefinition("t", "d", Map.of()))
                .addActiveSkill("skill1")
                .memoryContext("mem")
                .build();

            Map<String, Object> map = ctx.toMap();
            assertEquals("sys", map.get("systemPrompt"));
            assertEquals("msg", map.get("userMessage"));
            assertEquals(1, map.get("historyCount"));
            assertEquals(1, map.get("toolCount"));
            assertEquals(List.of("skill1"), map.get("activeSkills"));
            assertEquals(true, map.get("hasMemory"));
        }
    }
}
