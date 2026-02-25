package com.lightweightai.assessment.model;

import java.util.List;

/**
 * A user's submission of answers for a scale.
 */
public record AssessmentSubmission(
    String userId,
    ScaleType scaleType,
    List<Integer> answers
) {}
