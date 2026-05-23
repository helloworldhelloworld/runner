package com.lightweightai.assessment.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Assessment 模型类测试")
class AssessmentModelTest {

    @Nested
    @DisplayName("AssessmentResult")
    class AssessmentResultTests {

        @Test
        @DisplayName("全参构造后所有字段正确")
        void fullConstruction() {
            AssessmentResult r = new AssessmentResult(
                    "r-1", "u-1", ScaleType.PHQ9, 15, "中重度抑郁", "建议就医", 1700000000L);

            assertEquals("r-1", r.getId());
            assertEquals("u-1", r.getUserId());
            assertEquals(ScaleType.PHQ9, r.getScaleType());
            assertEquals(15, r.getTotalScore());
            assertEquals("中重度抑郁", r.getSeverity());
            assertEquals("建议就医", r.getAiReport());
            assertEquals(1700000000L, r.getCreatedAt());
        }

        @Test
        @DisplayName("无参构造 + setter 填充")
        void defaultConstructionWithSetters() {
            AssessmentResult r = new AssessmentResult();
            r.setId("r-2");
            r.setUserId("u-2");
            r.setScaleType(ScaleType.GAD7);
            r.setTotalScore(8);
            r.setSeverity("轻度焦虑");
            r.setAiReport("继续观察");
            r.setCreatedAt(1700000001L);

            assertEquals("r-2", r.getId());
            assertEquals("u-2", r.getUserId());
            assertEquals(ScaleType.GAD7, r.getScaleType());
            assertEquals(8, r.getTotalScore());
            assertEquals("轻度焦虑", r.getSeverity());
            assertEquals("继续观察", r.getAiReport());
            assertEquals(1700000001L, r.getCreatedAt());
        }

        @Test
        @DisplayName("各量表类型都可赋值")
        void allScaleTypes() {
            for (ScaleType type : ScaleType.values()) {
                AssessmentResult r = new AssessmentResult();
                r.setScaleType(type);
                assertEquals(type, r.getScaleType());
            }
        }
    }

    @Nested
    @DisplayName("AssessmentSubmission")
    class AssessmentSubmissionTests {

        @Test
        @DisplayName("record 构造字段正确")
        void recordConstruction() {
            List<Integer> answers = List.of(0, 1, 2, 3, 0, 1, 2, 3, 0);
            AssessmentSubmission sub = new AssessmentSubmission("u-1", ScaleType.PHQ9, answers);

            assertEquals("u-1", sub.userId());
            assertEquals(ScaleType.PHQ9, sub.scaleType());
            assertEquals(9, sub.answers().size());
            assertEquals(0, sub.answers().get(0));
            assertEquals(3, sub.answers().get(3));
        }

        @Test
        @DisplayName("答案列表不可变（来自 List.of）")
        void answersImmutable() {
            List<Integer> answers = List.of(1, 2, 3);
            AssessmentSubmission sub = new AssessmentSubmission("u-1", ScaleType.GAD7, answers);

            assertThrows(UnsupportedOperationException.class, () ->
                    sub.answers().add(99));
        }
    }

    @Nested
    @DisplayName("ScaleDefinition")
    class ScaleDefinitionTests {

        @Test
        @DisplayName("record 构造字段正确")
        void recordConstruction() {
            List<ScaleDefinition.Question> questions = List.of(
                    new ScaleDefinition.Question(0, "感到情绪低落？"),
                    new ScaleDefinition.Question(1, "对事物缺乏兴趣？")
            );
            List<String> options = List.of("从不", "偶尔", "经常", "总是");
            List<Integer> scores = List.of(0, 1, 2, 3);

            ScaleDefinition def = new ScaleDefinition(
                    ScaleType.PHQ9, "患者健康问卷-9", "评估抑郁程度",
                    questions, options, scores);

            assertEquals(ScaleType.PHQ9, def.type());
            assertEquals("患者健康问卷-9", def.title());
            assertEquals("评估抑郁程度", def.description());
            assertEquals(2, def.questions().size());
            assertEquals(0, def.questions().get(0).index());
            assertEquals("感到情绪低落？", def.questions().get(0).text());
            assertEquals(4, def.options().size());
            assertEquals(4, def.optionScores().size());
        }

        @Test
        @DisplayName("Question record 字段正确")
        void questionRecord() {
            ScaleDefinition.Question q = new ScaleDefinition.Question(5, "睡眠质量差？");

            assertEquals(5, q.index());
            assertEquals("睡眠质量差？", q.text());
        }
    }

    @Nested
    @DisplayName("ScaleType 枚举")
    class ScaleTypeTests {

        @Test
        @DisplayName("三种量表类型")
        void threeScaleTypes() {
            assertEquals(3, ScaleType.values().length);
        }

        @Test
        @DisplayName("PHQ9 显示名称和问题数")
        void phq9Properties() {
            assertEquals("患者健康问卷-9", ScaleType.PHQ9.getDisplayName());
            assertEquals(9, ScaleType.PHQ9.getQuestionCount());
        }

        @Test
        @DisplayName("GAD7 显示名称和问题数")
        void gad7Properties() {
            assertEquals("广泛性焦虑量表-7", ScaleType.GAD7.getDisplayName());
            assertEquals(7, ScaleType.GAD7.getQuestionCount());
        }

        @Test
        @DisplayName("PSS10 显示名称和问题数")
        void pss10Properties() {
            assertEquals("压力知觉量表-10", ScaleType.PSS10.getDisplayName());
            assertEquals(10, ScaleType.PSS10.getQuestionCount());
        }
    }
}
