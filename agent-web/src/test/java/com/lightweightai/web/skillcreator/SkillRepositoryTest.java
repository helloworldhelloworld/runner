package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillRepository 单元测试
 *
 * 覆盖关键路径：
 * - 新建 skill 时自动生成 UUID
 * - save 持久化与 findById 读取
 * - 同 ID 再次 save 触发 upsert (ON CONFLICT DO UPDATE)
 * - JSON 字段 (toolNames / triggers / metadata) 的序列化往返
 * - findAll / findActive 状态过滤
 * - delete / updateStatus
 */
@DisplayName("SkillRepository - Skill 草稿 SQLite 持久化")
class SkillRepositoryTest {

    private SkillRepository repository;
    private Path tempDb;

    @BeforeEach
    void setUp() throws Exception {
        tempDb = Files.createTempFile("skills-", ".db");
        // SQLite 创建空文件会干扰,删掉让 repository 自己建表
        Files.deleteIfExists(tempDb);
        repository = new SkillRepository(tempDb.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempDb);
    }

    private static SkillDraft sampleDraft(String name) {
        SkillDraft draft = new SkillDraft();
        draft.setName(name);
        draft.setDescription("desc " + name);
        draft.setVersion("1.2.3");
        draft.setCategory("工具类");
        draft.setScope("对话内可用");
        draft.setSystemPrompt("you are " + name);
        draft.setToolNames(List.of("tool_a", "tool_b"));
        draft.setTriggers(List.of("trigger1", "trigger2"));
        draft.setPriority(5);
        draft.setMetadata(Map.of("author", "alice", "version_tag", "beta"));
        draft.setStatus("draft");
        return draft;
    }

    @Test
    @DisplayName("save 无 ID 应自动生成 UUID")
    void shouldGenerateIdIfMissing() {
        SkillDraft draft = sampleDraft("auto-id");

        SkillDraft saved = repository.save(draft);

        assertNotNull(saved.getId());
        assertDoesNotThrow(() -> UUID.fromString(saved.getId()));
    }

    @Test
    @DisplayName("save 与 findById 能完整往返所有字段 (含 JSON 列)")
    void shouldRoundtripAllFields() {
        SkillDraft draft = sampleDraft("roundtrip");

        SkillDraft saved = repository.save(draft);
        Optional<SkillDraft> loaded = repository.findById(saved.getId());

        assertTrue(loaded.isPresent());
        SkillDraft d = loaded.get();
        assertEquals("roundtrip", d.getName());
        assertEquals("desc roundtrip", d.getDescription());
        assertEquals("1.2.3", d.getVersion());
        assertEquals("工具类", d.getCategory());
        assertEquals("对话内可用", d.getScope());
        assertEquals("you are roundtrip", d.getSystemPrompt());
        assertEquals(List.of("tool_a", "tool_b"), d.getToolNames());
        assertEquals(List.of("trigger1", "trigger2"), d.getTriggers());
        assertEquals(5, d.getPriority());
        assertEquals("alice", d.getMetadata().get("author"));
        assertEquals("draft", d.getStatus());
    }

    @Test
    @DisplayName("相同 ID 二次 save 应触发 upsert 而非插入新记录")
    void shouldUpsertOnDuplicateId() {
        SkillDraft draft = sampleDraft("upsert-me");
        SkillDraft saved = repository.save(draft);
        String id = saved.getId();

        SkillDraft update = sampleDraft("upsert-me");
        update.setId(id);
        update.setDescription("updated-desc");
        update.setStatus("active");
        repository.save(update);

        List<SkillDraft> all = repository.findAll();
        assertEquals(1, all.size(), "duplicate id should not create new row");
        assertEquals("updated-desc", all.get(0).getDescription());
        assertEquals("active", all.get(0).getStatus());
    }

    @Test
    @DisplayName("findById 未知 ID 返回空")
    void shouldReturnEmptyForUnknownId() {
        assertTrue(repository.findById("no-such-id").isEmpty());
    }

    @Test
    @DisplayName("findActive 仅返回 status=active 的 skill, 并按 priority 升序")
    void shouldFilterActiveByStatus() {
        SkillDraft a = sampleDraft("a");
        a.setStatus("active");
        a.setPriority(10);
        SkillDraft b = sampleDraft("b");
        b.setStatus("active");
        b.setPriority(1);
        SkillDraft c = sampleDraft("c");
        c.setStatus("draft");

        repository.save(a);
        repository.save(b);
        repository.save(c);

        List<SkillDraft> active = repository.findActive();
        assertEquals(2, active.size());
        assertEquals("b", active.get(0).getName(), "lower priority comes first");
        assertEquals("a", active.get(1).getName());
    }

    @Test
    @DisplayName("delete 命中返回 true, 之后 findById 应为空")
    void shouldDeleteExisting() {
        SkillDraft saved = repository.save(sampleDraft("to-delete"));
        String id = saved.getId();

        assertTrue(repository.delete(id));
        assertTrue(repository.findById(id).isEmpty());
    }

    @Test
    @DisplayName("delete 未知 ID 返回 false")
    void shouldReturnFalseWhenDeletingUnknown() {
        assertFalse(repository.delete("no-such-id"));
    }

    @Test
    @DisplayName("updateStatus 应更新 status 字段并返回 true")
    void shouldUpdateStatus() {
        SkillDraft saved = repository.save(sampleDraft("status-test"));
        String id = saved.getId();

        assertTrue(repository.updateStatus(id, "disabled"));

        SkillDraft loaded = repository.findById(id).orElseThrow();
        assertEquals("disabled", loaded.getStatus());
    }

    @Test
    @DisplayName("updateStatus 未知 ID 应返回 false")
    void shouldReturnFalseWhenUpdatingUnknownStatus() {
        assertFalse(repository.updateStatus("no-such-id", "active"));
    }
}
