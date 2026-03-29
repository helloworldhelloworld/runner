package com.lightweightai.web.skillcreator;

import java.util.List;
import java.util.Map;

/**
 * 单用例测试结果
 */
public class TestCaseResult {

    private String id;
    private String runId;
    private String testCaseId;
    private boolean passed;
    private String llmOutput;
    private List<Map<String, Object>> toolCalls;
    private List<Map<String, Object>> assertions; // [{text, pass}]
    private long durationMs;
    private String errorMessage;

    public TestCaseResult() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getTestCaseId() { return testCaseId; }
    public void setTestCaseId(String testCaseId) { this.testCaseId = testCaseId; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getLlmOutput() { return llmOutput; }
    public void setLlmOutput(String llmOutput) { this.llmOutput = llmOutput; }

    public List<Map<String, Object>> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; }

    public List<Map<String, Object>> getAssertions() { return assertions; }
    public void setAssertions(List<Map<String, Object>> assertions) { this.assertions = assertions; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
