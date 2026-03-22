package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AssessmentReportGenerator - AI报告生成")
class AssessmentReportGeneratorTest {

    // ==================== 无LLM时模板报告 ====================

    @Test
    @DisplayName("无LLMProvider时使用模板报告")
    void shouldUseTemplateWhenNoLlmProvider() {
        var generator = new AssessmentReportGenerator(null);
        var result = new AssessmentResult("ar-test1", "user1", ScaleType.PHQ9, 3, "无抑郁", null, 1000);

        String report = generator.generate(result);

        assertNotNull(report);
        assertTrue(report.contains("患者健康问卷-9"));
        assertTrue(report.contains("3"));
        assertTrue(report.contains("无抑郁"));
    }

    @Test
    @DisplayName("低分PHQ-9模板报告包含正面信息")
    void shouldGeneratePositiveTemplateForLowPhq9() {
        var generator = new AssessmentReportGenerator(null);
        var result = new AssessmentResult("ar-test2", "user1", ScaleType.PHQ9, 2, "无抑郁", null, 1000);

        String report = generator.generate(result);

        assertTrue(report.contains("良好"));
    }

    @Test
    @DisplayName("中分PHQ-9模板报告建议关注")
    void shouldGenerateConcernTemplateForMidPhq9() {
        var generator = new AssessmentReportGenerator(null);
        var result = new AssessmentResult("ar-test3", "user1", ScaleType.PHQ9, 7, "轻度抑郁", null, 1000);

        String report = generator.generate(result);

        assertTrue(report.contains("咨询师"));
    }

    @Test
    @DisplayName("高分PHQ-9模板报告建议专业帮助")
    void shouldGenerateUrgentTemplateForHighPhq9() {
        var generator = new AssessmentReportGenerator(null);
        var result = new AssessmentResult("ar-test4", "user1", ScaleType.PHQ9, 18, "中重度抑郁", null, 1000);

        String report = generator.generate(result);

        assertTrue(report.contains("专业"));
    }

    @Test
    @DisplayName("低分GAD-7模板报告正面")
    void shouldGeneratePositiveTemplateForLowGad7() {
        var generator = new AssessmentReportGenerator(null);
        var result = new AssessmentResult("ar-test5", "user1", ScaleType.GAD7, 2, "无焦虑", null, 1000);

        String report = generator.generate(result);

        assertTrue(report.contains("良好"));
    }

    @Test
    @DisplayName("高分GAD-7模板报告建议帮助")
    void shouldGenerateUrgentTemplateForHighGad7() {
        var generator = new AssessmentReportGenerator(null);
        var result = new AssessmentResult("ar-test6", "user1", ScaleType.GAD7, 16, "重度焦虑", null, 1000);

        String report = generator.generate(result);

        assertTrue(report.contains("专业"));
    }

    @Test
    @DisplayName("低分PSS-10模板报告正面")
    void shouldGeneratePositiveTemplateForLowPss10() {
        var generator = new AssessmentReportGenerator(null);
        var result = new AssessmentResult("ar-test7", "user1", ScaleType.PSS10, 10, "低压力", null, 1000);

        String report = generator.generate(result);

        assertTrue(report.contains("良好"));
    }

    @Test
    @DisplayName("中分PSS-10模板报告建议关注")
    void shouldGenerateConcernTemplateForMidPss10() {
        var generator = new AssessmentReportGenerator(null);
        var result = new AssessmentResult("ar-test8", "user1", ScaleType.PSS10, 20, "中等压力", null, 1000);

        String report = generator.generate(result);

        assertTrue(report.contains("咨询师"));
    }

    @Test
    @DisplayName("高分PSS-10模板报告建议专业帮助")
    void shouldGenerateUrgentTemplateForHighPss10() {
        var generator = new AssessmentReportGenerator(null);
        var result = new AssessmentResult("ar-test9", "user1", ScaleType.PSS10, 30, "高压力", null, 1000);

        String report = generator.generate(result);

        assertTrue(report.contains("专业"));
    }
}
