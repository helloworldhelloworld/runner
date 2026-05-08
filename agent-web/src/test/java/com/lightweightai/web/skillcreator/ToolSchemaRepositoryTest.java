package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ToolSchemaRepository} — SQLite persistence for tool schemas.
 *
 * Covers: CRUD, UPSERT by name, filtering by status/category, batch save.
 */
@DisplayName("ToolSchemaRepository - 工具 Schema 持久化")
class ToolSchemaRepositoryTest {

    @TempDir
    Path tempDir;

    private ToolSchemaRepository repo;

    @BeforeEach
    void setUp() {
        repo = new ToolSchemaRepository(tempDir.resolve("tool-schemas.db").toString());
    }

    private ToolSchemaEntry entry(String name, String category) {
        ToolSchemaEntry e = new ToolSchemaEntry();
        e.setName(name);
        e.setDescription("Description for " + name);
        e.setCategory(category);
        e.setInputSchemaJson("{\"type\":\"object\"}");
        return e;
    }

    // ==================== Save ====================

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("新建 — 生成 UUID 和时间戳")
        void save_new_generatesIdAndTimestamps() {
            ToolSchemaEntry e = entry("ToolA", "地理信息");
            assertNull(e.getId());

            ToolSchemaEntry saved = repo.save(e);

            assertNotNull(saved.getId());
            assertNotNull(saved.getCreatedAt());
            assertNotNull(saved.getUpdatedAt());
            assertEquals("enabled", saved.getStatus());
        }

        @Test
        @DisplayName("UPSERT 按 name — 更新描述")
        void save_upsertByName_updatesFields() {
            ToolSchemaEntry e1 = entry("ToolA", "Cat1");
            repo.save(e1);

            // Save again with same name but different description
            ToolSchemaEntry e2 = entry("ToolA", "Cat2");
            e2.setDescription("Updated description");
            repo.save(e2);

            List<ToolSchemaEntry> all = repo.findAll();
            assertEquals(1, all.size());
            assertEquals("Updated description", all.get(0).getDescription());
        }
    }

    // ==================== Find ====================

    @Nested
    @DisplayName("查询方法")
    class FindTests {

        @Test
        @DisplayName("findAll — 按 category, name 排序")
        void findAll_orderedByCategoryAndName() {
            repo.save(entry("Z-Tool", "B-Category"));
            repo.save(entry("A-Tool", "A-Category"));
            repo.save(entry("M-Tool", "A-Category"));

            List<ToolSchemaEntry> all = repo.findAll();
            assertEquals(3, all.size());
            assertEquals("A-Category", all.get(0).getCategory());
            assertEquals("A-Tool", all.get(0).getName());
        }

        @Test
        @DisplayName("findEnabled — 只返回 enabled 状态")
        void findEnabled_filtersStatus() {
            ToolSchemaEntry enabled = entry("Active", "Cat");
            repo.save(enabled);

            ToolSchemaEntry disabled = entry("Disabled", "Cat");
            disabled.setStatus("disabled");
            repo.save(disabled);

            List<ToolSchemaEntry> results = repo.findEnabled();
            assertEquals(1, results.size());
            assertEquals("Active", results.get(0).getName());
        }

        @Test
        @DisplayName("findByCategory — 按分类过滤")
        void findByCategory_filtersCorrectly() {
            repo.save(entry("ToolA", "地理信息"));
            repo.save(entry("ToolB", "导航"));
            repo.save(entry("ToolC", "地理信息"));

            List<ToolSchemaEntry> results = repo.findByCategory("地理信息");
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("findById — 存在时返回 Optional.of")
        void findById_exists() {
            ToolSchemaEntry saved = repo.save(entry("ToolA", "Cat"));

            Optional<ToolSchemaEntry> found = repo.findById(saved.getId());
            assertTrue(found.isPresent());
            assertEquals("ToolA", found.get().getName());
        }

        @Test
        @DisplayName("findById — 不存在返回 Optional.empty")
        void findById_notExists() {
            assertTrue(repo.findById("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("findByName — 按名称查找")
        void findByName_exists() {
            repo.save(entry("UniqueToolName", "Cat"));

            Optional<ToolSchemaEntry> found = repo.findByName("UniqueToolName");
            assertTrue(found.isPresent());
        }

        @Test
        @DisplayName("findByName — 不存在返回 empty")
        void findByName_notExists() {
            assertTrue(repo.findByName("ghost").isEmpty());
        }
    }

    // ==================== Delete ====================

    @Nested
    @DisplayName("删除")
    class DeleteTests {

        @Test
        @DisplayName("delete — 存在时返回 true")
        void delete_existing() {
            ToolSchemaEntry saved = repo.save(entry("ToolA", "Cat"));

            assertTrue(repo.delete(saved.getId()));
            assertTrue(repo.findById(saved.getId()).isEmpty());
        }

        @Test
        @DisplayName("delete — 不存在时返回 false")
        void delete_nonExistent() {
            assertFalse(repo.delete("ghost-id"));
        }

        @Test
        @DisplayName("deleteAll — 清空所有条目")
        void deleteAll_clearsEverything() {
            repo.save(entry("A", "C"));
            repo.save(entry("B", "C"));

            int deleted = repo.deleteAll();
            assertEquals(2, deleted);
            assertTrue(repo.findAll().isEmpty());
        }
    }

    // ==================== Batch Save ====================

    @Test
    @DisplayName("saveAll — 批量保存")
    void saveAll_savesMultiple() {
        int count = repo.saveAll(List.of(
            entry("Tool1", "Cat1"),
            entry("Tool2", "Cat1"),
            entry("Tool3", "Cat2")
        ));

        assertEquals(3, count);
        assertEquals(3, repo.findAll().size());
    }
}
