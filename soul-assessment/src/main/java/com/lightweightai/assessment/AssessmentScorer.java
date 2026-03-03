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
        List<Integer> answers = submission.getAnswers();
        switch (submission.getScaleType()) {
            case PHQ9:
            case GAD7:
                return answers.stream().mapToInt(Integer::intValue).sum();
            case PSS10:
                return scorePss10(answers);
            default:
                throw new IllegalArgumentException("Unknown scale type: " + submission.getScaleType());
        }
    }

    /**
     * @return severity label for the given scale and total score
     */
    public String severity(AssessmentSubmission submission, int totalScore) {
        switch (submission.getScaleType()) {
            case PHQ9:
                return Phq9Scale.severity(totalScore);
            case GAD7:
                return Gad7Scale.severity(totalScore);
            case PSS10:
                return Pss10Scale.severity(totalScore);
            default:
                throw new IllegalArgumentException("Unknown scale type: " + submission.getScaleType());
        }
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
