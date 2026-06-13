package com.lightweightai.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisDetector - 全量关键词 + 模式覆盖")
class CrisisDetectorComprehensiveTest {

    private CrisisDetector detector;

    @BeforeEach
    void setUp() {
        detector = new CrisisDetector();
    }

    @Nested
    @DisplayName("所有高风险关键词逐一验证")
    class AllHighRiskKeywords {

        @ParameterizedTest(name = "关键词: {0}")
        @ValueSource(strings = {
            "自杀", "自残", "割腕", "不想活", "不想活了", "活不下去", "活着没意思",
            "了结", "结束生命", "结束一切", "去死", "想死", "寻死", "轻生",
            "跳楼", "上吊", "服药", "吃药死", "安眠药", "死了算了",
            "不如死了", "宁可死", "死比活好", "死心", "没有活下去的理由",
            "没有活下去的意义", "再也不想见到明天", "不想醒来"
        })
        void shouldDetectKeyword(String keyword) {
            SafetyResult result = detector.check("我感到" + keyword + "的念头");
            assertTrue(result.isCrisis(), "Should detect keyword: " + keyword);
            assertTrue(result.matchedKeywords().contains(keyword),
                "matchedKeywords should contain: " + keyword);
            assertFalse(result.resources().isEmpty(),
                "Crisis result should include resources");
        }
    }

    @Nested
    @DisplayName("所有模式匹配逐一验证")
    class AllPatterns {

        @Test
        @DisplayName("模式: 不想.*活")
        void patternDontWantLive() {
            SafetyResult result = detector.check("我不想继续活着");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("模式: 活.*没.*意思")
        void patternLiveNoMeaning() {
            SafetyResult result = detector.check("活下去也没什么意思");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("模式: 结束.*生命")
        void patternEndLife() {
            SafetyResult result = detector.check("我想结束这无聊的生命");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("模式: 了结.*自己")
        void patternEndSelf() {
            SafetyResult result = detector.check("不如了结了自己");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("模式: 死.*算了")
        void patternDieForgetIt() {
            SafetyResult result = detector.check("死掉就算了吧");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("模式: 想.*去死")
        void patternWantToDie() {
            SafetyResult result = detector.check("我想要去死掉");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("模式: 自.*杀")
        void patternSelfKill() {
            SafetyResult result = detector.check("我在考虑自我了杀");
            assertTrue(result.isCrisis());
        }
    }

    @Nested
    @DisplayName("关键词作为子串嵌入消息中仍可检测")
    class KeywordAsSubstring {

        @Test
        @DisplayName("关键词被包裹在更长文本中")
        void keywordEmbeddedInText() {
            SafetyResult result = detector.check("我今天特别想自杀念头很强烈");
            assertTrue(result.isCrisis());
            assertTrue(result.matchedKeywords().contains("自杀"));
        }

        @Test
        @DisplayName("关键词在句首")
        void keywordAtStart() {
            SafetyResult result = detector.check("跳楼是唯一的出路");
            assertTrue(result.isCrisis());
        }

        @Test
        @DisplayName("关键词在句尾")
        void keywordAtEnd() {
            SafetyResult result = detector.check("我真的好想去死");
            assertTrue(result.isCrisis());
        }
    }

    @Nested
    @DisplayName("多关键词同时出现")
    class MultipleKeywords {

        @Test
        @DisplayName("多个不同关键词被识别")
        void multipleDistinctKeywords() {
            SafetyResult result = detector.check("我想自杀或者割腕或者跳楼");
            assertTrue(result.isCrisis());
            assertTrue(result.matchedKeywords().size() >= 3,
                "Should detect at least 3 keywords but got: " + result.matchedKeywords());
        }

        @Test
        @DisplayName("关键词命中后不再检查模式")
        void keywordsShortCircuitPatterns() {
            SafetyResult result = detector.check("我自杀了"); // keyword hit
            assertTrue(result.isCrisis());
            assertTrue(result.matchedKeywords().contains("自杀"));
            // pattern "自.*杀" should NOT appear since keywords matched first
            boolean hasPattern = result.matchedKeywords().stream()
                .anyMatch(k -> k.contains(".*"));
            assertFalse(hasPattern, "Patterns should not be checked when keywords match");
        }
    }

    @Nested
    @DisplayName("安全消息不误报")
    class FalsePositiveGuard {

        @Test
        @DisplayName("讨论自杀预防话题不应误报 — 但实际会触发(contains匹配)")
        void suicidePreventionTopic() {
            // CrisisDetector uses contains(), so "自杀预防" will trigger on "自杀"
            // This is intentional — crisis detection errs on the side of caution
            SafetyResult result = detector.check("自杀预防热线的号码是多少");
            assertTrue(result.isCrisis(), "Contains-based detection triggers on '自杀' substring");
        }

        @Test
        @DisplayName("普通生活消息")
        void normalLifeMessage() {
            SafetyResult result = detector.check("今天工作很累，但还是挺好的");
            assertFalse(result.isCrisis());
        }

        @Test
        @DisplayName("讨论天气")
        void weatherDiscussion() {
            SafetyResult result = detector.check("明天会下雨吗？");
            assertFalse(result.isCrisis());
        }

        @Test
        @DisplayName("含有'心'字但非危机")
        void heartWordNotCrisis() {
            SafetyResult result = detector.check("我很开心，今天过得不错");
            assertFalse(result.isCrisis());
        }
    }

    @Nested
    @DisplayName("危机资源完整性")
    class CrisisResourceCompleteness {

        @Test
        @DisplayName("DEFAULTS列表不可变")
        void defaultsListIsImmutable() {
            assertThrows(UnsupportedOperationException.class,
                () -> CrisisResource.DEFAULTS.add(
                    new CrisisResource("test", "123", "test")));
        }

        @Test
        @DisplayName("所有资源字段非空")
        void allResourceFieldsNonBlank() {
            for (CrisisResource r : CrisisResource.DEFAULTS) {
                assertNotNull(r.name(), "name should not be null");
                assertNotNull(r.phone(), "phone should not be null");
                assertNotNull(r.description(), "description should not be null");
                assertFalse(r.name().isBlank(), "name should not be blank");
                assertFalse(r.phone().isBlank(), "phone should not be blank");
                assertFalse(r.description().isBlank(), "description should not be blank");
            }
        }

        @Test
        @DisplayName("资源列表包含至少3个热线")
        void atLeastThreeResources() {
            assertTrue(CrisisResource.DEFAULTS.size() >= 3);
        }

        @Test
        @DisplayName("CrisisResource record equals and hashCode")
        void recordEquality() {
            CrisisResource a = new CrisisResource("A", "111", "desc");
            CrisisResource b = new CrisisResource("A", "111", "desc");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("CrisisResource record toString")
        void recordToString() {
            CrisisResource r = new CrisisResource("TestLine", "12345", "Test");
            String str = r.toString();
            assertTrue(str.contains("TestLine"));
            assertTrue(str.contains("12345"));
        }
    }
}
