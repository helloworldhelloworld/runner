package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TestRunRepository - 测试运行持久化")
class TestRunRepositoryTest {

    private TestRunRepository repository;
    private Path tempDb;

    @BeforeEach
    void setUp() throws Exception {
        tempDb = Files.createTempFile("test-runs-", ".db");
        repository = new TestRunRepository(tempDb.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempDb);
    }

    @Test
    @DisplayName("保存 TestRun 并按 ID 查询")
    void saveTestRun_andFindById() {
        TestRun run = new TestRun("run-1", "skill-1");
        run.setTotal(5);
        run.setPassed(3);
        run.setFailed(2);
        run.setStatus("completed");

        repository.saveRun(run);

        Optional<TestRun> found = repository.findRunById("run-1");
        assertTrue(found.isPresent());
        assertEquals("skill-1", found.get().getSkillId());
        assertEquals(5, found.get().getTotal());
        assertEquals(3, found.get().getPassed());
        assertEquals(2, found.get().getFailed());
        assertEquals("completed", found.get().getStatus());
    }

    @Test
    @DisplayName("按 skillId 查询，最新在前")
    void findBySkillId_orderedByDate() throws InterruptedException {
        TestRun run1 = new TestRun("run-1", "skill-A");
        run1.setTotal(2);
        run1.setStatus("completed");
        repository.saveRun(run1);

        // Ensure different timestamp
        Thread.sleep(10);

        TestRun run2 = new TestRun("run-2", "skill-A");
        run2.setTotal(3);
        run2.setStatus("completed");
        repository.saveRun(run2);

        List<TestRun> runs = repository.findRunsBySkillId("skill-A");
        assertEquals(2, runs.size());
        assertEquals("run-2", runs.get(0).getId(), "Most recent should be first");
        assertEquals("run-1", runs.get(1).getId());
    }

    @Test
    @DisplayName("保存用例结果并按 runId 查询")
    void saveCaseResult_andFindByRunId() {
        TestRun run = new TestRun("run-X", "skill-1");
        repository.saveRun(run);

        TestCaseResult result1 = new TestCaseResult();
        result1.setId("cr-1");
        result1.setRunId("run-X");
        result1.setTestCaseId("tc-1");
        result1.setPassed(true);
        result1.setLlmOutput("Output text");
        result1.setToolCalls(List.of(Map.of("name", "GetPoi", "args", Map.of("k", "v"))));
        result1.setAssertions(List.of(Map.of("text", "调用GetPoi", "pass", true)));
        result1.setDurationMs(150);

        TestCaseResult result2 = new TestCaseResult();
        result2.setId("cr-2");
        result2.setRunId("run-X");
        result2.setTestCaseId("tc-2");
        result2.setPassed(false);
        result2.setErrorMessage("assertion failed");
        result2.setDurationMs(200);

        repository.saveCaseResult(result1);
        repository.saveCaseResult(result2);

        List<TestCaseResult> results = repository.findCaseResultsByRunId("run-X");
        assertEquals(2, results.size());

        TestCaseResult r1 = results.stream().filter(r -> "cr-1".equals(r.getId())).findFirst().orElseThrow();
        assertTrue(r1.isPassed());
        assertEquals("Output text", r1.getLlmOutput());
        assertNotNull(r1.getToolCalls());
        assertEquals(1, r1.getToolCalls().size());
        assertEquals(150, r1.getDurationMs());

        TestCaseResult r2 = results.stream().filter(r -> "cr-2".equals(r.getId())).findFirst().orElseThrow();
        assertFalse(r2.isPassed());
        assertEquals("assertion failed", r2.getErrorMessage());
    }

    @Test
    @DisplayName("更新运行状态")
    void updateRunStatus() {
        TestRun run = new TestRun("run-S", "skill-1");
        run.setStatus("running");
        repository.saveRun(run);

        repository.updateRunStatus("run-S", "completed");

        Optional<TestRun> found = repository.findRunById("run-S");
        assertTrue(found.isPresent());
        assertEquals("completed", found.get().getStatus());
        assertNotNull(found.get().getCompletedAt());
    }

    @Test
    @DisplayName("TestRun passRate 计算")
    void testRunPassRate() {
        TestRun run = new TestRun();
        run.setTotal(10);
        run.setPassed(7);
        run.setFailed(3);
        assertEquals(0.7, run.getPassRate(), 0.001);

        TestRun empty = new TestRun();
        assertEquals(0.0, empty.getPassRate(), 0.001);
    }
}
