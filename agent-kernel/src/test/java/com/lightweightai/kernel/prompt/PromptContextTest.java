package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptContext - Prompt 上下文构建与可观测性")
class PromptContextTest {

    @Test
    @DisplayName("Builder 构建完整上下文")
    void buildFullContext() {
        PromptContext ctx = PromptContext.builder()
            .systemPrompt("You are a helpful assistant.")
            .userMessage("Hello")
            .historyMessages(List.of(Message.user("Hi"), Message.assistant("Hey")))
            .memoryContext("User prefers formal tone")
            .activeSkillNames(List.of("soul-comfort"))
            .addBuildLog("loaded skill: soul-comfort")
            .metadata("provider", "claude")
            .build();

        assertEquals("You are a helpful assistant.", ctx.getSystemPrompt());
        assertEquals("Hello", ctx.getUserMessage());
        assertEquals(2, ctx.getHistoryMessages().size());
        assertEquals("User prefers formal tone", ctx.getMemoryContext());
        assertEquals(List.of("soul-comfort"), ctx.getActiveSkillNames());
        assertEquals(1, ctx.getBuildLog().size());
        assertEquals("claude", ctx.getMetadata().get("provider"));
        assertNotNull(ctx.getBuildTime());
    }

    @Test
    @DisplayName("默认值为空")
    void defaultValuesAreEmpty() {
        PromptContext ctx = PromptContext.builder().build();

        assertEquals("", ctx.getSystemPrompt());
        assertEquals("", ctx.getUserMessage());
        assertTrue(ctx.getHistoryMessages().isEmpty());
        assertEquals("", ctx.getMemoryContext());
        assertTrue(ctx.getTools().isEmpty());
        assertTrue(ctx.getActiveSkillNames().isEmpty());
        assertTrue(ctx.getBuildLog().isEmpty());
        assertTrue(ctx.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("hasTools / hasMemoryContext 判断")
    void hasToolsAndMemoryContext() {
        PromptContext empty = PromptContext.builder().build();
        assertFalse(empty.hasTools());
        assertFalse(empty.hasMemoryContext());

        PromptContext withMemory = PromptContext.builder()
            .memoryContext("some context")
            .build();
        assertTrue(withMemory.hasMemoryContext());

        PromptContext emptyMemory = PromptContext.builder()
            .memoryContext("")
            .build();
        assertFalse(emptyMemory.hasMemoryContext());
    }

    @Test
    @DisplayName("getter 返回防御性副本")
    void gettersReturnDefensiveCopies() {
        PromptContext ctx = PromptContext.builder()
            .historyMessages(List.of(Message.user("test")))
            .activeSkillNames(List.of("skill1"))
            .addBuildLog("log1")
            .metadata("k", "v")
            .build();

        // Modifying returned lists should not affect the original
        List<Message> history = ctx.getHistoryMessages();
        history.clear();
        assertEquals(1, ctx.getHistoryMessages().size());

        List<String> skills = ctx.getActiveSkillNames();
        skills.clear();
        assertEquals(1, ctx.getActiveSkillNames().size());

        Map<String, Object> meta = ctx.getMetadata();
        meta.clear();
        assertEquals(1, ctx.getMetadata().size());
    }

    @Test
    @DisplayName("toDebugString 包含所有关键信息")
    void toDebugStringContainsAllSections() {
        PromptContext ctx = PromptContext.builder()
            .systemPrompt("Be kind.")
            .userMessage("How are you?")
            .historyMessages(List.of(Message.user("prev"), Message.assistant("response")))
            .memoryContext("User is stressed")
            .activeSkillNames(List.of("emotion-support"))
            .addBuildLog("skill detected via trigger")
            .build();

        String debug = ctx.toDebugString();

        assertTrue(debug.contains("Active Skills"));
        assertTrue(debug.contains("emotion-support"));
        assertTrue(debug.contains("System Prompt"));
        assertTrue(debug.contains("Be kind."));
        assertTrue(debug.contains("Memory Context"));
        assertTrue(debug.contains("User is stressed"));
        assertTrue(debug.contains("History"));
        assertTrue(debug.contains("2 messages"));
        assertTrue(debug.contains("User Message"));
        assertTrue(debug.contains("How are you?"));
        assertTrue(debug.contains("Build Log"));
        assertTrue(debug.contains("skill detected via trigger"));
    }

    @Test
    @DisplayName("toDebugString 长文本截断")
    void toDebugStringTruncatesLongText() {
        String longPrompt = "x".repeat(600);
        PromptContext ctx = PromptContext.builder()
            .systemPrompt(longPrompt)
            .userMessage("test")
            .build();

        String debug = ctx.toDebugString();
        assertTrue(debug.contains("(truncated)"));
    }

    @Test
    @DisplayName("toDebugString 无 memory 时不显示 Memory Context 段")
    void toDebugStringOmitsEmptyMemory() {
        PromptContext ctx = PromptContext.builder()
            .systemPrompt("prompt")
            .userMessage("test")
            .build();

        String debug = ctx.toDebugString();
        assertFalse(debug.contains("Memory Context"));
    }

    @Test
    @DisplayName("toDebugString 历史超过3条时显示省略提示")
    void toDebugStringShowsHistoryOverflow() {
        PromptContext ctx = PromptContext.builder()
            .systemPrompt("p")
            .userMessage("u")
            .historyMessages(List.of(
                Message.user("a"), Message.assistant("b"),
                Message.user("c"), Message.assistant("d"),
                Message.user("e")
            ))
            .build();

        String debug = ctx.toDebugString();
        assertTrue(debug.contains("5 messages"));
        assertTrue(debug.contains("and 2 more"));
    }

    @Test
    @DisplayName("toMap 包含所有关键字段")
    void toMapContainsAllFields() {
        PromptContext ctx = PromptContext.builder()
            .systemPrompt("prompt")
            .userMessage("hello")
            .historyMessages(List.of(Message.user("a")))
            .memoryContext("mem")
            .activeSkillNames(List.of("s1", "s2"))
            .build();

        Map<String, Object> map = ctx.toMap();

        assertEquals("prompt", map.get("systemPrompt"));
        assertEquals("hello", map.get("userMessage"));
        assertEquals(1, map.get("historyCount"));
        assertEquals(0, map.get("toolCount"));
        assertEquals(List.of("s1", "s2"), map.get("activeSkills"));
        assertEquals(true, map.get("hasMemory"));
        assertNotNull(map.get("buildTime"));
    }

    @Test
    @DisplayName("addActiveSkill 追加技能")
    void addActiveSkill() {
        PromptContext ctx = PromptContext.builder()
            .addActiveSkill("skill1")
            .addActiveSkill("skill2")
            .build();

        assertEquals(List.of("skill1", "skill2"), ctx.getActiveSkillNames());
    }

    @Test
    @DisplayName("addBuildLog 追加日志")
    void addBuildLog() {
        PromptContext ctx = PromptContext.builder()
            .addBuildLog("step1")
            .addBuildLog("step2")
            .build();

        assertEquals(2, ctx.getBuildLog().size());
        assertEquals("step1", ctx.getBuildLog().get(0));
    }
}
