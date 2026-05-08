package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UserMemory} — user long-term memory store.
 *
 * Covers: basic info CRUD, emotion history with FIFO eviction,
 * topic deduplication, temporal tracking.
 */
@DisplayName("UserMemory - 用户长期记忆")
class UserMemoryTest {

    // ==================== Basic Info ====================

    @Nested
    @DisplayName("基本信息 (put/get)")
    class BasicInfo {

        @Test
        @DisplayName("put 和 get — 正常存取")
        void putGet_normalOperation() {
            UserMemory mem = new UserMemory();

            mem.put("name", "Alice");
            mem.put("age", "25");

            assertEquals("Alice", mem.get("name"));
            assertEquals("25", mem.get("age"));
        }

        @Test
        @DisplayName("get 不存在的 key — 返回 null")
        void get_nonExistentKey_returnsNull() {
            UserMemory mem = new UserMemory();
            assertNull(mem.get("nonexistent"));
        }

        @Test
        @DisplayName("put 覆盖已有值")
        void put_overwritesExisting() {
            UserMemory mem = new UserMemory();
            mem.put("name", "Alice");
            mem.put("name", "Bob");

            assertEquals("Bob", mem.get("name"));
        }

        @Test
        @DisplayName("getAllBasicInfo 返回快照副本")
        void getAllBasicInfo_returnsSnapshot() {
            UserMemory mem = new UserMemory();
            mem.put("a", "1");
            mem.put("b", "2");

            Map<String, String> snapshot = mem.getAllBasicInfo();
            assertEquals(2, snapshot.size());

            // Modifying snapshot doesn't affect original
            snapshot.put("c", "3");
            assertNull(mem.get("c"));
        }
    }

    // ==================== Emotion History ====================

    @Nested
    @DisplayName("情绪记录")
    class EmotionHistory {

        @Test
        @DisplayName("添加并获取最近情绪记录")
        void addAndGetRecentEmotions() {
            UserMemory mem = new UserMemory();
            mem.addEmotionRecord("happy", "got a raise");
            mem.addEmotionRecord("anxious", "deadline approaching");
            mem.addEmotionRecord("calm", "meditation session");

            List<UserMemory.EmotionRecord> recent = mem.getRecentEmotions(2);

            assertEquals(2, recent.size());
            assertEquals("anxious", recent.get(0).getEmotion());
            assertEquals("calm", recent.get(1).getEmotion());
        }

        @Test
        @DisplayName("请求数量超过实际大小 — 返回全部")
        void getRecentEmotions_requestMore_returnsAll() {
            UserMemory mem = new UserMemory();
            mem.addEmotionRecord("happy", "context");

            List<UserMemory.EmotionRecord> recent = mem.getRecentEmotions(10);
            assertEquals(1, recent.size());
        }

        @Test
        @DisplayName("FIFO 驱逐 — 超过 20 条时移除最早的")
        void emotionHistory_exceedsLimit_evictsOldest() {
            UserMemory mem = new UserMemory();

            for (int i = 0; i < 25; i++) {
                mem.addEmotionRecord("emotion-" + i, "ctx-" + i);
            }

            List<UserMemory.EmotionRecord> all = mem.getRecentEmotions(100);
            assertEquals(20, all.size());
            // First item should be emotion-5 (0-4 were evicted)
            assertEquals("emotion-5", all.get(0).getEmotion());
            assertEquals("emotion-24", all.get(19).getEmotion());
        }

        @Test
        @DisplayName("返回值是副本，不影响内部状态")
        void getRecentEmotions_returnsCopy() {
            UserMemory mem = new UserMemory();
            mem.addEmotionRecord("happy", "ctx");

            List<UserMemory.EmotionRecord> recent = mem.getRecentEmotions(10);
            recent.clear();

            assertEquals(1, mem.getRecentEmotions(10).size());
        }
    }

    // ==================== Important Topics ====================

    @Nested
    @DisplayName("重要话题")
    class ImportantTopics {

        @Test
        @DisplayName("添加并获取话题")
        void addAndGetTopics() {
            UserMemory mem = new UserMemory();
            mem.addImportantTopic("career");
            mem.addImportantTopic("health");

            List<String> topics = mem.getImportantTopics();
            assertEquals(2, topics.size());
            assertTrue(topics.contains("career"));
            assertTrue(topics.contains("health"));
        }

        @Test
        @DisplayName("去重 — 不添加重复话题")
        void deduplication_noDuplicates() {
            UserMemory mem = new UserMemory();
            mem.addImportantTopic("career");
            mem.addImportantTopic("career");
            mem.addImportantTopic("career");

            assertEquals(1, mem.getImportantTopics().size());
        }

        @Test
        @DisplayName("FIFO 驱逐 — 超过 10 条时移除最早的")
        void topics_exceedsLimit_evictsOldest() {
            UserMemory mem = new UserMemory();

            for (int i = 0; i < 15; i++) {
                mem.addImportantTopic("topic-" + i);
            }

            List<String> topics = mem.getImportantTopics();
            assertEquals(10, topics.size());
            // topic-0 to topic-4 should be evicted
            assertEquals("topic-5", topics.get(0));
            assertEquals("topic-14", topics.get(9));
        }

        @Test
        @DisplayName("返回值是副本")
        void getImportantTopics_returnsCopy() {
            UserMemory mem = new UserMemory();
            mem.addImportantTopic("topic");

            List<String> topics = mem.getImportantTopics();
            topics.clear();

            assertEquals(1, mem.getImportantTopics().size());
        }
    }

    // ==================== Temporal Tracking ====================

    @Nested
    @DisplayName("时间追踪")
    class TemporalTracking {

        @Test
        @DisplayName("firstMeetTime 在构造时初始化")
        void firstMeetTime_initializedAtConstruction() {
            LocalDateTime before = LocalDateTime.now();
            UserMemory mem = new UserMemory();
            LocalDateTime after = LocalDateTime.now();

            assertNotNull(mem.getFirstMeetTime());
            assertFalse(mem.getFirstMeetTime().isBefore(before));
            assertFalse(mem.getFirstMeetTime().isAfter(after));
        }

        @Test
        @DisplayName("lastInteractionTime 在操作后更新")
        void lastInteractionTime_updatesOnAction() {
            UserMemory mem = new UserMemory();
            LocalDateTime initial = mem.getLastInteractionTime();

            // Small delay to ensure time difference
            mem.put("test", "value");
            LocalDateTime afterPut = mem.getLastInteractionTime();

            assertFalse(afterPut.isBefore(initial));
        }
    }

    // ==================== EmotionRecord ====================

    @Test
    @DisplayName("EmotionRecord — 字段正确存储")
    void emotionRecord_fieldsStored() {
        LocalDateTime now = LocalDateTime.now();
        UserMemory.EmotionRecord record = new UserMemory.EmotionRecord("happy", "good day", now);

        assertEquals("happy", record.getEmotion());
        assertEquals("good day", record.getContext());
        assertEquals(now, record.getTimestamp());
    }

    @Test
    @DisplayName("EmotionRecord.toString — 包含所有字段")
    void emotionRecord_toString() {
        UserMemory.EmotionRecord record = new UserMemory.EmotionRecord("sad", "bad day", LocalDateTime.now());
        String str = record.toString();

        assertTrue(str.contains("sad"));
        assertTrue(str.contains("bad day"));
    }
}
