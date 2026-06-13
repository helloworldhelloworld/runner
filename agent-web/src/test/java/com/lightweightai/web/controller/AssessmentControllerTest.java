package com.lightweightai.web.controller;

import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssessmentController - 心理评估API")
class AssessmentControllerTest {

    @Mock
    private AssessmentService assessmentService;

    private AssessmentController controller;

    @BeforeEach
    void setUp() {
        controller = new AssessmentController(assessmentService);
    }

    @Nested
    @DisplayName("GET /assessment/scales")
    class GetScales {
        @Test
        @DisplayName("返回所有量表定义")
        void shouldReturnAllScales() {
            Map<String, ScaleDefinition> scales = Map.of();
            when(assessmentService.getScales()).thenReturn(scales);

            ResponseEntity<Map<String, ScaleDefinition>> response = controller.getScales();

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
        }
    }

    @Nested
    @DisplayName("GET /assessment/scales/{type}")
    class GetScale {
        @Test
        @DisplayName("有效量表类型返回200")
        void shouldReturnScaleForValidType() {
            ScaleDefinition def = mock(ScaleDefinition.class);
            when(assessmentService.getScale(ScaleType.PHQ9)).thenReturn(def);

            ResponseEntity<ScaleDefinition> response = controller.getScale("phq9");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("无效量表类型返回400")
        void shouldReturn400ForInvalidType() {
            ResponseEntity<ScaleDefinition> response = controller.getScale("INVALID_TYPE");
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("大小写不敏感")
        void shouldBeCaseInsensitive() {
            ScaleDefinition def = mock(ScaleDefinition.class);
            when(assessmentService.getScale(ScaleType.GAD7)).thenReturn(def);

            ResponseEntity<ScaleDefinition> response = controller.getScale("gad7");
            assertEquals(200, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("POST /assessment/submit")
    class Submit {
        @Test
        @DisplayName("有效提交返回评估结果")
        void shouldReturnResultForValidSubmission() {
            AssessmentResult result = mock(AssessmentResult.class);
            when(assessmentService.submit(any(AssessmentSubmission.class))).thenReturn(result);

            var request = new AssessmentController.SubmitRequest("usr-1", "PHQ9", List.of(1, 2, 3, 0, 1, 2, 0, 1, 0));
            ResponseEntity<?> response = controller.submit(request);

            assertEquals(200, response.getStatusCode().value());

            ArgumentCaptor<AssessmentSubmission> captor = ArgumentCaptor.forClass(AssessmentSubmission.class);
            verify(assessmentService).submit(captor.capture());
            AssessmentSubmission captured = captor.getValue();
            assertEquals("usr-1", captured.userId());
            assertEquals(ScaleType.PHQ9, captured.scaleType());
            assertEquals(9, captured.answers().size());
        }

        @Test
        @DisplayName("无效量表类型返回400")
        void shouldReturn400ForInvalidScaleType() {
            var request = new AssessmentController.SubmitRequest("usr-1", "INVALID", List.of(1));
            ResponseEntity<?> response = controller.submit(request);
            assertEquals(400, response.getStatusCode().value());
        }

        @Test
        @DisplayName("服务异常返回500")
        void shouldReturn500ForServiceException() {
            when(assessmentService.submit(any())).thenThrow(new RuntimeException("DB error"));

            var request = new AssessmentController.SubmitRequest("usr-1", "PHQ9", List.of(1));
            ResponseEntity<?> response = controller.submit(request);
            assertEquals(500, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("GET /assessment/{userId}/history")
    class GetHistory {
        @Test
        @DisplayName("返回用户历史评估结果")
        void shouldReturnHistoryForUser() {
            when(assessmentService.getHistory("usr-1")).thenReturn(List.of());

            ResponseEntity<List<AssessmentResult>> response = controller.getHistory("usr-1");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isEmpty());
        }
    }
}
