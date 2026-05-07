package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserMemory - 用户长期记忆")
class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        memory = new UserMemory();
    }

    @Nested
    @DisplayName("基本信息存取")
    class BasicInfo {

        @Test
        @DisplayName("put/get → 存储和检索基本信息")
        void putAndGet() {
            memory.put("name", "Alice");
            memory.put("age", "30");

            assertEquals("Alice", memory.get("name"));
            assertEquals("30", memory.get("age"));
        }

        @Test
        @DisplayName("get 不存在的 key → 返回 null")
        void getMissingReturnsNull() {
            assertNull(memory.get("nonexistent"));
        }

        @Test
        @DisplayName("getAllBasicInfo → 返回全部信息的副本")
        void getAllReturnsAll() {
            memory.put("k1", "v1");
            memory.put("k2", "v2");

            Map<String, String> all = memory.getAllBasicInfo();
            assertEquals(2, all.size());
            assertEquals("v1", all.get("k1"));
        }

        @Test
        @DisplayName("getAllBasicInfo 返回副本 — 修改不影响原始")
        void getAllReturnsDefensiveCopy() {
            memory.put("k", "v");
            Map<String, String> copy = memory.getAllBasicInfo();
            copy.put("new", "val");

            assertNull(memory.get("new"));
        }

        @Test
        @DisplayName("put 相同 key → 覆盖旧值")
        void putOverwrites() {
            memory.put("name", "Alice");
            memory.put("name", "Bob");

            assertEquals("Bob", memory.get("name"));
        }
    }

    @Nested
    @DisplayName("情绪记录")
    class EmotionHistory {

        @Test
        @DisplayName("addEmotionRecord + getRecentEmotions → 存取情绪")
        void addAndGetEmotions() {
            memory.addEmotionRecord("happy", "got promoted");
            memory.addEmotionRecord("anxious", "deadline approaching");

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(5);
            assertEquals(2, recent.size());
            assertEquals("happy", recent.get(0).getEmotion());
            assertEquals("got promoted", recent.get(0).getContext());
            assertNotNull(recent.get(0).getTimestamp());
        }

        @Test
        @DisplayName("getRecentEmotions(N) → 只返回最近 N 条")
        void getRecentReturnsLatest() {
            memory.addEmotionRecord("e1", "c1");
            memory.addEmotionRecord("e2", "c2");
            memory.addEmotionRecord("e3", "c3");

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(2);
            assertEquals(2, recent.size());
            assertEquals("e2", recent.get(0).getEmotion());
            assertEquals("e3", recent.get(1).getEmotion());
        }

        @Test
        @DisplayName("getRecentEmotions 请求数超过实际 → 返回全部")
        void getRecentReturnsAllWhenFewer() {
            memory.addEmotionRecord("e1", "c1");

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
            assertEquals(1, recent.size());
        }

        @Test
        @DisplayName("情绪历史上限 20 条 — 超出后自动淘汰最早的")
        void emotionHistoryCapped() {
            for (int i = 0; i < 25; i++) {
                memory.addEmotionRecord("emotion" + i, "context" + i);
            }

            List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);
            assertEquals(20, all.size());
            assertEquals("emotion5", all.get(0).getEmotion());
        }
    }

    @Nested
    @DisplayName("重要话题")
    class ImportantTopics {

        @Test
        @DisplayName("addImportantTopic + getImportantTopics → 存取话题")
        void addAndGetTopics() {
            memory.addImportantTopic("career");
            memory.addImportantTopic("health");

            List<String> topics = memory.getImportantTopics();
            assertEquals(2, topics.size());
            assertTrue(topics.contains("career"));
            assertTrue(topics.contains("health"));
        }

        @Test
        @DisplayName("重复添加相同话题 → 不重复存储")
        void duplicateTopicIgnored() {
            memory.addImportantTopic("career");
            memory.addImportantTopic("career");

            assertEquals(1, memory.getImportantTopics().size());
        }

        @Test
        @DisplayName("话题上限 10 个 — 超出后淘汰最早的")
        void topicsCapped() {
            for (int i = 0; i < 15; i++) {
                memory.addImportantTopic("topic" + i);
            }

            List<String> topics = memory.getImportantTopics();
            assertEquals(10, topics.size());
            assertFalse(topics.contains("topic0"));
            assertTrue(topics.contains("topic14"));
        }

        @Test
        @DisplayName("getImportantTopics 返回副本")
        void returnsDefensiveCopy() {
            memory.addImportantTopic("x");
            List<String> copy = memory.getImportantTopics();
            copy.clear();

            assertEquals(1, memory.getImportantTopics().size());
        }
    }

    @Nested
    @DisplayName("时间追踪")
    class TimeTracking {

        @Test
        @DisplayName("firstMeetTime 在创建时设置")
        void firstMeetTimeSetOnCreation() {
            LocalDateTime before = LocalDateTime.now();
            UserMemory m = new UserMemory();
            LocalDateTime after = LocalDateTime.now();

            assertNotNull(m.getFirstMeetTime());
            assertFalse(m.getFirstMeetTime().isBefore(before));
            assertFalse(m.getFirstMeetTime().isAfter(after));
        }

        @Test
        @DisplayName("put 操作更新 lastInteractionTime")
        void putUpdatesLastInteraction() {
            LocalDateTime initial = memory.getLastInteractionTime();
            memory.put("key", "value");

            assertFalse(memory.getLastInteractionTime().isBefore(initial));
        }

        @Test
        @DisplayName("addEmotionRecord 更新 lastInteractionTime")
        void emotionUpdatesLastInteraction() {
            LocalDateTime initial = memory.getLastInteractionTime();
            memory.addEmotionRecord("happy", "test");

            assertFalse(memory.getLastInteractionTime().isBefore(initial));
        }
    }

    @Test
    @DisplayName("EmotionRecord.toString 包含关键信息")
    void emotionRecordToString() {
        UserMemory.EmotionRecord record = new UserMemory.EmotionRecord(
                "happy", "good news", LocalDateTime.of(2026, 1, 1, 12, 0));

        String str = record.toString();
        assertTrue(str.contains("happy"));
        assertTrue(str.contains("good news"));
    }
}
