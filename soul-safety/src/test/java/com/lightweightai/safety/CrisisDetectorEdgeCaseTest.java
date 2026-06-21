package com.lightweightai.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisDetector - Edge Cases for Critical Safety Paths")
class CrisisDetectorEdgeCaseTest {

    private CrisisDetector detector;

    @BeforeEach
    void setUp() {
        detector = new CrisisDetector();
    }

    // ==================== Pattern fragmentation detection ====================

    @Test
    @DisplayName("Pattern '自.*杀' catches '自 杀' with space between characters")
    void shouldMatchFragmentedSuicideWithSpace() {
        SafetyResult result = detector.check("自 杀");
        assertTrue(result.isCrisis(),
            "Pattern '自.*杀' should match characters separated by a space");
        assertTrue(result.matchedKeywords().contains("自.*杀"));
    }

    @Test
    @DisplayName("Pattern '自.*杀' catches '自——杀' with dash between characters")
    void shouldMatchFragmentedSuicideWithDash() {
        SafetyResult result = detector.check("想要自——杀");
        assertTrue(result.isCrisis(),
            "Pattern '自.*杀' should match characters separated by dashes");
    }

    @Test
    @DisplayName("Pattern '自.*杀' catches '自我了断杀身成仁' - long gap between characters")
    void shouldMatchPatternWithLongGap() {
        SafetyResult result = detector.check("自我了断杀身成仁");
        // This matches because '自' is followed eventually by '杀' with .* in between
        assertTrue(result.isCrisis(),
            "Pattern '自.*杀' matches any content between the two characters");
    }

    @Test
    @DisplayName("Messages with only whitespace between crisis characters still match")
    void shouldMatchWithMultipleWhitespaceBetween() {
        SafetyResult result = detector.check("自\t\t杀");
        assertTrue(result.isCrisis(),
            "Pattern '自.*杀' should match with tabs between characters");
    }

    @Test
    @DisplayName("Messages with newline between crisis characters still match")
    void shouldMatchWithNewlineBetween() {
        // Note: .* does not match newline by default, but \n is not in .
        // Actually checking the actual behavior - Pattern.compile("自.*杀") won't cross lines
        SafetyResult result = detector.check("自\n杀");
        // .* does NOT match \n by default (no DOTALL flag), so this should NOT trigger pattern
        // But "自杀" keyword exact match won't work either since there's a newline
        assertFalse(result.isCrisis(),
            "Pattern '自.*杀' without DOTALL does not cross newlines, and keyword '自杀' doesn't match '自\\n杀'");
    }

    // ==================== Pattern "不想.*活" variations ====================

    @Test
    @DisplayName("Pattern '不想.*活' matches '不想继续活下去'")
    void shouldMatchDontWantToContinueLiving() {
        SafetyResult result = detector.check("不想继续活下去");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("Pattern '不想.*活' matches '不想在这个世界上活'")
    void shouldMatchDontWantToLiveInWorld() {
        SafetyResult result = detector.check("不想在这个世界上活");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("Pattern '不想.*活' matches with many characters between")
    void shouldMatchDontWantToLiveWithLongMiddle() {
        SafetyResult result = detector.check("我真的不想在这里继续活着了");
        assertTrue(result.isCrisis(),
            "Pattern should match even with many characters between '不想' and '活'");
    }

    @Test
    @DisplayName("Pattern '活.*没.*意思' matches '活在世上没什么意思'")
    void shouldMatchLivingNoMeaning() {
        SafetyResult result = detector.check("活在世上没什么意思");
        assertTrue(result.isCrisis());
    }

    // ==================== Keywords embedded in long messages ====================

    @Test
    @DisplayName("Keyword embedded in middle of long message is detected")
    void shouldDetectKeywordInMiddleOfLongMessage() {
        String prefix = "今天早上起来之后去了公司，工作了一整天，回家之后感觉很累，";
        String suffix = "，然后吃了饭看了会电视就睡觉了。";
        String message = prefix + "突然觉得不想活了" + suffix;
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().contains("不想活了"));
    }

    @Test
    @DisplayName("Keyword at the very end of a long message is detected")
    void shouldDetectKeywordAtEndOfLongMessage() {
        String longPrefix = "我今天做了很多事情，早上去跑步，中午吃了午饭，下午开了三个会议，"
            + "晚上回家之后觉得特别疲惫，整个人状态非常不好，觉得自己很没用，一切都没有意义，"
            + "我真的";
        String message = longPrefix + "想死";
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().contains("想死"));
    }

    @Test
    @DisplayName("Keyword at the very beginning of a long message is detected")
    void shouldDetectKeywordAtBeginningOfLongMessage() {
        String longSuffix = "，我知道这样说不好，但是我最近压力真的很大，"
            + "工作上的事情让我喘不过气，人际关系也出了问题，"
            + "不知道该怎么办才好，希望有人能帮帮我。";
        String message = "自杀" + longSuffix;
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().contains("自杀"));
    }

    // ==================== Safe context - still triggers (no context awareness) ====================

    @Test
    @DisplayName("News discussion context still triggers - detector has no context awareness")
    void shouldTriggerEvenInNewsContext() {
        String message = "新闻报道说那个地方自杀率上升了";
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis(),
            "Detector uses keyword matching without context awareness - 'self-kill' keyword triggers regardless of context");
        assertTrue(result.matchedKeywords().contains("自杀"));
    }

    @Test
    @DisplayName("Academic discussion context still triggers")
    void shouldTriggerEvenInAcademicContext() {
        String message = "在这篇论文中，作者研究了自杀预防的方法";
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis(),
            "Detector matches keywords regardless of surrounding academic context");
    }

    @Test
    @DisplayName("Quoting someone else still triggers")
    void shouldTriggerEvenWhenQuotingOthers() {
        String message = "他跟我说他不想活了，我很担心他";
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis(),
            "Detector does not distinguish between self-reference and third-party quotes");
    }

    @Test
    @DisplayName("Historical discussion context still triggers")
    void shouldTriggerEvenInHistoricalContext() {
        String message = "日本武士有切腹自杀的传统文化";
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis(),
            "Detector matches 'self-kill' keyword even in historical/cultural discussion");
    }

    // ==================== Multiple patterns matching simultaneously ====================

    @Test
    @DisplayName("Message with multiple distinct patterns triggers with first pattern set found")
    void shouldMatchMultiplePatternsWhenNoKeywords() {
        // This message should NOT match any exact keyword but SHOULD match multiple patterns
        // "不想...活" and "死...算了" and "想...去死"
        String message = "不想再这样活着了，死掉算了，想直接去死";
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis());
        // Since keywords "不想活" is NOT an exact keyword, and "去死" IS a keyword,
        // actually "去死" is in HIGH_RISK_KEYWORDS so keywords take priority
        assertTrue(result.matchedKeywords().contains("去死"));
    }

    @Test
    @DisplayName("When keywords found, patterns are not checked - keyword takes priority")
    void shouldPrioritizeKeywordsOverPatterns() {
        // "自杀" is both a keyword AND would match "自.*杀" pattern
        String message = "我想自杀";
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis());
        // Since keyword "自杀" matches first, pattern "自.*杀" should NOT appear in results
        assertTrue(result.matchedKeywords().contains("自杀"));
        assertFalse(result.matchedKeywords().contains("自.*杀"),
            "When keywords match, patterns should not be checked");
    }

    @Test
    @DisplayName("Keyword '了结' takes priority even when patterns also match")
    void shouldPrioritizeKeywordOverPatternWhenBothPresent() {
        // "了结" is an exact keyword, message also matches pattern "了结.*自己"
        String message = "想了结掉自己";
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis());
        // Keyword "了结" matches, so patterns are never checked
        assertTrue(result.matchedKeywords().contains("了结"));
        assertFalse(result.matchedKeywords().contains("了结.*自己"),
            "Patterns should not be checked when keywords already matched");
    }

    @Test
    @DisplayName("Pure pattern match collects multiple patterns - no keyword overlap")
    void shouldCollectMultiplePatternsWhenNoKeywordOverlap() {
        // Carefully constructed: no exact keywords, but matches two patterns:
        // "不想再勉强活" -> matches "不想.*活"
        // "自 己动手杀" -> matches "自.*杀"
        String message = "不想再勉强活着了，要自己动手杀掉自我";
        SafetyResult result = detector.check(message);
        assertTrue(result.isCrisis());
        // Both patterns should be collected since no keywords matched
        assertTrue(result.matchedKeywords().contains("不想.*活"),
            "Pattern '不想.*活' should be in matched list");
        assertTrue(result.matchedKeywords().contains("自.*杀"),
            "Pattern '自.*杀' should be in matched list");
        assertTrue(result.matchedKeywords().size() >= 2,
            "At least two patterns should match");
    }

    // ==================== Very long messages - performance edge case ====================

    @Test
    @DisplayName("Very long message with keyword is still detected efficiently")
    void shouldDetectKeywordInVeryLongMessage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("这是一段普通的填充文本，用来测试长消息的处理性能。");
        }
        sb.append("我想自杀");
        for (int i = 0; i < 1000; i++) {
            sb.append("更多的普通文本在后面，验证不会影响检测结果。");
        }

        long start = System.nanoTime();
        SafetyResult result = detector.check(sb.toString());
        long elapsed = System.nanoTime() - start;

        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().contains("自杀"));
        // Should complete within 1 second even for very long messages
        assertTrue(elapsed < 1_000_000_000L,
            "Detection should complete within 1 second, took " + elapsed / 1_000_000 + "ms");
    }

    @Test
    @DisplayName("Very long message with only pattern match (no keywords) is still detected efficiently")
    void shouldDetectPatternInVeryLongMessage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("普通文本填充内容。");
        }
        // No exact keywords, but pattern "不想.*活" will match
        sb.append("不想再勉强活下去了");

        long start = System.nanoTime();
        SafetyResult result = detector.check(sb.toString());
        long elapsed = System.nanoTime() - start;

        assertTrue(result.isCrisis());
        // Should complete within 2 seconds (regex on long text can be slower)
        assertTrue(elapsed < 2_000_000_000L,
            "Pattern detection should complete within 2 seconds, took " + elapsed / 1_000_000 + "ms");
    }

    @Test
    @DisplayName("Very long message with no crisis signals returns SAFE efficiently")
    void shouldReturnSafeForVeryLongNonCrisisMessage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("这是一段完全正常的文本，讨论天气和日常生活。");
        }

        long start = System.nanoTime();
        SafetyResult result = detector.check(sb.toString());
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isCrisis());
        // Should complete within 2 seconds even with pattern scanning
        assertTrue(elapsed < 2_000_000_000L,
            "SAFE detection for long messages should complete within 2 seconds, took " + elapsed / 1_000_000 + "ms");
    }

    // ==================== Whitespace variations ====================

    @Test
    @DisplayName("Full-width space between crisis characters matches pattern")
    void shouldMatchWithFullWidthSpace() {
        // Full-width space (U+3000) between characters
        SafetyResult result = detector.check("自　杀");
        assertTrue(result.isCrisis(),
            "Pattern '自.*杀' should match with full-width space between characters");
    }

    @Test
    @DisplayName("Zero-width characters between crisis characters matches pattern")
    void shouldMatchWithZeroWidthCharacters() {
        // Zero-width space (U+200B) between characters
        SafetyResult result = detector.check("自​杀");
        assertTrue(result.isCrisis(),
            "Pattern '自.*杀' should match with zero-width space between characters");
    }

    @Test
    @DisplayName("Multiple mixed whitespace between crisis characters matches pattern")
    void shouldMatchWithMixedWhitespace() {
        SafetyResult result = detector.check("自 \t 杀");
        assertTrue(result.isCrisis(),
            "Pattern '自.*杀' should match with mixed whitespace between characters");
    }

    @Test
    @DisplayName("Arbitrary non-whitespace character between crisis characters matches pattern")
    void shouldMatchWithArbitraryCharBetween() {
        // "自x杀" - the 'x' character is matched by .* in pattern
        SafetyResult result = detector.check("自x杀");
        assertTrue(result.isCrisis(),
            "Pattern '自.*杀' should match with any non-newline character between");
    }

    @Test
    @DisplayName("Unicode punctuation between crisis characters matches pattern")
    void shouldMatchWithUnicodePunctuationBetween() {
        SafetyResult result = detector.check("自。。。杀");
        assertTrue(result.isCrisis(),
            "Pattern '自.*杀' should match with Chinese punctuation between characters");
    }
}
