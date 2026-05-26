package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() { memory = new UserMemory(); }

    @Nested
    class BasicInfoTests {

        @Test
        void putAndGet() {
            memory.put("name", "张三");
            assertEquals("张三", memory.get("name"));
        }

        @Test
        void getMissingReturnsNull() { assertNull(memory.get("nonexistent")); }

        @Test
        void overwrite() {
            memory.put("mood", "happy");
            memory.put("mood", "calm");
            assertEquals("calm", memory.get("mood"));
        }

        @Test
        void getAllBasicInfoIsCopy() {
            memory.put("a", "1");
            memory.getAllBasicInfo().put("b", "2");
            assertNull(memory.get("b"));
        }
    }

    @Nested
    class EmotionTests {

        @Test
        void addAndGetEmotions() {
            memory.addEmotionRecord("happy", "got a raise");
            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(5);
            assertEquals(1, recent.size());
            assertEquals("happy", recent.get(0).getEmotion());
            assertEquals("got a raise", recent.get(0).getContext());
            assertNotNull(recent.get(0).getTimestamp());
        }

        @Test
        void recentEmotionsLimited() {
            for (int i = 0; i < 5; i++) memory.addEmotionRecord("e" + i, "c" + i);
            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(2);
            assertEquals(2, recent.size());
            assertEquals("e3", recent.get(0).getEmotion());
        }

        @Test
        void emotionHistoryLimit20() {
            for (int i = 0; i < 25; i++) memory.addEmotionRecord("e" + i, "c" + i);
            assertEquals(20, memory.getRecentEmotions(100).size());
            assertEquals("e5", memory.getRecentEmotions(100).get(0).getEmotion());
        }
    }

    @Nested
    class TopicTests {

        @Test
        void addAndGetTopics() {
            memory.addImportantTopic("career");
            assertTrue(memory.getImportantTopics().contains("career"));
        }

        @Test
        void duplicateTopicIgnored() {
            memory.addImportantTopic("sleep");
            memory.addImportantTopic("sleep");
            assertEquals(1, memory.getImportantTopics().size());
        }

        @Test
        void topicLimit10() {
            for (int i = 0; i < 15; i++) memory.addImportantTopic("topic" + i);
            assertEquals(10, memory.getImportantTopics().size());
            assertFalse(memory.getImportantTopics().contains("topic0"));
        }
    }

    @Test
    void firstMeetTimeSet() {
        assertNotNull(memory.getFirstMeetTime());
        assertTrue(memory.getFirstMeetTime().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void emotionRecordToString() {
        UserMemory.EmotionRecord rec = new UserMemory.EmotionRecord("anxious", "deadline", LocalDateTime.now());
        assertTrue(rec.toString().contains("anxious"));
    }
}
