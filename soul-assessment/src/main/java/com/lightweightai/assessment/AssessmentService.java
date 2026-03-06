package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import com.lightweightai.assessment.scale.Gad7Scale;
import com.lightweightai.assessment.scale.Phq9Scale;
import com.lightweightai.assessment.scale.Pss10Scale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business facade for psychological assessments.
 */
public class AssessmentService {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentService.class);

    private final AssessmentScorer scorer;
    private final AssessmentReportGenerator reportGenerator;
    private final AssessmentRepository repository;

    public AssessmentService(AssessmentScorer scorer,
                             AssessmentReportGenerator reportGenerator,
                             AssessmentRepository repository) {
        this.scorer = scorer;
        this.reportGenerator = reportGenerator;
        this.repository = repository;
    }

    /**
     * Get all available scale definitions.
     */
    public Map<String, ScaleDefinition> getScales() {
        return Map.of(
            ScaleType.PHQ9.name(), Phq9Scale.definition(),
            ScaleType.GAD7.name(), Gad7Scale.definition(),
            ScaleType.PSS10.name(), Pss10Scale.definition()
        );
    }

    /**
     * Get a single scale definition.
     */
    public ScaleDefinition getScale(ScaleType type) {
        return switch (type) {
            case PHQ9 -> Phq9Scale.definition();
            case GAD7 -> Gad7Scale.definition();
            case PSS10 -> Pss10Scale.definition();
        };
    }

    /**
     * Score and save an assessment submission, generating an AI report.
     */
    public AssessmentResult submit(AssessmentSubmission submission) {
        int totalScore = scorer.score(submission);
        String severity = scorer.severity(submission, totalScore);

        String id = "ar-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();

        AssessmentResult result = new AssessmentResult(
            id, submission.userId(), submission.scaleType(),
            totalScore, severity, null, now
        );

        // Generate AI report
        String aiReport = reportGenerator.generate(result);
        result.setAiReport(aiReport);

        repository.save(result, submission.answers());
        logger.info("Assessment submitted - user: {}, scale: {}, score: {}, severity: {}",
            submission.userId(), submission.scaleType(), totalScore, severity);

        return result;
    }

    /**
     * Get assessment history for a user.
     */
    public List<AssessmentResult> getHistory(String userId) {
        return repository.findByUserId(userId);
    }
}
