package com.lightweightai.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentFilter - Edge Cases for Content Safety Paths")
class ContentFilterEdgeCaseTest {

    private ContentFilter filter;

    private static final String SOFT_REPLACEMENT =
        "你描述的这些感受值得被认真对待。我建议你考虑和专业的心理咨询师或医生聊聊，他们能给你更专业的支持。";

    @BeforeEach
    void setUp() {
        filter = new ContentFilter();
    }

    // ==================== Multiple different diagnostic patterns ====================

    @Test
    @DisplayName("Response with multiple different pattern types all get replaced")
    void shouldFilterMultipleDifferentPatternTypes() {
        String response = "你患有抑郁症。根据你的描述，我判断为中度焦虑。建议立即就医。";
        String filtered = filter.filter(response);

        assertFalse(filtered.contains("你患有抑郁症"),
            "First pattern type (diagnosis) should be filtered");
        assertFalse(filtered.contains("判断为"),
            "Second pattern type (based-on-description) should be filtered");
        assertFalse(filtered.contains("建议立即就医"),
            "Third pattern type (urgent medical advice) should be filtered");
        assertTrue(filtered.contains(SOFT_REPLACEMENT),
            "Replacement text should be present");
    }

    @Test
    @DisplayName("All four pattern categories can be triggered in single response")
    void shouldFilterAllFourPatternCategories() {
        // Pattern 1: 你(患有|得了|有|是)(condition)
        // Pattern 2: 根据你的描述.{0,20}(诊断|判断)为
        // Pattern 3: 这是(condition)的(症状|表现|信号)
        // Pattern 4: 建议(立即|马上|尽快)(action)
        String response = "你患有焦虑症。根据你的描述可以诊断为重度。这是焦虑的表现。建议马上看医生。";
        String filtered = filter.filter(response);

        assertFalse(filtered.contains("你患有焦虑症"));
        assertFalse(filtered.contains("诊断为"));
        assertFalse(filtered.contains("这是焦虑的表现"));
        assertFalse(filtered.contains("建议马上看医生"));
    }

    // ==================== Same pattern appearing twice ====================

    @Test
    @DisplayName("Same pattern appearing twice in text - both occurrences replaced")
    void shouldFilterDuplicatePatternOccurrences() {
        String response = "你患有抑郁症，而且你患有焦虑症。";
        String filtered = filter.filter(response);

        assertFalse(filtered.contains("你患有抑郁症"),
            "First occurrence should be replaced");
        assertFalse(filtered.contains("你患有焦虑症"),
            "Second occurrence should be replaced");
    }

    @Test
    @DisplayName("Pattern '建议立即就医' appearing twice - both instances replaced")
    void shouldFilterRepeatedUrgentAdvice() {
        String response = "建议立即就医，真的建议立即就医。";
        String filtered = filter.filter(response);

        // After replaceAll, neither instance should remain
        assertFalse(filtered.contains("建议立即就医"));
        // The replacement text should appear (at least once)
        assertTrue(filtered.contains(SOFT_REPLACEMENT));
    }

    @Test
    @DisplayName("Exact same diagnostic sentence repeated verbatim")
    void shouldFilterExactDuplicateSentences() {
        String response = "你得了双相。你得了双相。";
        String filtered = filter.filter(response);

        assertFalse(filtered.contains("你得了双相"));
    }

    // ==================== Pattern at end of very long response ====================

    @Test
    @DisplayName("Diagnostic pattern at end of very long response is still caught")
    void shouldFilterPatternAtEndOfLongResponse() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("我理解你的感受，这种情况下产生这样的情绪是可以理解的。");
        }
        sb.append("你患有抑郁症。");

        String filtered = filter.filter(sb.toString());

        assertFalse(filtered.contains("你患有抑郁症"),
            "Pattern at end of long response should be filtered");
        assertTrue(filtered.contains(SOFT_REPLACEMENT));
    }

    @Test
    @DisplayName("Diagnostic pattern at beginning of very long response is caught")
    void shouldFilterPatternAtBeginningOfLongResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("你得了焦虑症，");
        for (int i = 0; i < 500; i++) {
            sb.append("但是不用太担心，让我们一起来看看可以做些什么。");
        }

        String filtered = filter.filter(sb.toString());

        assertFalse(filtered.contains("你得了焦虑症"),
            "Pattern at beginning of long response should be filtered");
    }

    @Test
    @DisplayName("Performance: very long response with no patterns returns quickly")
    void shouldReturnLongSafeResponseEfficiently() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("这是完全安全的回复内容，没有任何诊断性语言。");
        }
        String response = sb.toString();

        long start = System.nanoTime();
        String filtered = filter.filter(response);
        long elapsed = System.nanoTime() - start;

        assertEquals(response, filtered, "Safe content should be unchanged");
        assertTrue(elapsed < 1_000_000_000L,
            "Should process within 1 second, took " + elapsed / 1_000_000 + "ms");
    }

    // ==================== Almost matches but doesn't (boundary tests) ====================

    @Test
    @DisplayName("'你有一只猫' - '你有' without a condition does not trigger")
    void shouldNotFilterYouHaveWithoutCondition() {
        String response = "你有一只猫，看起来你很开心。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "'你有' must be followed by a specific condition to trigger");
    }

    @Test
    @DisplayName("'你是个好人' - '你是' without a condition does not trigger")
    void shouldNotFilterYouAreWithoutCondition() {
        String response = "你是个好人，你值得被好好对待。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "'你是' must be followed by a specific condition like PTSD to trigger");
    }

    @Test
    @DisplayName("'建议你多休息' - '建议' without urgency prefix does not trigger")
    void shouldNotFilterNonUrgentAdvice() {
        String response = "建议你多休息，好好照顾自己。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "'建议' without '立即/马上/尽快' should not trigger");
    }

    @Test
    @DisplayName("'根据你的描述来看' without '诊断/判断为' does not trigger")
    void shouldNotFilterDescriptionWithoutDiagnosis() {
        String response = "根据你的描述来看，你可能需要更多休息时间。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "Pattern requires '诊断为' or '判断为' after the description prefix");
    }

    @Test
    @DisplayName("'这是正常的反应' does not trigger - '正常' is not in condition list")
    void shouldNotFilterNormalReaction() {
        String response = "这是正常的反应，不用担心。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "Pattern only matches specific conditions like 抑郁/焦虑/双相/躁郁/精神疾病");
    }

    @Test
    @DisplayName("'建议适当就医' - '适当' is not in urgency list, should not trigger")
    void shouldNotFilterModerateAdvice() {
        String response = "建议适当就医，如果方便的话可以去看看。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "Only '立即/马上/尽快' trigger the urgent advice pattern");
    }

    @Test
    @DisplayName("'根据你的描述' with too many chars before '诊断为' does not match")
    void shouldNotMatchWhenGapTooLong() {
        // Pattern is: 根据你的描述.{0,20}(诊断|判断)为
        // So more than 20 characters between should not match
        String response = "根据你的描述，以及综合考虑了你的年龄、性别、生活环境等各方面因素之后，我判断为需要休息。";
        String filtered = filter.filter(response);

        // Count chars between "描述" and "判断为": "，以及综合考虑了你的年龄、性别、生活环境等各方面因素之后，我" is more than 20
        // The gap is "，以及综合考虑了你的年龄、性别、生活环境等各方面因素之后，我" which is clearly >20 chars
        assertEquals(response, filtered,
            "Gap of more than 20 chars between '描述' and '诊断/判断为' should not trigger");
    }

    // ==================== Chinese text with partial patterns ====================

    @Test
    @DisplayName("'你有很多朋友' - '你有' + non-condition word does not trigger")
    void shouldNotFilterPartialPatternWithFriends() {
        String response = "你有很多朋友关心你，这是好事。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered);
    }

    @Test
    @DisplayName("'这是一种常见的情绪' - '这是' + '情绪' does not match condition list")
    void shouldNotFilterCommonEmotion() {
        String response = "这是一种常见的情绪，很多人都会经历。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "'情绪' is not in the condition list (抑郁/焦虑/双相/躁郁/精神疾病)");
    }

    @Test
    @DisplayName("'患有' without '你' prefix does not trigger")
    void shouldNotFilterDiagnosisWithoutYouPrefix() {
        String response = "很多人患有抑郁症，这是一种常见的心理健康问题。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "Pattern requires '你' prefix before '患有/得了/有/是'");
    }

    @Test
    @DisplayName("'你的抑郁情绪' - partial keyword in different grammatical structure does not trigger")
    void shouldNotFilterPartialKeywordInDifferentStructure() {
        String response = "你的抑郁情绪是可以被理解的，很多人在类似情况下都会有这种感受。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "Pattern requires '你(患有|得了|有|是)' not '你的' + condition");
    }

    @Test
    @DisplayName("'建议去看医生' without urgency word does not trigger")
    void shouldNotFilterCasualMedicalSuggestion() {
        String response = "建议去看医生了解一下情况，不用太紧张。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "Missing urgency prefix '立即/马上/尽快' means pattern does not match");
    }

    @Test
    @DisplayName("Response mentioning '焦虑' in non-diagnostic context is safe")
    void shouldNotFilterAnxietyMentionInContext() {
        String response = "感到焦虑是人之常情，特别是在压力大的时候。让我们来聊聊你最近的生活。";
        String filtered = filter.filter(response);
        assertEquals(response, filtered,
            "Mentioning a condition word without the diagnostic sentence structure should not trigger");
    }

    // ==================== Replacement text integrity ====================

    @Test
    @DisplayName("Surrounding text is preserved after filtering")
    void shouldPreserveSurroundingText() {
        String prefix = "让我先理解一下你的情况。";
        String diagnostic = "你患有抑郁症。";
        String suffix = "不过请放心，我们可以一起面对。";
        String response = prefix + diagnostic + suffix;

        String filtered = filter.filter(response);

        assertTrue(filtered.contains(prefix), "Prefix text should be preserved");
        assertTrue(filtered.contains(suffix), "Suffix text should be preserved");
        assertFalse(filtered.contains("你患有抑郁症"), "Diagnostic text should be removed");
        assertTrue(filtered.contains(SOFT_REPLACEMENT), "Replacement should be inserted");
    }

    @Test
    @DisplayName("Filter is idempotent - filtering an already-filtered response has no further effect")
    void shouldBeIdempotent() {
        String response = "你患有焦虑症。";
        String firstFilter = filter.filter(response);
        String secondFilter = filter.filter(firstFilter);

        assertEquals(firstFilter, secondFilter,
            "Applying filter twice should produce the same result as applying once");
    }
}
