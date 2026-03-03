package com.lightweightai.assessment.model;

import java.util.List;

/**
 * A user's submission of answers for a scale.
 */
public class AssessmentSubmission {

    private final String userId;
    private final ScaleType scaleType;
    private final List<Integer> answers;

    public AssessmentSubmission(String userId, ScaleType scaleType, List<Integer> answers) {
        this.userId = userId;
        this.scaleType = scaleType;
        this.answers = answers;
    }

    public String getUserId() { return userId; }
    public ScaleType getScaleType() { return scaleType; }
    public List<Integer> getAnswers() { return answers; }
}
