package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 仿真场景 — 多轮对话回放
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationScenario {

    private String id;
    private String name;
    private List<ScenarioTurn> turns = new ArrayList<>();

    public SimulationScenario() {}

    public SimulationScenario(String id, String name, List<ScenarioTurn> turns) {
        this.id = id;
        this.name = name;
        this.turns = turns != null ? turns : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<ScenarioTurn> getTurns() { return turns; }
    public void setTurns(List<ScenarioTurn> turns) { this.turns = turns != null ? turns : new ArrayList<>(); }

    /**
     * 单轮对话
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScenarioTurn {

        private String userInput;
        private List<String> assertions = new ArrayList<>();
        private Map<String, Object> mockToolResponses;

        public ScenarioTurn() {}

        public ScenarioTurn(String userInput, List<String> assertions, Map<String, Object> mockToolResponses) {
            this.userInput = userInput;
            this.assertions = assertions != null ? assertions : new ArrayList<>();
            this.mockToolResponses = mockToolResponses;
        }

        public String getUserInput() { return userInput; }
        public void setUserInput(String userInput) { this.userInput = userInput; }

        public List<String> getAssertions() { return assertions; }
        public void setAssertions(List<String> assertions) { this.assertions = assertions; }

        public Map<String, Object> getMockToolResponses() { return mockToolResponses; }
        public void setMockToolResponses(Map<String, Object> mockToolResponses) { this.mockToolResponses = mockToolResponses; }
    }
}
