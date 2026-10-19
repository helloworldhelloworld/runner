package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UserMemory — long-term user information storage.
 *
 * Covers:
 * - Basic info CRUD
 * - Emotion history with cap at 20
 * - Important topics with dedup and cap at 10
 * - Timestamp tracking
 */
@DisplayName("UserMemory — long-term user memory store")
class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        memory = new UserMemory();
    }

    @Test
    @DisplayName("put/get basic info")
    void basicInfo_putGet() {
        memory.put("name", "Alice");
        memory.put("age", "25");

        assertEquals("Alice", memory.get("name"));
        assertEquals("25", memory.get("age"));
        assertNull(memory.get("nonexistent"));
    }

    @Test
    @DisplayName("getAllBasicInfo returns defensive copy")
    void getAllBasicInfo_defensiveCopy() {
        memory.put("key1", "val1");
        var info = memory.getAllBasicInfo();
        info.put("key2", "val2");

        assertNull(memory.get("key2"), "External mutation should not affect internal state");
    }

    @Test
    @DisplayName("emotion history stores and retrieves recent records")
    void emotionHistory_recentRecords() {
        memory.addEmotionRecord("happy", "got promotion");
        memory.addEmotionRecord("anxious", "deadline approaching");
        memory.addEmotionRecord("calm", "meditation");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(2);
        assertEquals(2, recent.size());
        assertEquals("anxious", recent.get(0).getEmotion());
        assertEquals("calm", recent.get(1).getEmotion());
    }

    @Test
    @DisplayName("emotion history is capped at 20")
    void emotionHistory_capped() {
        for (int i = 0; i < 25; i++) {
            memory.addEmotionRecord("emotion_" + i, "context_" + i);
        }

        List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);
        assertEquals(20, all.size(), "Emotion history should be capped at 20");
        assertEquals("emotion_5", all.get(0).getEmotion(),
                "Oldest entries should be evicted; first remaining should be emotion_5");
    }

    @Test
    @DisplayName("getRecentEmotions with count > history size returns all")
    void getRecentEmotions_largeCount() {
        memory.addEmotionRecord("happy", "test");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(100);
        assertEquals(1, recent.size());
    }

    @Test
    @DisplayName("important topics with deduplication")
    void importantTopics_dedup() {
        memory.addImportantTopic("coding");
        memory.addImportantTopic("music");
        memory.addImportantTopic("coding"); // duplicate

        List<String> topics = memory.getImportantTopics();
        assertEquals(2, topics.size());
        assertTrue(topics.contains("coding"));
        assertTrue(topics.contains("music"));
    }

    @Test
    @DisplayName("important topics capped at 10")
    void importantTopics_capped() {
        for (int i = 0; i < 15; i++) {
            memory.addImportantTopic("topic_" + i);
        }

        List<String> topics = memory.getImportantTopics();
        assertEquals(10, topics.size(), "Topics should be capped at 10");
        assertEquals("topic_5", topics.get(0),
                "Oldest topics should be evicted; first remaining should be topic_5");
    }

    @Test
    @DisplayName("getImportantTopics returns defensive copy")
    void getImportantTopics_defensiveCopy() {
        memory.addImportantTopic("original");
        List<String> topics = memory.getImportantTopics();
        topics.add("injected");

        assertEquals(1, memory.getImportantTopics().size(),
                "External mutation should not affect internal state");
    }

    @Test
    @DisplayName("timestamps are tracked")
    void timestamps_tracked() {
        assertNotNull(memory.getFirstMeetTime());
        assertNotNull(memory.getLastInteractionTime());

        var before = memory.getLastInteractionTime();
        memory.put("trigger", "update");
        var after = memory.getLastInteractionTime();

        assertTrue(!after.isBefore(before),
                "Last interaction time should be updated on put");
    }

    @Test
    @DisplayName("EmotionRecord toString format")
    void emotionRecord_toString() {
        memory.addEmotionRecord("happy", "got a raise");
        UserMemory.EmotionRecord record = memory.getRecentEmotions(1).get(0);

        String str = record.toString();
        assertTrue(str.contains("happy"), "toString should contain emotion");
        assertTrue(str.contains("got a raise"), "toString should contain context");
    }
}
