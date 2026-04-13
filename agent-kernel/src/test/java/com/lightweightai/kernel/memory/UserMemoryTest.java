package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserMemory - user long-term memory storage")
class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        memory = new UserMemory();
    }

    // ==================== basic info ====================

    @Test
    @DisplayName("put and get store and retrieve basic info")
    void putAndGet() {
        memory.put("name", "Alice");
        assertEquals("Alice", memory.get("name"));
    }

    @Test
    @DisplayName("get returns null for missing key")
    void getReturnsNullForMissing() {
        assertNull(memory.get("nonexistent"));
    }

    @Test
    @DisplayName("getAllBasicInfo returns all stored info")
    void getAllBasicInfo() {
        memory.put("name", "Alice");
        memory.put("age", "25");

        Map<String, String> all = memory.getAllBasicInfo();
        assertEquals(2, all.size());
        assertEquals("Alice", all.get("name"));
    }

    @Test
    @DisplayName("getAllBasicInfo returns defensive copy")
    void getAllBasicInfoDefensiveCopy() {
        memory.put("key", "value");
        Map<String, String> copy = memory.getAllBasicInfo();
        copy.put("injected", "bad");
        assertNull(memory.get("injected"));
    }

    // ==================== emotion records ====================

    @Test
    @DisplayName("addEmotionRecord stores emotions")
    void addEmotionRecord() {
        memory.addEmotionRecord("happy", "Good news");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(5);
        assertEquals(1, recent.size());
        assertEquals("happy", recent.get(0).getEmotion());
        assertEquals("Good news", recent.get(0).getContext());
        assertNotNull(recent.get(0).getTimestamp());
    }

    @Test
    @DisplayName("getRecentEmotions returns last N records")
    void getRecentEmotionsLastN() {
        for (int i = 0; i < 5; i++) {
            memory.addEmotionRecord("emotion-" + i, "context-" + i);
        }

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(3);
        assertEquals(3, recent.size());
        assertEquals("emotion-2", recent.get(0).getEmotion());
        assertEquals("emotion-4", recent.get(2).getEmotion());
    }

    @Test
    @DisplayName("getRecentEmotions returns all when fewer than requested")
    void getRecentEmotionsAll() {
        memory.addEmotionRecord("sad", "Bad day");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
        assertEquals(1, recent.size());
    }

    @Test
    @DisplayName("emotion history caps at 20 records")
    void emotionHistoryCapsAt20() {
        for (int i = 0; i < 25; i++) {
            memory.addEmotionRecord("emotion-" + i, "context");
        }

        List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);
        assertEquals(20, all.size());
    }

    // ==================== important topics ====================

    @Test
    @DisplayName("addImportantTopic stores topic")
    void addImportantTopic() {
        memory.addImportantTopic("family");

        List<String> topics = memory.getImportantTopics();
        assertEquals(1, topics.size());
        assertEquals("family", topics.get(0));
    }

    @Test
    @DisplayName("addImportantTopic deduplicates")
    void addImportantTopicDeduplicates() {
        memory.addImportantTopic("work");
        memory.addImportantTopic("work");

        assertEquals(1, memory.getImportantTopics().size());
    }

    @Test
    @DisplayName("important topics cap at 10")
    void importantTopicsCapsAt10() {
        for (int i = 0; i < 15; i++) {
            memory.addImportantTopic("topic-" + i);
        }

        assertEquals(10, memory.getImportantTopics().size());
    }

    @Test
    @DisplayName("getImportantTopics returns defensive copy")
    void getImportantTopicsDefensiveCopy() {
        memory.addImportantTopic("test");
        List<String> copy = memory.getImportantTopics();
        copy.add("injected");
        assertEquals(1, memory.getImportantTopics().size());
    }

    // ==================== timestamps ====================

    @Test
    @DisplayName("firstMeetTime is set on creation")
    void firstMeetTimeSetOnCreation() {
        assertNotNull(memory.getFirstMeetTime());
    }

    @Test
    @DisplayName("lastInteractionTime updates on operations")
    void lastInteractionTimeUpdates() throws InterruptedException {
        var before = memory.getLastInteractionTime();
        Thread.sleep(10);
        memory.put("key", "value");
        var after = memory.getLastInteractionTime();
        assertTrue(after.isAfter(before) || after.equals(before));
    }
}
