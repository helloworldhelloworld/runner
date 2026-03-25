package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptContext 单元测试 — 可观测的完整上下文
 *
 * 覆盖：Builder、getter 防御性拷贝、hasTools/hasMemory 判断、toDebugString、toMap
 */
@DisplayName("PromptContext - 可观测上下文")
class PromptContextTest {

    @Test
    @DisplayName("Builder 默认值均为空/空集合")
    void builderDefaults() {
        PromptContext ctx = PromptContext.builder().build();
        assertEquals("", ctx.getSystemPrompt());
        assertEquals("", ctx.getUserMessage());
        assertTrue(ctx.getHistoryMessages().isEmpty());
        assertEquals("", ctx.getMemoryContext());
        assertTrue(ctx.getTools().isEmpty());
        assertTrue(ctx.getActiveSkillNames().isEmpty());
        assertTrue(ctx.getBuildLog().isEmpty());
        assertNotNull(ctx.getBuildTime());
        assertTrue(ctx.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("hasTools 为 false 当无工具")
    void hasToolsFalseWhenEmpty() {
        PromptContext ctx = PromptContext.builder().build();
        assertFalse(ctx.hasTools());
    }

    @Test
    @DisplayName("hasTools 为 true 当有工具")
    void hasToolsTrueWhenPresent() {
        Skill.ToolDefinition tool = new Skill.ToolDefinition("calc", "calculate", Map.of());
        PromptContext ctx = PromptContext.builder()
                .addTool(tool)
                .build();
        assertTrue(ctx.hasTools());
    }

    @Test
    @DisplayName("hasMemoryContext 对空字符串返回 false")
    void hasMemoryContextEmpty() {
        PromptContext ctx = PromptContext.builder().memoryContext("").build();
        assertFalse(ctx.hasMemoryContext());
    }

    @Test
    @DisplayName("hasMemoryContext 对非空内容返回 true")
    void hasMemoryContextPresent() {
        PromptContext ctx = PromptContext.builder().memoryContext("User likes coffee").build();
        assertTrue(ctx.hasMemoryContext());
    }

    @Test
    @DisplayName("getter 返回防御性拷贝，修改不影响内部状态")
    void defensiveCopy() {
        PromptContext ctx = PromptContext.builder()
                .activeSkillNames(List.of("skill1"))
                .build();

        List<String> names = ctx.getActiveSkillNames();
        names.add("injected");

        assertEquals(1, ctx.getActiveSkillNames().size());
    }

    @Test
    @DisplayName("toMap 包含所有必要字段")
    void toMapContainsRequiredFields() {
        PromptContext ctx = PromptContext.builder()
                .systemPrompt("You are helpful")
                .userMessage("Hello")
                .activeSkillNames(List.of("weather"))
                .build();

        Map<String, Object> map = ctx.toMap();
        assertEquals("You are helpful", map.get("systemPrompt"));
        assertEquals("Hello", map.get("userMessage"));
        assertEquals(0, map.get("historyCount"));
        assertEquals(0, map.get("toolCount"));
        assertNotNull(map.get("buildTime"));
    }

    @Test
    @DisplayName("toDebugString 包含各区域标题")
    void toDebugStringContainsSections() {
        PromptContext ctx = PromptContext.builder()
                .systemPrompt("Base prompt")
                .userMessage("Hi there")
                .memoryContext("Remembers coffee preference")
                .activeSkillNames(List.of("weather"))
                .historyMessages(List.of(Message.user("prev msg")))
                .addBuildLog("Step 1 done")
                .build();

        String debug = ctx.toDebugString();
        assertTrue(debug.contains("Active Skills"));
        assertTrue(debug.contains("weather"));
        assertTrue(debug.contains("System Prompt"));
        assertTrue(debug.contains("Memory Context"));
        assertTrue(debug.contains("History"));
        assertTrue(debug.contains("User Message"));
        assertTrue(debug.contains("Build Log"));
    }

    @Test
    @DisplayName("toDebugString 截断过长的 systemPrompt")
    void toDebugStringTruncatesLongSystemPrompt() {
        String longPrompt = "x".repeat(600);
        PromptContext ctx = PromptContext.builder()
                .systemPrompt(longPrompt)
                .userMessage("test")
                .build();

        String debug = ctx.toDebugString();
        assertTrue(debug.contains("...(truncated)"));
    }

    @Test
    @DisplayName("Builder metadata 设置正确")
    void builderMetadata() {
        PromptContext ctx = PromptContext.builder()
                .metadata("key1", "value1")
                .metadata("key2", 42)
                .build();

        assertEquals("value1", ctx.getMetadata().get("key1"));
        assertEquals(42, ctx.getMetadata().get("key2"));
    }
}
