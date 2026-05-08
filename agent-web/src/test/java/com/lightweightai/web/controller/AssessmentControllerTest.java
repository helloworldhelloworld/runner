package com.lightweightai.web.controller;

import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AssessmentController} — REST API for psychological assessments.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssessmentController - 心理评估 API")
class AssessmentControllerTest {

    @Mock
    private AssessmentService assessmentService;

    private AssessmentController controller;

    @BeforeEach
    void setUp() {
        controller = new AssessmentController(assessmentService);
    }

    // ==================== GET /assessment/scales ====================

    @Nested
    @DisplayName("GET /assessment/scales")
    class GetScalesTests {

        @Test
        @DisplayName("返回所有量表定义")
        void getScales_returnsAll() {
            Map<String, ScaleDefinition> scales = Map.of(
                "PHQ9", mock(ScaleDefinition.class),
                "GAD7", mock(ScaleDefinition.class)
            );
            when(assessmentService.getScales()).thenReturn(scales);

            ResponseEntity<Map<String, ScaleDefinition>> response = controller.getScales();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(2, response.getBody().size());
        }
    }

    // ==================== GET /assessment/scales/{type} ====================

    @Nested
    @DisplayName("GET /assessment/scales/{type}")
    class GetScaleTests {

        @Test
        @DisplayName("有效类型 — 返回量表定义")
        void getScale_validType_returnsDefinition() {
            ScaleDefinition def = mock(ScaleDefinition.class);
            when(assessmentService.getScale(ScaleType.PHQ9)).thenReturn(def);

            ResponseEntity<ScaleDefinition> response = controller.getScale("phq9");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("大小写不敏感")
        void getScale_caseInsensitive() {
            ScaleDefinition def = mock(ScaleDefinition.class);
            when(assessmentService.getScale(ScaleType.GAD7)).thenReturn(def);

            ResponseEntity<ScaleDefinition> response = controller.getScale("gad7");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("无效类型 — 返回 400 Bad Request")
        void getScale_invalidType_returnsBadRequest() {
            ResponseEntity<ScaleDefinition> response = controller.getScale("INVALID_SCALE");
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    // ==================== POST /assessment/submit ====================

    @Nested
    @DisplayName("POST /assessment/submit")
    class SubmitTests {

        @Test
        @DisplayName("有效提交 — 返回评估结果")
        void submit_valid_returnsResult() {
            AssessmentResult result = mock(AssessmentResult.class);
            when(assessmentService.submit(any(AssessmentSubmission.class))).thenReturn(result);

            AssessmentController.SubmitRequest request = new AssessmentController.SubmitRequest(
                "user-1", "PHQ9", List.of(0, 1, 2, 3, 0, 1, 2, 3, 0)
            );

            ResponseEntity<?> response = controller.submit(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertSame(result, response.getBody());
        }

        @Test
        @DisplayName("无效量表类型 — 返回 400")
        void submit_invalidScaleType_returnsBadRequest() {
            AssessmentController.SubmitRequest request = new AssessmentController.SubmitRequest(
                "user-1", "INVALID", List.of(0, 1, 2)
            );

            ResponseEntity<?> response = controller.submit(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("服务异常 — 返回 500")
        void submit_serviceThrows_returnsInternalError() {
            when(assessmentService.submit(any(AssessmentSubmission.class)))
                .thenThrow(new RuntimeException("DB error"));

            AssessmentController.SubmitRequest request = new AssessmentController.SubmitRequest(
                "user-1", "PHQ9", List.of(0, 1, 2, 3, 0, 1, 2, 3, 0)
            );

            ResponseEntity<?> response = controller.submit(request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }
    }

    // ==================== GET /assessment/{userId}/history ====================

    @Nested
    @DisplayName("GET /assessment/{userId}/history")
    class HistoryTests {

        @Test
        @DisplayName("返回用户评估历史")
        void getHistory_returnsResults() {
            AssessmentResult r1 = mock(AssessmentResult.class);
            AssessmentResult r2 = mock(AssessmentResult.class);
            when(assessmentService.getHistory("user-1")).thenReturn(List.of(r1, r2));

            ResponseEntity<List<AssessmentResult>> response = controller.getHistory("user-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(2, response.getBody().size());
        }

        @Test
        @DisplayName("无历史记录 — 返回空列表")
        void getHistory_empty_returnsEmptyList() {
            when(assessmentService.getHistory("new-user")).thenReturn(List.of());

            ResponseEntity<List<AssessmentResult>> response = controller.getHistory("new-user");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().isEmpty());
        }
    }
}
