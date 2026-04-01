package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phq9Scale - PHQ-9 抑郁量表")
class Phq9ScaleTest {

    @Test
    @DisplayName("量表定义正确")
    void definition() {
        ScaleDefinition def = Phq9Scale.definition();
        assertEquals(ScaleType.PHQ9, def.type());
        assertEquals(9, def.questions().size());
        assertEquals(4, def.options().size());
        assertEquals(4, def.optionScores().size());
    }

    @Test
    @DisplayName("severity 分级 - 无抑郁")
    void severityNone() {
        assertEquals("无抑郁", Phq9Scale.severity(0));
        assertEquals("无抑郁", Phq9Scale.severity(4));
    }

    @Test
    @DisplayName("severity 分级 - 轻度")
    void severityMild() {
        assertEquals("轻度抑郁", Phq9Scale.severity(5));
        assertEquals("轻度抑郁", Phq9Scale.severity(9));
    }

    @Test
    @DisplayName("severity 分级 - 中度")
    void severityModerate() {
        assertEquals("中度抑郁", Phq9Scale.severity(10));
        assertEquals("中度抑郁", Phq9Scale.severity(14));
    }

    @Test
    @DisplayName("severity 分级 - 中重度")
    void severityModeratelySevere() {
        assertEquals("中重度抑郁", Phq9Scale.severity(15));
        assertEquals("中重度抑郁", Phq9Scale.severity(19));
    }

    @Test
    @DisplayName("severity 分级 - 重度")
    void severitySevere() {
        assertEquals("重度抑郁", Phq9Scale.severity(20));
        assertEquals("重度抑郁", Phq9Scale.severity(27));
    }
}
