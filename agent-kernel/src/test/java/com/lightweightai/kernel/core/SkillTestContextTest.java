package com.lightweightai.kernel.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillTestContext - Skill 测试上下文")
class SkillTestContextTest {

    @Nested
    @DisplayName("Builder 构建")
    class BuilderTests {

        @Test
        @DisplayName("完整构建并获取所有字段")
        void fullBuild() {
            List<Map<String, Object>> tools = List.of(Map.of("name", "search"));
            Map<String, Object> mocks = Map.of("search", "mock result");

            SkillTestContext ctx = SkillTestContext.builder()
                .testCaseId("tc-001")
                .systemPrompt("You are a helper")
                .toolDefinitions(tools)
                .userInput("hello")
                .memoryContext("previous chat")
                .mockResponses(mocks)
                .build();

            assertEquals("tc-001", ctx.getTestCaseId());
            assertEquals("You are a helper", ctx.getSystemPrompt());
            assertEquals(1, ctx.getToolDefinitions().size());
            assertEquals("hello", ctx.getUserInput());
            assertEquals("previous chat", ctx.getMemoryContext());
            assertEquals("mock result", ctx.getMockResponses().get("search"));
        }

        @Test
        @DisplayName("userInput 为空时抛出 IllegalArgumentException")
        void blankUserInputThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> SkillTestContext.builder()
                    .userInput("  ")
                    .build());
        }

        @Test
        @DisplayName("null userInput 抛出 IllegalArgumentException")
        void nullUserInputThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> SkillTestContext.builder()
                    .userInput(null)
                    .build());
        }

        @Test
        @DisplayName("null toolDefinitions 默认为空列表")
        void nullToolDefinitionsDefaultsToEmpty() {
            SkillTestContext ctx = SkillTestContext.builder()
                .userInput("test")
                .toolDefinitions(null)
                .build();

            assertNotNull(ctx.getToolDefinitions());
            assertTrue(ctx.getToolDefinitions().isEmpty());
        }

        @Test
        @DisplayName("null mockResponses 默认为空 Map")
        void nullMockResponsesDefaultsToEmpty() {
            SkillTestContext ctx = SkillTestContext.builder()
                .userInput("test")
                .mockResponses(null)
                .build();

            assertNotNull(ctx.getMockResponses());
            assertTrue(ctx.getMockResponses().isEmpty());
        }

        @Test
        @DisplayName("toolDefinitions 返回不可变列表")
        void toolDefinitionsUnmodifiable() {
            SkillTestContext ctx = SkillTestContext.builder()
                .userInput("test")
                .toolDefinitions(List.of(Map.of("name", "t")))
                .build();

            assertThrows(UnsupportedOperationException.class,
                () -> ctx.getToolDefinitions().add(Map.of()));
        }

        @Test
        @DisplayName("mockResponses 返回不可变 Map")
        void mockResponsesUnmodifiable() {
            SkillTestContext ctx = SkillTestContext.builder()
                .userInput("test")
                .mockResponses(Map.of("k", "v"))
                .build();

            assertThrows(UnsupportedOperationException.class,
                () -> ctx.getMockResponses().put("new", "val"));
        }
    }
}
