package com.lightweightai.web.controller;

import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * REST API for psychological assessments.
 *
 * GET  /assessment/scales               → all scale definitions
 * POST /assessment/submit               → score + AI report
 * GET  /assessment/{userId}/history     → past results
 */
@RestController
@RequestMapping("/assessment")
@CrossOrigin(origins = "*")
public class AssessmentController {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentController.class);

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/scales")
    public ResponseEntity<Map<String, ScaleDefinition>> getScales() {
        return ResponseEntity.ok(assessmentService.getScales());
    }

    @GetMapping("/scales/{type}")
    public ResponseEntity<ScaleDefinition> getScale(@PathVariable String type) {
        try {
            ScaleType scaleType = ScaleType.valueOf(type.toUpperCase());
            return ResponseEntity.ok(assessmentService.getScale(scaleType));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody SubmitRequest request) {
        try {
            ScaleType scaleType = ScaleType.valueOf(request.getScaleType().toUpperCase());
            AssessmentSubmission submission = new AssessmentSubmission(
                request.getUserId(), scaleType, request.getAnswers()
            );
            AssessmentResult result = assessmentService.submit(submission);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Invalid scale type: " + request.getScaleType()));
        } catch (Exception e) {
            logger.error("Assessment submission failed", e);
            return ResponseEntity.status(500).body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<AssessmentResult>> getHistory(@PathVariable String userId) {
        return ResponseEntity.ok(assessmentService.getHistory(userId));
    }

    public static class SubmitRequest {
        private String userId;
        private String scaleType;
        private List<Integer> answers;

        public SubmitRequest() {}

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getScaleType() { return scaleType; }
        public void setScaleType(String scaleType) { this.scaleType = scaleType; }

        public List<Integer> getAnswers() { return answers; }
        public void setAnswers(List<Integer> answers) { this.answers = answers; }
    }
}
