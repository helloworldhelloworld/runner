package com.lightweightai.web.skillcreator;

import java.time.Instant;

/**
 * 测试运行记录
 */
public class TestRun {

    private String id;
    private String skillId;
    private Instant startedAt;
    private Instant completedAt;
    private int total;
    private int passed;
    private int failed;
    private String status; // running | completed | failed

    private String candidateJson;
    private String candidateDigest;
    private String evaluationJson;
    private String evaluationDigest;
    private String reportDigest;

    public TestRun() {}

    public TestRun(String id, String skillId) {
        this.id = id;
        this.skillId = skillId;
        this.startedAt = Instant.now();
        this.status = "running";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getPassed() { return passed; }
    public void setPassed(int passed) { this.passed = passed; }

    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCandidateJson() { return candidateJson; }
    public void setCandidateJson(String value) { this.candidateJson = value; }
    public String getCandidateDigest() { return candidateDigest; }
    public void setCandidateDigest(String value) { this.candidateDigest = value; }
    public String getEvaluationJson() { return evaluationJson; }
    public void setEvaluationJson(String value) { this.evaluationJson = value; }
    public String getEvaluationDigest() { return evaluationDigest; }
    public void setEvaluationDigest(String value) { this.evaluationDigest = value; }
    public String getReportDigest() { return reportDigest; }
    public void setReportDigest(String value) { this.reportDigest = value; }

    public double getPassRate() {
        return total > 0 ? (double) passed / total : 0.0;
    }
}
