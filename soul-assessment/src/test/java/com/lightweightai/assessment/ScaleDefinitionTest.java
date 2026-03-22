package com.lightweightai.assessment;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import com.lightweightai.assessment.scale.Gad7Scale;
import com.lightweightai.assessment.scale.Phq9Scale;
import com.lightweightai.assessment.scale.Pss10Scale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Scale Definitions - 量表定义")
class ScaleDefinitionTest {

    // ==================== PHQ-9 ====================

    @Test
    @DisplayName("PHQ-9: 包含9个问题")
    void phq9ShouldHave9Questions() {
        ScaleDefinition def = Phq9Scale.definition();
        assertEquals(9, def.questions().size());
        assertEquals(ScaleType.PHQ9, def.type());
    }

    @Test
    @DisplayName("PHQ-9: 有4个选项(0-3分)")
    void phq9ShouldHave4Options() {
        ScaleDefinition def = Phq9Scale.definition();
        assertEquals(4, def.options().size());
        assertEquals(4, def.optionScores().size());
        assertEquals(0, def.optionScores().get(0));
        assertEquals(3, def.optionScores().get(3));
    }

    @Test
    @DisplayName("PHQ-9: 问题索引从0到8")
    void phq9QuestionsIndexed0To8() {
        ScaleDefinition def = Phq9Scale.definition();
        for (int i = 0; i < 9; i++) {
            assertEquals(i, def.questions().get(i).index());
            assertFalse(def.questions().get(i).text().isBlank());
        }
    }

    @Test
    @DisplayName("PHQ-9: 严重程度边界正确")
    void phq9SeverityBoundaries() {
        assertEquals("无抑郁", Phq9Scale.severity(0));
        assertEquals("无抑郁", Phq9Scale.severity(4));
        assertEquals("轻度抑郁", Phq9Scale.severity(5));
        assertEquals("轻度抑郁", Phq9Scale.severity(9));
        assertEquals("中度抑郁", Phq9Scale.severity(10));
        assertEquals("中度抑郁", Phq9Scale.severity(14));
        assertEquals("中重度抑郁", Phq9Scale.severity(15));
        assertEquals("中重度抑郁", Phq9Scale.severity(19));
        assertEquals("重度抑郁", Phq9Scale.severity(20));
        assertEquals("重度抑郁", Phq9Scale.severity(27));
    }

    // ==================== GAD-7 ====================

    @Test
    @DisplayName("GAD-7: 包含7个问题")
    void gad7ShouldHave7Questions() {
        ScaleDefinition def = Gad7Scale.definition();
        assertEquals(7, def.questions().size());
        assertEquals(ScaleType.GAD7, def.type());
    }

    @Test
    @DisplayName("GAD-7: 有4个选项(0-3分)")
    void gad7ShouldHave4Options() {
        ScaleDefinition def = Gad7Scale.definition();
        assertEquals(4, def.options().size());
        assertEquals(0, def.optionScores().get(0));
        assertEquals(3, def.optionScores().get(3));
    }

    @Test
    @DisplayName("GAD-7: 严重程度边界正确")
    void gad7SeverityBoundaries() {
        assertEquals("无焦虑", Gad7Scale.severity(0));
        assertEquals("无焦虑", Gad7Scale.severity(4));
        assertEquals("轻度焦虑", Gad7Scale.severity(5));
        assertEquals("轻度焦虑", Gad7Scale.severity(9));
        assertEquals("中度焦虑", Gad7Scale.severity(10));
        assertEquals("中度焦虑", Gad7Scale.severity(14));
        assertEquals("重度焦虑", Gad7Scale.severity(15));
        assertEquals("重度焦虑", Gad7Scale.severity(21));
    }

    // ==================== PSS-10 ====================

    @Test
    @DisplayName("PSS-10: 包含10个问题")
    void pss10ShouldHave10Questions() {
        ScaleDefinition def = Pss10Scale.definition();
        assertEquals(10, def.questions().size());
        assertEquals(ScaleType.PSS10, def.type());
    }

    @Test
    @DisplayName("PSS-10: 有5个选项(0-4分)")
    void pss10ShouldHave5Options() {
        ScaleDefinition def = Pss10Scale.definition();
        assertEquals(5, def.options().size());
        assertEquals(0, def.optionScores().get(0));
        assertEquals(4, def.optionScores().get(4));
    }

    @Test
    @DisplayName("PSS-10: 反向计分项为{3,4,6,7}")
    void pss10ReversedItemsShouldBe3467() {
        assertTrue(Pss10Scale.REVERSED_ITEMS.contains(3));
        assertTrue(Pss10Scale.REVERSED_ITEMS.contains(4));
        assertTrue(Pss10Scale.REVERSED_ITEMS.contains(6));
        assertTrue(Pss10Scale.REVERSED_ITEMS.contains(7));
        assertEquals(4, Pss10Scale.REVERSED_ITEMS.size());
    }

    @Test
    @DisplayName("PSS-10: 严重程度边界正确")
    void pss10SeverityBoundaries() {
        assertEquals("低压力", Pss10Scale.severity(0));
        assertEquals("低压力", Pss10Scale.severity(13));
        assertEquals("中等压力", Pss10Scale.severity(14));
        assertEquals("中等压力", Pss10Scale.severity(26));
        assertEquals("高压力", Pss10Scale.severity(27));
        assertEquals("高压力", Pss10Scale.severity(40));
    }

    // ==================== ScaleType 枚举 ====================

    @Test
    @DisplayName("ScaleType: 问题数量匹配")
    void scaleTypeQuestionCounts() {
        assertEquals(9, ScaleType.PHQ9.getQuestionCount());
        assertEquals(7, ScaleType.GAD7.getQuestionCount());
        assertEquals(10, ScaleType.PSS10.getQuestionCount());
    }

    @Test
    @DisplayName("ScaleType: 显示名称不为空")
    void scaleTypeDisplayNames() {
        for (ScaleType type : ScaleType.values()) {
            assertNotNull(type.getDisplayName());
            assertFalse(type.getDisplayName().isBlank());
        }
    }
}
