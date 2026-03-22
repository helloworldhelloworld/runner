package com.lightweightai.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisDetector - 危机信号检测")
class CrisisDetectorTest {

    private CrisisDetector detector;

    @BeforeEach
    void setUp() {
        detector = new CrisisDetector();
    }

    // ==================== 安全消息 ====================

    @Test
    @DisplayName("普通消息返回SAFE")
    void shouldReturnSafeForNormalMessage() {
        SafetyResult result = detector.check("今天天气很好，心情不错");
        assertFalse(result.isCrisis());
        assertEquals(SafetyResult.Level.SAFE, result.level());
        assertTrue(result.matchedKeywords().isEmpty());
        assertTrue(result.resources().isEmpty());
    }

    @Test
    @DisplayName("null消息返回SAFE")
    void shouldReturnSafeForNull() {
        SafetyResult result = detector.check(null);
        assertFalse(result.isCrisis());
    }

    @Test
    @DisplayName("空白消息返回SAFE")
    void shouldReturnSafeForBlank() {
        SafetyResult result = detector.check("   ");
        assertFalse(result.isCrisis());
    }

    @Test
    @DisplayName("空字符串返回SAFE")
    void shouldReturnSafeForEmpty() {
        SafetyResult result = detector.check("");
        assertFalse(result.isCrisis());
    }

    // ==================== 关键词触发 ====================

    @Test
    @DisplayName("检测到'自杀'关键词返回CRISIS")
    void shouldDetectSuicideKeyword() {
        SafetyResult result = detector.check("我想自杀");
        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().contains("自杀"));
        assertFalse(result.resources().isEmpty());
    }

    @Test
    @DisplayName("检测到'不想活了'关键词返回CRISIS")
    void shouldDetectDontWantToLiveKeyword() {
        SafetyResult result = detector.check("我不想活了");
        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().contains("不想活了"));
    }

    @Test
    @DisplayName("检测到'跳楼'关键词返回CRISIS")
    void shouldDetectJumpKeyword() {
        SafetyResult result = detector.check("我想跳楼");
        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().contains("跳楼"));
    }

    @Test
    @DisplayName("检测到'割腕'关键词返回CRISIS")
    void shouldDetectSelfHarmKeyword() {
        SafetyResult result = detector.check("我在割腕");
        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().contains("割腕"));
    }

    @Test
    @DisplayName("检测到'活着没意思'关键词返回CRISIS")
    void shouldDetectLifeMeaninglessKeyword() {
        SafetyResult result = detector.check("活着没意思");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("检测到多个关键词")
    void shouldDetectMultipleKeywords() {
        SafetyResult result = detector.check("不想活了，想去死");
        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().size() >= 2);
    }

    // ==================== 模式匹配触发 ====================

    @Test
    @DisplayName("模式匹配'不想...活'")
    void shouldMatchPatternDontWantLive() {
        SafetyResult result = detector.check("我真的不想再活下去了");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("模式匹配'结束...生命'")
    void shouldMatchPatternEndLife() {
        SafetyResult result = detector.check("我想结束自己的生命");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("模式匹配'想...去死'")
    void shouldMatchPatternWantToDie() {
        SafetyResult result = detector.check("有时候想一个人去死");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("模式匹配'死...算了'")
    void shouldMatchPatternDieForget() {
        SafetyResult result = detector.check("死了就算了");
        assertTrue(result.isCrisis());
    }

    // ==================== 危机资源 ====================

    @Test
    @DisplayName("CRISIS结果包含危机资源")
    void shouldIncludeCrisisResources() {
        SafetyResult result = detector.check("我想自杀");
        assertTrue(result.isCrisis());
        assertFalse(result.resources().isEmpty());
        assertEquals(CrisisResource.DEFAULTS.size(), result.resources().size());
    }

    @Test
    @DisplayName("危机资源包含北京心理危机热线")
    void shouldIncludeBeijingHotline() {
        SafetyResult result = detector.check("我想自杀");
        boolean hasBeijingHotline = result.resources().stream()
            .anyMatch(r -> r.phone().equals("010-82951332"));
        assertTrue(hasBeijingHotline);
    }
}
