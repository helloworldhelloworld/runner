package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.core.DefaultTestExecutor;
import com.lightweightai.kernel.core.SkillTestContext;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.TestExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;

/**
 * Skill 测试运行编排服务
 *
 * 加载 SkillDraft + TestCase 列表，逐用例调用 TestExecutor 执行，
 * 评估断言，持久化结果，流式返回事件。
 */
public class SkillTestExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(SkillTestExecutionService.class);

    private final TestExecutor testExecutor;
    private final SkillRepository skillRepository;
    private final SkillTestCaseRepository testCaseRepository;
    private final TestRunRepository testRunRepository;

    public SkillTestExecutionService(TestExecutor testExecutor,
                                     SkillRepository skillRepository,
                                     SkillTestCaseRepository testCaseRepository,
                                     TestRunRepository testRunRepository) {
        this.testExecutor = testExecutor;
        this.skillRepository = skillRepository;
        this.testCaseRepository = testCaseRepository;
        this.testRunRepository = testRunRepository;
    }

    /**
     * 执行指定 Skill 的全部测试用例
     *
     * @return 流式事件：per-case start/result + final run_complete
     */
    public Flux<StreamEvent> executeAll(String skillId) {
        return Flux.defer(() -> {
            SkillDraft skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
            List<SkillTestCase> testCases = testCaseRepository.findBySkillId(skillId);
            if (testCases.isEmpty()) {
                return Flux.error(new IllegalStateException("No test cases for skill: " + skillId));
            }

            TestRun run = new TestRun(UUID.randomUUID().toString(), skillId);
            run.setTotal(testCases.size());
            testRunRepository.saveRun(run);

            // Execute test cases sequentially, accumulate results
            List<TestCaseResult> allResults = Collections.synchronizedList(new ArrayList<>());

            Flux<StreamEvent> caseFlux = Flux.fromIterable(testCases)
                .concatMap(tc -> executeSingleCase(skill, tc, run.getId(), allResults));

            // After all cases, emit run complete
            return caseFlux.concatWith(Flux.defer(() -> {
                int passed = (int) allResults.stream().filter(TestCaseResult::isPassed).count();
                int failed = allResults.size() - passed;
                run.setPassed(passed);
                run.setFailed(failed);
                run.setStatus("completed");
                run.setCompletedAt(Instant.now());
                testRunRepository.saveRun(run);

                Map<String, Object> completeData = new LinkedHashMap<>();
                completeData.put("runId", run.getId());
                completeData.put("skillId", skillId);
                completeData.put("total", run.getTotal());
                completeData.put("passed", passed);
                completeData.put("failed", failed);
                completeData.put("passRate", run.getPassRate());
                return Flux.just(StreamEvent.postProcessData("test_run_complete", completeData));
            }));
        });
    }

    /**
     * 执行单个测试用例
     */
    public Flux<StreamEvent> executeSingle(String skillId, String testCaseId) {
        return Flux.defer(() -> {
            SkillDraft skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
            SkillTestCase tc = testCaseRepository.findBySkillId(skillId).stream()
                .filter(c -> testCaseId.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Test case not found: " + testCaseId));

            return executeSingleCase(skill, tc, null, new ArrayList<>());
        });
    }

    /**
     * 获取最新的测试运行报告
     */
    public Optional<TestRun> getLatestReport(String skillId) {
        List<TestRun> runs = testRunRepository.findRunsBySkillId(skillId);
        return runs.isEmpty() ? Optional.empty() : Optional.of(runs.get(0));
    }

    private Flux<StreamEvent> executeSingleCase(SkillDraft skill, SkillTestCase tc,
                                                 String runId, List<TestCaseResult> accumulator) {
        // Emit test_case_start
        Map<String, Object> startData = new LinkedHashMap<>();
        startData.put("testCaseId", tc.getId());
        startData.put("description", tc.getDescription());
        startData.put("input", tc.getInput());
        StreamEvent startEvent = StreamEvent.postProcessData("test_case_start", startData);

        // Build context
        SkillTestContext context = SkillTestContext.builder()
            .testCaseId(tc.getId())
            .systemPrompt(skill.getSystemPrompt())
            .userInput(tc.getInput())
            .memoryContext(tc.getMemory())
            .mockResponses(tc.getMockResponses())
            .build();

        return Flux.just(startEvent)
            .concatWith(testExecutor.executeTestCase(context)
                .filter(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA
                    && "test_case_result".equals(e.getCategory()))
                .map(resultEvent -> {
                    Map<String, Object> data = resultEvent.getData();

                    // Evaluate assertions
                    String llmOutput = (String) data.getOrDefault("llmOutput", "");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) data.getOrDefault("toolCalls", List.of());

                    List<SkillTesterService.ParsedToolCall> parsedCalls =
                        SkillTesterService.parseToolCalls(llmOutput);
                    // Also include tool calls from the event data (actual calls made via ToolCallingLoop)
                    for (Map<String, Object> tc2 : toolCalls) {
                        String name = (String) tc2.get("name");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = (Map<String, Object>) tc2.getOrDefault("arguments", Map.of());
                        if (name != null && parsedCalls.stream().noneMatch(p -> p.functionName().equals(name))) {
                            parsedCalls.add(new SkillTesterService.ParsedToolCall(name, args));
                        }
                    }

                    List<SkillTesterService.AssertionResult> assertionResults =
                        SkillTesterService.evaluateAssertions(parsedCalls, tc.getAssertions());

                    boolean allPassed = assertionResults.isEmpty() || assertionResults.stream().allMatch(SkillTesterService.AssertionResult::pass);
                    String error = (String) data.get("error");
                    if (error != null) allPassed = false;

                    // Persist result
                    TestCaseResult caseResult = new TestCaseResult();
                    caseResult.setRunId(runId);
                    caseResult.setTestCaseId(tc.getId());
                    caseResult.setPassed(allPassed);
                    caseResult.setLlmOutput(llmOutput);
                    caseResult.setToolCalls(toolCalls);
                    caseResult.setAssertions(assertionResults.stream()
                        .map(a -> Map.<String, Object>of("text", a.text(), "pass", a.pass()))
                        .toList());
                    caseResult.setDurationMs(data.get("durationMs") != null ? ((Number) data.get("durationMs")).longValue() : 0);
                    caseResult.setErrorMessage(error);

                    if (runId != null) {
                        testRunRepository.saveCaseResult(caseResult);
                    }
                    accumulator.add(caseResult);

                    // Build enriched result event
                    Map<String, Object> enrichedData = new LinkedHashMap<>(data);
                    enrichedData.put("passed", allPassed);
                    enrichedData.put("assertions", caseResult.getAssertions());
                    return StreamEvent.postProcessData("test_case_result", enrichedData);
                }));
    }
}
