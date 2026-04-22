package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
    @DisplayName("基本信息")
    class BasicInfoTests {

        @Test
        @DisplayName("put/get 存取基本信息")
        void putAndGet() {
            memory.put("name", "Alice");
            assertEquals("Alice", memory.get("name"));
        }

        @Test
        @DisplayName("get 不存在的 key 返回 null")
        void getReturnsNullForMissing() {
            assertNull(memory.get("missing"));
        }

        @Test
        @DisplayName("put 覆盖已有 key")
        void putOverwrites() {
            memory.put("age", "25");
            memory.put("age", "26");
            assertEquals("26", memory.get("age"));
        }

        @Test
        @DisplayName("getAllBasicInfo 返回副本")
        void getAllBasicInfoReturnsCopy() {
            memory.put("k1", "v1");
            Map<String, String> info = memory.getAllBasicInfo();
            info.clear();
            assertEquals("v1", memory.get("k1"));
        }
    }

    @Nested
    @DisplayName("情绪记录")
    class EmotionTests {

        @Test
        @DisplayName("addEmotionRecord 添加情绪记录并可查询")
        void addAndGetEmotions() {
            memory.addEmotionRecord("happy", "got promotion");
            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(5);
            assertEquals(1, recent.size());
            assertEquals("happy", recent.get(0).getEmotion());
            assertEquals("got promotion", recent.get(0).getContext());
        }

        @Test
        @DisplayName("getRecentEmotions 返回最近 N 条")
        void getRecentReturnsLastN() {
            memory.addEmotionRecord("sad", "c1");
            memory.addEmotionRecord("happy", "c2");
            memory.addEmotionRecord("angry", "c3");

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(2);
            assertEquals(2, recent.size());
            assertEquals("happy", recent.get(0).getEmotion());
            assertEquals("angry", recent.get(1).getEmotion());
        }

        @Test
        @DisplayName("getRecentEmotions 不足 N 条时返回全部")
        void getRecentReturnsAllIfFewer() {
            memory.addEmotionRecord("calm", "rest");
            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
            assertEquals(1, recent.size());
        }

        @Test
        @DisplayName("情绪历史上限 20 条，超出后移除最早的")
        void emotionHistoryCapAt20() {
            for (int i = 0; i < 25; i++) {
                memory.addEmotionRecord("e" + i, "ctx" + i);
            }
            List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);
            assertEquals(20, all.size());
            assertEquals("e5", all.get(0).getEmotion());
            assertEquals("e24", all.get(all.size() - 1).getEmotion());
        }
    }

    @Nested
    @DisplayName("重要话题")
    class TopicTests {

        @Test
        @DisplayName("addImportantTopic 添加话题")
        void addTopic() {
            memory.addImportantTopic("work");
            assertEquals(List.of("work"), memory.getImportantTopics());
        }

        @Test
        @DisplayName("重复话题不重复添加")
        void duplicateTopicIgnored() {
            memory.addImportantTopic("family");
            memory.addImportantTopic("family");
            assertEquals(1, memory.getImportantTopics().size());
        }

        @Test
        @DisplayName("话题上限 10 条，超出后移除最早的")
        void topicCapAt10() {
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
        void returnsCopy() {
            memory.addImportantTopic("t1");
            List<String> topics = memory.getImportantTopics();
            topics.clear();
            assertEquals(1, memory.getImportantTopics().size());
        }
    }

    @Nested
    @DisplayName("时间戳")
    class TimestampTests {

        @Test
        @DisplayName("构造时设置 firstMeetTime")
        void firstMeetTimeSet() {
            assertNotNull(memory.getFirstMeetTime());
            assertTrue(memory.getFirstMeetTime().isBefore(LocalDateTime.now().plusSeconds(1)));
        }

        @Test
        @DisplayName("操作后更新 lastInteractionTime")
        void lastInteractionTimeUpdated() {
            LocalDateTime before = memory.getLastInteractionTime();
            memory.put("k", "v");
            LocalDateTime after = memory.getLastInteractionTime();
            assertFalse(after.isBefore(before));
        }
    }
}
