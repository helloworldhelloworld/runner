package com.lightweightai.web.controller;

import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssessmentController - assessment REST endpoints")
class AssessmentControllerTest {

    @Mock private AssessmentService assessmentService;

    private AssessmentController controller;

    @BeforeEach
    void setUp() {
        controller = new AssessmentController(assessmentService);
    }

    @Nested
    @DisplayName("GET /assessment/scales")
    class GetScales {

        @Test
        @DisplayName("returns all scales from service")
        void returnsAllScales() {
            Map<String, ScaleDefinition> scales = Map.of();
            when(assessmentService.getScales()).thenReturn(scales);

            ResponseEntity<Map<String, ScaleDefinition>> response = controller.getScales();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
        }
    }

    @Nested
    @DisplayName("GET /assessment/scales/{type}")
    class GetScale {

        @Test
        @DisplayName("returns scale for valid type")
        void returnsForValidType() {
            ScaleDefinition def = new ScaleDefinition(
                    ScaleType.PHQ9, "PHQ-9", "Depression",
                    List.of(), List.of(), List.of());
            when(assessmentService.getScale(ScaleType.PHQ9)).thenReturn(def);

            ResponseEntity<ScaleDefinition> response = controller.getScale("phq9");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("PHQ-9", response.getBody().title());
        }

        @Test
        @DisplayName("returns 400 for invalid scale type")
        void badRequestForInvalidType() {
            ResponseEntity<ScaleDefinition> response = controller.getScale("INVALID");

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("POST /assessment/submit")
    class SubmitAssessment {

        @Test
        @DisplayName("valid submission returns result")
        void validSubmission() {
            AssessmentResult mockResult = new AssessmentResult(
                    "r1", "user1", ScaleType.PHQ9, 15, "Moderately severe", "AI report", System.currentTimeMillis());
            when(assessmentService.submit(any())).thenReturn(mockResult);

            AssessmentController.SubmitRequest request =
                    new AssessmentController.SubmitRequest("user1", "PHQ9", List.of(1, 2, 3));

            ResponseEntity<?> response = controller.submit(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("invalid scale type returns 400 with error message")
        void invalidScaleType() {
            AssessmentController.SubmitRequest request =
                    new AssessmentController.SubmitRequest("user1", "INVALID_SCALE", List.of(1, 2));

            ResponseEntity<?> response = controller.submit(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, String> body = (Map<String, String>) response.getBody();
            assertNotNull(body);
            assertTrue(body.get("error").contains("INVALID_SCALE"));
        }
    }

    @Nested
    @DisplayName("GET /assessment/{userId}/history")
    class GetHistory {

        @Test
        @DisplayName("returns user assessment history")
        void returnsHistory() {
            when(assessmentService.getHistory("user1")).thenReturn(List.of());

            ResponseEntity<List<AssessmentResult>> response = controller.getHistory("user1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
        }
    }
}
