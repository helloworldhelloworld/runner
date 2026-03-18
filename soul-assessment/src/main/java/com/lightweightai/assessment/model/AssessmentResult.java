package com.lightweightai.assessment.model;

/**
 * The scored result of an assessment submission.
 */
public class AssessmentResult {
    private String id;
    private String userId;
    private ScaleType scaleType;
    private int totalScore;
    private String severity;
    private String aiReport;
    private long createdAt;

    public AssessmentResult() {}

    public AssessmentResult(String id, String userId, ScaleType scaleType,
                            int totalScore, String severity, String aiReport, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.scaleType = scaleType;
        this.totalScore = totalScore;
        this.severity = severity;
        this.aiReport = aiReport;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public ScaleType getScaleType() {
        return scaleType;
    }
    public void setScaleType(ScaleType scaleType) {
        this.scaleType = scaleType;
    }
    public int getTotalScore() {
        return totalScore;
    }
    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }
    public String getSeverity() {
        return severity;
    }
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    public String getAiReport() {
        return aiReport;
    }
    public void setAiReport(String aiReport) {
        this.aiReport = aiReport;
    }
    public long getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
