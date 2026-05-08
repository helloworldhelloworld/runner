package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SkillRepository} — SQLite persistence layer for skills.
 *
 * Covers: CRUD operations, UPSERT semantics, status filtering, ID generation.
 */
@DisplayName("SkillRepository - SQLite 持久化")
class SkillRepositoryTest {

    @TempDir
    Path tempDir;

    private SkillRepository repo;

    @BeforeEach
    void setUp() {
        repo = new SkillRepository(tempDir.resolve("test-skills.db").toString());
    }

    private SkillDraft validDraft(String name) {
        SkillDraft draft = new SkillDraft();
        draft.setName(name);
        draft.setDescription("测试技能: " + name);
        draft.setSystemPrompt("# " + name + "\n\n处理请求");
        draft.setToolNames(List.of("ToolA"));
        draft.setTriggers(List.of("trigger1"));
        return draft;
    }

    // ==================== Save ====================

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("新建 skill — 生成 UUID 并返回")
        void save_newDraft_generatesId() {
            SkillDraft draft = validDraft("my-skill");
            assertNull(draft.getId());

            SkillDraft saved = repo.save(draft);

            assertNotNull(saved.getId());
            assertFalse(saved.getId().isBlank());
        }

        @Test
        @DisplayName("更新 skill — 保留 ID，字段更新")
        void save_existingDraft_updatesFields() {
            SkillDraft draft = validDraft("updatable");
            repo.save(draft);
            String id = draft.getId();

            draft.setDescription("updated description");
            draft.setPriority(5);
            repo.save(draft);

            Optional<SkillDraft> found = repo.findById(id);
            assertTrue(found.isPresent());
            assertEquals("updated description", found.get().getDescription());
            assertEquals(5, found.get().getPriority());
        }

        @Test
        @DisplayName("保留已有 ID，不重新生成")
        void save_withExistingId_keepsIt() {
            SkillDraft draft = validDraft("existing-id");
            draft.setId("my-custom-id");

            repo.save(draft);

            assertEquals("my-custom-id", draft.getId());
            assertTrue(repo.findById("my-custom-id").isPresent());
        }

        @Test
        @DisplayName("JSON 字段（toolNames, triggers, metadata）正确序列化/反序列化")
        void save_jsonFields_roundTrip() {
            SkillDraft draft = validDraft("json-test");
            draft.setToolNames(List.of("ToolA", "ToolB", "ToolC"));
            draft.setTriggers(List.of("导航", "天气查询"));
            draft.setMetadata(java.util.Map.of("author", "test", "version", "1"));

            repo.save(draft);
            SkillDraft loaded = repo.findById(draft.getId()).orElseThrow();

            assertEquals(List.of("ToolA", "ToolB", "ToolC"), loaded.getToolNames());
            assertEquals(List.of("导航", "天气查询"), loaded.getTriggers());
            assertEquals("test", loaded.getMetadata().get("author"));
        }
    }

    // ==================== Find ====================

    @Nested
    @DisplayName("查询")
    class FindTests {

        @Test
        @DisplayName("findAll — 空数据库返回空列表")
        void findAll_empty_returnsEmptyList() {
            assertTrue(repo.findAll().isEmpty());
        }

        @Test
        @DisplayName("findAll — 返回所有 skills")
        void findAll_multipleSkills_returnsAll() {
            repo.save(validDraft("skill-a"));
            repo.save(validDraft("skill-b"));
            repo.save(validDraft("skill-c"));

            assertEquals(3, repo.findAll().size());
        }

        @Test
        @DisplayName("findActive — 只返回 active 状态")
        void findActive_filtersCorrectly() {
            SkillDraft active1 = validDraft("active-1");
            active1.setStatus("active");
            active1.setPriority(2);
            repo.save(active1);

            SkillDraft active2 = validDraft("active-2");
            active2.setStatus("active");
            active2.setPriority(1);
            repo.save(active2);

            SkillDraft draft = validDraft("draft-1");
            draft.setStatus("draft");
            repo.save(draft);

            List<SkillDraft> actives = repo.findActive();
            assertEquals(2, actives.size());
            // Ordered by priority
            assertEquals("active-2", actives.get(0).getName());
            assertEquals("active-1", actives.get(1).getName());
        }

        @Test
        @DisplayName("findById — 存在时返回 Optional.of")
        void findById_exists_returnsPresent() {
            SkillDraft draft = validDraft("findable");
            repo.save(draft);

            Optional<SkillDraft> found = repo.findById(draft.getId());
            assertTrue(found.isPresent());
            assertEquals("findable", found.get().getName());
        }

        @Test
        @DisplayName("findById — 不存在时返回 Optional.empty")
        void findById_notExists_returnsEmpty() {
            assertTrue(repo.findById("nonexistent-id").isEmpty());
        }
    }

    // ==================== Delete ====================

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("删除存在的 skill — 返回 true")
        void delete_existing_returnsTrue() {
            SkillDraft draft = validDraft("deletable");
            repo.save(draft);

            assertTrue(repo.delete(draft.getId()));
            assertTrue(repo.findById(draft.getId()).isEmpty());
        }

        @Test
        @DisplayName("删除不存在的 skill — 返回 false")
        void delete_nonExistent_returnsFalse() {
            assertFalse(repo.delete("no-such-id"));
        }
    }

    // ==================== UpdateStatus ====================

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("更新状态成功")
        void updateStatus_existing_returnsTrue() {
            SkillDraft draft = validDraft("status-test");
            draft.setStatus("draft");
            repo.save(draft);

            assertTrue(repo.updateStatus(draft.getId(), "active"));

            SkillDraft updated = repo.findById(draft.getId()).orElseThrow();
            assertEquals("active", updated.getStatus());
        }

        @Test
        @DisplayName("不存在的 ID — 返回 false")
        void updateStatus_nonExistent_returnsFalse() {
            assertFalse(repo.updateStatus("no-such-id", "active"));
        }
    }

    // ==================== Schema Migration ====================

    @Test
    @DisplayName("version, category, scope 列在初始化时创建")
    void init_addsNewColumns() {
        SkillDraft draft = validDraft("migration-test");
        draft.setVersion("2.0.0");
        draft.setCategory("工具类");
        draft.setScope("全局");
        repo.save(draft);

        SkillDraft loaded = repo.findById(draft.getId()).orElseThrow();
        assertEquals("2.0.0", loaded.getVersion());
        assertEquals("工具类", loaded.getCategory());
        assertEquals("全局", loaded.getScope());
    }
}
