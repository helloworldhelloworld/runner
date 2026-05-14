package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillDraft - 草稿验证与序列化")
class SkillDraftValidationTest {

    @Test
    @DisplayName("isValid 需要 name, description, systemPrompt 均非空")
    void isValidRequiresAllThreeFields() {
        SkillDraft draft = new SkillDraft();
        assertFalse(draft.isValid());

        draft.setName("skill-1");
        assertFalse(draft.isValid());

        draft.setDescription("A skill");
        assertFalse(draft.isValid());

        draft.setSystemPrompt("You are a helper");
        assertTrue(draft.isValid());
    }

    @Test
    @DisplayName("isValid 空白字符串视为无效")
    void isValidBlankStringsInvalid() {
        SkillDraft draft = new SkillDraft();
        draft.setName("  ");
        draft.setDescription("desc");
        draft.setSystemPrompt("prompt");
        assertFalse(draft.isValid());
    }

    @Test
    @DisplayName("toMap 包含所有非空字段")
    void toMapIncludesNonNullFields() {
        SkillDraft draft = new SkillDraft("draft-1");
        draft.setName("test-skill");
        draft.setDescription("A test");
        draft.setSystemPrompt("system prompt");
        draft.setCategory("对话类");
        draft.setToolNames(List.of("weather", "search"));

        Map<String, Object> map = draft.toMap();
        assertEquals("draft-1", map.get("id"));
        assertEquals("test-skill", map.get("name"));
        assertEquals("A test", map.get("description"));
        assertEquals("system prompt", map.get("systemPrompt"));
        assertEquals("对话类", map.get("category"));
        assertEquals(List.of("weather", "search"), map.get("toolNames"));
        assertEquals("draft", map.get("status"));
        assertEquals(10, map.get("priority"));
    }

    @Test
    @DisplayName("toMap 不包含空 toolNames 和 triggers")
    void toMapOmitsEmptyCollections() {
        SkillDraft draft = new SkillDraft();
        draft.setName("skill");

        Map<String, Object> map = draft.toMap();
        assertFalse(map.containsKey("toolNames"));
        assertFalse(map.containsKey("triggers"));
    }

    @Test
    @DisplayName("默认值: version=1.0.0, scope=对话内可用, priority=10, status=draft")
    void defaultValues() {
        SkillDraft draft = new SkillDraft();
        assertEquals("1.0.0", draft.getVersion());
        assertEquals("对话内可用", draft.getScope());
        assertEquals(10, draft.getPriority());
        assertEquals("draft", draft.getStatus());
    }

    @Test
    @DisplayName("setVersion(null) 回退到 1.0.0")
    void setVersionNullFallback() {
        SkillDraft draft = new SkillDraft();
        draft.setVersion(null);
        assertEquals("1.0.0", draft.getVersion());
    }

    @Test
    @DisplayName("setToolNames(null) 回退到空列表")
    void setToolNamesNullFallback() {
        SkillDraft draft = new SkillDraft();
        draft.setToolNames(null);
        assertNotNull(draft.getToolNames());
        assertTrue(draft.getToolNames().isEmpty());
    }

    @Test
    @DisplayName("setTriggers(null) 回退到空列表")
    void setTriggersNullFallback() {
        SkillDraft draft = new SkillDraft();
        draft.setTriggers(null);
        assertNotNull(draft.getTriggers());
        assertTrue(draft.getTriggers().isEmpty());
    }

    @Test
    @DisplayName("setMetadata(null) 回退到空 Map")
    void setMetadataNullFallback() {
        SkillDraft draft = new SkillDraft();
        draft.setMetadata(null);
        assertNotNull(draft.getMetadata());
        assertTrue(draft.getMetadata().isEmpty());
    }
}
