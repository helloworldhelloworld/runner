package com.lightweightai.kernel.feedback;

import java.util.List;

public interface FeedbackIndex {
    FeedbackRecord append(FeedbackRecord feedback);
    List<FeedbackRecord> search(String candidateDigest, String kind);
}
