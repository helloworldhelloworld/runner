package com.lightweightai.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContentFilter 边界条件与安全关键路径测试
 *
 * 覆盖：
 * - 多种诊断模式全覆盖
 * - 多个诊断语句同时出现
 * - 非诊断性安全内容不被过滤
 * - 部分匹配验证
 * - 替换文本内容验证
 * - 诊断性+安全内容混合
 */
@DisplayName("ContentFilter - 安全关键路径边界测试")
class ContentFilterEdgeCaseTest {

    private ContentFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ContentFilter();
    }

    // ==================== 诊断模式全覆盖 ====================

    @Nested
    @DisplayName("诊断模式全覆盖")
    class FullPatternCoverage {

        @Test
        @DisplayName("过滤 '你是精神分裂'")
        void shouldFilterSchizophreniaDiagnosis() {
            String response = "你是精神分裂的表现。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("你是精神分裂"));
        }

        @Test
        @DisplayName("过滤 '你有边缘型人格障碍'")
        void shouldFilterBPDDiagnosis() {
            String response = "你有边缘型人格障碍，需要专业帮助。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("你有边缘型人格障碍"));
        }

        @Test
        @DisplayName("过滤 '根据你的描述判断为...'")
        void shouldFilterJudgmentBasedOnDescription() {
            String response = "根据你的描述可以判断为中度焦虑。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("判断为"));
        }

        @Test
        @DisplayName("过滤 '这是焦虑的信号'")
        void shouldFilterAnxietySignal() {
            String response = "这是焦虑的信号，你需要注意。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("这是焦虑的信号"));
        }

        @Test
        @DisplayName("过滤 '这是双相的表现'")
        void shouldFilterBipolarManifestation() {
            String response = "这是双相的表现，建议治疗。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("这是双相的表现"));
        }

        @Test
        @DisplayName("过滤 '建议马上就诊'")
        void shouldFilterUrgentVisitAdvice() {
            String response = "建议马上就诊，不要拖延。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("建议马上就诊"));
        }

        @Test
        @DisplayName("过滤 '建议尽快看医生'")
        void shouldFilterSeeDocAdvice() {
            String response = "建议尽快看医生。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("建议尽快看医生"));
        }

        @Test
        @DisplayName("过滤 '建议立即用药'")
        void shouldFilterImmediateMedication() {
            String response = "建议立即用药控制症状。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("建议立即用药"));
        }

        @Test
        @DisplayName("过滤 '你得了躁郁症'")
        void shouldFilterBipolarDiagnosis() {
            String response = "你得了躁郁症，情况不太好。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("你得了躁郁症"));
        }
    }

    // ==================== 安全内容不被过滤 ====================

    @Nested
    @DisplayName("安全内容保护")
    class SafeContentProtection {

        @Test
        @DisplayName("共情性表达不被过滤")
        void shouldNotFilterEmpatheticExpression() {
            String response = "我能理解你的感受，这种焦虑感是很正常的，很多人都有类似的经历。";
            assertEquals(response, filter.filter(response));
        }

        @Test
        @DisplayName("一般性建议不被过滤")
        void shouldNotFilterGeneralAdvice() {
            String response = "你可以试试深呼吸放松，或者去散散步，这些对缓解情绪都有帮助。";
            assertEquals(response, filter.filter(response));
        }

        @Test
        @DisplayName("温和就医建议不被过滤")
        void shouldNotFilterGentleMedicalAdvice() {
            String response = "如果你觉得需要，可以考虑咨询专业的心理医生。";
            assertEquals(response, filter.filter(response));
        }

        @Test
        @DisplayName("心理知识科普不被过滤")
        void shouldNotFilterMentalHealthEducation() {
            String response = "焦虑是一种常见的情绪反应，适度焦虑甚至有助于提高效率。";
            assertEquals(response, filter.filter(response));
        }

        @Test
        @DisplayName("鼓励性语言不被过滤")
        void shouldNotFilterEncouragement() {
            String response = "你已经很勇敢了，愿意说出来就是一个很好的开始。";
            assertEquals(response, filter.filter(response));
        }
    }

    // ==================== 混合内容 ====================

    @Nested
    @DisplayName("混合内容处理")
    class MixedContent {

        @Test
        @DisplayName("诊断语句被替换，其他内容保留")
        void shouldOnlyReplaceDiagnosticParts() {
            String response = "我理解你的感受。你患有抑郁症。但这并不代表你不好。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("你患有抑郁症"));
            assertTrue(filtered.contains("我理解你的感受"));
            assertTrue(filtered.contains("但这并不代表你不好"));
        }

        @Test
        @DisplayName("替换后文本包含专业引导语")
        void shouldContainProfessionalGuidanceAfterFilter() {
            String response = "你患有抑郁症。";
            String filtered = filter.filter(response);
            assertTrue(filtered.contains("专业的心理咨询师或医生"));
        }

        @Test
        @DisplayName("多个不同模式的诊断全部被过滤")
        void shouldFilterAllDifferentDiagnosticPatterns() {
            String response = "你得了焦虑症。这是精神疾病的表现。建议立即就医。";
            String filtered = filter.filter(response);
            assertFalse(filtered.contains("你得了焦虑症"));
            assertFalse(filtered.contains("精神疾病的表现"));
            assertFalse(filtered.contains("建议立即就医"));
        }
    }

    // ==================== 边界输入 ====================

    @Nested
    @DisplayName("边界输入")
    class BoundaryInput {

        @Test
        @DisplayName("仅包含空白字符的输入返回原值")
        void shouldReturnWhitespaceAsIs() {
            assertEquals("\t\n", filter.filter("\t\n"));
        }

        @Test
        @DisplayName("超长非诊断文本不被过滤")
        void shouldNotFilterLongNonDiagnosticText() {
            String response = "你做得很好，继续加油。".repeat(500);
            assertEquals(response, filter.filter(response));
        }
    }
}
