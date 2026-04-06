package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.prompt.PromptEngine;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillPublishService - 版本发布管理")
class SkillPublishServiceTest {

    private SkillPublishService publishService;
    private SkillRepository skillRepository;
    private SkillVersionRepository versionRepository;
    private TestRunRepository testRunRepository;
    private PromptEngine promptEngine;
    private Path tempDb;

    @BeforeEach
    void setUp() throws Exception {
        tempDb = Files.createTempFile("publish-", ".db");
        String dbPath = tempDb.toString();
        skillRepository = new SkillRepository(dbPath);
        versionRepository = new SkillVersionRepository(dbPath);
        testRunRepository = new TestRunRepository(dbPath);
        promptEngine = PromptEngine.builder().build();
        ToolRegistry toolRegistry = new ToolRegistry();

        publishService = new SkillPublishService(
            skillRepository, versionRepository, testRunRepository, promptEngine, toolRegistry, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempDb);
    }

    @Test
    @DisplayName("stage 创建不可变版本快照")
    void stage_createsImmutableSnapshot() {
        String skillId = setupSkillWithPassingTestRun();

        SkillVersion version = publishService.stage(skillId, 0.5);

        assertNotNull(version.getId());
        assertEquals(skillId, version.getSkillId());
        assertEquals("1.0.0", version.getVersionTag());
        assertEquals("staged", version.getStatus());
        assertNotNull(version.getSnapshotJson());
        assertTrue(version.getSnapshotJson().contains("test-skill"));
        assertNotNull(version.getTestRunId());
    }

    @Test
    @DisplayName("stage 失败 - 无测试运行记录")
    void stage_failsIfNoTestRun() {
        String skillId = setupSkillOnly();

        assertThrows(IllegalStateException.class, () ->
            publishService.stage(skillId, 0.9));
    }

    @Test
    @DisplayName("stage 失败 - 通过率低于阈值")
    void stage_failsIfPassRateBelowThreshold() {
        String skillId = setupSkillWithTestRun(10, 5, 5); // 50% pass rate

        assertThrows(IllegalStateException.class, () ->
            publishService.stage(skillId, 0.9));
    }

    @Test
    @DisplayName("stage 成功 - 通过率达到阈值")
    void stage_succeedsAboveThreshold() {
        String skillId = setupSkillWithTestRun(10, 9, 1); // 90% pass rate

        SkillVersion version = publishService.stage(skillId, 0.9);
        assertEquals("staged", version.getStatus());
    }

    @Test
    @DisplayName("activate 注册到 PromptEngine")
    void activate_registersToPromptEngine() {
        String skillId = setupSkillWithPassingTestRun();
        SkillVersion version = publishService.stage(skillId, 0.5);

        publishService.activate(version.getId());

        assertTrue(promptEngine.hasSkill("test-skill"), "PromptEngine should have the skill");
    }

    @Test
    @DisplayName("activate 归档旧版本")
    void activate_archivesPreviousVersion() {
        String skillId = setupSkillWithPassingTestRun();

        // Stage and activate v1
        SkillVersion v1 = publishService.stage(skillId, 0.5);
        publishService.activate(v1.getId());

        // Stage and activate v2
        SkillVersion v2 = publishService.stage(skillId, 0.5);
        publishService.activate(v2.getId());

        // v1 should be archived
        SkillVersion v1Updated = versionRepository.findById(v1.getId()).orElseThrow();
        assertEquals("archived", v1Updated.getStatus());

        // v2 should be active
        SkillVersion v2Updated = versionRepository.findById(v2.getId()).orElseThrow();
        assertEquals("active", v2Updated.getStatus());
    }

    @Test
    @DisplayName("rollback 重新激活旧版本")
    void rollback_reactivatesPreviousVersion() {
        String skillId = setupSkillWithPassingTestRun();

        SkillVersion v1 = publishService.stage(skillId, 0.5);
        publishService.activate(v1.getId());
        SkillVersion v2 = publishService.stage(skillId, 0.5);
        publishService.activate(v2.getId());

        // Rollback to v1
        publishService.rollback(skillId, v1.getId());

        SkillVersion v1After = versionRepository.findById(v1.getId()).orElseThrow();
        assertEquals("active", v1After.getStatus());

        SkillVersion v2After = versionRepository.findById(v2.getId()).orElseThrow();
        assertEquals("archived", v2After.getStatus());
    }

    // ==================== Setup helpers ====================

    private String setupSkillOnly() {
        SkillDraft draft = new SkillDraft();
        draft.setId("skill-" + UUID.randomUUID().toString().substring(0, 8));
        draft.setName("test-skill");
        draft.setDescription("Test skill for publish");
        draft.setSystemPrompt("You are a test skill.");
        draft.setStatus("draft");
        draft.setToolNames(List.of());
        draft.setTriggers(List.of("test"));
        skillRepository.save(draft);
        return draft.getId();
    }

    private String setupSkillWithPassingTestRun() {
        return setupSkillWithTestRun(5, 5, 0);
    }

    private String setupSkillWithTestRun(int total, int passed, int failed) {
        String skillId = setupSkillOnly();

        TestRun run = new TestRun(UUID.randomUUID().toString(), skillId);
        run.setTotal(total);
        run.setPassed(passed);
        run.setFailed(failed);
        run.setStatus("completed");
        run.setCompletedAt(Instant.now());
        testRunRepository.saveRun(run);

        return skillId;
    }
}
