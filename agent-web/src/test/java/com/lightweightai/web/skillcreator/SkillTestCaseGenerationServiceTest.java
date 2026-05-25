package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillTestCaseGenerationService — parseCases 与 session 管理")
class SkillTestCaseGenerationServiceTest {

    private SkillTestCaseGenerationService service;
    private SkillRepository skillRepo;
    private SkillTestCaseRepository testCaseRepo;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        skillRepo = new SkillRepository(tempDir.resolve("skills.db").toString());
        testCaseRepo = new SkillTestCaseRepository(tempDir.resolve("test-cases.db").toString());

        SkillDraft skill = new SkillDraft();
        skill.setId("skill-1");
        skill.setName("导航技能");
        skill.setDescription("帮助用户导航到目的地");
        skill.setSystemPrompt("你是一个导航助手");
        skill.setToolNames(List.of("GetPoi", "Navigate"));
        skill.setTriggers(List.of("导航", "去"));
        skillRepo.save(skill);

        service = new SkillTestCaseGenerationService(null, skillRepo, testCaseRepo);
    }

    @Test
    @DisplayName("getCases 对未使用的 session 返回空列表")
    void getCasesEmptyForNewSession() {
        List<SkillTestCase> cases = service.getCases("unknown-session");
        assertNotNull(cases);
        assertTrue(cases.isEmpty());
    }

    @Test
    @DisplayName("confirmCases 对无用例的 session 抛出 IllegalStateException")
    void confirmCasesThrowsWhenEmpty() {
        assertThrows(IllegalStateException.class, () -> service.confirmCases("no-cases-session"));
    }

    @Test
    @DisplayName("chat 对不存在的 skillId 抛出 IllegalArgumentException")
    void chatWithInvalidSkillIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.chat("sess-1", "nonexistent-skill", "生成用例"));
    }
}
