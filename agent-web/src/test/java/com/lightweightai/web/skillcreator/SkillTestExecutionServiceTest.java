package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.core.SkillTestContext;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.TestExecutor;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillTestExecutionService - 测试运行编排")
class SkillTestExecutionServiceTest {

    private SkillTestExecutionService service;
    private SkillRepository skillRepository;
    private SkillTestCaseRepository testCaseRepository;
    private TestRunRepository testRunRepository;
    private Path tempDb;

    @BeforeEach
    void setUp() throws Exception {
        tempDb = Files.createTempFile("exec-svc-", ".db");
        String dbPath = tempDb.toString();
        skillRepository = new SkillRepository(dbPath);
        testCaseRepository = new SkillTestCaseRepository(dbPath);
        testRunRepository = new TestRunRepository(dbPath);
        MockTestExecutor mockExecutor = new MockTestExecutor();

        service = new SkillTestExecutionService(
            mockExecutor, skillRepository, testCaseRepository, testRunRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempDb);
    }

    @Test
    @DisplayName("执行全部用例 - 3 个用例全部执行并发射结果事件")
    void executeAll_runsAllTestCases() {
        String skillId = setupSkillWithTestCases(3);

        List<StreamEvent> events = service.executeAll(skillId).collectList().block();
        assertNotNull(events);

        long startEvents = events.stream()
            .filter(e -> "test_case_start".equals(e.getCategory()))
            .count();
        long resultEvents = events.stream()
            .filter(e -> "test_case_result".equals(e.getCategory()))
            .count();
        assertEquals(3, startEvents, "Should emit 3 start events");
        assertEquals(3, resultEvents, "Should emit 3 result events");
    }

    @Test
    @DisplayName("执行全部用例 - 持久化 TestRun 和 CaseResults")
    void executeAll_persistsTestRunAndResults() {
        String skillId = setupSkillWithTestCases(2);

        service.executeAll(skillId).blockLast();

        List<TestRun> runs = testRunRepository.findRunsBySkillId(skillId);
        assertEquals(1, runs.size());
        TestRun run = runs.get(0);
        assertEquals(2, run.getTotal());
        assertEquals("completed", run.getStatus());
        assertNotNull(run.getCompletedAt());

        List<TestCaseResult> results = testRunRepository.findCaseResultsByRunId(run.getId());
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("执行全部用例 - 最终事件含聚合统计")
    void executeAll_emitsRunCompleteWithStats() {
        String skillId = setupSkillWithTestCases(3);

        List<StreamEvent> events = service.executeAll(skillId).collectList().block();
        assertNotNull(events);

        StreamEvent completeEvent = events.stream()
            .filter(e -> "test_run_complete".equals(e.getCategory()))
            .findFirst().orElseThrow();

        Map<String, Object> data = completeEvent.getData();
        assertEquals(3, data.get("total"));
        assertNotNull(data.get("runId"));
        assertNotNull(data.get("passRate"));
        assertEquals(skillId, data.get("skillId"));
    }

    @Test
    @DisplayName("执行单个用例 - 只执行指定用例")
    void executeSingle_runsOneCase() {
        String skillId = setupSkillWithTestCases(3);
        List<SkillTestCase> cases = testCaseRepository.findBySkillId(skillId);
        String targetId = cases.get(1).getId();

        List<StreamEvent> events = service.executeSingle(skillId, targetId).collectList().block();
        assertNotNull(events);

        long resultEvents = events.stream()
            .filter(e -> "test_case_result".equals(e.getCategory()))
            .count();
        assertEquals(1, resultEvents, "Should have exactly 1 result event");

        StreamEvent resultEvent = events.stream()
            .filter(e -> "test_case_result".equals(e.getCategory()))
            .findFirst().orElseThrow();
        assertEquals(targetId, resultEvent.getData().get("testCaseId"));
    }

    // ==================== Setup helpers ====================

    private String setupSkillWithTestCases(int count) {
        SkillDraft draft = new SkillDraft();
        draft.setId("skill-" + UUID.randomUUID().toString().substring(0, 8));
        draft.setName("test-skill-" + draft.getId());
        draft.setDescription("Test skill");
        draft.setSystemPrompt("You are a test skill. When asked to navigate, call Navigate tool.");
        draft.setStatus("draft");
        skillRepository.save(draft);

        for (int i = 0; i < count; i++) {
            SkillTestCase tc = new SkillTestCase();
            tc.setId("tc-" + draft.getId() + "-" + i);
            tc.setSkillId(draft.getId());
            tc.setCategory("test");
            tc.setDescription("Test case " + i);
            tc.setInput("user input " + i);
            tc.setAssertions(List.of("调用Navigate"));
            tc.setMockResponses(Map.of("Navigate", Map.of("status", "ok")));
            testCaseRepository.save(tc);
        }

        return draft.getId();
    }

    /**
     * Mock TestExecutor: simulates LLM calling Navigate tool
     */
    private static class MockTestExecutor implements TestExecutor {
        @Override
        public Flux<StreamEvent> executeTestCase(SkillTestContext context) {
            String output = "我来帮你导航。{\"functionName\": \"Navigate\", \"parameters\": {\"dest\": \"北京\"}}";
            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("testCaseId", context.getTestCaseId());
            resultData.put("llmOutput", output);
            resultData.put("toolCalls", List.of(Map.of("name", "Navigate", "arguments", Map.of("dest", "北京"))));
            resultData.put("durationMs", 100L);
            return Flux.just(StreamEvent.postProcessData("test_case_result", resultData));
        }
    }
}
