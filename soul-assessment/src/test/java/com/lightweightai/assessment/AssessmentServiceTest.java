package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AssessmentService - 评估服务集成")
class AssessmentServiceTest {

    private AssessmentService service;
    private AssessmentRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test_service.db").toString();
        repository = new AssessmentRepository(dbPath);
        var scorer = new AssessmentScorer();
        var generator = new AssessmentReportGenerator(null); // template fallback
        service = new AssessmentService(scorer, generator, repository);
    }

    // ==================== 获取量表 ====================

    @Test
    @DisplayName("获取所有量表定义")
    void shouldGetAllScales() {
        Map<String, ScaleDefinition> scales = service.getScales();
        assertEquals(3, scales.size());
        assertTrue(scales.containsKey("PHQ9"));
        assertTrue(scales.containsKey("GAD7"));
        assertTrue(scales.containsKey("PSS10"));
    }

    @Test
    @DisplayName("获取单个量表定义")
    void shouldGetSingleScale() {
        ScaleDefinition phq9 = service.getScale(ScaleType.PHQ9);
        assertNotNull(phq9);
        assertEquals(ScaleType.PHQ9, phq9.type());

        ScaleDefinition gad7 = service.getScale(ScaleType.GAD7);
        assertEquals(ScaleType.GAD7, gad7.type());

        ScaleDefinition pss10 = service.getScale(ScaleType.PSS10);
        assertEquals(ScaleType.PSS10, pss10.type());
    }

    // ==================== 提交评估 ====================

    @Test
    @DisplayName("提交PHQ-9评估并获得结果")
    void shouldSubmitPhq9Assessment() {
        var submission = new AssessmentSubmission("user1", ScaleType.PHQ9,
            List.of(1, 2, 1, 0, 1, 2, 1, 0, 2));

        AssessmentResult result = service.submit(submission);

        assertNotNull(result);
        assertTrue(result.getId().startsWith("ar-"));
        assertEquals(16 + 3, result.getId().length()); // "ar-" + 16 chars
        assertEquals("user1", result.getUserId());
        assertEquals(ScaleType.PHQ9, result.getScaleType());
        assertEquals(10, result.getTotalScore());
        assertEquals("中度抑郁", result.getSeverity());
        assertNotNull(result.getAiReport());
        assertTrue(result.getCreatedAt() > 0);
    }

    @Test
    @DisplayName("提交GAD-7评估并获得结果")
    void shouldSubmitGad7Assessment() {
        var submission = new AssessmentSubmission("user1", ScaleType.GAD7,
            List.of(0, 0, 0, 0, 0, 0, 0));

        AssessmentResult result = service.submit(submission);

        assertEquals(0, result.getTotalScore());
        assertEquals("无焦虑", result.getSeverity());
    }

    @Test
    @DisplayName("提交PSS-10评估（含反向计分）")
    void shouldSubmitPss10Assessment() {
        var submission = new AssessmentSubmission("user1", ScaleType.PSS10,
            List.of(2, 2, 2, 2, 2, 2, 2, 2, 2, 2));
        // Normal(0,1,2,5,8,9): 2*6=12
        // Reversed(3,4,6,7): (4-2)*4=8
        // Total: 20

        AssessmentResult result = service.submit(submission);

        assertEquals(20, result.getTotalScore());
        assertEquals("中等压力", result.getSeverity());
    }

    // ==================== 历史记录 ====================

    @Test
    @DisplayName("提交后可查询历史记录")
    void shouldQueryHistoryAfterSubmit() {
        service.submit(new AssessmentSubmission("user1", ScaleType.PHQ9,
            List.of(1, 1, 1, 1, 1, 1, 1, 1, 1)));

        List<AssessmentResult> history = service.getHistory("user1");
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("多次提交累积历史记录")
    void shouldAccumulateHistory() {
        service.submit(new AssessmentSubmission("user1", ScaleType.PHQ9,
            List.of(1, 1, 1, 1, 1, 1, 1, 1, 1)));
        service.submit(new AssessmentSubmission("user1", ScaleType.GAD7,
            List.of(2, 2, 2, 2, 2, 2, 2)));

        List<AssessmentResult> history = service.getHistory("user1");
        assertEquals(2, history.size());
    }

    @Test
    @DisplayName("无历史记录返回空列表")
    void shouldReturnEmptyHistoryForNewUser() {
        assertTrue(service.getHistory("no-such-user").isEmpty());
    }

    // ==================== AI报告生成 ====================

    @Test
    @DisplayName("提交结果包含模板报告")
    void shouldIncludeTemplateReport() {
        var submission = new AssessmentSubmission("user1", ScaleType.PHQ9,
            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0));

        AssessmentResult result = service.submit(submission);

        assertNotNull(result.getAiReport());
        assertFalse(result.getAiReport().isBlank());
        assertTrue(result.getAiReport().contains("患者健康问卷-9"));
    }
}
