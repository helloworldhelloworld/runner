package com.lightweightai.assessment.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScaleType — 心理量表枚举")
class ScaleTypeTest {

    @Test
    @DisplayName("PHQ-9 属性正确")
    void phq9Properties() {
        assertEquals("患者健康问卷-9", ScaleType.PHQ9.getDisplayName());
        assertEquals(9, ScaleType.PHQ9.getQuestionCount());
    }

    @Test
    @DisplayName("GAD-7 属性正确")
    void gad7Properties() {
        assertEquals("广泛性焦虑量表-7", ScaleType.GAD7.getDisplayName());
        assertEquals(7, ScaleType.GAD7.getQuestionCount());
    }

    @Test
    @DisplayName("PSS-10 属性正确")
    void pss10Properties() {
        assertEquals("压力知觉量表-10", ScaleType.PSS10.getDisplayName());
        assertEquals(10, ScaleType.PSS10.getQuestionCount());
    }

    @Test
    @DisplayName("枚举值数量为3")
    void shouldHaveThreeValues() {
        assertEquals(3, ScaleType.values().length);
    }

    @Test
    @DisplayName("valueOf 可解析")
    void shouldParseFromString() {
        assertEquals(ScaleType.PHQ9, ScaleType.valueOf("PHQ9"));
        assertEquals(ScaleType.GAD7, ScaleType.valueOf("GAD7"));
        assertEquals(ScaleType.PSS10, ScaleType.valueOf("PSS10"));
    }
}
