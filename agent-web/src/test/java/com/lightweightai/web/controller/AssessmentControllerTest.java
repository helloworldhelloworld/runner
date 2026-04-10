package com.lightweightai.web.controller;

import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AssessmentController - 心理评估 API")
class AssessmentControllerTest {

    private AssessmentService assessmentService;
    private AssessmentController controller;

    @BeforeEach
    void setUp() {
        assessmentService = mock(AssessmentService.class);
        controller = new AssessmentController(assessmentService);
    }

    // ==================== GET /assessment/scales ====================

    @Test
    @DisplayName("获取所有量表定义")
    void shouldReturnAllScales() {
        Map<String, ScaleDefinition> scales = Map.of();
        when(assessmentService.getScales()).thenReturn(scales);

        ResponseEntity<Map<String, ScaleDefinition>> response = controller.getScales();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    // ==================== GET /assessment/scales/{type} ====================

    @Nested
    @DisplayName("获取单个量表")
    class GetScale {

        @Test
        @DisplayName("合法类型返回量表定义")
        void shouldReturnScaleForValidType() {
            ScaleDefinition definition = new ScaleDefinition(
                ScaleType.PHQ9, "PHQ-9", "描述", List.of(), List.of(), List.of());
            when(assessmentService.getScale(ScaleType.PHQ9)).thenReturn(definition);

            ResponseEntity<ScaleDefinition> response = controller.getScale("phq9");
            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("非法类型返回 400")
        void shouldReturn400ForInvalidType() {
            ResponseEntity<ScaleDefinition> response = controller.getScale("invalid");
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("类型大小写不敏感")
        void shouldBeCaseInsensitive() {
            ScaleDefinition definition = new ScaleDefinition(
                ScaleType.GAD7, "GAD-7", "描述", List.of(), List.of(), List.of());
            when(assessmentService.getScale(ScaleType.GAD7)).thenReturn(definition);

            ResponseEntity<ScaleDefinition> response = controller.getScale("gad7");
            assertEquals(200, response.getStatusCode().value());
        }
    }

    // ==================== POST /assessment/submit ====================

    @Nested
    @DisplayName("提交评估")
    class Submit {

        @Test
        @DisplayName("成功提交返回结果")
        void shouldSubmitSuccessfully() {
            AssessmentResult result = mock(AssessmentResult.class);
            when(assessmentService.submit(any())).thenReturn(result);

            var request = new AssessmentController.SubmitRequest("user-1", "PHQ9", List.of(0, 1, 2, 3, 0, 1, 2, 3, 0));
            ResponseEntity<?> response = controller.submit(request);
            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("非法量表类型返回 400")
        void shouldRejectInvalidScaleType() {
            var request = new AssessmentController.SubmitRequest("user-1", "INVALID", List.of(1, 2, 3));
            ResponseEntity<?> response = controller.submit(request);
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("服务异常返回 500")
        void shouldReturn500OnServiceError() {
            when(assessmentService.submit(any())).thenThrow(new RuntimeException("DB error"));

            var request = new AssessmentController.SubmitRequest("user-1", "PHQ9", List.of(0, 0, 0));
            ResponseEntity<?> response = controller.submit(request);
            assertEquals(500, response.getStatusCode().value());
        }
    }

    // ==================== GET /assessment/{userId}/history ====================

    @Test
    @DisplayName("获取评估历史")
    void shouldReturnHistory() {
        when(assessmentService.getHistory("user-1")).thenReturn(List.of());

        ResponseEntity<List<AssessmentResult>> response = controller.getHistory("user-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
}
