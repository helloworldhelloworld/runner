package com.lightweightai.assessment.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScaleType - 量表类型枚举")
class ScaleTypeTest {

    @Test
    @DisplayName("三种量表类型")
    void allTypes() {
        assertEquals(3, ScaleType.values().length);
        assertNotNull(ScaleType.valueOf("PHQ9"));
        assertNotNull(ScaleType.valueOf("GAD7"));
        assertNotNull(ScaleType.valueOf("PSS10"));
    }

    @Test
    @DisplayName("PHQ9 属性")
    void phq9Properties() {
        assertEquals("患者健康问卷-9", ScaleType.PHQ9.getDisplayName());
        assertEquals(9, ScaleType.PHQ9.getQuestionCount());
    }

    @Test
    @DisplayName("GAD7 属性")
    void gad7Properties() {
        assertEquals("广泛性焦虑量表-7", ScaleType.GAD7.getDisplayName());
        assertEquals(7, ScaleType.GAD7.getQuestionCount());
    }

    @Test
    @DisplayName("PSS10 属性")
    void pss10Properties() {
        assertEquals("压力知觉量表-10", ScaleType.PSS10.getDisplayName());
        assertEquals(10, ScaleType.PSS10.getQuestionCount());
    }
}
