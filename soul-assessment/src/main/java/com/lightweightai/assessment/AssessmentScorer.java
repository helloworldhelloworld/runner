package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.scale.Gad7Scale;
import com.lightweightai.assessment.scale.Phq9Scale;
import com.lightweightai.assessment.scale.Pss10Scale;
import java.util.List;

/**
 * Calculates total score and severity for a submitted assessment.
 */
public class AssessmentScorer {

    /**
     * @return total score (reverse-scoring applied where applicable)
     */
    public int score(AssessmentSubmission submission) {
        List<Integer> answers = submission.answers();
        return switch (submission.scaleType()) {
            case PHQ9, GAD7 -> answers.stream().mapToInt(Integer::intValue).sum();
            case PSS10 -> scorePss10(answers);
        };
    }

    /**
     * @return severity label for the given scale and total score
     */
    public String severity(AssessmentSubmission submission, int totalScore) {
        return switch (submission.scaleType()) {
            case PHQ9 -> Phq9Scale.severity(totalScore);
            case GAD7 -> Gad7Scale.severity(totalScore);
            case PSS10 -> Pss10Scale.severity(totalScore);
        };
    }

    private int scorePss10(List<Integer> answers) {
        int total = 0;
        int maxScore = 4;
        for (int i = 0; i < answers.size(); i++) {
            int raw = answers.get(i);
            if (Pss10Scale.REVERSED_ITEMS.contains(i)) {
                total += (maxScore - raw);
            } else {
                total += raw;
            }
        }
        return total;
    }
}
