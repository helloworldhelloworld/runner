package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.SkillTestContext;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.TestExecutor;
import com.lightweightai.kernel.prompt.PromptEngine;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Skill Pipeline - 端到端集成测试")
class SkillPipelineIntegrationTest {

    private SkillRepository skillRepository;
    private SkillTestCaseRepository testCaseRepository;
    private TestRunRepository testRunRepository;
    private SkillVersionRepository versionRepository;
    private SimulationRepository simulationRepository;
    private PromptEngine promptEngine;
    private SkillTestExecutionService testExecutionService;
    private SkillPublishService publishService;
    private Path tempDb;

    @BeforeEach
    void setUp() throws Exception {
        tempDb = Files.createTempFile("pipeline-", ".db");
        String dbPath = tempDb.toString();
        skillRepository = new SkillRepository(dbPath);
        testCaseRepository = new SkillTestCaseRepository(dbPath);
        testRunRepository = new TestRunRepository(dbPath);
        versionRepository = new SkillVersionRepository(dbPath);
        simulationRepository = new SimulationRepository(dbPath);
        promptEngine = PromptEngine.builder().build();
        ToolRegistry toolRegistry = new ToolRegistry();

        testExecutionService = new SkillTestExecutionService(
            new MockTestExecutor(), skillRepository, testCaseRepository, testRunRepository);
        publishService = new SkillPublishService(
            skillRepository, versionRepository, testRunRepository, promptEngine, toolRegistry);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempDb);
    }

    @Test
    @DisplayName("完整流水线: Creator → Test → Publish → Activate")
    void fullPipeline_createTestPublishActivate() {
        // ====== Phase: Creator (simulate saving a draft) ======
        SkillDraft draft = new SkillDraft();
        draft.setId("skill-pipeline-test");
        draft.setName("nav-skill");
        draft.setDescription("Navigation skill");
        draft.setSystemPrompt("You are a navigation assistant. Call Navigate tool when user asks for directions.");
        draft.setStatus("draft");
        draft.setToolNames(List.of("Navigate"));
        draft.setTriggers(List.of("导航", "怎么走"));
        skillRepository.save(draft);

        // Add test cases
        for (int i = 0; i < 3; i++) {
            SkillTestCase tc = new SkillTestCase();
            tc.setId("tc-pipeline-" + i);
            tc.setSkillId(draft.getId());
            tc.setCategory("navigation");
            tc.setDescription("Navigation test " + i);
            tc.setInput("导航去地点" + i);
            tc.setAssertions(List.of("调用 Navigate"));
            tc.setMockResponses(Map.of("Navigate", Map.of("status", "ok")));
            testCaseRepository.save(tc);
        }

        // ====== Phase: Test ======
        List<StreamEvent> testEvents = testExecutionService.executeAll(draft.getId()).collectList().block();
        assertNotNull(testEvents);

        StreamEvent runComplete = testEvents.stream()
            .filter(e -> "test_run_complete".equals(e.getCategory()))
            .findFirst().orElseThrow();
        assertEquals(3, runComplete.getData().get("total"));
        assertEquals(3, runComplete.getData().get("passed"));
        assertEquals(0, runComplete.getData().get("failed"));

        // Verify test run persisted
        Optional<TestRun> latestRun = testExecutionService.getLatestReport(draft.getId());
        assertTrue(latestRun.isPresent());
        assertEquals(1.0, latestRun.get().getPassRate(), 0.001);

        // ====== Phase: Publish (stage) ======
        SkillVersion version = publishService.stage(draft.getId(), 0.9);
        assertEquals("staged", version.getStatus());
        assertEquals("1.0.0", version.getVersionTag());
        assertTrue(version.getSnapshotJson().contains("nav-skill"));

        // ====== Phase: Publish (activate) ======
        SkillVersion activated = publishService.activate(version.getId());
        assertEquals("active", activated.getStatus());

        // Verify registered in PromptEngine
        assertTrue(promptEngine.hasSkill("nav-skill"),
            "Skill should be registered in production PromptEngine after activation");

        // ====== Phase: Version history ======
        List<SkillVersion> history = publishService.getVersionHistory(draft.getId());
        assertEquals(1, history.size());
        assertEquals("active", history.get(0).getStatus());

        // ====== Phase: Stage v2 and rollback ======
        SkillVersion v2 = publishService.stage(draft.getId(), 0.9);
        assertEquals("1.0.1", v2.getVersionTag());
        publishService.activate(v2.getId());

        // v1 should be archived
        SkillVersion v1After = versionRepository.findById(version.getId()).orElseThrow();
        assertEquals("archived", v1After.getStatus());

        // Rollback to v1
        publishService.rollback(draft.getId(), version.getId());
        SkillVersion v1Rolled = versionRepository.findById(version.getId()).orElseThrow();
        assertEquals("active", v1Rolled.getStatus());
        SkillVersion v2After = versionRepository.findById(v2.getId()).orElseThrow();
        assertEquals("archived", v2After.getStatus());
    }

    /**
     * Mock TestExecutor: always returns Navigate tool call
     */
    private static class MockTestExecutor implements TestExecutor {
        @Override
        public Flux<StreamEvent> executeTestCase(SkillTestContext context) {
            String output = "我来帮你导航。{\"functionName\": \"Navigate\", \"parameters\": {\"dest\": \"目标\"}}";
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("testCaseId", context.getTestCaseId());
            data.put("llmOutput", output);
            data.put("toolCalls", List.of(Map.of("name", "Navigate", "arguments", Map.of("dest", "目标"))));
            data.put("durationMs", 50L);
            return Flux.just(StreamEvent.postProcessData("test_case_result", data));
        }
    }
}
