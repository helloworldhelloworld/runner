package com.lightweightai.assessment.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AssessmentResult - 评估结果模型")
class AssessmentResultTest {

    @Test
    @DisplayName("无参构造")
    void defaultConstruction() {
        AssessmentResult result = new AssessmentResult();
        assertNull(result.getId());
        assertNull(result.getUserId());
        assertNull(result.getScaleType());
        assertEquals(0, result.getTotalScore());
        assertNull(result.getSeverity());
    }

    @Test
    @DisplayName("全参构造")
    void fullConstruction() {
        long now = System.currentTimeMillis();
        AssessmentResult result = new AssessmentResult(
                "r1", "u1", ScaleType.PHQ9, 12, "中度抑郁", "AI report", now);

        assertEquals("r1", result.getId());
        assertEquals("u1", result.getUserId());
        assertEquals(ScaleType.PHQ9, result.getScaleType());
        assertEquals(12, result.getTotalScore());
        assertEquals("中度抑郁", result.getSeverity());
        assertEquals("AI report", result.getAiReport());
        assertEquals(now, result.getCreatedAt());
    }

    @Test
    @DisplayName("setter 正确更新字段")
    void setters() {
        AssessmentResult result = new AssessmentResult();
        result.setId("r2");
        result.setUserId("u2");
        result.setScaleType(ScaleType.GAD7);
        result.setTotalScore(8);
        result.setSeverity("轻度焦虑");
        result.setAiReport("report content");
        result.setCreatedAt(12345L);

        assertEquals("r2", result.getId());
        assertEquals("u2", result.getUserId());
        assertEquals(ScaleType.GAD7, result.getScaleType());
        assertEquals(8, result.getTotalScore());
        assertEquals("轻度焦虑", result.getSeverity());
        assertEquals("report content", result.getAiReport());
        assertEquals(12345L, result.getCreatedAt());
    }
}
