package com.lightweightai.kernel.feedback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FileFeedbackIndexTest {
    @Test void feedbackIsIdempotentAndBoundToCandidate(@TempDir Path dir) {
        FileFeedbackIndex index = new FileFeedbackIndex(dir.resolve("feedback.json"));
        FeedbackRecord feedback = new FeedbackRecord("f-1", "alice", "user", "t-1", "r-1", "c-1", "e-1", "failure", "late", Map.of(), 1);
        assertSame(feedback, index.append(feedback));
        assertSame(feedback, index.append(new FeedbackRecord("f-1", "alice", "user", "t-2", "r-2", "c-2", "e-2", "other", "duplicate", Map.of(), 2)));
        assertEquals(1, index.search("c-1", "failure").size());
        assertTrue(index.search("c-2", null).isEmpty());
    }
}
