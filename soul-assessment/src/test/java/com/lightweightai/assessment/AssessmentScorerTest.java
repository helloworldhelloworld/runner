package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AssessmentScorer - 评分计算")
class AssessmentScorerTest {

    private AssessmentScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new AssessmentScorer();
    }

    // ==================== PHQ-9 评分 ====================

    @Test
    @DisplayName("PHQ-9: 全零答案得0分")
    void phq9AllZerosShouldScoreZero() {
        var sub = new AssessmentSubmission("user1", ScaleType.PHQ9,
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(0, scorer.score(sub));
    }

    @Test
    @DisplayName("PHQ-9: 全满分答案得27分")
    void phq9AllMaxShouldScore27() {
        var sub = new AssessmentSubmission("user1", ScaleType.PHQ9,
            List.of(3, 3, 3, 3, 3, 3, 3, 3, 3));
        assertEquals(27, scorer.score(sub));
    }

    @Test
    @DisplayName("PHQ-9: 混合答案求和")
    void phq9MixedAnswers() {
        var sub = new AssessmentSubmission("user1", ScaleType.PHQ9,
            List.of(1, 2, 0, 3, 1, 0, 2, 1, 0));
        assertEquals(10, scorer.score(sub));
    }

    // ==================== GAD-7 评分 ====================

    @Test
    @DisplayName("GAD-7: 全零答案得0分")
    void gad7AllZerosShouldScoreZero() {
        var sub = new AssessmentSubmission("user1", ScaleType.GAD7,
            List.of(0, 0, 0, 0, 0, 0, 0));
        assertEquals(0, scorer.score(sub));
    }

    @Test
    @DisplayName("GAD-7: 全满分答案得21分")
    void gad7AllMaxShouldScore21() {
        var sub = new AssessmentSubmission("user1", ScaleType.GAD7,
            List.of(3, 3, 3, 3, 3, 3, 3));
        assertEquals(21, scorer.score(sub));
    }

    @Test
    @DisplayName("GAD-7: 混合答案求和")
    void gad7MixedAnswers() {
        var sub = new AssessmentSubmission("user1", ScaleType.GAD7,
            List.of(1, 0, 2, 1, 3, 0, 1));
        assertEquals(8, scorer.score(sub));
    }

    // ==================== PSS-10 反向计分 ====================

    @Test
    @DisplayName("PSS-10: 全零答案（反向项得4分）得16分")
    void pss10AllZerosShouldApplyReverse() {
        // Items 3,4,6,7 are reversed: (4-0)=4 each → 4*4=16, others: 0*6=0
        var sub = new AssessmentSubmission("user1", ScaleType.PSS10,
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(16, scorer.score(sub));
    }

    @Test
    @DisplayName("PSS-10: 全满分答案（反向项得0分）得24分")
    void pss10AllMaxShouldApplyReverse() {
        // Items 3,4,6,7 reversed: (4-4)=0 each → 0, others: 4*6=24
        var sub = new AssessmentSubmission("user1", ScaleType.PSS10,
            List.of(4, 4, 4, 4, 4, 4, 4, 4, 4, 4));
        assertEquals(24, scorer.score(sub));
    }

    @Test
    @DisplayName("PSS-10: 反向计分项正确反转")
    void pss10ReverseItemsCorrectlyReversed() {
        // All answers = 1
        // Normal items (0,1,2,5,8,9): 1 each → 6
        // Reversed items (3,4,6,7): (4-1)=3 each → 12
        // Total: 18
        var sub = new AssessmentSubmission("user1", ScaleType.PSS10,
            List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
        assertEquals(18, scorer.score(sub));
    }

    @Test
    @DisplayName("PSS-10: 仅反向项有值")
    void pss10OnlyReversedItemsHaveValue() {
        // Answers: all 0 except items 3,4,6,7 = 2
        // Normal items: 0*6=0
        // Reversed: (4-2)*4 = 8
        // Plus normal zeros' reversed items: wait, let me be precise
        // index: 0=0, 1=0, 2=0, 3=2, 4=2, 5=0, 6=2, 7=2, 8=0, 9=0
        // Normal(0,1,2,5,8,9): 0+0+0+0+0+0 = 0
        // Reversed(3,4,6,7): (4-2)+(4-2)+(4-2)+(4-2) = 8
        var sub = new AssessmentSubmission("user1", ScaleType.PSS10,
            List.of(0, 0, 0, 2, 2, 0, 2, 2, 0, 0));
        assertEquals(8, scorer.score(sub));
    }

    // ==================== 严重程度判定 ====================

    @Test
    @DisplayName("PHQ-9: 得分4应为'无抑郁'")
    void phq9Severity4ShouldBeNone() {
        var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
        assertEquals("无抑郁", scorer.severity(sub, 4));
    }

    @Test
    @DisplayName("PHQ-9: 得分5应为'轻度抑郁'")
    void phq9Severity5ShouldBeMild() {
        var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
        assertEquals("轻度抑郁", scorer.severity(sub, 5));
    }

    @Test
    @DisplayName("PHQ-9: 得分10应为'中度抑郁'")
    void phq9Severity10ShouldBeModerate() {
        var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
        assertEquals("中度抑郁", scorer.severity(sub, 10));
    }

    @Test
    @DisplayName("PHQ-9: 得分15应为'中重度抑郁'")
    void phq9Severity15ShouldBeModSevere() {
        var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
        assertEquals("中重度抑郁", scorer.severity(sub, 15));
    }

    @Test
    @DisplayName("PHQ-9: 得分20应为'重度抑郁'")
    void phq9Severity20ShouldBeSevere() {
        var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
        assertEquals("重度抑郁", scorer.severity(sub, 20));
    }

    @Test
    @DisplayName("GAD-7: 得分4应为'无焦虑'")
    void gad7Severity4ShouldBeNone() {
        var sub = new AssessmentSubmission("user1", ScaleType.GAD7, Collections.emptyList());
        assertEquals("无焦虑", scorer.severity(sub, 4));
    }

    @Test
    @DisplayName("GAD-7: 得分7应为'轻度焦虑'")
    void gad7Severity7ShouldBeMild() {
        var sub = new AssessmentSubmission("user1", ScaleType.GAD7, Collections.emptyList());
        assertEquals("轻度焦虑", scorer.severity(sub, 7));
    }

    @Test
    @DisplayName("GAD-7: 得分15应为'重度焦虑'")
    void gad7Severity15ShouldBeSevere() {
        var sub = new AssessmentSubmission("user1", ScaleType.GAD7, Collections.emptyList());
        assertEquals("重度焦虑", scorer.severity(sub, 15));
    }

    @Test
    @DisplayName("PSS-10: 得分13应为'低压力'")
    void pss10Severity13ShouldBeLow() {
        var sub = new AssessmentSubmission("user1", ScaleType.PSS10, Collections.emptyList());
        assertEquals("低压力", scorer.severity(sub, 13));
    }

    @Test
    @DisplayName("PSS-10: 得分20应为'中等压力'")
    void pss10Severity20ShouldBeMedium() {
        var sub = new AssessmentSubmission("user1", ScaleType.PSS10, Collections.emptyList());
        assertEquals("中等压力", scorer.severity(sub, 20));
    }

    @Test
    @DisplayName("PSS-10: 得分27应为'高压力'")
    void pss10Severity27ShouldBeHigh() {
        var sub = new AssessmentSubmission("user1", ScaleType.PSS10, Collections.emptyList());
        assertEquals("高压力", scorer.severity(sub, 27));
    }
}
