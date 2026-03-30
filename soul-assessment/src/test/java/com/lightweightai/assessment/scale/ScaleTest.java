package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("心理评估量表定义与评分")
class ScaleTest {

    @Nested
    @DisplayName("GAD-7 焦虑量表")
    class Gad7Tests {

        @Test
        @DisplayName("definition 返回正确的量表定义")
        void definition() {
            ScaleDefinition def = Gad7Scale.definition();

            assertEquals(ScaleType.GAD7, def.type());
            assertEquals(7, def.questions().size());
            assertEquals(4, def.options().size());
            assertEquals(4, def.optionScores().size());
            assertEquals(0, def.optionScores().get(0));
            assertEquals(3, def.optionScores().get(3));
            assertTrue(def.title().contains("GAD-7"));
        }

        @ParameterizedTest
        @CsvSource({
            "0, 无焦虑",
            "4, 无焦虑",
            "5, 轻度焦虑",
            "9, 轻度焦虑",
            "10, 中度焦虑",
            "14, 中度焦虑",
            "15, 重度焦虑",
            "21, 重度焦虑"
        })
        @DisplayName("severity 边界值测试")
        void severity(int score, String expected) {
            assertEquals(expected, Gad7Scale.severity(score));
        }
    }

    @Nested
    @DisplayName("PHQ-9 抑郁量表")
    class Phq9Tests {

        @Test
        @DisplayName("definition 返回正确的量表定义")
        void definition() {
            ScaleDefinition def = Phq9Scale.definition();

            assertEquals(ScaleType.PHQ9, def.type());
            assertEquals(9, def.questions().size());
            assertEquals(4, def.options().size());
            assertTrue(def.title().contains("PHQ-9"));
        }

        @ParameterizedTest
        @CsvSource({
            "0, 无抑郁",
            "4, 无抑郁",
            "5, 轻度抑郁",
            "9, 轻度抑郁",
            "10, 中度抑郁",
            "14, 中度抑郁",
            "15, 中重度抑郁",
            "19, 中重度抑郁",
            "20, 重度抑郁",
            "27, 重度抑郁"
        })
        @DisplayName("severity 边界值测试")
        void severity(int score, String expected) {
            assertEquals(expected, Phq9Scale.severity(score));
        }
    }

    @Nested
    @DisplayName("PSS-10 压力量表")
    class Pss10Tests {

        @Test
        @DisplayName("definition 返回正确的量表定义")
        void definition() {
            ScaleDefinition def = Pss10Scale.definition();

            assertEquals(ScaleType.PSS10, def.type());
            assertEquals(10, def.questions().size());
            assertEquals(5, def.options().size());
            assertEquals(5, def.optionScores().size());
            assertEquals(0, def.optionScores().get(0));
            assertEquals(4, def.optionScores().get(4));
            assertTrue(def.title().contains("PSS-10"));
        }

        @ParameterizedTest
        @CsvSource({
            "0, 低压力",
            "13, 低压力",
            "14, 中等压力",
            "26, 中等压力",
            "27, 高压力",
            "40, 高压力"
        })
        @DisplayName("severity 边界值测试")
        void severity(int score, String expected) {
            assertEquals(expected, Pss10Scale.severity(score));
        }

        @Test
        @DisplayName("反向计分项定义正确")
        void reversedItems() {
            assertEquals(4, Pss10Scale.REVERSED_ITEMS.size());
            assertTrue(Pss10Scale.REVERSED_ITEMS.contains(3));
            assertTrue(Pss10Scale.REVERSED_ITEMS.contains(4));
            assertTrue(Pss10Scale.REVERSED_ITEMS.contains(6));
            assertTrue(Pss10Scale.REVERSED_ITEMS.contains(7));
        }
    }

    @Nested
    @DisplayName("ScaleType 枚举")
    class ScaleTypeTests {

        @Test
        @DisplayName("PHQ9 属性正确")
        void phq9() {
            assertEquals("患者健康问卷-9", ScaleType.PHQ9.getDisplayName());
            assertEquals(9, ScaleType.PHQ9.getQuestionCount());
        }

        @Test
        @DisplayName("GAD7 属性正确")
        void gad7() {
            assertEquals("广泛性焦虑量表-7", ScaleType.GAD7.getDisplayName());
            assertEquals(7, ScaleType.GAD7.getQuestionCount());
        }

        @Test
        @DisplayName("PSS10 属性正确")
        void pss10() {
            assertEquals("压力知觉量表-10", ScaleType.PSS10.getDisplayName());
            assertEquals(10, ScaleType.PSS10.getQuestionCount());
        }

        @Test
        @DisplayName("valueOf 正确")
        void valueOf() {
            assertEquals(ScaleType.PHQ9, ScaleType.valueOf("PHQ9"));
            assertEquals(ScaleType.GAD7, ScaleType.valueOf("GAD7"));
            assertEquals(ScaleType.PSS10, ScaleType.valueOf("PSS10"));
        }
    }
}
