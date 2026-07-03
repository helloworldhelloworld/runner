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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssessmentController - Assessment REST API")
class AssessmentControllerTest {

    @Mock
    private AssessmentService assessmentService;

    private AssessmentController controller;

    @BeforeEach
    void setUp() {
        controller = new AssessmentController(assessmentService);
    }

    @Nested
    @DisplayName("GET /assessment/scales - list all scales")
    class GetScales {

        @Test
        @DisplayName("returns all scale definitions with 200 OK")
        void returnsAllScales() {
            ScaleDefinition phq9Def = new ScaleDefinition(
                    ScaleType.PHQ9, "PHQ-9", "Depression", List.of(), List.of(), List.of());
            Map<String, ScaleDefinition> scales = Map.of("PHQ9", phq9Def);
            when(assessmentService.getScales()).thenReturn(scales);

            ResponseEntity<Map<String, ScaleDefinition>> response = controller.getScales();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().containsKey("PHQ9"));
            assertEquals("PHQ-9", response.getBody().get("PHQ9").title());
        }
    }

    @Nested
    @DisplayName("GET /assessment/scales/{type} - get single scale")
    class GetScale {

        @Test
        @DisplayName("returns scale definition for valid type")
        void validScaleType() {
            ScaleDefinition gad7Def = new ScaleDefinition(
                    ScaleType.GAD7, "GAD-7", "Anxiety", List.of(), List.of(), List.of());
            when(assessmentService.getScale(ScaleType.GAD7)).thenReturn(gad7Def);

            ResponseEntity<ScaleDefinition> response = controller.getScale("gad7");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("GAD-7", response.getBody().title());
        }

        @Test
        @DisplayName("returns 400 for invalid scale type")
        void invalidScaleType() {
            ResponseEntity<ScaleDefinition> response = controller.getScale("UNKNOWN_SCALE");

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNull(response.getBody());
        }

        @Test
        @DisplayName("case-insensitive scale type lookup")
        void caseInsensitive() {
            ScaleDefinition pss10Def = new ScaleDefinition(
                    ScaleType.PSS10, "PSS-10", "Stress", List.of(), List.of(), List.of());
            when(assessmentService.getScale(ScaleType.PSS10)).thenReturn(pss10Def);

            ResponseEntity<ScaleDefinition> response = controller.getScale("pss10");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("PSS-10", response.getBody().title());
        }
    }

    @Nested
    @DisplayName("POST /assessment/submit - submit assessment")
    class Submit {

        @Test
        @DisplayName("successful submission returns result with 200 OK")
        void successfulSubmission() {
            AssessmentResult result = new AssessmentResult(
                    "ar-123", "user1", ScaleType.PHQ9, 12, "moderate", "AI report text", 1000L);
            when(assessmentService.submit(any(AssessmentSubmission.class))).thenReturn(result);

            AssessmentController.SubmitRequest request =
                    new AssessmentController.SubmitRequest("user1", "phq9", List.of(1, 2, 3, 0, 1, 2, 1, 1, 1));

            ResponseEntity<?> response = controller.submit(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            AssessmentResult body = (AssessmentResult) response.getBody();
            assertNotNull(body);
            assertEquals("ar-123", body.getId());
            assertEquals(12, body.getTotalScore());
            assertEquals("moderate", body.getSeverity());
        }

        @Test
        @DisplayName("passes correct submission to service")
        void passesCorrectSubmission() {
            AssessmentResult result = new AssessmentResult(
                    "ar-456", "user2", ScaleType.GAD7, 5, "mild", null, 2000L);
            when(assessmentService.submit(any(AssessmentSubmission.class))).thenReturn(result);

            List<Integer> answers = List.of(0, 1, 2, 0, 1, 1, 0);
            AssessmentController.SubmitRequest request =
                    new AssessmentController.SubmitRequest("user2", "GAD7", answers);

            controller.submit(request);

            ArgumentCaptor<AssessmentSubmission> captor = ArgumentCaptor.forClass(AssessmentSubmission.class);
            verify(assessmentService).submit(captor.capture());
            AssessmentSubmission captured = captor.getValue();
            assertEquals("user2", captured.userId());
            assertEquals(ScaleType.GAD7, captured.scaleType());
            assertEquals(answers, captured.answers());
        }

        @Test
        @DisplayName("invalid scale type returns 400 with error message")
        void invalidScaleType() {
            AssessmentController.SubmitRequest request =
                    new AssessmentController.SubmitRequest("user1", "INVALID", List.of(1, 2, 3));

            ResponseEntity<?> response = controller.submit(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, String> body = (Map<String, String>) response.getBody();
            assertNotNull(body);
            assertTrue(body.get("error").contains("INVALID"));
        }

        @Test
        @DisplayName("service exception returns 500 with error message")
        void serviceException() {
            when(assessmentService.submit(any(AssessmentSubmission.class)))
                    .thenThrow(new RuntimeException("Database error"));

            AssessmentController.SubmitRequest request =
                    new AssessmentController.SubmitRequest("user1", "phq9", List.of(1, 2, 3));

            ResponseEntity<?> response = controller.submit(request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, String> body = (Map<String, String>) response.getBody();
            assertNotNull(body);
            assertEquals("Database error", body.get("error"));
        }
    }

    @Nested
    @DisplayName("GET /assessment/{userId}/history - user history")
    class GetHistory {

        @Test
        @DisplayName("returns history list with 200 OK")
        void returnsHistory() {
            List<AssessmentResult> history = List.of(
                    new AssessmentResult("ar-1", "user1", ScaleType.PHQ9, 8, "mild", null, 1000L),
                    new AssessmentResult("ar-2", "user1", ScaleType.GAD7, 14, "moderate", null, 2000L));
            when(assessmentService.getHistory("user1")).thenReturn(history);

            ResponseEntity<List<AssessmentResult>> response = controller.getHistory("user1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().size());
            assertEquals("ar-1", response.getBody().get(0).getId());
        }

        @Test
        @DisplayName("returns empty list when no history exists")
        void emptyHistory() {
            when(assessmentService.getHistory("new-user")).thenReturn(List.of());

            ResponseEntity<List<AssessmentResult>> response = controller.getHistory("new-user");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isEmpty());
        }
    }
}
