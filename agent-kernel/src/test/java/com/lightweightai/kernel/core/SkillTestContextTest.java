package com.lightweightai.kernel.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillTestContext - Skill 测试上下文构建器")
class SkillTestContextTest {

    @Test
    @DisplayName("最小化构建 - 只需 userInput")
    void minimalBuild() {
        SkillTestContext ctx = SkillTestContext.builder()
                .userInput("hello")
                .build();

        assertEquals("hello", ctx.getUserInput());
        assertNull(ctx.getTestCaseId());
        assertNull(ctx.getSystemPrompt());
        assertNull(ctx.getMemoryContext());
        assertTrue(ctx.getToolDefinitions().isEmpty());
        assertTrue(ctx.getMockResponses().isEmpty());
    }

    @Test
    @DisplayName("完整构建 - 所有字段")
    void fullBuild() {
        List<Map<String, Object>> tools = List.of(Map.of("name", "search"));
        Map<String, Object> mocks = Map.of("search", "result");

        SkillTestContext ctx = SkillTestContext.builder()
                .testCaseId("tc-001")
                .systemPrompt("You are a helper")
                .toolDefinitions(tools)
                .userInput("find something")
                .memoryContext("previous context")
                .mockResponses(mocks)
                .build();

        assertEquals("tc-001", ctx.getTestCaseId());
        assertEquals("You are a helper", ctx.getSystemPrompt());
        assertEquals(1, ctx.getToolDefinitions().size());
        assertEquals("find something", ctx.getUserInput());
        assertEquals("previous context", ctx.getMemoryContext());
        assertEquals("result", ctx.getMockResponses().get("search"));
    }

    @Test
    @DisplayName("userInput 为空时抛异常")
    void blankUserInputThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                SkillTestContext.builder().userInput("  ").build());
    }

    @Test
    @DisplayName("userInput 为 null 时抛异常")
    void nullUserInputThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                SkillTestContext.builder().build());
    }

    @Test
    @DisplayName("toolDefinitions 不可变")
    void toolDefinitionsImmutable() {
        SkillTestContext ctx = SkillTestContext.builder()
                .toolDefinitions(List.of(Map.of("name", "tool1")))
                .userInput("test")
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                ctx.getToolDefinitions().add(Map.of("name", "tool2")));
    }

    @Test
    @DisplayName("mockResponses 不可变")
    void mockResponsesImmutable() {
        SkillTestContext ctx = SkillTestContext.builder()
                .mockResponses(Map.of("k", "v"))
                .userInput("test")
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                ctx.getMockResponses().put("new", "val"));
    }
}
