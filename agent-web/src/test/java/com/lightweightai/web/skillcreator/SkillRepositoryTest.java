package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillRepository - SQLite 持久化层 CRUD")
class SkillRepositoryTest {

    private SkillRepository repository;
    private Path tempDbFile;

    @BeforeEach
    void setUp() throws IOException {
        tempDbFile = Files.createTempFile("skill-repo-test", ".db");
        repository = new SkillRepository(tempDbFile.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempDbFile);
    }

    private SkillDraft createDraft(String name) {
        SkillDraft draft = new SkillDraft();
        draft.setName(name);
        draft.setDescription("Description for " + name);
        draft.setSystemPrompt("# " + name + "\n\nSystem prompt content");
        draft.setVersion("1.0.0");
        draft.setCategory("工具类");
        draft.setToolNames(List.of("ToolA", "ToolB"));
        draft.setTriggers(List.of("trigger1", "trigger2"));
        draft.setPriority(10);
        return draft;
    }

    @Nested
    @DisplayName("Save")
    class SaveTests {

        @Test
        @DisplayName("保存新 draft 后生成 ID")
        void saveNewDraftGeneratesId() {
            SkillDraft draft = createDraft("test-skill");
            SkillDraft saved = repository.save(draft);

            assertNotNull(saved.getId());
            assertFalse(saved.getId().isBlank());
        }

        @Test
        @DisplayName("保存后可按 ID 查回")
        void savedDraftRetrievableById() {
            SkillDraft draft = createDraft("test-skill");
            SkillDraft saved = repository.save(draft);

            Optional<SkillDraft> found = repository.findById(saved.getId());
            assertTrue(found.isPresent());
            assertEquals("test-skill", found.get().getName());
            assertEquals("Description for test-skill", found.get().getDescription());
            assertEquals("1.0.0", found.get().getVersion());
            assertEquals("工具类", found.get().getCategory());
            assertEquals(2, found.get().getToolNames().size());
            assertEquals(2, found.get().getTriggers().size());
            assertEquals(10, found.get().getPriority());
        }

        @Test
        @DisplayName("更新已有 draft（相同 ID）")
        void updateExistingDraft() {
            SkillDraft draft = createDraft("v1");
            SkillDraft saved = repository.save(draft);
            String id = saved.getId();

            saved.setName("v2");
            saved.setDescription("Updated description");
            repository.save(saved);

            Optional<SkillDraft> found = repository.findById(id);
            assertTrue(found.isPresent());
            assertEquals("v2", found.get().getName());
            assertEquals("Updated description", found.get().getDescription());
        }
    }

    @Nested
    @DisplayName("FindAll / FindActive")
    class QueryTests {

        @Test
        @DisplayName("findAll 返回所有 drafts")
        void findAllReturnsSavedDrafts() {
            repository.save(createDraft("skill-a"));
            repository.save(createDraft("skill-b"));
            repository.save(createDraft("skill-c"));

            List<SkillDraft> all = repository.findAll();
            assertEquals(3, all.size());
        }

        @Test
        @DisplayName("findActive 只返回 active 状态的 drafts")
        void findActiveFiltersCorrectly() {
            SkillDraft active = createDraft("active-skill");
            active.setStatus("active");
            repository.save(active);

            SkillDraft draftStatus = createDraft("draft-skill");
            draftStatus.setStatus("draft");
            repository.save(draftStatus);

            List<SkillDraft> activeList = repository.findActive();
            assertEquals(1, activeList.size());
            assertEquals("active-skill", activeList.get(0).getName());
        }

        @Test
        @DisplayName("空库时 findAll 返回空列表")
        void findAllEmptyReturnsEmptyList() {
            assertTrue(repository.findAll().isEmpty());
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("删除已存在的 skill 返回 true")
        void deleteExistingReturnsTrue() {
            SkillDraft saved = repository.save(createDraft("to-delete"));
            assertTrue(repository.delete(saved.getId()));
        }

        @Test
        @DisplayName("删除后 findById 返回空")
        void deletedSkillNotFound() {
            SkillDraft saved = repository.save(createDraft("to-delete"));
            repository.delete(saved.getId());

            assertFalse(repository.findById(saved.getId()).isPresent());
        }

        @Test
        @DisplayName("删除不存在的 ID 返回 false")
        void deleteNonExistentReturnsFalse() {
            assertFalse(repository.delete("nonexistent-id"));
        }
    }

    @Nested
    @DisplayName("UpdateStatus")
    class UpdateStatusTests {

        @Test
        @DisplayName("更新状态从 draft 到 active")
        void updateStatusDraftToActive() {
            SkillDraft saved = repository.save(createDraft("my-skill"));
            assertTrue(repository.updateStatus(saved.getId(), "active"));

            Optional<SkillDraft> found = repository.findById(saved.getId());
            assertTrue(found.isPresent());
            assertEquals("active", found.get().getStatus());
        }

        @Test
        @DisplayName("更新不存在 ID 的状态返回 false")
        void updateStatusNonExistentReturnsFalse() {
            assertFalse(repository.updateStatus("nonexistent", "active"));
        }
    }

    @Nested
    @DisplayName("数据完整性")
    class DataIntegrityTests {

        @Test
        @DisplayName("toolNames 和 triggers 正确序列化和反序列化")
        void listsRoundTrip() {
            SkillDraft draft = createDraft("list-test");
            draft.setToolNames(List.of("WeatherAPI", "LocationResolver", "TimeZone"));
            draft.setTriggers(List.of("天气", "温度", "下雨吗"));
            SkillDraft saved = repository.save(draft);

            Optional<SkillDraft> found = repository.findById(saved.getId());
            assertTrue(found.isPresent());
            assertEquals(3, found.get().getToolNames().size());
            assertTrue(found.get().getToolNames().contains("WeatherAPI"));
            assertEquals(3, found.get().getTriggers().size());
            assertTrue(found.get().getTriggers().contains("天气"));
        }

        @Test
        @DisplayName("systemPrompt 保留完整 Markdown 内容")
        void systemPromptPreservesMarkdown() {
            SkillDraft draft = createDraft("md-test");
            String markdown = """
                    # 技能说明

                    ## 触发条件
                    | 触发 | 关键词 |
                    |------|--------|
                    | 触发 | 天气、温度 |

                    ## 执行流程
                    1. 解析城市
                    2. 调用 API
                    """;
            draft.setSystemPrompt(markdown);
            SkillDraft saved = repository.save(draft);

            Optional<SkillDraft> found = repository.findById(saved.getId());
            assertTrue(found.isPresent());
            assertEquals(markdown, found.get().getSystemPrompt());
        }
    }
}
