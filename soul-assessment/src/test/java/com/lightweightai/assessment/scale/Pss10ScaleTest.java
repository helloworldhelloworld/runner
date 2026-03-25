package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PSS-10 压力量表测试
 *
 * 覆盖：definition 结构、反向计分项正确性、severity 分界判断
 */
@DisplayName("Pss10Scale - PSS-10 压力量表")
class Pss10ScaleTest {

    @Test
    @DisplayName("definition 返回 10 道题目")
    void definitionHas10Questions() {
        ScaleDefinition def = Pss10Scale.definition();
        assertEquals(ScaleType.PSS10, def.type());
        assertEquals(10, def.questions().size());
    }

    @Test
    @DisplayName("definition 选项为 5 级 Likert")
    void definitionHas5Options() {
        ScaleDefinition def = Pss10Scale.definition();
        assertEquals(5, def.options().size());
        assertEquals(5, def.optionScores().size());
        assertEquals(0, def.optionScores().get(0));
        assertEquals(4, def.optionScores().get(4));
    }

    @Test
    @DisplayName("反向计分项为 3,4,6,7")
    void reversedItemsCorrect() {
        assertEquals(Set.of(3, 4, 6, 7), Pss10Scale.REVERSED_ITEMS);
    }

    @Test
    @DisplayName("反向计分项数量为 4")
    void reversedItemsCount() {
        assertEquals(4, Pss10Scale.REVERSED_ITEMS.size());
    }

    @ParameterizedTest(name = "score={0} → {1}")
    @DisplayName("severity 分级边界值")
    @CsvSource({
            "0,  低压力",
            "13, 低压力",
            "14, 中等压力",
            "26, 中等压力",
            "27, 高压力",
            "40, 高压力"
    })
    void severityBoundaries(int score, String expected) {
        assertEquals(expected, Pss10Scale.severity(score));
    }
}
