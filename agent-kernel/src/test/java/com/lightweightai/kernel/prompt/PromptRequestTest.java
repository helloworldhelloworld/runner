package com.lightweightai.kernel.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptRequest 单元测试 — Prompt 构建请求
 *
 * 覆盖：Builder 默认值、不可变性、字段赋值
 */
@DisplayName("PromptRequest - 构建请求")
class PromptRequestTest {

    @Test
    @DisplayName("Builder 默认值")
    void builderDefaults() {
        PromptRequest req = PromptRequest.builder().build();
        assertEquals("", req.getUserMessage());
        assertEquals("default", req.getSessionId());
        assertTrue(req.getActiveSkills().isEmpty());
        assertFalse(req.isAutoDetectSkills());
        assertEquals(20, req.getMaxHistoryMessages());
        assertEquals(5, req.getMaxMemoryResults());
        assertTrue(req.getContext().isEmpty());
    }

    @Test
    @DisplayName("所有字段可通过 Builder 设置")
    void builderFullConfig() {
        PromptRequest req = PromptRequest.builder()
                .userMessage("Hello")
                .sessionId("sess-1")
                .activeSkills(List.of("weather", "calc"))
                .autoDetectSkills(true)
                .maxHistoryMessages(10)
                .maxMemoryResults(3)
                .context("key1", "val1")
                .build();

        assertEquals("Hello", req.getUserMessage());
        assertEquals("sess-1", req.getSessionId());
        assertEquals(List.of("weather", "calc"), req.getActiveSkills());
        assertTrue(req.isAutoDetectSkills());
        assertEquals(10, req.getMaxHistoryMessages());
        assertEquals(3, req.getMaxMemoryResults());
        assertEquals("val1", req.getContext().get("key1"));
    }

    @Test
    @DisplayName("addActiveSkill 累加")
    void addActiveSkillAccumulates() {
        PromptRequest req = PromptRequest.builder()
                .addActiveSkill("a")
                .addActiveSkill("b")
                .build();
        assertEquals(2, req.getActiveSkills().size());
    }

    @Test
    @DisplayName("getter 返回防御性拷贝")
    void defensiveCopy() {
        PromptRequest req = PromptRequest.builder()
                .activeSkills(List.of("skill1"))
                .build();

        List<String> skills = req.getActiveSkills();
        skills.add("injected");
        assertEquals(1, req.getActiveSkills().size());
    }
}
