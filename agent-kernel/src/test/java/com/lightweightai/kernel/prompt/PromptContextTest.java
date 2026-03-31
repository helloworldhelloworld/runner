package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptContext - Prompt 上下文构建与可观测性")
class PromptContextTest {

    // ==================== Builder ====================

    @Test
    @DisplayName("builder 构建完整上下文")
    void shouldBuildCompleteContext() {
        PromptContext ctx = PromptContext.builder()
            .systemPrompt("You are a helpful assistant.")
            .userMessage("Hello")
            .memoryContext("User prefers Chinese")
            .addActiveSkill("weather")
            .addBuildLog("loaded 3 skills")
            .metadata("source", "test")
            .build();

        assertEquals("You are a helpful assistant.", ctx.getSystemPrompt());
        assertEquals("Hello", ctx.getUserMessage());
        assertEquals("User prefers Chinese", ctx.getMemoryContext());
        assertEquals(List.of("weather"), ctx.getActiveSkillNames());
        assertEquals(1, ctx.getBuildLog().size());
        assertEquals("test", ctx.getMetadata().get("source"));
        assertNotNull(ctx.getBuildTime());
    }

    @Test
    @DisplayName("builder 默认值")
    void shouldHaveEmptyDefaults() {
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
    @DisplayName("historyMessages 设置和获取")
    void shouldSetHistoryMessages() {
        Message msg1 = Message.user("Hi");
        Message msg2 = Message.assistant("Hello");
        PromptContext ctx = PromptContext.builder()
            .historyMessages(List.of(msg1, msg2))
            .build();

        assertEquals(2, ctx.getHistoryMessages().size());
        assertEquals("user", ctx.getHistoryMessages().get(0).getRole());
    }

    @Test
    @DisplayName("tools 设置和获取")
    void shouldSetTools() {
        Skill.ToolDefinition tool = new Skill.ToolDefinition("calc", "Calculator", Map.of());
        PromptContext ctx = PromptContext.builder()
            .addTool(tool)
            .build();

        assertEquals(1, ctx.getTools().size());
        assertEquals("calc", ctx.getTools().get(0).getName());
    }

    @Test
    @DisplayName("activeSkillNames 批量设置")
    void shouldSetActiveSkillNames() {
        PromptContext ctx = PromptContext.builder()
            .activeSkillNames(List.of("weather", "math", "memory"))
            .build();

        assertEquals(3, ctx.getActiveSkillNames().size());
    }

    // ==================== Defensive Copies ====================

    @Test
    @DisplayName("getHistoryMessages 返回防御性副本")
    void shouldReturnDefensiveCopyOfHistory() {
        Message msg = Message.user("Hi");
        PromptContext ctx = PromptContext.builder()
            .historyMessages(List.of(msg))
            .build();

        List<Message> history = ctx.getHistoryMessages();
        history.clear();
        assertEquals(1, ctx.getHistoryMessages().size());
    }

    @Test
    @DisplayName("getTools 返回防御性副本")
    void shouldReturnDefensiveCopyOfTools() {
        Skill.ToolDefinition tool = new Skill.ToolDefinition("t", "desc", Map.of());
        PromptContext ctx = PromptContext.builder()
            .tools(List.of(tool))
            .build();

        List<Skill.ToolDefinition> tools = ctx.getTools();
        tools.clear();
        assertEquals(1, ctx.getTools().size());
    }

    @Test
    @DisplayName("getMetadata 返回防御性副本")
    void shouldReturnDefensiveCopyOfMetadata() {
        PromptContext ctx = PromptContext.builder()
            .metadata("k", "v")
            .build();

        Map<String, Object> meta = ctx.getMetadata();
        meta.clear();
        assertEquals("v", ctx.getMetadata().get("k"));
    }

    // ==================== Query Methods ====================

    @Test
    @DisplayName("hasTools 有工具时返回 true")
    void shouldReturnTrueWhenHasTools() {
        Skill.ToolDefinition tool = new Skill.ToolDefinition("t", "desc", Map.of());
        PromptContext ctx = PromptContext.builder().addTool(tool).build();

        assertTrue(ctx.hasTools());
    }

    @Test
    @DisplayName("hasTools 无工具时返回 false")
    void shouldReturnFalseWhenNoTools() {
        PromptContext ctx = PromptContext.builder().build();

        assertFalse(ctx.hasTools());
    }

    @Test
    @DisplayName("hasMemoryContext 有记忆上下文时返回 true")
    void shouldReturnTrueWhenHasMemory() {
        PromptContext ctx = PromptContext.builder()
            .memoryContext("some context")
            .build();

        assertTrue(ctx.hasMemoryContext());
    }

    @Test
    @DisplayName("hasMemoryContext 空字符串返回 false")
    void shouldReturnFalseForEmptyMemory() {
        PromptContext ctx = PromptContext.builder()
            .memoryContext("")
            .build();

        assertFalse(ctx.hasMemoryContext());
    }

    @Test
    @DisplayName("hasMemoryContext null 返回 false")
    void shouldReturnFalseForNullMemory() {
        PromptContext ctx = PromptContext.builder()
            .memoryContext(null)
            .build();

        assertFalse(ctx.hasMemoryContext());
    }

    // ==================== Observability ====================

    @Test
    @DisplayName("toDebugString 包含关键段落")
    void shouldProduceDebugString() {
        PromptContext ctx = PromptContext.builder()
            .systemPrompt("System prompt here")
            .userMessage("User says hello")
            .addActiveSkill("weather")
            .addBuildLog("step 1: loaded config")
            .build();

        String debug = ctx.toDebugString();
        assertTrue(debug.contains("Active Skills"));
        assertTrue(debug.contains("weather"));
        assertTrue(debug.contains("System Prompt"));
        assertTrue(debug.contains("System prompt here"));
        assertTrue(debug.contains("User Message"));
        assertTrue(debug.contains("User says hello"));
        assertTrue(debug.contains("Build Log"));
        assertTrue(debug.contains("step 1: loaded config"));
    }

    @Test
    @DisplayName("toDebugString 长 systemPrompt 被截断")
    void shouldTruncateLongSystemPrompt() {
        String longPrompt = "A".repeat(1000);
        PromptContext ctx = PromptContext.builder()
            .systemPrompt(longPrompt)
            .build();

        String debug = ctx.toDebugString();
        assertTrue(debug.contains("truncated"));
    }

    @Test
    @DisplayName("toDebugString 有记忆上下文时包含 Memory Context")
    void shouldIncludeMemoryContextInDebug() {
        PromptContext ctx = PromptContext.builder()
            .memoryContext("User likes tea")
            .build();

        String debug = ctx.toDebugString();
        assertTrue(debug.contains("Memory Context"));
        assertTrue(debug.contains("User likes tea"));
    }

    @Test
    @DisplayName("toDebugString 历史消息显示前3条")
    void shouldShowFirst3HistoryMessages() {
        PromptContext ctx = PromptContext.builder()
            .historyMessages(List.of(
                Message.user("msg1"),
                Message.assistant("msg2"),
                Message.user("msg3"),
                Message.assistant("msg4")
            ))
            .build();

        String debug = ctx.toDebugString();
        assertTrue(debug.contains("4 messages"));
        assertTrue(debug.contains("and 1 more"));
    }

    @Test
    @DisplayName("toMap 包含关键字段")
    void shouldProduceMap() {
        Skill.ToolDefinition tool = new Skill.ToolDefinition("t", "d", Map.of());
        PromptContext ctx = PromptContext.builder()
            .systemPrompt("sys")
            .userMessage("user msg")
            .addTool(tool)
            .addActiveSkill("skill1")
            .build();

        Map<String, Object> map = ctx.toMap();
        assertEquals("sys", map.get("systemPrompt"));
        assertEquals("user msg", map.get("userMessage"));
        assertEquals(1, map.get("toolCount"));
        assertEquals(List.of("skill1"), map.get("activeSkills"));
    }
}
