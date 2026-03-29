package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.*;
import com.lightweightai.kernel.llm.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;

/**
 * Skill 仿真服务
 *
 * 在沙箱环境中回放对话场景，验证 Skill 在多轮对话中的表现。
 * 使用独立的 TestExecutor（不影响生产 PromptEngine）。
 */
public class SkillSimulationService {

    private static final Logger logger = LoggerFactory.getLogger(SkillSimulationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LLMProvider llmProvider;
    private final SkillVersionRepository versionRepository;
    private final SimulationRepository simulationRepository;

    public SkillSimulationService(LLMProvider llmProvider,
                                  SkillVersionRepository versionRepository,
                                  SimulationRepository simulationRepository) {
        this.llmProvider = llmProvider;
        this.versionRepository = versionRepository;
        this.simulationRepository = simulationRepository;
    }

    /**
     * 对 staged 版本运行仿真场景
     */
    @SuppressWarnings("unchecked")
    public Flux<StreamEvent> simulate(String versionId, List<SimulationScenario> scenarios) {
        return Flux.defer(() -> {
            SkillVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

            // Parse skill system prompt from snapshot
            String systemPrompt;
            try {
                Map<String, Object> snapshot = MAPPER.readValue(version.getSnapshotJson(), Map.class);
                systemPrompt = (String) snapshot.getOrDefault("systemPrompt", "");
            } catch (Exception e) {
                return Flux.error(new RuntimeException("Failed to parse version snapshot", e));
            }

            // Create simulation record
            SimulationRepository.SimulationRecord record = new SimulationRepository.SimulationRecord();
            record.setSkillVersionId(versionId);
            record.setScenarioCount(scenarios.size());
            record.setStatus("running");
            simulationRepository.save(record);

            List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
            List<Boolean> scenarioPasses = Collections.synchronizedList(new ArrayList<>());

            // Execute scenarios sequentially
            Flux<StreamEvent> scenarioFlux = Flux.fromIterable(scenarios)
                .concatMap(scenario -> executeScenario(scenario, systemPrompt, latencies, scenarioPasses));

            // Emit simulation_complete
            return scenarioFlux.concatWith(Flux.defer(() -> {
                int passCount = (int) scenarioPasses.stream().filter(b -> b).count();
                double avgLatency = latencies.isEmpty() ? 0 :
                    latencies.stream().mapToLong(Long::longValue).average().orElse(0);

                record.setPassCount(passCount);
                record.setAvgLatencyMs(avgLatency);
                record.setStatus("completed");
                record.setCompletedAt(Instant.now());

                Map<String, Object> reportData = new LinkedHashMap<>();
                reportData.put("scenarioCount", scenarios.size());
                reportData.put("passCount", passCount);
                reportData.put("avgLatencyMs", avgLatency);
                try {
                    record.setReportJson(MAPPER.writeValueAsString(reportData));
                } catch (Exception ignored) {}
                simulationRepository.save(record);

                Map<String, Object> completeData = new LinkedHashMap<>();
                completeData.put("simulationId", record.getId());
                completeData.put("versionId", versionId);
                completeData.put("scenarioCount", scenarios.size());
                completeData.put("passCount", passCount);
                completeData.put("passRate", scenarios.isEmpty() ? 0 : (double) passCount / scenarios.size());
                completeData.put("avgLatencyMs", avgLatency);
                return Flux.just(StreamEvent.postProcessData("simulation_complete", completeData));
            }));
        });
    }

    private Flux<StreamEvent> executeScenario(SimulationScenario scenario, String systemPrompt,
                                               List<Long> latencies, List<Boolean> scenarioPasses) {
        // Execute each turn sequentially, collecting results
        TestExecutor executor = new DefaultTestExecutor(llmProvider, 3);
        List<Boolean> turnPasses = new ArrayList<>();

        return Flux.fromIterable(scenario.getTurns())
            .index()
            .concatMap(indexed -> {
                long turnIndex = indexed.getT1();
                SimulationScenario.ScenarioTurn turn = indexed.getT2();

                SkillTestContext context = SkillTestContext.builder()
                    .testCaseId(scenario.getId() + "-turn-" + turnIndex)
                    .systemPrompt(systemPrompt)
                    .userInput(turn.getUserInput())
                    .mockResponses(turn.getMockToolResponses())
                    .build();

                long startTime = System.currentTimeMillis();

                return executor.executeTestCase(context)
                    .filter(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA
                        && "test_case_result".equals(e.getCategory()))
                    .map(resultEvent -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        latencies.add(elapsed);

                        Map<String, Object> data = resultEvent.getData();
                        String llmOutput = (String) data.getOrDefault("llmOutput", "");

                        // Evaluate turn assertions
                        List<SkillTesterService.ParsedToolCall> parsedCalls =
                            SkillTesterService.parseToolCalls(llmOutput);
                        List<SkillTesterService.AssertionResult> assertResults =
                            SkillTesterService.evaluateAssertions(parsedCalls, turn.getAssertions());
                        boolean turnPassed = assertResults.isEmpty() ||
                            assertResults.stream().allMatch(SkillTesterService.AssertionResult::pass);
                        if (data.containsKey("error")) turnPassed = false;
                        turnPasses.add(turnPassed);

                        Map<String, Object> turnData = new LinkedHashMap<>();
                        turnData.put("scenarioId", scenario.getId());
                        turnData.put("scenarioName", scenario.getName());
                        turnData.put("turnIndex", turnIndex);
                        turnData.put("input", turn.getUserInput());
                        turnData.put("output", llmOutput);
                        turnData.put("passed", turnPassed);
                        turnData.put("assertions", assertResults.stream()
                            .map(a -> Map.of("text", a.text(), "pass", a.pass()))
                            .toList());
                        turnData.put("durationMs", elapsed);
                        return StreamEvent.postProcessData("simulation_turn", turnData);
                    });
            })
            .doOnComplete(() -> {
                boolean scenarioPassed = !turnPasses.isEmpty() && turnPasses.stream().allMatch(b -> b);
                scenarioPasses.add(scenarioPassed);
            });
    }
}
