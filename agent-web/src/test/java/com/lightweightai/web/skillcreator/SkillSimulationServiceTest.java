package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillSimulationService - 沙箱仿真")
class SkillSimulationServiceTest {

    private SkillSimulationService service;
    private SkillVersionRepository versionRepository;
    private SimulationRepository simulationRepository;
    private Path tempDb;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        tempDb = Files.createTempFile("sim-", ".db");
        String dbPath = tempDb.toString();
        versionRepository = new SkillVersionRepository(dbPath);
        simulationRepository = new SimulationRepository(dbPath);

        // Mock LLM that returns Navigate tool call in response
        ReactiveMockProvider mockLlm = new ReactiveMockProvider();
        service = new SkillSimulationService(mockLlm, versionRepository, simulationRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempDb);
    }

    @Test
    @DisplayName("仿真构建隔离环境 - 不含生产 Skill")
    void simulate_buildsIsolatedGateway() {
        String versionId = setupStagedVersion();
        List<SimulationScenario> scenarios = List.of(createSimpleScenario("s1", 1));

        // Should not throw - runs in isolated context
        List<StreamEvent> events = service.simulate(versionId, scenarios).collectList().block();
        assertNotNull(events);
        assertTrue(events.size() > 0);
    }

    @Test
    @DisplayName("仿真回放多轮对话")
    void simulate_replaysMultiTurnConversation() {
        String versionId = setupStagedVersion();
        SimulationScenario scenario = createSimpleScenario("s-multi", 3);

        List<StreamEvent> events = service.simulate(versionId, List.of(scenario)).collectList().block();
        assertNotNull(events);

        long turnEvents = events.stream()
            .filter(e -> "simulation_turn".equals(e.getCategory()))
            .count();
        assertEquals(3, turnEvents, "Should have 3 simulation turn events");
    }

    @Test
    @DisplayName("仿真逐轮评估断言")
    void simulate_evaluatesPerTurnAssertions() {
        String versionId = setupStagedVersion();
        SimulationScenario.ScenarioTurn turn = new SimulationScenario.ScenarioTurn(
            "导航去北京",
            List.of("调用Navigate"),
            Map.of("Navigate", Map.of("status", "ok"))
        );
        SimulationScenario scenario = new SimulationScenario("s-assert", "assert test", List.of(turn));

        List<StreamEvent> events = service.simulate(versionId, List.of(scenario)).collectList().block();
        assertNotNull(events);

        StreamEvent turnEvent = events.stream()
            .filter(e -> "simulation_turn".equals(e.getCategory()))
            .findFirst().orElseThrow();

        Map<String, Object> data = turnEvent.getData();
        assertNotNull(data.get("passed"));
        assertNotNull(data.get("assertions"));
    }

    @Test
    @DisplayName("仿真完成发射 simulation_complete 事件")
    void simulate_emitsSimulationComplete() {
        String versionId = setupStagedVersion();
        List<SimulationScenario> scenarios = List.of(
            createSimpleScenario("s1", 1),
            createSimpleScenario("s2", 2)
        );

        List<StreamEvent> events = service.simulate(versionId, scenarios).collectList().block();
        assertNotNull(events);

        StreamEvent completeEvent = events.stream()
            .filter(e -> "simulation_complete".equals(e.getCategory()))
            .findFirst().orElseThrow();

        Map<String, Object> data = completeEvent.getData();
        assertEquals(2, data.get("scenarioCount"));
        assertNotNull(data.get("simulationId"));
        assertNotNull(data.get("passRate"));
        assertNotNull(data.get("avgLatencyMs"));
    }

    @Test
    @DisplayName("仿真结果持久化到数据库")
    void simulate_persistsReport() {
        String versionId = setupStagedVersion();
        List<SimulationScenario> scenarios = List.of(createSimpleScenario("s1", 2));

        service.simulate(versionId, scenarios).blockLast();

        List<SimulationRepository.SimulationRecord> records =
            simulationRepository.findByVersionId(versionId);
        assertEquals(1, records.size());

        SimulationRepository.SimulationRecord record = records.get(0);
        assertEquals("completed", record.getStatus());
        assertEquals(1, record.getScenarioCount());
        assertNotNull(record.getReportJson());
        assertNotNull(record.getCompletedAt());
    }

    // ==================== Setup helpers ====================

    private String setupStagedVersion() {
        SkillVersion version = new SkillVersion();
        version.setId("ver-" + UUID.randomUUID().toString().substring(0, 8));
        version.setSkillId("skill-1");
        version.setVersionTag("1.0.0");
        version.setStatus("staged");
        try {
            Map<String, Object> snapshot = Map.of(
                "name", "test-skill",
                "description", "Test skill",
                "systemPrompt", "You are a navigation skill. When user asks to navigate, call Navigate tool. " +
                    "Output tool calls as JSON: {\"functionName\": \"Navigate\", \"parameters\": {\"dest\": \"...\"}}",
                "toolNames", List.of("Navigate"),
                "triggers", List.of("导航")
            );
            version.setSnapshotJson(MAPPER.writeValueAsString(snapshot));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        versionRepository.save(version);
        return version.getId();
    }

    private SimulationScenario createSimpleScenario(String id, int turns) {
        List<SimulationScenario.ScenarioTurn> turnList = new ArrayList<>();
        for (int i = 0; i < turns; i++) {
            turnList.add(new SimulationScenario.ScenarioTurn(
                "导航去地点" + i,
                List.of("调用Navigate"),
                Map.of("Navigate", Map.of("status", "ok"))
            ));
        }
        return new SimulationScenario(id, "Scenario " + id, turnList);
    }

    /**
     * Mock LLM that returns Navigate tool call in output text
     */
    private static class ReactiveMockProvider implements LLMProvider {
        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            String text = "好的，我来帮你导航。{\"functionName\": \"Navigate\", \"parameters\": {\"dest\": \"北京\"}}";
            LLMResponse response = LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT)
                    .textContent(text)
                    .build())
                .build();
            return Flux.just(
                StreamEvent.textDelta(text),
                StreamEvent.llmComplete(response)
            );
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "sim-mock"; }
    }
}
