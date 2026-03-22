package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.ScaleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AssessmentRepository - 评估结果持久化")
class AssessmentRepositoryTest {

    private AssessmentRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test_assessment.db").toString();
        repository = new AssessmentRepository(dbPath);
    }

    @Test
    @DisplayName("保存并查询评估结果")
    void shouldSaveAndFindResult() {
        AssessmentResult result = new AssessmentResult(
            "ar-test123456789a", "user1", ScaleType.PHQ9,
            12, "中度抑郁", "测评报告内容", System.currentTimeMillis()
        );
        List<Integer> answers = List.of(1, 2, 1, 2, 1, 2, 1, 1, 1);

        repository.save(result, answers);

        List<AssessmentResult> found = repository.findByUserId("user1");
        assertEquals(1, found.size());
        assertEquals("ar-test123456789a", found.get(0).getId());
        assertEquals("user1", found.get(0).getUserId());
        assertEquals(ScaleType.PHQ9, found.get(0).getScaleType());
        assertEquals(12, found.get(0).getTotalScore());
        assertEquals("中度抑郁", found.get(0).getSeverity());
        assertEquals("测评报告内容", found.get(0).getAiReport());
    }

    @Test
    @DisplayName("按时间倒序返回结果")
    void shouldReturnResultsInReverseChronologicalOrder() {
        long now = System.currentTimeMillis();

        AssessmentResult older = new AssessmentResult(
            "ar-older000000001", "user1", ScaleType.PHQ9,
            5, "轻度抑郁", null, now - 10000
        );
        AssessmentResult newer = new AssessmentResult(
            "ar-newer000000001", "user1", ScaleType.GAD7,
            10, "中度焦虑", null, now
        );

        repository.save(older, List.of(1, 0, 1, 0, 1, 0, 1, 0, 1));
        repository.save(newer, List.of(2, 1, 2, 1, 1, 1, 2));

        List<AssessmentResult> results = repository.findByUserId("user1");
        assertEquals(2, results.size());
        assertEquals("ar-newer000000001", results.get(0).getId());
        assertEquals("ar-older000000001", results.get(1).getId());
    }

    @Test
    @DisplayName("查询不存在的用户返回空列表")
    void shouldReturnEmptyForNonExistentUser() {
        List<AssessmentResult> results = repository.findByUserId("no-such-user");
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("aiReport可以为null")
    void shouldHandleNullAiReport() {
        AssessmentResult result = new AssessmentResult(
            "ar-nullreport0001", "user1", ScaleType.GAD7,
            3, "无焦虑", null, System.currentTimeMillis()
        );
        repository.save(result, List.of(0, 1, 0, 1, 0, 1, 0));

        List<AssessmentResult> found = repository.findByUserId("user1");
        assertEquals(1, found.size());
        assertNull(found.get(0).getAiReport());
    }

    @Test
    @DisplayName("不同用户的结果互不影响")
    void shouldIsolateResultsByUser() {
        repository.save(
            new AssessmentResult("ar-u1result00001", "user1", ScaleType.PHQ9, 5, "轻度抑郁", null, 1000),
            List.of(1, 0, 1, 0, 1, 0, 1, 0, 1)
        );
        repository.save(
            new AssessmentResult("ar-u2result00001", "user2", ScaleType.GAD7, 8, "轻度焦虑", null, 2000),
            List.of(1, 1, 1, 1, 1, 1, 2)
        );

        assertEquals(1, repository.findByUserId("user1").size());
        assertEquals(1, repository.findByUserId("user2").size());
    }
}
