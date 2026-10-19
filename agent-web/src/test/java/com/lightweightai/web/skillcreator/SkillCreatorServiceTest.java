package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.prompt.PromptEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SkillCreatorService 单元测试 — 覆盖 draft 管理、save/activate、CRUD 关键路径
 *
 * 使用真实 SQLite repository + mock LLM/PromptEngine
 */
class SkillCreatorServiceTest {

    @TempDir
    Path tempDir;

    private SkillCreatorService service;
    private SkillRepository skillRepository;
    private ToolSchemaRepository toolSchemaRepository;
    private PromptEngine mockPromptEngine;

    @BeforeEach
    void setUp() {
        LLMProvider mockLlm = mock(LLMProvider.class);
        ToolRegistry toolRegistry = new ToolRegistry();
        mockPromptEngine = mock(PromptEngine.class);
        skillRepository = new SkillRepository(tempDir.resolve("skills.db").toString());
        toolSchemaRepository = new ToolSchemaRepository(tempDir.resolve("tool-schemas.db").toString());

        service = new SkillCreatorService(
                mockLlm, toolRegistry, mockPromptEngine, skillRepository, toolSchemaRepository);
    }

    // ==================== getDraft ====================

    @Test
    @DisplayName("getDraft 对新 session 返回空 draft")
    void shouldReturnEmptyDraftForNewSession() {
        SkillDraft draft = service.getDraft("new-session");
        assertNotNull(draft);
    }

    // ==================== save ====================

    @Test
    @DisplayName("save 无 draft 时抛出 IllegalStateException")
    void shouldThrowWhenSavingWithoutDraft() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.save("no-draft-session"));
        assertTrue(ex.getMessage().contains("No draft found"));
    }

    // ==================== saveAndActivate ====================

    @Test
    @DisplayName("saveAndActivate 无 draft 时抛出 IllegalStateException")
    void shouldThrowWhenActivatingWithoutDraft() {
        assertThrows(IllegalStateException.class,
                () -> service.saveAndActivate("no-draft-session"));
    }

    // ==================== listSkills ====================

    @Test
    @DisplayName("listSkills 返回数据库中所有已保存的技能")
    void shouldListAllSavedSkills() {
        // 直接通过 repository 插入数据
        SkillDraft draft1 = new SkillDraft();
        draft1.setName("skill-1");
        draft1.setDescription("desc1");
        draft1.setSystemPrompt("prompt1");
        skillRepository.save(draft1);

        SkillDraft draft2 = new SkillDraft();
        draft2.setName("skill-2");
        draft2.setDescription("desc2");
        draft2.setSystemPrompt("prompt2");
        skillRepository.save(draft2);

        List<SkillDraft> skills = service.listSkills();
        assertEquals(2, skills.size());
    }

    @Test
    @DisplayName("listSkills 空数据库返回空列表")
    void shouldReturnEmptyListWhenNoSkills() {
        assertTrue(service.listSkills().isEmpty());
    }

    // ==================== deleteSkill ====================

    @Test
    @DisplayName("deleteSkill 删除已存在的技能返回 true")
    void shouldDeleteExistingSkill() {
        SkillDraft draft = new SkillDraft();
        draft.setName("to-delete");
        draft.setDescription("d");
        draft.setSystemPrompt("p");
        SkillDraft saved = skillRepository.save(draft);

        assertTrue(service.deleteSkill(saved.getId()));
        assertTrue(service.listSkills().isEmpty());
    }

    @Test
    @DisplayName("deleteSkill 删除不存在的技能返回 false")
    void shouldReturnFalseWhenDeletingNonexistent() {
        assertFalse(service.deleteSkill("nonexistent-id"));
    }

    // ==================== loadSkill ====================

    @Test
    @DisplayName("loadSkill 将数据库技能加载到 session draft")
    void shouldLoadSkillIntoDraft() {
        SkillDraft draft = new SkillDraft();
        draft.setName("loaded-skill");
        draft.setDescription("loaded desc");
        draft.setSystemPrompt("loaded prompt");
        draft.setToolNames(List.of("ToolA"));
        SkillDraft saved = skillRepository.save(draft);

        SkillDraft loaded = service.loadSkill(saved.getId(), "session-1");

        assertEquals("loaded-skill", loaded.getName());

        // getDraft 也应该返回加载的 draft
        SkillDraft sessionDraft = service.getDraft("session-1");
        assertEquals("loaded-skill", sessionDraft.getName());
    }

    @Test
    @DisplayName("loadSkill 不存在的 skillId 抛出 IllegalArgumentException")
    void shouldThrowWhenLoadingNonexistentSkill() {
        assertThrows(IllegalArgumentException.class,
                () -> service.loadSkill("nonexistent", "session-1"));
    }
}
