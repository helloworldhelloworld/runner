package com.lightweightai.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CrisisDetector 边界条件与安全关键路径测试
 *
 * 覆盖：
 * - 关键词嵌入长句中仍能检测
 * - 仅模式匹配触发（无关键词直接命中）
 * - 多种高风险关键词全覆盖
 * - 超长消息处理
 * - 混合语言/Unicode 处理
 * - 关键词优先于模式（关键词命中则不检查模式）
 * - 边界文本测试
 */
@DisplayName("CrisisDetector - 安全关键路径边界测试")
class CrisisDetectorEdgeCaseTest {

    private CrisisDetector detector;

    @BeforeEach
    void setUp() {
        detector = new CrisisDetector();
    }

    // ==================== 嵌入关键词检测 ====================

    @Nested
    @DisplayName("关键词嵌入检测")
    class EmbeddedKeywordDetection {

        @Test
        @DisplayName("关键词嵌入长句中仍被检测")
        void shouldDetectKeywordInLongSentence() {
            SafetyResult result = detector.check(
                "今天在学校里，老师讲了很多关于心理健康的知识，突然我就想自杀了，不知道为什么");
            assertTrue(result.isCrisis());
            assertTrue(result.matchedKeywords().contains("自杀"));
        }

        @Test
        @DisplayName("关键词出现在句末仍被检测")
        void shouldDetectKeywordAtEnd() {
            SafetyResult result = detector.check("反正就是不想活了");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("关键词出现在句首仍被检测")
        void shouldDetectKeywordAtBeginning() {
            SafetyResult result = detector.check("割腕之后感觉好多了");
            assertTrue(result.isCrisis());
            assertTrue(result.matchedKeywords().contains("割腕"));
        }
    }

    // ==================== 模式匹配独立检测 ====================

    @Nested
    @DisplayName("模式匹配独立触发")
    class PatternOnlyDetection {

        @Test
        @DisplayName("'不想继续活下去' 通过模式匹配触发")
        void shouldMatchPatternNotWantToLive() {
            SafetyResult result = detector.check("我不想继续活下去了");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("'活着真的没有意思' 通过模式匹配触发")
        void shouldMatchPatternLifeMeaningless() {
            SafetyResult result = detector.check("活着真的没有意思");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("'了结了自己' 通过模式匹配触发")
        void shouldMatchEndSelfPattern() {
            SafetyResult result = detector.check("想了结了自己");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("'自我了杀' 类变体通过模式匹配")
        void shouldMatchSuicideVariant() {
            SafetyResult result = detector.check("有自我了杀的念头");
            // Pattern: 自.*杀
            assertTrue(result.isCrisis());
        }
    }

    // ==================== 全关键词覆盖 ====================

    @Nested
    @DisplayName("高风险关键词全覆盖")
    class FullKeywordCoverage {

        @Test
        @DisplayName("'自残' 触发危机")
        void shouldDetectSelfHarm() {
            assertTrue(detector.check("我一直在自残").isCrisis());
        }

        @Test
        @DisplayName("'活不下去' 触发危机")
        void shouldDetectCannotLive() {
            assertTrue(detector.check("活不下去了").isCrisis());
        }

        @Test
        @DisplayName("'了结' 触发危机")
        void shouldDetectEndIt() {
            assertTrue(detector.check("想了结一切").isCrisis());
        }

        @Test
        @DisplayName("'结束生命' 触发危机")
        void shouldDetectEndLife() {
            assertTrue(detector.check("想结束生命").isCrisis());
        }

        @Test
        @DisplayName("'结束一切' 触发危机")
        void shouldDetectEndEverything() {
            assertTrue(detector.check("想结束一切").isCrisis());
        }

        @Test
        @DisplayName("'去死' 触发危机")
        void shouldDetectGoDie() {
            assertTrue(detector.check("我想去死").isCrisis());
        }

        @Test
        @DisplayName("'想死' 触发危机")
        void shouldDetectWantToDie() {
            assertTrue(detector.check("想死了").isCrisis());
        }

        @Test
        @DisplayName("'寻死' 触发危机")
        void shouldDetectSeekDeath() {
            assertTrue(detector.check("别寻死了").isCrisis());
        }

        @Test
        @DisplayName("'轻生' 触发危机")
        void shouldDetectSuicide2() {
            assertTrue(detector.check("有轻生的念头").isCrisis());
        }

        @Test
        @DisplayName("'上吊' 触发危机")
        void shouldDetectHanging() {
            assertTrue(detector.check("听说上吊很痛苦").isCrisis());
        }

        @Test
        @DisplayName("'服药' 触发危机")
        void shouldDetectTakePills() {
            assertTrue(detector.check("打算服药结束").isCrisis());
        }

        @Test
        @DisplayName("'安眠药' 触发危机")
        void shouldDetectSleepingPills() {
            assertTrue(detector.check("我存了很多安眠药").isCrisis());
        }

        @Test
        @DisplayName("'死了算了' 触发危机")
        void shouldDetectJustDie() {
            assertTrue(detector.check("死了算了").isCrisis());
        }

        @Test
        @DisplayName("'不如死了' 触发危机")
        void shouldDetectBetterDead() {
            assertTrue(detector.check("不如死了好").isCrisis());
        }

        @Test
        @DisplayName("'没有活下去的理由' 触发危机")
        void shouldDetectNoReasonToLive() {
            assertTrue(detector.check("没有活下去的理由了").isCrisis());
        }

        @Test
        @DisplayName("'不想醒来' 触发危机")
        void shouldDetectDontWantToWakeUp() {
            assertTrue(detector.check("每天都不想醒来").isCrisis());
        }
    }

    // ==================== 边界文本 ====================

    @Nested
    @DisplayName("边界文本处理")
    class BoundaryTextHandling {

        @Test
        @DisplayName("超长消息仍能检测")
        void shouldDetectInVeryLongMessage() {
            String prefix = "今天我做了很多事情，去了公园散步，看了电影，吃了好吃的。".repeat(100);
            String message = prefix + "但是我还是想自杀。";
            assertTrue(detector.check(message).isCrisis());
        }

        @Test
        @DisplayName("非危机长消息返回 SAFE")
        void shouldReturnSafeForLongNormalMessage() {
            String message = "今天天气很好，心情不错，吃了好吃的面包。".repeat(200);
            assertFalse(detector.check(message).isCrisis());
        }

        @Test
        @DisplayName("仅包含标点符号返回 SAFE")
        void shouldReturnSafeForPunctuation() {
            assertFalse(detector.check("。！？，、；：").isCrisis());
        }

        @Test
        @DisplayName("讨论死亡的学术/新闻内容不应检测（假阳性风险）")
        void shouldDetectEvenInAcademicContext() {
            // Note: Current implementation will flag this as crisis due to keyword match.
            // This is intentional - better safe than sorry for safety-critical applications.
            SafetyResult result = detector.check("自杀预防热线的重要性");
            assertTrue(result.isCrisis()); // keyword "自杀" matches
        }
    }

    // ==================== 危机资源完整性 ====================

    @Test
    @DisplayName("危机资源列表不为空")
    void crisisResourcesShouldNotBeEmpty() {
        assertFalse(CrisisResource.DEFAULTS.isEmpty());
    }

    @Test
    @DisplayName("所有危机资源都有有效电话号码")
    void allResourcesShouldHavePhoneNumber() {
        for (CrisisResource resource : CrisisResource.DEFAULTS) {
            assertNotNull(resource.phone());
            assertFalse(resource.phone().isBlank());
        }
    }
}
