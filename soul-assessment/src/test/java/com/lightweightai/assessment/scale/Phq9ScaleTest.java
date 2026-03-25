package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHQ-9 抑郁量表测试
 *
 * 覆盖：definition 结构完整性、severity 分界判断
 */
@DisplayName("Phq9Scale - PHQ-9 抑郁量表")
class Phq9ScaleTest {

    @Test
    @DisplayName("definition 返回 9 道题目")
    void definitionHas9Questions() {
        ScaleDefinition def = Phq9Scale.definition();
        assertEquals(ScaleType.PHQ9, def.type());
        assertEquals(9, def.questions().size());
    }

    @Test
    @DisplayName("definition 选项为 4 级 Likert")
    void definitionHas4Options() {
        ScaleDefinition def = Phq9Scale.definition();
        assertEquals(4, def.options().size());
        assertEquals(4, def.optionScores().size());
        assertEquals(0, def.optionScores().get(0));
        assertEquals(3, def.optionScores().get(3));
    }

    @Test
    @DisplayName("definition 题目索引从 0 连续")
    void definitionQuestionIndicesSequential() {
        ScaleDefinition def = Phq9Scale.definition();
        for (int i = 0; i < def.questions().size(); i++) {
            assertEquals(i, def.questions().get(i).index());
        }
    }

    @ParameterizedTest(name = "score={0} → {1}")
    @DisplayName("severity 分级边界值")
    @CsvSource({
            "0,  无抑郁",
            "4,  无抑郁",
            "5,  轻度抑郁",
            "9,  轻度抑郁",
            "10, 中度抑郁",
            "14, 中度抑郁",
            "15, 中重度抑郁",
            "19, 中重度抑郁",
            "20, 重度抑郁",
            "27, 重度抑郁"
    })
    void severityBoundaries(int score, String expected) {
        assertEquals(expected, Phq9Scale.severity(score));
    }
}
