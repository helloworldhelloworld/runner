package com.lightweightai.kernel.feedback;

import java.util.Map;

public record FeedbackRecord(String feedbackId, String principalId, String source, String taskId,
                             String runId, String candidateDigest, String evaluationDigest,
                             String kind, String text, Map<String, Object> attributes, long createdAt) {
    public FeedbackRecord {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
