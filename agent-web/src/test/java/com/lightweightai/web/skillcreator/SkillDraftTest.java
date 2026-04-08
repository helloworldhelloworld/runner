package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillDraft 单元测试
 *
 * 覆盖关键路径：
 * - 默认字段值 (version / scope / priority / status)
 * - isValid(): 所有关键字段非空时有效
 * - setVersion(null) 回退默认
 * - setToolNames/setTriggers/setMetadata 对 null 的保护
 * - toMap 仅包含非空字段
 */
@DisplayName("SkillDraft - Skill 草稿模型")
class SkillDraftTest {

    @Test
    @DisplayName("默认构造器应设置默认字段")
    void shouldHaveDefaultFieldValues() {
        SkillDraft draft = new SkillDraft();

        assertEquals("1.0.0", draft.getVersion());
        assertEquals("对话内可用", draft.getScope());
        assertEquals(10, draft.getPriority());
        assertEquals("draft", draft.getStatus());
        assertNotNull(draft.getToolNames());
        assertTrue(draft.getToolNames().isEmpty());
        assertNotNull(draft.getTriggers());
        assertTrue(draft.getTriggers().isEmpty());
        assertNotNull(draft.getMetadata());
        assertTrue(draft.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("isValid: name/description/systemPrompt 都非空时应返回 true")
    void shouldBeValidWithAllRequiredFields() {
        SkillDraft draft = new SkillDraft();
        draft.setName("n");
        draft.setDescription("d");
        draft.setSystemPrompt("p");

        assertTrue(draft.isValid());
    }

    @Test
    @DisplayName("isValid: 任意关键字段为空或空白都应返回 false")
    void shouldBeInvalidWhenFieldsMissing() {
        SkillDraft empty = new SkillDraft();
        assertFalse(empty.isValid());

        SkillDraft noName = new SkillDraft();
        noName.setDescription("d");
        noName.setSystemPrompt("p");
        assertFalse(noName.isValid());

        SkillDraft blankDesc = new SkillDraft();
        blankDesc.setName("n");
        blankDesc.setDescription("   ");
        blankDesc.setSystemPrompt("p");
        assertFalse(blankDesc.isValid());

        SkillDraft noPrompt = new SkillDraft();
        noPrompt.setName("n");
        noPrompt.setDescription("d");
        assertFalse(noPrompt.isValid());
    }

    @Test
    @DisplayName("setVersion(null) 应回退到默认 1.0.0")
    void shouldFallbackVersionOnNull() {
        SkillDraft draft = new SkillDraft();
        draft.setVersion("2.0.0");
        draft.setVersion(null);
        assertEquals("1.0.0", draft.getVersion());
    }

    @Test
    @DisplayName("setToolNames/Triggers/Metadata(null) 应回退到空集合")
    void shouldProtectFromNullCollections() {
        SkillDraft draft = new SkillDraft();
        draft.setToolNames(null);
        draft.setTriggers(null);
        draft.setMetadata(null);

        assertNotNull(draft.getToolNames());
        assertTrue(draft.getToolNames().isEmpty());
        assertNotNull(draft.getTriggers());
        assertTrue(draft.getTriggers().isEmpty());
        assertNotNull(draft.getMetadata());
        assertTrue(draft.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("toMap 应包含非空字段并剥离空集合")
    void shouldBuildMapWithPopulatedFields() {
        SkillDraft draft = new SkillDraft();
        draft.setId("id-1");
        draft.setName("n");
        draft.setDescription("d");
        draft.setSystemPrompt("p");
        draft.setCategory("工具类");
        draft.setToolNames(List.of("t1"));
        draft.setTriggers(List.of("tr1"));
        draft.setMetadata(Map.of("k", "v"));
        draft.setPriority(42);
        draft.setStatus("active");

        Map<String, Object> map = draft.toMap();

        assertEquals("id-1", map.get("id"));
        assertEquals("n", map.get("name"));
        assertEquals("d", map.get("description"));
        assertEquals("p", map.get("systemPrompt"));
        assertEquals("工具类", map.get("category"));
        assertEquals(List.of("t1"), map.get("toolNames"));
        assertEquals(List.of("tr1"), map.get("triggers"));
        assertEquals(Map.of("k", "v"), map.get("metadata"));
        assertEquals(42, map.get("priority"));
        assertEquals("active", map.get("status"));
    }

    @Test
    @DisplayName("toMap 空集合字段应被剥离")
    void shouldOmitEmptyCollectionsInMap() {
        SkillDraft draft = new SkillDraft();
        draft.setName("n");

        Map<String, Object> map = draft.toMap();

        assertFalse(map.containsKey("toolNames"));
        assertFalse(map.containsKey("triggers"));
        assertFalse(map.containsKey("metadata"));
        assertEquals("n", map.get("name"));
    }

    @Test
    @DisplayName("ID 构造器应设置 id 字段")
    void shouldAcceptIdConstructor() {
        SkillDraft draft = new SkillDraft("my-id");
        assertEquals("my-id", draft.getId());
    }
}
