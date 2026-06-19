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
@DisplayName("AssessmentController - 心理评估 API")
class AssessmentControllerTest {

    @Mock
    private AssessmentService assessmentService;

    private AssessmentController controller;

    @BeforeEach
    void setUp() {
        controller = new AssessmentController(assessmentService);
    }

    @Nested
    @DisplayName("GET /assessment/scales - 获取所有量表定义")
    class GetScales {

        @Test
        @DisplayName("返回所有量表定义的 Map")
        void returnsAllScales() {
            ScaleDefinition phq9 = new ScaleDefinition(
                    ScaleType.PHQ9, "PHQ-9", "抑郁筛查",
                    List.of(new ScaleDefinition.Question(0, "兴趣减退")),
                    List.of("没有", "偶尔", "经常", "几乎每天"),
                    List.of(0, 1, 2, 3)
            );
            Map<String, ScaleDefinition> scales = Map.of("PHQ9", phq9);
            when(assessmentService.getScales()).thenReturn(scales);

            ResponseEntity<Map<String, ScaleDefinition>> response = controller.getScales();

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            assertTrue(response.getBody().containsKey("PHQ9"));
            assertEquals("PHQ-9", response.getBody().get("PHQ9").title());
        }
    }

    @Nested
    @DisplayName("GET /assessment/scales/{type} - 获取单个量表定义")
    class GetScale {

        @Test
        @DisplayName("有效的量表类型返回量表定义")
        void validScaleType() {
            ScaleDefinition gad7 = new ScaleDefinition(
                    ScaleType.GAD7, "GAD-7", "焦虑筛查",
                    List.of(new ScaleDefinition.Question(0, "紧张焦虑")),
                    List.of("没有", "偶尔", "经常", "几乎每天"),
                    List.of(0, 1, 2, 3)
            );
            when(assessmentService.getScale(ScaleType.GAD7)).thenReturn(gad7);

            ResponseEntity<ScaleDefinition> response = controller.getScale("gad7");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(ScaleType.GAD7, response.getBody().type());
            assertEquals("GAD-7", response.getBody().title());
        }

        @Test
        @DisplayName("大写类型名也能正确解析")
        void uppercaseScaleType() {
            ScaleDefinition pss10 = new ScaleDefinition(
                    ScaleType.PSS10, "PSS-10", "压力量表",
                    List.of(), List.of(), List.of()
            );
            when(assessmentService.getScale(ScaleType.PSS10)).thenReturn(pss10);

            ResponseEntity<ScaleDefinition> response = controller.getScale("PSS10");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(ScaleType.PSS10, response.getBody().type());
        }

        @Test
        @DisplayName("无效的量表类型返回 400")
        void invalidScaleType() {
            ResponseEntity<ScaleDefinition> response = controller.getScale("INVALID");

            assertEquals(400, response.getStatusCode().value());
            assertNull(response.getBody());
        }
    }

    @Nested
    @DisplayName("POST /assessment/submit - 提交评估")
    class Submit {

        @Test
        @DisplayName("提交有效评估返回评估结果")
        void submitValid() {
            AssessmentResult result = new AssessmentResult(
                    "r1", "u1", ScaleType.PHQ9, 12, "moderate",
                    "中度抑郁建议就医", System.currentTimeMillis()
            );
            when(assessmentService.submit(any(AssessmentSubmission.class))).thenReturn(result);

            AssessmentController.SubmitRequest request =
                    new AssessmentController.SubmitRequest("u1", "phq9", List.of(1, 2, 1, 0, 2, 1, 2, 1, 2));
            ResponseEntity<?> response = controller.submit(request);

            assertEquals(200, response.getStatusCode().value());
            AssessmentResult body = (AssessmentResult) response.getBody();
            assertNotNull(body);
            assertEquals("r1", body.getId());
            assertEquals("u1", body.getUserId());
            assertEquals(ScaleType.PHQ9, body.getScaleType());
            assertEquals(12, body.getTotalScore());
            assertEquals("moderate", body.getSeverity());
            assertEquals("中度抑郁建议就医", body.getAiReport());

            // Verify the submission was constructed with correct ScaleType
            ArgumentCaptor<AssessmentSubmission> captor =
                    ArgumentCaptor.forClass(AssessmentSubmission.class);
            verify(assessmentService).submit(captor.capture());
            AssessmentSubmission captured = captor.getValue();
            assertEquals("u1", captured.userId());
            assertEquals(ScaleType.PHQ9, captured.scaleType());
            assertEquals(9, captured.answers().size());
        }

        @Test
        @DisplayName("无效的量表类型返回 400 并包含错误信息")
        void submitInvalidScaleType() {
            AssessmentController.SubmitRequest request =
                    new AssessmentController.SubmitRequest("u1", "BOGUS", List.of(1, 2, 3));
            ResponseEntity<?> response = controller.submit(request);

            assertEquals(400, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            String error = (String) body.get("error");
            assertTrue(error.contains("BOGUS"));
        }

        @Test
        @DisplayName("服务异常返回 500")
        void submitServiceError() {
            when(assessmentService.submit(any(AssessmentSubmission.class)))
                    .thenThrow(new RuntimeException("AI service unavailable"));

            AssessmentController.SubmitRequest request =
                    new AssessmentController.SubmitRequest("u1", "phq9", List.of(1, 2, 3));
            ResponseEntity<?> response = controller.submit(request);

            assertEquals(500, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals("AI service unavailable", body.get("error"));
        }
    }

    @Nested
    @DisplayName("GET /assessment/{userId}/history - 评估历史")
    class GetHistory {

        @Test
        @DisplayName("返回用户的评估历史列表")
        void returnsHistory() {
            AssessmentResult r1 = new AssessmentResult(
                    "r1", "u1", ScaleType.PHQ9, 5, "mild", "轻度", 1000L);
            AssessmentResult r2 = new AssessmentResult(
                    "r2", "u1", ScaleType.GAD7, 10, "moderate", "中度", 2000L);
            when(assessmentService.getHistory("u1")).thenReturn(List.of(r1, r2));

            ResponseEntity<List<AssessmentResult>> response = controller.getHistory("u1");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().size());
            assertEquals("r1", response.getBody().get(0).getId());
            assertEquals(ScaleType.PHQ9, response.getBody().get(0).getScaleType());
            assertEquals("r2", response.getBody().get(1).getId());
            assertEquals(ScaleType.GAD7, response.getBody().get(1).getScaleType());
        }

        @Test
        @DisplayName("没有历史记录返回空列表")
        void emptyHistory() {
            when(assessmentService.getHistory("new-user")).thenReturn(List.of());

            ResponseEntity<List<AssessmentResult>> response = controller.getHistory("new-user");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isEmpty());
        }
    }
}
