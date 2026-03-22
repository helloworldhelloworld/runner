package com.lightweightai.user;

import com.lightweightai.user.model.EmotionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmotionRepository - 情绪记录持久化")
class EmotionRepositoryTest {

    private EmotionRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test_emotions.db").toString();
        repository = new EmotionRepository(dbPath);
    }

    @Test
    @DisplayName("保存并查询情绪记录")
    void shouldSaveAndQueryRecord() {
        long now = System.currentTimeMillis();
        EmotionRecord record = new EmotionRecord("er-test1", "user1", "😊", "今天心情不错", now);
        repository.save(record);

        List<EmotionRecord> found = repository.findByUserIdWithinDays("user1", 1);
        assertEquals(1, found.size());
        assertEquals("er-test1", found.get(0).getId());
        assertEquals("user1", found.get(0).getUserId());
        assertEquals("😊", found.get(0).getEmotion());
        assertEquals("今天心情不错", found.get(0).getNote());
    }

    @Test
    @DisplayName("按时间倒序返回记录")
    void shouldReturnRecordsInReverseChronologicalOrder() {
        long now = System.currentTimeMillis();
        repository.save(new EmotionRecord("er-1", "user1", "😊", null, now - 3600000));
        repository.save(new EmotionRecord("er-2", "user1", "😢", null, now));

        List<EmotionRecord> found = repository.findByUserIdWithinDays("user1", 1);
        assertEquals(2, found.size());
        assertEquals("er-2", found.get(0).getId());
        assertEquals("er-1", found.get(1).getId());
    }

    @Test
    @DisplayName("只返回指定天数内的记录")
    void shouldFilterByDaysWindow() {
        long now = System.currentTimeMillis();
        long threeDaysAgo = now - 3L * 24 * 60 * 60 * 1000;
        long tenDaysAgo = now - 10L * 24 * 60 * 60 * 1000;

        repository.save(new EmotionRecord("er-recent", "user1", "😊", null, now));
        repository.save(new EmotionRecord("er-old", "user1", "😢", null, tenDaysAgo));

        List<EmotionRecord> within7Days = repository.findByUserIdWithinDays("user1", 7);
        assertEquals(1, within7Days.size());
        assertEquals("er-recent", within7Days.get(0).getId());

        List<EmotionRecord> within30Days = repository.findByUserIdWithinDays("user1", 30);
        assertEquals(2, within30Days.size());
    }

    @Test
    @DisplayName("查询不存在的用户返回空列表")
    void shouldReturnEmptyForNonExistentUser() {
        assertTrue(repository.findByUserIdWithinDays("no-user", 30).isEmpty());
    }

    @Test
    @DisplayName("note可以为null")
    void shouldHandleNullNote() {
        long now = System.currentTimeMillis();
        repository.save(new EmotionRecord("er-null", "user1", "😊", null, now));

        List<EmotionRecord> found = repository.findByUserIdWithinDays("user1", 1);
        assertEquals(1, found.size());
        assertNull(found.get(0).getNote());
    }

    @Test
    @DisplayName("今日签到检测 - 有签到")
    void shouldDetectTodayCheckin() {
        long now = System.currentTimeMillis();
        repository.save(new EmotionRecord("er-today", "user1", "😊", null, now));

        assertTrue(repository.hasCheckinToday("user1"));
    }

    @Test
    @DisplayName("今日签到检测 - 无签到")
    void shouldDetectNoTodayCheckin() {
        assertFalse(repository.hasCheckinToday("user1"));
    }

    @Test
    @DisplayName("不同用户记录互不影响")
    void shouldIsolateRecordsByUser() {
        long now = System.currentTimeMillis();
        repository.save(new EmotionRecord("er-u1", "user1", "😊", null, now));
        repository.save(new EmotionRecord("er-u2", "user2", "😢", null, now));

        assertEquals(1, repository.findByUserIdWithinDays("user1", 1).size());
        assertEquals(1, repository.findByUserIdWithinDays("user2", 1).size());
    }
}
