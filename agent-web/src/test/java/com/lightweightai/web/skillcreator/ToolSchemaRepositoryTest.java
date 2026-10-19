package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolSchemaRepository 单元测试 — SQLite CRUD 操作关键路径
 */
class ToolSchemaRepositoryTest {

    @TempDir
    Path tempDir;

    private ToolSchemaRepository repository;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test-tool-schemas.db").toString();
        repository = new ToolSchemaRepository(dbPath);
    }

    // ==================== Save & Find ====================

    @Test
    @DisplayName("save 后 findByName 能检索到完整数据")
    void shouldSaveAndFindByName() {
        ToolSchemaEntry entry = createEntry("GeoInfo.GetPosition", "获取位置", "地理信息");
        entry.setInputSchemaJson("{\"type\":\"object\"}");

        repository.save(entry);

        Optional<ToolSchemaEntry> found = repository.findByName("GeoInfo.GetPosition");
        assertTrue(found.isPresent());
        assertEquals("GeoInfo.GetPosition", found.get().getName());
        assertEquals("获取位置", found.get().getDescription());
        assertEquals("地理信息", found.get().getCategory());
        assertEquals("{\"type\":\"object\"}", found.get().getInputSchemaJson());
        assertEquals("enabled", found.get().getStatus());
        assertNotNull(found.get().getId());
        assertNotNull(found.get().getCreatedAt());
        assertNotNull(found.get().getUpdatedAt());
    }

    @Test
    @DisplayName("save 自动生成 id 如果为空")
    void shouldAutoGenerateId() {
        ToolSchemaEntry entry = createEntry("Tool1", "desc", "cat");
        assertNull(entry.getId());

        ToolSchemaEntry saved = repository.save(entry);
        assertNotNull(saved.getId());
        assertFalse(saved.getId().isBlank());
    }

    @Test
    @DisplayName("save 相同 name 执行 upsert 更新")
    void shouldUpsertOnDuplicateName() {
        ToolSchemaEntry entry1 = createEntry("Tool1", "old desc", "cat1");
        repository.save(entry1);

        ToolSchemaEntry entry2 = createEntry("Tool1", "new desc", "cat2");
        repository.save(entry2);

        List<ToolSchemaEntry> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("new desc", all.get(0).getDescription());
        assertEquals("cat2", all.get(0).getCategory());
    }

    @Test
    @DisplayName("findById 能通过 ID 检索")
    void shouldFindById() {
        ToolSchemaEntry entry = createEntry("Tool1", "desc", "cat");
        ToolSchemaEntry saved = repository.save(entry);

        Optional<ToolSchemaEntry> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Tool1", found.get().getName());
    }

    @Test
    @DisplayName("findById 不存在时返回 empty")
    void shouldReturnEmptyForMissingId() {
        Optional<ToolSchemaEntry> found = repository.findById("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("findByName 不存在时返回 empty")
    void shouldReturnEmptyForMissingName() {
        Optional<ToolSchemaEntry> found = repository.findByName("nonexistent");
        assertTrue(found.isEmpty());
    }

    // ==================== findAll & findEnabled ====================

    @Test
    @DisplayName("findAll 返回所有记录，按 category/name 排序")
    void shouldFindAll() {
        repository.save(createEntry("ToolB", "descB", "catB"));
        repository.save(createEntry("ToolA", "descA", "catA"));
        repository.save(createEntry("ToolC", "descC", "catA"));

        List<ToolSchemaEntry> all = repository.findAll();
        assertEquals(3, all.size());
        // 按 category, name 排序：catA.ToolA, catA.ToolC, catB.ToolB
        assertEquals("ToolA", all.get(0).getName());
        assertEquals("ToolC", all.get(1).getName());
        assertEquals("ToolB", all.get(2).getName());
    }

    @Test
    @DisplayName("findEnabled 仅返回 status=enabled 的记录")
    void shouldFindOnlyEnabled() {
        repository.save(createEntry("Tool1", "desc1", "cat"));

        ToolSchemaEntry disabled = createEntry("Tool2", "desc2", "cat");
        disabled.setStatus("disabled");
        repository.save(disabled);

        List<ToolSchemaEntry> enabled = repository.findEnabled();
        assertEquals(1, enabled.size());
        assertEquals("Tool1", enabled.get(0).getName());
    }

    // ==================== findByCategory ====================

    @Test
    @DisplayName("findByCategory 按分类过滤")
    void shouldFindByCategory() {
        repository.save(createEntry("Tool1", "desc1", "地理信息"));
        repository.save(createEntry("Tool2", "desc2", "天气"));
        repository.save(createEntry("Tool3", "desc3", "地理信息"));

        List<ToolSchemaEntry> geo = repository.findByCategory("地理信息");
        assertEquals(2, geo.size());
        assertTrue(geo.stream().allMatch(e -> "地理信息".equals(e.getCategory())));
    }

    // ==================== saveAll ====================

    @Test
    @DisplayName("saveAll 批量保存并返回成功数量")
    void shouldSaveAllBatch() {
        List<ToolSchemaEntry> entries = List.of(
                createEntry("Tool1", "desc1", "cat1"),
                createEntry("Tool2", "desc2", "cat2"),
                createEntry("Tool3", "desc3", "cat3")
        );

        int saved = repository.saveAll(entries);
        assertEquals(3, saved);
        assertEquals(3, repository.findAll().size());
    }

    // ==================== Delete ====================

    @Test
    @DisplayName("delete 删除指定 ID 的记录")
    void shouldDeleteById() {
        ToolSchemaEntry saved = repository.save(createEntry("Tool1", "desc", "cat"));

        boolean deleted = repository.delete(saved.getId());
        assertTrue(deleted);
        assertTrue(repository.findById(saved.getId()).isEmpty());
    }

    @Test
    @DisplayName("delete 不存在的 ID 返回 false")
    void shouldReturnFalseWhenDeletingNonExistent() {
        assertFalse(repository.delete("nonexistent"));
    }

    @Test
    @DisplayName("deleteAll 清空所有记录并返回删除数量")
    void shouldDeleteAll() {
        repository.save(createEntry("Tool1", "desc1", "cat"));
        repository.save(createEntry("Tool2", "desc2", "cat"));

        int count = repository.deleteAll();
        assertEquals(2, count);
        assertTrue(repository.findAll().isEmpty());
    }

    // ==================== Helpers ====================

    private ToolSchemaEntry createEntry(String name, String description, String category) {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setName(name);
        entry.setDescription(description);
        entry.setCategory(category);
        return entry;
    }
}
