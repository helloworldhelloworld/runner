package com.lightweightai.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentFilter - AI响应内容过滤")
class ContentFilterTest {

    private ContentFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ContentFilter();
    }

    // ==================== 安全内容 ====================

    @Test
    @DisplayName("正常内容不被过滤")
    void shouldNotFilterNormalContent() {
        String response = "我理解你的感受，这种情况下感到焦虑是正常的。";
        assertEquals(response, filter.filter(response));
    }

    @Test
    @DisplayName("null输入返回null")
    void shouldReturnNullForNull() {
        assertNull(filter.filter(null));
    }

    @Test
    @DisplayName("空白输入返回原值")
    void shouldReturnBlankForBlank() {
        assertEquals("   ", filter.filter("   "));
    }

    @Test
    @DisplayName("空字符串返回原值")
    void shouldReturnEmptyForEmpty() {
        assertEquals("", filter.filter(""));
    }

    // ==================== 诊断内容过滤 ====================

    @Test
    @DisplayName("过滤'你患有抑郁症'诊断语句")
    void shouldFilterDiagnosisYouHaveDepression() {
        String response = "你患有抑郁症，需要注意休息。";
        String filtered = filter.filter(response);
        assertNotEquals(response, filtered);
        assertFalse(filtered.contains("你患有抑郁症"));
        assertTrue(filtered.contains("专业"));
    }

    @Test
    @DisplayName("过滤'你得了焦虑症'诊断语句")
    void shouldFilterDiagnosisYouGotAnxiety() {
        String response = "你得了焦虑症，建议治疗。";
        String filtered = filter.filter(response);
        assertFalse(filtered.contains("你得了焦虑症"));
    }

    @Test
    @DisplayName("过滤'你有双相'诊断语句")
    void shouldFilterBipolarDiagnosis() {
        String response = "你有双相，这是比较严重的情况。";
        String filtered = filter.filter(response);
        assertFalse(filtered.contains("你有双相"));
    }

    @Test
    @DisplayName("过滤'你是PTSD'诊断语句")
    void shouldFilterPtsdDiagnosis() {
        String response = "你是PTSD的典型表现。";
        String filtered = filter.filter(response);
        assertFalse(filtered.contains("你是PTSD"));
    }

    @Test
    @DisplayName("过滤'根据描述诊断为'语句")
    void shouldFilterDiagnosisBasedOnDescription() {
        String response = "根据你的描述，我诊断为中度抑郁。";
        String filtered = filter.filter(response);
        assertFalse(filtered.contains("诊断为"));
    }

    @Test
    @DisplayName("过滤'这是抑郁的症状'语句")
    void shouldFilterSymptomStatement() {
        String response = "这是抑郁的症状，你要小心。";
        String filtered = filter.filter(response);
        assertFalse(filtered.contains("这是抑郁的症状"));
    }

    @Test
    @DisplayName("过滤'建议立即就医'语句")
    void shouldFilterUrgentMedicalAdvice() {
        String response = "建议立即就医，不要拖延。";
        String filtered = filter.filter(response);
        assertFalse(filtered.contains("建议立即就医"));
    }

    @Test
    @DisplayName("过滤'建议尽快服药'语句")
    void shouldFilterMedicationAdvice() {
        String response = "建议尽快服药控制症状。";
        String filtered = filter.filter(response);
        assertFalse(filtered.contains("建议尽快服药"));
    }

    // ==================== 替换内容验证 ====================

    @Test
    @DisplayName("过滤后替换为温和引导语")
    void shouldReplaceWithSoftGuidance() {
        String response = "你患有抑郁症，情况比较严重。";
        String filtered = filter.filter(response);
        assertTrue(filtered.contains("专业的心理咨询师或医生"));
    }

    @Test
    @DisplayName("多个诊断语句都被过滤")
    void shouldFilterMultipleDiagnosticStatements() {
        String response = "你患有抑郁症。这是焦虑的表现。建议立即就医。";
        String filtered = filter.filter(response);
        assertFalse(filtered.contains("你患有抑郁症"));
        assertFalse(filtered.contains("建议立即就医"));
    }
}
