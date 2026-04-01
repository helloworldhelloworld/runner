package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pss10Scale - PSS-10 压力量表")
class Pss10ScaleTest {

    @Test
    @DisplayName("量表定义正确")
    void definition() {
        ScaleDefinition def = Pss10Scale.definition();
        assertEquals(ScaleType.PSS10, def.type());
        assertEquals(10, def.questions().size());
        assertEquals(5, def.options().size());
        assertEquals(5, def.optionScores().size());
    }

    @Test
    @DisplayName("反向计分题目索引正确")
    void reversedItems() {
        assertTrue(Pss10Scale.REVERSED_ITEMS.contains(3));
        assertTrue(Pss10Scale.REVERSED_ITEMS.contains(4));
        assertTrue(Pss10Scale.REVERSED_ITEMS.contains(6));
        assertTrue(Pss10Scale.REVERSED_ITEMS.contains(7));
        assertEquals(4, Pss10Scale.REVERSED_ITEMS.size());
    }

    @Test
    @DisplayName("severity 分级 - 低压力")
    void severityLow() {
        assertEquals("低压力", Pss10Scale.severity(0));
        assertEquals("低压力", Pss10Scale.severity(13));
    }

    @Test
    @DisplayName("severity 分级 - 中等压力")
    void severityModerate() {
        assertEquals("中等压力", Pss10Scale.severity(14));
        assertEquals("中等压力", Pss10Scale.severity(26));
    }

    @Test
    @DisplayName("severity 分级 - 高压力")
    void severityHigh() {
        assertEquals("高压力", Pss10Scale.severity(27));
        assertEquals("高压力", Pss10Scale.severity(40));
    }
}
