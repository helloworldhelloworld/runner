package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.Skill;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SkillCreatorService} — the core skill authoring orchestration.
 *
 * Covers: getDraft, save, saveAndActivate, listSkills, deleteSkill, loadSkill.
 * Note: chat() requires AgentLoop reactive streaming which is tested at integration level.
 */
@DisplayName("SkillCreatorService - Skill 创建服务")
class SkillCreatorServiceTest {

    @TempDir
    Path tempDir;

    private SkillRepository skillRepository;
    private ToolSchemaRepository toolSchemaRepository;
    private ToolRegistry toolRegistry;
    private PromptEngine promptEngine;
    private LLMProvider llmProvider;
    private SkillCreatorService service;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test.db").toString();
        skillRepository = new SkillRepository(dbPath);
        toolSchemaRepository = new ToolSchemaRepository(tempDir.resolve("tool-schemas.db").toString());
        toolRegistry = new ToolRegistry();
        promptEngine = mock(PromptEngine.class);
        llmProvider = mock(LLMProvider.class);

        service = new SkillCreatorService(
            llmProvider, toolRegistry, promptEngine,
            skillRepository, toolSchemaRepository
        );
    }

    private void putDraftInSession(String sessionId, SkillDraft draft) {
        // Use getDraft to confirm empty, then save to repo + loadSkill to inject into session
        if (draft.getId() == null) draft.setId("test-id-" + sessionId);
        skillRepository.save(draft);
        service.loadSkill(draft.getId(), sessionId);
    }

    private SkillDraft validDraft() {
        SkillDraft draft = new SkillDraft();
        draft.setName("test-skill");
        draft.setDescription("测试技能");
        draft.setSystemPrompt("# 测试\n\n处理请求");
        draft.setToolNames(List.of("ToolA"));
        draft.setTriggers(List.of("测试"));
        return draft;
    }

    // ==================== getDraft ====================

    @Nested
    @DisplayName("getDraft()")
    class GetDraftTests {

        @Test
        @DisplayName("新 session 返回空 draft")
        void getDraft_newSession_returnsEmptyDraft() {
            SkillDraft draft = service.getDraft("new-session");

            assertNotNull(draft);
            assertNull(draft.getName());
        }

        @Test
        @DisplayName("有 draft 的 session 返回该 draft")
        void getDraft_existingSession_returnsDraft() {
            SkillDraft expected = validDraft();
            putDraftInSession("sess-1", expected);

            SkillDraft result = service.getDraft("sess-1");
            assertEquals("test-skill", result.getName());
        }
    }

    // ==================== save ====================

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("无 draft 时抛出 IllegalStateException")
        void save_noDraft_throws() {
            assertThrows(IllegalStateException.class, () ->
                service.save("empty-session"));
        }

        @Test
        @DisplayName("draft 不完整时抛出 IllegalStateException")
        void save_incompleteDraft_throws() {
            SkillDraft incomplete = new SkillDraft();
            incomplete.setName("incomplete");
            // missing description and systemPrompt
            putDraftInSession("sess-bad", incomplete);

            assertThrows(IllegalStateException.class, () ->
                service.save("sess-bad"));
        }

        @Test
        @DisplayName("有效 draft 保存成功，状态为 draft")
        void save_validDraft_savesToRepo() {
            SkillDraft draft = validDraft();
            putDraftInSession("sess-ok", draft);

            SkillDraft saved = service.save("sess-ok");

            assertNotNull(saved.getId());
            // 验证确实持久化到了 repo
            assertTrue(skillRepository.findById(saved.getId()).isPresent());
        }

        @Test
        @DisplayName("save 不设置 status 时默认为 draft")
        void save_noStatus_defaultsDraft() {
            SkillDraft draft = validDraft();
            draft.setStatus(null);
            putDraftInSession("sess-status", draft);

            SkillDraft saved = service.save("sess-status");
            assertEquals("draft", saved.getStatus());
        }
    }

    // ==================== saveAndActivate ====================

    @Nested
    @DisplayName("saveAndActivate()")
    class SaveAndActivateTests {

        @Test
        @DisplayName("保存并激活 — 状态为 active，注册到 PromptEngine")
        void saveAndActivate_registersToPromptEngine() {
            SkillDraft draft = validDraft();
            putDraftInSession("sess-activate", draft);

            SkillDraft saved = service.saveAndActivate("sess-activate");

            assertEquals("active", saved.getStatus());
            verify(promptEngine).registerSkill(any(Skill.class));
        }

        @Test
        @DisplayName("saveAndActivate 关联运行时 Tool")
        void saveAndActivate_linksRuntimeTools() {
            Tool mockTool = mock(Tool.class);
            when(mockTool.getName()).thenReturn("ToolA");
            when(mockTool.getDescription()).thenReturn("A tool");
            when(mockTool.getSchema()).thenReturn(ToolSchema.empty());
            toolRegistry.register(mockTool);

            SkillDraft draft = validDraft();
            putDraftInSession("sess-tools", draft);

            service.saveAndActivate("sess-tools");

            verify(promptEngine).registerSkill(argThat(skill ->
                skill.getName().equals("test-skill")
            ));
        }

        @Test
        @DisplayName("无 draft 时抛异常")
        void saveAndActivate_noDraft_throws() {
            assertThrows(IllegalStateException.class, () ->
                service.saveAndActivate("no-draft-session"));
        }
    }

    // ==================== listSkills / deleteSkill ====================

    @Nested
    @DisplayName("listSkills / deleteSkill")
    class ListDeleteTests {

        @Test
        @DisplayName("listSkills 代理到 repository")
        void listSkills_delegatesToRepo() {
            skillRepository.save(validDraft());
            List<SkillDraft> skills = service.listSkills();

            assertFalse(skills.isEmpty());
            assertEquals("test-skill", skills.get(0).getName());
        }

        @Test
        @DisplayName("deleteSkill 代理到 repository")
        void deleteSkill_delegatesToRepo() {
            SkillDraft draft = validDraft();
            skillRepository.save(draft);
            String id = draft.getId();

            assertTrue(service.deleteSkill(id));
            assertFalse(service.deleteSkill(id));
        }
    }

    // ==================== loadSkill ====================

    @Nested
    @DisplayName("loadSkill()")
    class LoadSkillTests {

        @Test
        @DisplayName("加载存在的 skill 到 session")
        void loadSkill_existing_loadsIntoSession() {
            SkillDraft draft = validDraft();
            skillRepository.save(draft);

            SkillDraft loaded = service.loadSkill(draft.getId(), "sess-load");
            assertEquals("test-skill", loaded.getName());

            // Verify it's now in the session
            SkillDraft sessionDraft = service.getDraft("sess-load");
            assertEquals("test-skill", sessionDraft.getName());
        }

        @Test
        @DisplayName("加载不存在的 skill 抛出 IllegalArgumentException")
        void loadSkill_notFound_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                service.loadSkill("nonexistent", "sess"));
        }
    }

    // ==================== Active Skills Loading ====================

    @Test
    @DisplayName("构造函数加载 active skills 到 PromptEngine")
    void constructor_loadsActiveSkills() {
        SkillDraft active = validDraft();
        active.setName("preloaded-skill");
        active.setStatus("active");
        skillRepository.save(active);

        // Re-create service to trigger loadActiveSkills
        PromptEngine freshEngine = mock(PromptEngine.class);
        new SkillCreatorService(
            llmProvider, toolRegistry, freshEngine,
            skillRepository, toolSchemaRepository
        );

        verify(freshEngine).registerSkill(argThat(skill ->
            skill.getName().equals("preloaded-skill")
        ));
    }
}
