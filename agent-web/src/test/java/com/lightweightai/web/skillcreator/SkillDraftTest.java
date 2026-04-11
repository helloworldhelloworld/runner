package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SkillDraft}.
 *
 * Covers key paths used by SkillCreatorService:
 * - Default state (version/scope/priority/status)
 * - isValid() pre-save guard
 * - toMap() serialization (used for draft event payload in stream)
 * - Null-safe setters (toolNames / triggers / metadata)
 * - JSON round-trip (used to parse <skill_draft> blocks from LLM)
 */
class SkillDraftTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== Defaults ====================

    @Test
    void defaultConstructor_hasDefaults() {
        SkillDraft draft = new SkillDraft();

        assertNull(draft.getId());
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
    void idConstructor_setsId() {
        SkillDraft draft = new SkillDraft("skill-123");
        assertEquals("skill-123", draft.getId());
    }

    // ==================== isValid ====================

    @Test
    void isValid_allRequiredSet_returnsTrue() {
        SkillDraft draft = new SkillDraft();
        draft.setName("navigation");
        draft.setDescription("导航技能");
        draft.setSystemPrompt("# 导航\n\n处理导航请求");

        assertTrue(draft.isValid());
    }

    @Test
    void isValid_missingName_returnsFalse() {
        SkillDraft draft = new SkillDraft();
        draft.setDescription("d");
        draft.setSystemPrompt("s");
        assertFalse(draft.isValid());
    }

    @Test
    void isValid_blankName_returnsFalse() {
        SkillDraft draft = new SkillDraft();
        draft.setName("   ");
        draft.setDescription("d");
        draft.setSystemPrompt("s");
        assertFalse(draft.isValid());
    }

    @Test
    void isValid_missingDescription_returnsFalse() {
        SkillDraft draft = new SkillDraft();
        draft.setName("n");
        draft.setSystemPrompt("s");
        assertFalse(draft.isValid());
    }

    @Test
    void isValid_missingSystemPrompt_returnsFalse() {
        SkillDraft draft = new SkillDraft();
        draft.setName("n");
        draft.setDescription("d");
        assertFalse(draft.isValid());
    }

    @Test
    void isValid_blankSystemPrompt_returnsFalse() {
        SkillDraft draft = new SkillDraft();
        draft.setName("n");
        draft.setDescription("d");
        draft.setSystemPrompt("");
        assertFalse(draft.isValid());
    }

    // ==================== Null-safe setters ====================

    @Test
    void setVersion_null_fallsBackToDefault() {
        SkillDraft draft = new SkillDraft();
        draft.setVersion("2.0.0");
        assertEquals("2.0.0", draft.getVersion());
        draft.setVersion(null);
        assertEquals("1.0.0", draft.getVersion());
    }

    @Test
    void setToolNames_null_usesEmptyList() {
        SkillDraft draft = new SkillDraft();
        draft.setToolNames(null);
        assertNotNull(draft.getToolNames());
        assertTrue(draft.getToolNames().isEmpty());
    }

    @Test
    void setTriggers_null_usesEmptyList() {
        SkillDraft draft = new SkillDraft();
        draft.setTriggers(null);
        assertNotNull(draft.getTriggers());
        assertTrue(draft.getTriggers().isEmpty());
    }

    @Test
    void setMetadata_null_usesEmptyMap() {
        SkillDraft draft = new SkillDraft();
        draft.setMetadata(null);
        assertNotNull(draft.getMetadata());
        assertTrue(draft.getMetadata().isEmpty());
    }

    // ==================== toMap ====================

    @Test
    void toMap_onlyIncludesSetFields() {
        SkillDraft draft = new SkillDraft();
        draft.setName("nav");
        draft.setDescription("导航");

        Map<String, Object> map = draft.toMap();

        assertEquals("nav", map.get("name"));
        assertEquals("导航", map.get("description"));
        assertEquals("1.0.0", map.get("version"));
        assertEquals(10, map.get("priority"));
        assertEquals("draft", map.get("status"));

        // id/category/systemPrompt not set -> absent
        assertFalse(map.containsKey("id"));
        assertFalse(map.containsKey("category"));
        assertFalse(map.containsKey("systemPrompt"));

        // Empty tools/triggers/metadata -> absent
        assertFalse(map.containsKey("toolNames"));
        assertFalse(map.containsKey("triggers"));
        assertFalse(map.containsKey("metadata"));
    }

    @Test
    void toMap_includesToolsAndTriggersWhenNonEmpty() {
        SkillDraft draft = new SkillDraft("id-1");
        draft.setName("n");
        draft.setDescription("d");
        draft.setCategory("工具类");
        draft.setSystemPrompt("# s");
        draft.setToolNames(List.of("ToolA", "ToolB"));
        draft.setTriggers(List.of("关键词"));
        draft.setPriority(5);
        draft.setStatus("active");
        draft.setMetadata(Map.of("author", "claude"));

        Map<String, Object> map = draft.toMap();

        assertEquals("id-1", map.get("id"));
        assertEquals("工具类", map.get("category"));
        assertEquals("# s", map.get("systemPrompt"));
        assertEquals(List.of("ToolA", "ToolB"), map.get("toolNames"));
        assertEquals(List.of("关键词"), map.get("triggers"));
        assertEquals(5, map.get("priority"));
        assertEquals("active", map.get("status"));
        assertEquals(Map.of("author", "claude"), map.get("metadata"));
    }

    // ==================== JSON round-trip ====================

    @Test
    void jsonRoundTrip_parsesSkillDraftBlock() throws Exception {
        // Matches the format LLM emits in <skill_draft>...</skill_draft>
        String json = """
            {
              "name": "celia-navigation",
              "description": "帮助用户导航",
              "version": "1.0.0",
              "category": "工具类",
              "systemPrompt": "# 技能说明\\n导航技能",
              "toolNames": ["NavigationTool"],
              "triggers": ["导航", "去哪"],
              "priority": 10
            }
            """;

        SkillDraft draft = MAPPER.readValue(json, SkillDraft.class);

        assertEquals("celia-navigation", draft.getName());
        assertEquals("帮助用户导航", draft.getDescription());
        assertEquals("工具类", draft.getCategory());
        assertTrue(draft.getSystemPrompt().contains("导航技能"));
        assertEquals(List.of("NavigationTool"), draft.getToolNames());
        assertEquals(List.of("导航", "去哪"), draft.getTriggers());
        assertTrue(draft.isValid());
    }

    @Test
    void jsonRoundTrip_unknownFieldsIgnored() throws Exception {
        String json = """
            {
              "name": "x",
              "description": "y",
              "systemPrompt": "z",
              "unknownField": "ignored"
            }
            """;

        SkillDraft draft = MAPPER.readValue(json, SkillDraft.class);
        assertEquals("x", draft.getName());
        assertTrue(draft.isValid());
    }
}
