package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssessmentScorer 边界条件测试
 *
 * 覆盖：
 * - 空答案列表
 * - 单答案
 * - 边界严重程度值（恰好在阈值上）
 * - PHQ-9/GAD-7/PSS-10 严重程度全覆盖
 * - PSS-10 反向计分边界
 * - 最大/最小得分的严重程度
 */
@DisplayName("AssessmentScorer - 边界条件")
class AssessmentScorerEdgeCaseTest {

    private AssessmentScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new AssessmentScorer();
    }

    // ==================== 空/单答案 ====================

    @Nested
    @DisplayName("空答案与单答案")
    class EmptyAndSingleAnswer {

        @Test
        @DisplayName("PHQ-9 空答案得0分")
        void phq9EmptyAnswersShouldScoreZero() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, List.of());
            assertEquals(0, scorer.score(sub));
        }

        @Test
        @DisplayName("GAD-7 空答案得0分")
        void gad7EmptyAnswersShouldScoreZero() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7, List.of());
            assertEquals(0, scorer.score(sub));
        }

        @Test
        @DisplayName("PSS-10 空答案得0分")
        void pss10EmptyAnswersShouldScoreZero() {
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10, List.of());
            assertEquals(0, scorer.score(sub));
        }

        @Test
        @DisplayName("PHQ-9 单答案正确求和")
        void phq9SingleAnswerShouldSum() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, List.of(2));
            assertEquals(2, scorer.score(sub));
        }

        @Test
        @DisplayName("GAD-7 单答案正确求和")
        void gad7SingleAnswerShouldSum() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7, List.of(3));
            assertEquals(3, scorer.score(sub));
        }
    }

    // ==================== PHQ-9 严重程度边界 ====================

    @Nested
    @DisplayName("PHQ-9 严重程度边界")
    class Phq9SeverityBoundary {

        @Test
        @DisplayName("得分0为无抑郁")
        void score0ShouldBeNone() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("无抑郁", scorer.severity(sub, 0));
        }

        @Test
        @DisplayName("得分4为无抑郁（阈值下界）")
        void score4ShouldBeNone() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("无抑郁", scorer.severity(sub, 4));
        }

        @Test
        @DisplayName("得分5为轻度抑郁（阈值下界）")
        void score5ShouldBeMild() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("轻度抑郁", scorer.severity(sub, 5));
        }

        @Test
        @DisplayName("得分9为轻度抑郁（阈值上界）")
        void score9ShouldBeMild() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("轻度抑郁", scorer.severity(sub, 9));
        }

        @Test
        @DisplayName("得分10为中度抑郁（阈值下界）")
        void score10ShouldBeModerate() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("中度抑郁", scorer.severity(sub, 10));
        }

        @Test
        @DisplayName("得分14为中度抑郁（阈值上界）")
        void score14ShouldBeModerate() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("中度抑郁", scorer.severity(sub, 14));
        }

        @Test
        @DisplayName("得分15为中重度抑郁（阈值下界）")
        void score15ShouldBeModSevere() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("中重度抑郁", scorer.severity(sub, 15));
        }

        @Test
        @DisplayName("得分19为中重度抑郁（阈值上界）")
        void score19ShouldBeModSevere() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("中重度抑郁", scorer.severity(sub, 19));
        }

        @Test
        @DisplayName("得分20为重度抑郁（阈值下界）")
        void score20ShouldBeSevere() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("重度抑郁", scorer.severity(sub, 20));
        }

        @Test
        @DisplayName("得分27为重度抑郁（最大值）")
        void score27ShouldBeSevere() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9, List.of());
            assertEquals("重度抑郁", scorer.severity(sub, 27));
        }
    }

    // ==================== GAD-7 严重程度边界 ====================

    @Nested
    @DisplayName("GAD-7 严重程度边界")
    class Gad7SeverityBoundary {

        @Test
        @DisplayName("得分0为无焦虑")
        void score0ShouldBeNone() {
            var sub = new AssessmentSubmission("u", ScaleType.GAD7, List.of());
            assertEquals("无焦虑", scorer.severity(sub, 0));
        }

        @Test
        @DisplayName("得分4为无焦虑（阈值上界）")
        void score4ShouldBeNone() {
            var sub = new AssessmentSubmission("u", ScaleType.GAD7, List.of());
            assertEquals("无焦虑", scorer.severity(sub, 4));
        }

        @Test
        @DisplayName("得分5为轻度焦虑")
        void score5ShouldBeMild() {
            var sub = new AssessmentSubmission("u", ScaleType.GAD7, List.of());
            assertEquals("轻度焦虑", scorer.severity(sub, 5));
        }

        @Test
        @DisplayName("得分9为轻度焦虑（阈值上界）")
        void score9ShouldBeMild() {
            var sub = new AssessmentSubmission("u", ScaleType.GAD7, List.of());
            assertEquals("轻度焦虑", scorer.severity(sub, 9));
        }

        @Test
        @DisplayName("得分10为中度焦虑")
        void score10ShouldBeModerate() {
            var sub = new AssessmentSubmission("u", ScaleType.GAD7, List.of());
            assertEquals("中度焦虑", scorer.severity(sub, 10));
        }

        @Test
        @DisplayName("得分14为中度焦虑（阈值上界）")
        void score14ShouldBeModerate() {
            var sub = new AssessmentSubmission("u", ScaleType.GAD7, List.of());
            assertEquals("中度焦虑", scorer.severity(sub, 14));
        }

        @Test
        @DisplayName("得分15为重度焦虑")
        void score15ShouldBeSevere() {
            var sub = new AssessmentSubmission("u", ScaleType.GAD7, List.of());
            assertEquals("重度焦虑", scorer.severity(sub, 15));
        }

        @Test
        @DisplayName("得分21为重度焦虑（最大值）")
        void score21ShouldBeSevere() {
            var sub = new AssessmentSubmission("u", ScaleType.GAD7, List.of());
            assertEquals("重度焦虑", scorer.severity(sub, 21));
        }
    }

    // ==================== PSS-10 严重程度边界 ====================

    @Nested
    @DisplayName("PSS-10 严重程度边界")
    class Pss10SeverityBoundary {

        @Test
        @DisplayName("得分0为低压力")
        void score0ShouldBeLow() {
            var sub = new AssessmentSubmission("u", ScaleType.PSS10, List.of());
            assertEquals("低压力", scorer.severity(sub, 0));
        }

        @Test
        @DisplayName("得分13为低压力（阈值上界）")
        void score13ShouldBeLow() {
            var sub = new AssessmentSubmission("u", ScaleType.PSS10, List.of());
            assertEquals("低压力", scorer.severity(sub, 13));
        }

        @Test
        @DisplayName("得分14为中等压力")
        void score14ShouldBeMedium() {
            var sub = new AssessmentSubmission("u", ScaleType.PSS10, List.of());
            assertEquals("中等压力", scorer.severity(sub, 14));
        }

        @Test
        @DisplayName("得分26为中等压力（阈值上界）")
        void score26ShouldBeMedium() {
            var sub = new AssessmentSubmission("u", ScaleType.PSS10, List.of());
            assertEquals("中等压力", scorer.severity(sub, 26));
        }

        @Test
        @DisplayName("得分27为高压力")
        void score27ShouldBeHigh() {
            var sub = new AssessmentSubmission("u", ScaleType.PSS10, List.of());
            assertEquals("高压力", scorer.severity(sub, 27));
        }

        @Test
        @DisplayName("得分40为高压力（最大值）")
        void score40ShouldBeHigh() {
            var sub = new AssessmentSubmission("u", ScaleType.PSS10, List.of());
            assertEquals("高压力", scorer.severity(sub, 40));
        }
    }

    // ==================== PSS-10 反向计分边界 ====================

    @Nested
    @DisplayName("PSS-10 反向计分边界")
    class Pss10ReverseScoring {

        @Test
        @DisplayName("反向项全为0时得最大反向分 (4*4=16)")
        void reversedAllZerosShouldMax() {
            // Indices 3,4,6,7 are reversed. All zeros.
            // Normal: 0+0+0+0+0+0 = 0
            // Reversed: (4-0)*4 = 16
            var sub = new AssessmentSubmission("u", ScaleType.PSS10,
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
            assertEquals(16, scorer.score(sub));
        }

        @Test
        @DisplayName("反向项全为4时得0反向分")
        void reversedAllFoursShouldBeZero() {
            // Indices 3,4,6,7 reversed = (4-4)*4 = 0
            // Normal: 4*6 = 24
            var sub = new AssessmentSubmission("u", ScaleType.PSS10,
                List.of(4, 4, 4, 4, 4, 4, 4, 4, 4, 4));
            assertEquals(24, scorer.score(sub));
        }

        @Test
        @DisplayName("反向项中间值2时正确计算")
        void reversedMiddleValueCorrectlyScored() {
            // Answers: normal items=0, reversed items=2
            // Normal: 0*6 = 0
            // Reversed: (4-2)*4 = 8
            var sub = new AssessmentSubmission("u", ScaleType.PSS10,
                List.of(0, 0, 0, 2, 2, 0, 2, 2, 0, 0));
            assertEquals(8, scorer.score(sub));
        }

        @Test
        @DisplayName("正常项和反向项分别有值")
        void mixedNormalAndReversedValues() {
            // Normal(0,1,2,5,8,9) = 3+2+1+2+1+3 = 12
            // Reversed(3,4,6,7) = (4-1)+(4-2)+(4-0)+(4-3) = 3+2+4+1 = 10
            // Total = 22
            var sub = new AssessmentSubmission("u", ScaleType.PSS10,
                List.of(3, 2, 1, 1, 2, 2, 0, 3, 1, 3));
            assertEquals(22, scorer.score(sub));
        }
    }

    // ==================== 计分与严重程度一致性 ====================

    @Nested
    @DisplayName("计分与严重程度一致性")
    class ScoreAndSeverityConsistency {

        @Test
        @DisplayName("PHQ-9 全3分 = 27分 = 重度抑郁")
        void phq9MaxScoreConsistency() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9,
                List.of(3, 3, 3, 3, 3, 3, 3, 3, 3));
            int score = scorer.score(sub);
            assertEquals(27, score);
            assertEquals("重度抑郁", scorer.severity(sub, score));
        }

        @Test
        @DisplayName("GAD-7 全3分 = 21分 = 重度焦虑")
        void gad7MaxScoreConsistency() {
            var sub = new AssessmentSubmission("u", ScaleType.GAD7,
                List.of(3, 3, 3, 3, 3, 3, 3));
            int score = scorer.score(sub);
            assertEquals(21, score);
            assertEquals("重度焦虑", scorer.severity(sub, score));
        }

        @Test
        @DisplayName("PHQ-9 全0分 = 0分 = 无抑郁")
        void phq9MinScoreConsistency() {
            var sub = new AssessmentSubmission("u", ScaleType.PHQ9,
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0));
            int score = scorer.score(sub);
            assertEquals(0, score);
            assertEquals("无抑郁", scorer.severity(sub, score));
        }
    }
}
