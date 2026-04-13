package com.lightweightai.web.controller;

import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssessmentController - psychological assessment REST API")
class AssessmentControllerTest {

    @Mock
    private AssessmentService assessmentService;

    private AssessmentController controller;

    @BeforeEach
    void setUp() {
        controller = new AssessmentController(assessmentService);
    }

    // ==================== getScales ====================

    @Test
    @DisplayName("getScales returns all scale definitions")
    void getScalesReturnsAll() {
        ScaleDefinition phq9 = new ScaleDefinition(ScaleType.PHQ9, "PHQ-9", "desc", List.of(), List.of(), List.of());
        Map<String, ScaleDefinition> scales = Map.of("PHQ9", phq9);
        when(assessmentService.getScales()).thenReturn(scales);

        ResponseEntity<Map<String, ScaleDefinition>> result = controller.getScales();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().containsKey("PHQ9"));
    }

    // ==================== getScale ====================

    @Test
    @DisplayName("getScale returns specific scale for valid type")
    void getScaleReturnsForValidType() {
        ScaleDefinition definition = new ScaleDefinition(ScaleType.PHQ9, "PHQ-9", "desc", List.of(), List.of(), List.of());
        when(assessmentService.getScale(ScaleType.PHQ9)).thenReturn(definition);

        ResponseEntity<ScaleDefinition> result = controller.getScale("phq9");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
    }

    @Test
    @DisplayName("getScale returns bad request for invalid type")
    void getScaleReturnsBadRequestForInvalidType() {
        ResponseEntity<ScaleDefinition> result = controller.getScale("INVALID");

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    @DisplayName("getScale is case insensitive")
    void getScaleIsCaseInsensitive() {
        ScaleDefinition definition = new ScaleDefinition(ScaleType.PHQ9, "PHQ-9", "desc", List.of(), List.of(), List.of());
        when(assessmentService.getScale(ScaleType.GAD7)).thenReturn(definition);

        ResponseEntity<ScaleDefinition> result = controller.getScale("gad7");

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    // ==================== submit ====================

    @Test
    @DisplayName("submit processes valid submission and returns result")
    void submitProcessesValidSubmission() {
        AssessmentResult expectedResult = new AssessmentResult();
        when(assessmentService.submit(any(AssessmentSubmission.class))).thenReturn(expectedResult);

        AssessmentController.SubmitRequest request =
            new AssessmentController.SubmitRequest("user1", "PHQ9", List.of(1, 2, 3));

        ResponseEntity<?> result = controller.submit(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        ArgumentCaptor<AssessmentSubmission> captor = ArgumentCaptor.forClass(AssessmentSubmission.class);
        verify(assessmentService).submit(captor.capture());
        AssessmentSubmission sub = captor.getValue();
        assertEquals("user1", sub.userId());
        assertEquals(ScaleType.PHQ9, sub.scaleType());
        assertEquals(List.of(1, 2, 3), sub.answers());
    }

    @Test
    @DisplayName("submit returns bad request for invalid scale type")
    void submitReturnsBadRequestForInvalidType() {
        AssessmentController.SubmitRequest request =
            new AssessmentController.SubmitRequest("user1", "INVALID", List.of(1, 2));

        ResponseEntity<?> result = controller.submit(request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    @DisplayName("submit returns internal error on service exception")
    void submitReturnsErrorOnServiceException() {
        when(assessmentService.submit(any())).thenThrow(new RuntimeException("DB error"));

        AssessmentController.SubmitRequest request =
            new AssessmentController.SubmitRequest("user1", "PHQ9", List.of(1));

        ResponseEntity<?> result = controller.submit(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    }

    // ==================== getHistory ====================

    @Test
    @DisplayName("getHistory returns assessment history for user")
    void getHistoryReturnsResults() {
        List<AssessmentResult> history = List.of(new AssessmentResult());
        when(assessmentService.getHistory("user1")).thenReturn(history);

        ResponseEntity<List<AssessmentResult>> result = controller.getHistory("user1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().size());
    }
}
