package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Gad7Scale - GAD-7 焦虑量表")
class Gad7ScaleTest {

    @Test
    @DisplayName("量表定义正确")
    void definition() {
        ScaleDefinition def = Gad7Scale.definition();
        assertEquals(ScaleType.GAD7, def.scaleType());
        assertEquals(7, def.questions().size());
        assertEquals(4, def.optionLabels().size());
    }

    @Test
    @DisplayName("severity 分级 - 无焦虑")
    void severityNone() {
        assertEquals("无焦虑", Gad7Scale.severity(0));
        assertEquals("无焦虑", Gad7Scale.severity(4));
    }

    @Test
    @DisplayName("severity 分级 - 轻度")
    void severityMild() {
        assertEquals("轻度焦虑", Gad7Scale.severity(5));
        assertEquals("轻度焦虑", Gad7Scale.severity(9));
    }

    @Test
    @DisplayName("severity 分级 - 中度")
    void severityModerate() {
        assertEquals("中度焦虑", Gad7Scale.severity(10));
        assertEquals("中度焦虑", Gad7Scale.severity(14));
    }

    @Test
    @DisplayName("severity 分级 - 重度")
    void severitySevere() {
        assertEquals("重度焦虑", Gad7Scale.severity(15));
        assertEquals("重度焦虑", Gad7Scale.severity(21));
    }
}
