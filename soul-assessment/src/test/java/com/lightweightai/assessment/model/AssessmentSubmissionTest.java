package com.lightweightai.assessment.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AssessmentSubmission - 评估提交记录")
class AssessmentSubmissionTest {

    @Test
    @DisplayName("record 属性正确")
    void recordProperties() {
        List<Integer> answers = List.of(0, 1, 2, 3, 1, 0, 2, 1, 0);
        AssessmentSubmission submission = new AssessmentSubmission("user1", ScaleType.PHQ9, answers);

        assertEquals("user1", submission.userId());
        assertEquals(ScaleType.PHQ9, submission.scaleType());
        assertEquals(9, submission.answers().size());
        assertEquals(0, submission.answers().get(0));
    }
}
