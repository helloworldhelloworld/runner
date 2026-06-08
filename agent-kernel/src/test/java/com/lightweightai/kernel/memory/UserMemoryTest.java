package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        @DisplayName("put 和 get 基本信息")
        void putAndGet() {
            memory.put("name", "Alice");
            assertEquals("Alice", memory.get("name"));
        }

        @Test
        @DisplayName("get 不存在的 key 返回 null")
        void getNonExistentReturnsNull() {
            assertNull(memory.get("nonexistent"));
        }

        @Test
        @DisplayName("getAllBasicInfo 返回所有键值对")
        void getAllBasicInfo() {
            memory.put("name", "Alice");
            memory.put("age", "30");
            assertEquals(2, memory.getAllBasicInfo().size());
        }
    }

    @Nested
    @DisplayName("情绪记录")
    class EmotionRecords {

        @Test
        @DisplayName("添加并获取情绪记录")
        void addAndGetRecent() {
            memory.addEmotionRecord("happy", "got promotion");
            memory.addEmotionRecord("sad", "lost pet");

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(5);
            assertEquals(2, recent.size());
            assertEquals("happy", recent.get(0).getEmotion());
            assertEquals("got promotion", recent.get(0).getContext());
        }

        @Test
        @DisplayName("getRecentEmotions 限制返回数量")
        void limitRecentCount() {
            for (int i = 0; i < 5; i++) {
                memory.addEmotionRecord("emotion" + i, "ctx" + i);
            }

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(2);
            assertEquals(2, recent.size());
            assertEquals("emotion3", recent.get(0).getEmotion());
            assertEquals("emotion4", recent.get(1).getEmotion());
        }

        @Test
        @DisplayName("情绪历史超过 20 条时移除最早的")
        void emotionHistoryCappedAt20() {
            for (int i = 0; i < 25; i++) {
                memory.addEmotionRecord("e" + i, "c" + i);
            }

            List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);
            assertEquals(20, all.size());
            assertEquals("e5", all.get(0).getEmotion());
        }

        @Test
        @DisplayName("EmotionRecord toString 包含所有字段")
        void emotionRecordToString() {
            memory.addEmotionRecord("happy", "birthday");
            String str = memory.getRecentEmotions(1).get(0).toString();
            assertTrue(str.contains("happy"));
            assertTrue(str.contains("birthday"));
        }
    }

    @Nested
    @DisplayName("重要话题")
    class ImportantTopics {

        @Test
        @DisplayName("添加并获取话题")
        void addAndGetTopics() {
            memory.addImportantTopic("career");
            memory.addImportantTopic("health");

            List<String> topics = memory.getImportantTopics();
            assertEquals(2, topics.size());
            assertTrue(topics.contains("career"));
        }

        @Test
        @DisplayName("重复话题不会被添加")
        void duplicateTopicIgnored() {
            memory.addImportantTopic("career");
            memory.addImportantTopic("career");

            assertEquals(1, memory.getImportantTopics().size());
        }

        @Test
        @DisplayName("话题超过 10 个时移除最早的")
        void topicsCappedAt10() {
            for (int i = 0; i < 15; i++) {
                memory.addImportantTopic("topic" + i);
            }

            List<String> topics = memory.getImportantTopics();
            assertEquals(10, topics.size());
            assertFalse(topics.contains("topic0"));
        }
    }

    @Nested
    @DisplayName("时间戳")
    class Timestamps {

        @Test
        @DisplayName("构造时记录 firstMeetTime")
        void firstMeetTimeRecorded() {
            assertNotNull(memory.getFirstMeetTime());
        }

        @Test
        @DisplayName("操作后 lastInteractionTime 更新")
        void lastInteractionTimeUpdates() {
            var before = memory.getLastInteractionTime();
            memory.put("key", "value");
            var after = memory.getLastInteractionTime();
            assertFalse(after.isBefore(before));
        }
    }
}
