package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GAD-7 焦虑量表测试
 *
 * 覆盖：definition 结构完整性、severity 分界判断
 */
@DisplayName("Gad7Scale - GAD-7 焦虑量表")
class Gad7ScaleTest {

    @Test
    @DisplayName("definition 返回 7 道题目")
    void definitionHas7Questions() {
        ScaleDefinition def = Gad7Scale.definition();
        assertEquals(ScaleType.GAD7, def.type());
        assertEquals(7, def.questions().size());
    }

    @Test
    @DisplayName("definition 选项为 4 级 Likert")
    void definitionHas4Options() {
        ScaleDefinition def = Gad7Scale.definition();
        assertEquals(4, def.options().size());
        assertEquals(0, def.optionScores().get(0));
        assertEquals(3, def.optionScores().get(3));
    }

    @ParameterizedTest(name = "score={0} → {1}")
    @DisplayName("severity 分级边界值")
    @CsvSource({
            "0,  无焦虑",
            "4,  无焦虑",
            "5,  轻度焦虑",
            "9,  轻度焦虑",
            "10, 中度焦虑",
            "14, 中度焦虑",
            "15, 重度焦虑",
            "21, 重度焦虑"
    })
    void severityBoundaries(int score, String expected) {
        assertEquals(expected, Gad7Scale.severity(score));
    }
}
