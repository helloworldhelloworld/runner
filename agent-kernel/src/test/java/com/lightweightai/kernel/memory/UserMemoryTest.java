package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserMemory — 用户长期记忆")
class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        memory = new UserMemory();
    }

    @Nested
    @DisplayName("基本信息存取")
    class BasicInfoTests {

        @Test
        @DisplayName("put/get 存取基本信息")
        void putAndGet() {
            memory.put("name", "Alice");
            assertEquals("Alice", memory.get("name"));
        }

        @Test
        @DisplayName("get 不存在的 key 返回 null")
        void getNonexistentReturnsNull() {
            assertNull(memory.get("nonexistent"));
        }

        @Test
        @DisplayName("put 覆盖已有 key")
        void putOverwritesExisting() {
            memory.put("name", "Alice");
            memory.put("name", "Bob");
            assertEquals("Bob", memory.get("name"));
        }

        @Test
        @DisplayName("getAllBasicInfo 返回防御性拷贝")
        void getAllBasicInfoReturnsDefensiveCopy() {
            memory.put("key", "value");
            Map<String, String> info = memory.getAllBasicInfo();
            info.put("hack", "injected");

            assertNull(memory.get("hack"), "Original map should not be affected");
        }
    }

    @Nested
    @DisplayName("情绪记录")
    class EmotionTests {

        @Test
        @DisplayName("添加情绪记录后可通过 getRecentEmotions 获取")
        void addAndGetEmotions() {
            memory.addEmotionRecord("happy", "finished project");

            List<UserMemory.EmotionRecord> emotions = memory.getRecentEmotions(5);
            assertEquals(1, emotions.size());
            assertEquals("happy", emotions.get(0).getEmotion());
            assertEquals("finished project", emotions.get(0).getContext());
            assertNotNull(emotions.get(0).getTimestamp());
        }

        @Test
        @DisplayName("getRecentEmotions 只返回最近 N 条")
        void getRecentEmotionsLimitedByCount() {
            for (int i = 0; i < 10; i++) {
                memory.addEmotionRecord("emotion" + i, "context" + i);
            }

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(3);
            assertEquals(3, recent.size());
            assertEquals("emotion7", recent.get(0).getEmotion());
            assertEquals("emotion9", recent.get(2).getEmotion());
        }

        @Test
        @DisplayName("情绪历史超过 20 条时自动淘汰最旧的")
        void emotionHistoryEvictsOldest() {
            for (int i = 0; i < 25; i++) {
                memory.addEmotionRecord("e" + i, "c" + i);
            }

            List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);
            assertEquals(20, all.size());
            assertEquals("e5", all.get(0).getEmotion());
        }

        @Test
        @DisplayName("getRecentEmotions 请求数大于实际数时返回全部")
        void getRecentEmotionsReturnsAllWhenCountExceeds() {
            memory.addEmotionRecord("sad", "rainy day");

            List<UserMemory.EmotionRecord> emotions = memory.getRecentEmotions(100);
            assertEquals(1, emotions.size());
        }

        @Test
        @DisplayName("getRecentEmotions 返回防御性拷贝")
        void getRecentEmotionsReturnsDefensiveCopy() {
            memory.addEmotionRecord("happy", "context");

            List<UserMemory.EmotionRecord> emotions = memory.getRecentEmotions(5);
            emotions.clear();

            assertEquals(1, memory.getRecentEmotions(5).size());
        }
    }

    @Nested
    @DisplayName("重要话题")
    class TopicTests {

        @Test
        @DisplayName("添加话题后可获取")
        void addAndGetTopics() {
            memory.addImportantTopic("AI");
            memory.addImportantTopic("Music");

            List<String> topics = memory.getImportantTopics();
            assertEquals(2, topics.size());
            assertTrue(topics.contains("AI"));
            assertTrue(topics.contains("Music"));
        }

        @Test
        @DisplayName("重复话题不会重复添加")
        void duplicateTopicIgnored() {
            memory.addImportantTopic("AI");
            memory.addImportantTopic("AI");

            assertEquals(1, memory.getImportantTopics().size());
        }

        @Test
        @DisplayName("话题超过 10 个时淘汰最旧的")
        void topicsEvictOldest() {
            for (int i = 0; i < 15; i++) {
                memory.addImportantTopic("topic" + i);
            }

            List<String> topics = memory.getImportantTopics();
            assertEquals(10, topics.size());
            assertFalse(topics.contains("topic0"));
            assertTrue(topics.contains("topic14"));
        }

        @Test
        @DisplayName("getImportantTopics 返回防御性拷贝")
        void getTopicsReturnsDefensiveCopy() {
            memory.addImportantTopic("AI");

            List<String> topics = memory.getImportantTopics();
            topics.clear();

            assertEquals(1, memory.getImportantTopics().size());
        }
    }

    @Nested
    @DisplayName("时间戳")
    class TimestampTests {

        @Test
        @DisplayName("构造时记录 firstMeetTime")
        void firstMeetTimeSetOnConstruction() {
            assertNotNull(memory.getFirstMeetTime());
        }

        @Test
        @DisplayName("put 操作更新 lastInteractionTime")
        void putUpdatesLastInteraction() {
            var before = memory.getLastInteractionTime();
            memory.put("key", "value");
            var after = memory.getLastInteractionTime();

            assertFalse(after.isBefore(before));
        }

        @Test
        @DisplayName("addEmotionRecord 更新 lastInteractionTime")
        void addEmotionUpdatesLastInteraction() {
            var before = memory.getLastInteractionTime();
            memory.addEmotionRecord("happy", "test");
            var after = memory.getLastInteractionTime();

            assertFalse(after.isBefore(before));
        }

        @Test
        @DisplayName("addImportantTopic 更新 lastInteractionTime")
        void addTopicUpdatesLastInteraction() {
            var before = memory.getLastInteractionTime();
            memory.addImportantTopic("test");
            var after = memory.getLastInteractionTime();

            assertFalse(after.isBefore(before));
        }
    }

    @Test
    @DisplayName("EmotionRecord.toString 包含情绪和上下文")
    void emotionRecordToString() {
        memory.addEmotionRecord("anxious", "exam tomorrow");
        String str = memory.getRecentEmotions(1).get(0).toString();
        assertTrue(str.contains("anxious"));
        assertTrue(str.contains("exam tomorrow"));
    }
}
