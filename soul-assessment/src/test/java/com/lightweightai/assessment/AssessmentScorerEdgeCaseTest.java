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

@DisplayName("AssessmentScorer - Edge Cases")
class AssessmentScorerEdgeCaseTest {

    private AssessmentScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new AssessmentScorer();
    }

    // ==================== Empty answers ====================

    @Nested
    @DisplayName("Empty answer lists")
    class EmptyAnswers {

        @Test
        @DisplayName("PHQ-9 with empty answers returns 0 (stream sum of empty)")
        void phq9EmptyAnswersReturnsZero() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
            assertEquals(0, scorer.score(sub));
        }

        @Test
        @DisplayName("GAD-7 with empty answers returns 0 (stream sum of empty)")
        void gad7EmptyAnswersReturnsZero() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7, Collections.emptyList());
            assertEquals(0, scorer.score(sub));
        }

        @Test
        @DisplayName("PSS-10 with empty answers returns 0 (loop does not execute)")
        void pss10EmptyAnswersReturnsZero() {
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10, Collections.emptyList());
            assertEquals(0, scorer.score(sub));
        }
    }

    // ==================== Fewer items than expected ====================

    @Nested
    @DisplayName("Fewer items than scale expects")
    class FewerItems {

        @Test
        @DisplayName("PHQ-9 with single answer still sums correctly")
        void phq9SingleAnswerSumsCorrectly() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, List.of(2));
            assertEquals(2, scorer.score(sub));
        }

        @Test
        @DisplayName("PHQ-9 with 3 answers (fewer than 9) still works")
        void phq9ThreeAnswersWorks() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, List.of(1, 2, 3));
            assertEquals(6, scorer.score(sub));
        }

        @Test
        @DisplayName("GAD-7 with single answer still sums correctly")
        void gad7SingleAnswerSumsCorrectly() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7, List.of(3));
            assertEquals(3, scorer.score(sub));
        }

        @Test
        @DisplayName("PSS-10 with 3 items (fewer than 10) processes only those items")
        void pss10ThreeItemsProcessesAvailable() {
            // Items 0,1,2 are all normal (not reversed), so values are added directly
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10, List.of(2, 3, 1));
            assertEquals(6, scorer.score(sub));
        }

        @Test
        @DisplayName("PSS-10 with 5 items includes some reversed items (3,4)")
        void pss10FiveItemsIncludesSomeReversed() {
            // Items 0,1,2 normal; items 3,4 reversed
            // Normal: 1+2+3 = 6
            // Reversed: (4-2)+(4-1) = 2+3 = 5
            // Total: 11
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10, List.of(1, 2, 3, 2, 1));
            assertEquals(11, scorer.score(sub));
        }
    }

    // ==================== More items than expected ====================

    @Nested
    @DisplayName("More items than scale expects")
    class MoreItems {

        @Test
        @DisplayName("PSS-10 with 12 items processes all (no bounds check)")
        void pss10TwelveItemsProcessesAll() {
            // REVERSED_ITEMS = {3, 4, 6, 7}
            // Items 0-9 (normal PSS-10) + items 10,11 (extra, not in REVERSED_ITEMS)
            // Normal items (0,1,2,5,8,9,10,11): 1*8 = 8
            // Reversed items (3,4,6,7): (4-1)*4 = 12
            // Total: 20
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10,
                List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
            assertEquals(20, scorer.score(sub));
        }

        @Test
        @DisplayName("PHQ-9 with 12 answers sums all (no validation of count)")
        void phq9TwelveAnswersSumsAll() {
            // All values = 2, 12 items -> sum = 24
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9,
                List.of(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2));
            assertEquals(24, scorer.score(sub));
        }
    }

    // ==================== Values exceeding normal range ====================

    @Nested
    @DisplayName("Answers exceeding normal scale range (no validation)")
    class OutOfRangeValues {

        @Test
        @DisplayName("PHQ-9 with values > 3 (max should be 3) sums them anyway")
        void phq9ValuesExceedingMaxRange() {
            // Normal PHQ-9 range is 0-3, but no validation exists
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9,
                List.of(5, 5, 5, 5, 5, 5, 5, 5, 5));
            assertEquals(45, scorer.score(sub));
        }

        @Test
        @DisplayName("GAD-7 with value of 10 (max should be 3) sums it anyway")
        void gad7ValueExceedingMaxRange() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7,
                List.of(10, 0, 0, 0, 0, 0, 0));
            assertEquals(10, scorer.score(sub));
        }

        @Test
        @DisplayName("PSS-10 with values > 4 (max should be 4) produces negative reversed scores")
        void pss10ValuesExceedingMaxProducesNegativeReversed() {
            // Reversed items use (4 - raw), so raw=5 gives 4-5 = -1
            // Item 3 is reversed: (4-5) = -1
            // All other items at 0 except item 3 at value 5
            // Normal items: all 0
            // Reversed items: item 3 = (4-5) = -1, items 4,6,7 = (4-0) = 4 each -> 12
            // Total: -1 + 12 = 11
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10,
                List.of(0, 0, 0, 5, 0, 0, 0, 0, 0, 0));
            assertEquals(11, scorer.score(sub));
        }
    }

    // ==================== Negative values ====================

    @Nested
    @DisplayName("Negative answer values (no validation)")
    class NegativeValues {

        @Test
        @DisplayName("PHQ-9 with negative values produces negative score")
        void phq9NegativeValuesProducesNegativeScore() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9,
                List.of(-1, -2, -3, 0, 0, 0, 0, 0, 0));
            assertEquals(-6, scorer.score(sub));
        }

        @Test
        @DisplayName("GAD-7 with all negative values produces negative sum")
        void gad7AllNegativeValues() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7,
                List.of(-1, -1, -1, -1, -1, -1, -1));
            assertEquals(-7, scorer.score(sub));
        }

        @Test
        @DisplayName("PSS-10 with negative values on reversed items produces inflated reversed scores")
        void pss10NegativeOnReversedItemsInflatesScore() {
            // Item 3 is reversed: (4 - (-2)) = 6
            // All items = -2
            // Normal items (0,1,2,5,8,9): -2 * 6 = -12
            // Reversed items (3,4,6,7): (4-(-2)) * 4 = 6 * 4 = 24
            // Total: -12 + 24 = 12
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10,
                List.of(-2, -2, -2, -2, -2, -2, -2, -2, -2, -2));
            assertEquals(12, scorer.score(sub));
        }
    }

    // ==================== Severity boundary tests ====================

    @Nested
    @DisplayName("Severity boundaries - exact transition points")
    class SeverityBoundaries {

        // PHQ-9 boundaries: <=4 none, <=9 mild, <=14 moderate, <=19 mod-severe, >=20 severe

        @Test
        @DisplayName("PHQ-9: score 4 is 'none', score 5 transitions to 'mild'")
        void phq9BoundaryNoneToMild() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
            assertEquals("无抑郁", scorer.severity(sub, 4));
            assertEquals("轻度抑郁", scorer.severity(sub, 5));
        }

        @Test
        @DisplayName("PHQ-9: score 9 is 'mild', score 10 transitions to 'moderate'")
        void phq9BoundaryMildToModerate() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
            assertEquals("轻度抑郁", scorer.severity(sub, 9));
            assertEquals("中度抑郁", scorer.severity(sub, 10));
        }

        @Test
        @DisplayName("PHQ-9: score 14 is 'moderate', score 15 transitions to 'mod-severe'")
        void phq9BoundaryModerateToModSevere() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
            assertEquals("中度抑郁", scorer.severity(sub, 14));
            assertEquals("中重度抑郁", scorer.severity(sub, 15));
        }

        @Test
        @DisplayName("PHQ-9: score 19 is 'mod-severe', score 20 transitions to 'severe'")
        void phq9BoundaryModSevereToSevere() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
            assertEquals("中重度抑郁", scorer.severity(sub, 19));
            assertEquals("重度抑郁", scorer.severity(sub, 20));
        }

        // GAD-7 boundaries: <=4 none, <=9 mild, <=14 moderate, >=15 severe

        @Test
        @DisplayName("GAD-7: score 4 is 'none', score 5 transitions to 'mild'")
        void gad7BoundaryNoneToMild() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7, Collections.emptyList());
            assertEquals("无焦虑", scorer.severity(sub, 4));
            assertEquals("轻度焦虑", scorer.severity(sub, 5));
        }

        @Test
        @DisplayName("GAD-7: score 9 is 'mild', score 10 transitions to 'moderate'")
        void gad7BoundaryMildToModerate() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7, Collections.emptyList());
            assertEquals("轻度焦虑", scorer.severity(sub, 9));
            assertEquals("中度焦虑", scorer.severity(sub, 10));
        }

        @Test
        @DisplayName("GAD-7: score 14 is 'moderate', score 15 transitions to 'severe'")
        void gad7BoundaryModerateToSevere() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7, Collections.emptyList());
            assertEquals("中度焦虑", scorer.severity(sub, 14));
            assertEquals("重度焦虑", scorer.severity(sub, 15));
        }

        // PSS-10 boundaries: <=13 low, <=26 moderate, >=27 high

        @Test
        @DisplayName("PSS-10: score 13 is 'low', score 14 transitions to 'moderate'")
        void pss10BoundaryLowToModerate() {
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10, Collections.emptyList());
            assertEquals("低压力", scorer.severity(sub, 13));
            assertEquals("中等压力", scorer.severity(sub, 14));
        }

        @Test
        @DisplayName("PSS-10: score 26 is 'moderate', score 27 transitions to 'high'")
        void pss10BoundaryModerateToHigh() {
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10, Collections.emptyList());
            assertEquals("中等压力", scorer.severity(sub, 26));
            assertEquals("高压力", scorer.severity(sub, 27));
        }

        // Edge: score 0 and maximum possible scores

        @Test
        @DisplayName("All scales: score 0 returns lowest severity")
        void allScalesScoreZeroIsLowest() {
            assertEquals("无抑郁", scorer.severity(
                new AssessmentSubmission("u", ScaleType.PHQ9, Collections.emptyList()), 0));
            assertEquals("无焦虑", scorer.severity(
                new AssessmentSubmission("u", ScaleType.GAD7, Collections.emptyList()), 0));
            assertEquals("低压力", scorer.severity(
                new AssessmentSubmission("u", ScaleType.PSS10, Collections.emptyList()), 0));
        }

        @Test
        @DisplayName("PHQ-9: maximum possible score (27) is 'severe'")
        void phq9MaxScoreIsSevere() {
            var sub = new AssessmentSubmission("user1", ScaleType.PHQ9, Collections.emptyList());
            assertEquals("重度抑郁", scorer.severity(sub, 27));
        }

        @Test
        @DisplayName("GAD-7: maximum possible score (21) is 'severe'")
        void gad7MaxScoreIsSevere() {
            var sub = new AssessmentSubmission("user1", ScaleType.GAD7, Collections.emptyList());
            assertEquals("重度焦虑", scorer.severity(sub, 21));
        }

        @Test
        @DisplayName("PSS-10: maximum possible score (40) is 'high'")
        void pss10MaxScoreIsHigh() {
            var sub = new AssessmentSubmission("user1", ScaleType.PSS10, Collections.emptyList());
            assertEquals("高压力", scorer.severity(sub, 40));
        }
    }
}
