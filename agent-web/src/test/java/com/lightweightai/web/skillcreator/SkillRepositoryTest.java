package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillRepository 单元测试 — SQLite CRUD 和状态管理关键路径
 */
class SkillRepositoryTest {

    @TempDir
    Path tempDir;

    private SkillRepository repository;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test-skills.db").toString();
        repository = new SkillRepository(dbPath);
    }

    // ==================== Save & Find ====================

    @Test
    @DisplayName("save 后 findById 检索到完整数据，包括 toolNames、triggers、metadata")
    void shouldSaveAndFindById() {
        SkillDraft draft = createValidDraft("test-skill", "测试技能");
        draft.setToolNames(List.of("ToolA", "ToolB"));
        draft.setTriggers(List.of("导航", "路线"));
        draft.setPriority(5);

        SkillDraft saved = repository.save(draft);
        assertNotNull(saved.getId());

        Optional<SkillDraft> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());

        SkillDraft loaded = found.get();
        assertEquals("test-skill", loaded.getName());
        assertEquals("测试技能", loaded.getDescription());
        assertEquals("# test prompt", loaded.getSystemPrompt());
        assertEquals(List.of("ToolA", "ToolB"), loaded.getToolNames());
        assertEquals(List.of("导航", "路线"), loaded.getTriggers());
        assertEquals(5, loaded.getPriority());
        assertEquals("draft", loaded.getStatus());
    }

    @Test
    @DisplayName("save 自动生成 UUID id")
    void shouldAutoGenerateId() {
        SkillDraft draft = createValidDraft("skill-1", "desc");
        assertNull(draft.getId());

        SkillDraft saved = repository.save(draft);
        assertNotNull(saved.getId());
        assertFalse(saved.getId().isBlank());
    }

    @Test
    @DisplayName("save 相同 id 执行 upsert 更新")
    void shouldUpsertOnSameId() {
        SkillDraft draft1 = createValidDraft("skill-1", "desc v1");
        SkillDraft saved = repository.save(draft1);
        String id = saved.getId();

        SkillDraft draft2 = createValidDraft("skill-1-updated", "desc v2");
        draft2.setId(id);
        repository.save(draft2);

        Optional<SkillDraft> found = repository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("skill-1-updated", found.get().getName());
        assertEquals("desc v2", found.get().getDescription());
    }

    @Test
    @DisplayName("findById 不存在时返回 empty")
    void shouldReturnEmptyForMissingId() {
        assertTrue(repository.findById("nonexistent").isEmpty());
    }

    // ==================== findAll ====================

    @Test
    @DisplayName("findAll 返回所有技能")
    void shouldFindAll() {
        repository.save(createValidDraft("skill-1", "desc1"));
        repository.save(createValidDraft("skill-2", "desc2"));
        repository.save(createValidDraft("skill-3", "desc3"));

        List<SkillDraft> all = repository.findAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("空数据库 findAll 返回空列表")
    void shouldReturnEmptyListWhenNoSkills() {
        assertTrue(repository.findAll().isEmpty());
    }

    // ==================== findActive ====================

    @Test
    @DisplayName("findActive 仅返回 status=active 的技能，按 priority 排序")
    void shouldFindOnlyActiveSkills() {
        SkillDraft draft1 = createValidDraft("skill-draft", "d1");
        draft1.setStatus("draft");
        repository.save(draft1);

        SkillDraft active1 = createValidDraft("skill-active-low", "a1");
        active1.setStatus("active");
        active1.setPriority(20);
        repository.save(active1);

        SkillDraft active2 = createValidDraft("skill-active-high", "a2");
        active2.setStatus("active");
        active2.setPriority(5);
        repository.save(active2);

        List<SkillDraft> active = repository.findActive();
        assertEquals(2, active.size());
        // 按 priority 排序，5 < 20
        assertEquals("skill-active-high", active.get(0).getName());
        assertEquals("skill-active-low", active.get(1).getName());
    }

    // ==================== Delete ====================

    @Test
    @DisplayName("delete 删除指定 ID 的技能")
    void shouldDeleteById() {
        SkillDraft saved = repository.save(createValidDraft("to-delete", "d"));

        assertTrue(repository.delete(saved.getId()));
        assertTrue(repository.findById(saved.getId()).isEmpty());
    }

    @Test
    @DisplayName("delete 不存在的 ID 返回 false")
    void shouldReturnFalseForNonexistentDelete() {
        assertFalse(repository.delete("nonexistent"));
    }

    // ==================== updateStatus ====================

    @Test
    @DisplayName("updateStatus 更新技能状态")
    void shouldUpdateStatus() {
        SkillDraft saved = repository.save(createValidDraft("skill", "d"));
        assertEquals("draft", saved.getStatus());

        assertTrue(repository.updateStatus(saved.getId(), "active"));

        Optional<SkillDraft> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("active", found.get().getStatus());
    }

    @Test
    @DisplayName("updateStatus 不存在的 ID 返回 false")
    void shouldReturnFalseWhenUpdatingNonexistent() {
        assertFalse(repository.updateStatus("nonexistent", "active"));
    }

    // ==================== JSON 序列化 ====================

    @Test
    @DisplayName("toolNames 和 triggers 正确序列化/反序列化为 JSON")
    void shouldSerializeAndDeserializeJsonFields() {
        SkillDraft draft = createValidDraft("json-test", "d");
        draft.setToolNames(List.of("A", "B", "C"));
        draft.setTriggers(List.of("hello", "你好"));
        draft.setMetadata(java.util.Map.of("key", "value"));

        SkillDraft saved = repository.save(draft);
        SkillDraft loaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals(List.of("A", "B", "C"), loaded.getToolNames());
        assertEquals(List.of("hello", "你好"), loaded.getTriggers());
        assertEquals("value", loaded.getMetadata().get("key"));
    }

    @Test
    @DisplayName("空列表字段正确处理")
    void shouldHandleEmptyLists() {
        SkillDraft draft = createValidDraft("empty-lists", "d");
        draft.setToolNames(List.of());
        draft.setTriggers(List.of());

        SkillDraft saved = repository.save(draft);
        SkillDraft loaded = repository.findById(saved.getId()).orElseThrow();

        assertTrue(loaded.getToolNames().isEmpty());
        assertTrue(loaded.getTriggers().isEmpty());
    }

    // ==================== Helpers ====================

    private SkillDraft createValidDraft(String name, String description) {
        SkillDraft draft = new SkillDraft();
        draft.setName(name);
        draft.setDescription(description);
        draft.setSystemPrompt("# test prompt");
        return draft;
    }
}
