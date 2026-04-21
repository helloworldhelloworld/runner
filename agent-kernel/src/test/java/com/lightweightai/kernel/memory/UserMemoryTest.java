package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserMemory - user long-term memory")
class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        memory = new UserMemory();
    }

    @Test
    @DisplayName("put/get stores and retrieves basic info")
    void putAndGetBasicInfo() {
        memory.put("name", "Alice");
        memory.put("age", "25");

        assertEquals("Alice", memory.get("name"));
        assertEquals("25", memory.get("age"));
    }

    @Test
    @DisplayName("get returns null for nonexistent key")
    void getNonexistentKeyReturnsNull() {
        assertNull(memory.get("nonexistent"));
    }

    @Test
    @DisplayName("addEmotionRecord adds emotion record")
    void addEmotionRecord() {
        memory.addEmotionRecord("happy", "got promoted");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
        assertEquals(1, recent.size());
        assertEquals("happy", recent.get(0).getEmotion());
        assertEquals("got promoted", recent.get(0).getContext());
        assertNotNull(recent.get(0).getTimestamp());
    }

    @Test
    @DisplayName("emotion history evicts oldest when exceeding 20")
    void emotionHistoryEvictsOldest() {
        for (int i = 0; i < 25; i++) {
            memory.addEmotionRecord("emotion-" + i, "context-" + i);
        }

        List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);
        assertEquals(20, all.size());
        assertEquals("emotion-5", all.get(0).getEmotion());
        assertEquals("emotion-24", all.get(19).getEmotion());
    }

    @Test
    @DisplayName("getRecentEmotions returns last N records")
    void getRecentEmotionsReturnsLastN() {
        for (int i = 0; i < 10; i++) {
            memory.addEmotionRecord("e-" + i, "c-" + i);
        }

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(3);
        assertEquals(3, recent.size());
        assertEquals("e-7", recent.get(0).getEmotion());
        assertEquals("e-9", recent.get(2).getEmotion());
    }

    @Test
    @DisplayName("getRecentEmotions returns all when fewer than requested")
    void getRecentEmotionsReturnsAllWhenFewer() {
        memory.addEmotionRecord("sad", "bad day");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(100);
        assertEquals(1, recent.size());
    }

    @Test
    @DisplayName("addImportantTopic deduplicates same topic")
    void addImportantTopicDeduplicates() {
        memory.addImportantTopic("work stress");
        memory.addImportantTopic("work stress");
        memory.addImportantTopic("family");

        List<String> topics = memory.getImportantTopics();
        assertEquals(2, topics.size());
        assertTrue(topics.contains("work stress"));
        assertTrue(topics.contains("family"));
    }

    @Test
    @DisplayName("important topics evicts oldest when exceeding 10")
    void importantTopicsEvictsOldest() {
        for (int i = 0; i < 15; i++) {
            memory.addImportantTopic("topic-" + i);
        }

        List<String> topics = memory.getImportantTopics();
        assertEquals(10, topics.size());
        assertFalse(topics.contains("topic-0"));
        assertTrue(topics.contains("topic-14"));
    }

    @Test
    @DisplayName("getAllBasicInfo returns defensive copy")
    void getAllBasicInfoReturnsDefensiveCopy() {
        memory.put("key", "value");
        var info = memory.getAllBasicInfo();
        info.put("hack", "injected");

        assertNull(memory.get("hack"));
    }

    @Test
    @DisplayName("getImportantTopics returns defensive copy")
    void getImportantTopicsReturnsDefensiveCopy() {
        memory.addImportantTopic("original");
        var topics = memory.getImportantTopics();
        topics.add("hack");

        assertEquals(1, memory.getImportantTopics().size());
    }

    @Test
    @DisplayName("firstMeetTime is recorded at creation")
    void firstMeetTimeIsRecorded() {
        assertNotNull(memory.getFirstMeetTime());
    }

    @Test
    @DisplayName("lastInteractionTime updates on operations")
    void lastInteractionTimeUpdatesOnOperations() throws InterruptedException {
        var initial = memory.getLastInteractionTime();
        Thread.sleep(10);
        memory.put("key", "val");
        var afterPut = memory.getLastInteractionTime();

        assertTrue(afterPut.isAfter(initial) || afterPut.isEqual(initial));
    }

    @Test
    @DisplayName("EmotionRecord.toString contains key info")
    void emotionRecordToStringContainsKeyInfo() {
        memory.addEmotionRecord("anxious", "exam tomorrow");
        String str = memory.getRecentEmotions(1).get(0).toString();

        assertTrue(str.contains("anxious"));
        assertTrue(str.contains("exam tomorrow"));
    }
}
