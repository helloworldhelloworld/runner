package com.lightweightai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisDetector - 危机信号检测扩展测试")
class CrisisDetectorExtendedTest {

    private final CrisisDetector detector = new CrisisDetector();

    @Test
    @DisplayName("null 输入返回 SAFE")
    void nullInput() {
        SafetyResult result = detector.check(null);
        assertFalse(result.isCrisis());
    }

    @Test
    @DisplayName("空字符串返回 SAFE")
    void emptyInput() {
        SafetyResult result = detector.check("");
        assertFalse(result.isCrisis());
    }

    @Test
    @DisplayName("正常消息返回 SAFE")
    void normalMessage() {
        SafetyResult result = detector.check("今天天气真好，我很开心");
        assertFalse(result.isCrisis());
    }

    @Test
    @DisplayName("高危关键词触发 CRISIS - 自杀")
    void crisisKeyword1() {
        SafetyResult result = detector.check("我想自杀");
        assertTrue(result.isCrisis());
        assertTrue(result.matchedKeywords().contains("自杀"));
        assertFalse(result.resources().isEmpty());
    }

    @Test
    @DisplayName("高危关键词触发 CRISIS - 不想活了")
    void crisisKeyword2() {
        SafetyResult result = detector.check("我不想活了");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("高危关键词触发 CRISIS - 自残")
    void crisisKeyword3() {
        SafetyResult result = detector.check("我一直在自残");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("模式匹配触发 CRISIS - 不想...活")
    void crisisPattern1() {
        SafetyResult result = detector.check("我不想继续活下去");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("模式匹配触发 CRISIS - 想...去死")
    void crisisPattern2() {
        SafetyResult result = detector.check("我想要去死");
        assertTrue(result.isCrisis());
    }

    @Test
    @DisplayName("危机结果包含援助资源")
    void crisisResultHasResources() {
        SafetyResult result = detector.check("我想自杀");
        assertTrue(result.isCrisis());
        assertFalse(result.resources().isEmpty());
        // 验证资源包含电话号码
        result.resources().forEach(r -> {
            assertNotNull(r.phone());
            assertFalse(r.phone().isBlank());
        });
    }
}
