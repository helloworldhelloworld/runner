package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserMemory - long-term user memory store")
class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        memory = new UserMemory();
    }

    @Nested
    @DisplayName("Given basic info operations")
    class BasicInfo {

        @Test
        @DisplayName("put and get store and retrieve a value")
        void shouldStoreAndRetrieve() {
            // When
            memory.put("name", "Alice");

            // Then
            assertEquals("Alice", memory.get("name"));
        }

        @Test
        @DisplayName("get returns null for unknown key")
        void shouldReturnNullForUnknownKey() {
            assertNull(memory.get("nonexistent"));
        }

        @Test
        @DisplayName("put overwrites existing value")
        void shouldOverwriteExistingValue() {
            memory.put("city", "Beijing");
            memory.put("city", "Shanghai");

            assertEquals("Shanghai", memory.get("city"));
        }

        @Test
        @DisplayName("multiple keys are stored independently")
        void shouldStoreMultipleKeys() {
            memory.put("name", "Bob");
            memory.put("age", "30");

            assertEquals("Bob", memory.get("name"));
            assertEquals("30", memory.get("age"));
        }

        @Test
        @DisplayName("getAllBasicInfo returns all stored entries")
        void shouldReturnAllBasicInfo() {
            memory.put("k1", "v1");
            memory.put("k2", "v2");

            var info = memory.getAllBasicInfo();
            assertEquals(2, info.size());
            assertEquals("v1", info.get("k1"));
            assertEquals("v2", info.get("k2"));
        }
    }

    @Nested
    @DisplayName("Given emotion history")
    class EmotionHistory {

        @Test
        @DisplayName("addEmotionRecord stores an emotion record")
        void shouldStoreEmotionRecord() {
            memory.addEmotionRecord("happy", "got promoted");

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
            assertEquals(1, recent.size());
            assertEquals("happy", recent.get(0).getEmotion());
            assertEquals("got promoted", recent.get(0).getContext());
            assertNotNull(recent.get(0).getTimestamp());
        }

        @Test
        @DisplayName("getRecentEmotions returns last N emotions")
        void shouldReturnLastNEmotions() {
            memory.addEmotionRecord("happy", "ctx1");
            memory.addEmotionRecord("sad", "ctx2");
            memory.addEmotionRecord("excited", "ctx3");
            memory.addEmotionRecord("calm", "ctx4");

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(2);

            assertEquals(2, recent.size());
            assertEquals("excited", recent.get(0).getEmotion());
            assertEquals("calm", recent.get(1).getEmotion());
        }

        @Test
        @DisplayName("getRecentEmotions returns all when count exceeds size")
        void shouldReturnAllWhenCountExceedsSize() {
            memory.addEmotionRecord("happy", "ctx1");
            memory.addEmotionRecord("sad", "ctx2");

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);

            assertEquals(2, recent.size());
        }

        @Test
        @DisplayName("emotion history caps at 20 entries, removing oldest")
        void shouldCapAt20Entries() {
            // Given - add 22 emotion records
            for (int i = 1; i <= 22; i++) {
                memory.addEmotionRecord("emotion-" + i, "context-" + i);
            }

            // When
            List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);

            // Then - should have 20 entries, oldest removed
            assertEquals(20, all.size());
            // The first two (emotion-1, emotion-2) should have been removed
            assertEquals("emotion-3", all.get(0).getEmotion());
            assertEquals("emotion-22", all.get(19).getEmotion());
        }

        @Test
        @DisplayName("getRecentEmotions returns a defensive copy")
        void shouldReturnDefensiveCopy() {
            memory.addEmotionRecord("happy", "ctx");

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
            recent.clear();

            // Original should not be affected
            assertEquals(1, memory.getRecentEmotions(10).size());
        }
    }

    @Nested
    @DisplayName("Given important topics")
    class ImportantTopics {

        @Test
        @DisplayName("addImportantTopic stores a topic")
        void shouldStoreTopic() {
            memory.addImportantTopic("machine learning");

            List<String> topics = memory.getImportantTopics();
            assertEquals(1, topics.size());
            assertEquals("machine learning", topics.get(0));
        }

        @Test
        @DisplayName("duplicate topics are not added")
        void shouldDeduplicateTopics() {
            memory.addImportantTopic("AI");
            memory.addImportantTopic("AI");
            memory.addImportantTopic("AI");

            List<String> topics = memory.getImportantTopics();
            assertEquals(1, topics.size());
        }

        @Test
        @DisplayName("topics cap at 10 entries, removing oldest")
        void shouldCapAt10Topics() {
            // Given - add 12 unique topics
            for (int i = 1; i <= 12; i++) {
                memory.addImportantTopic("topic-" + i);
            }

            // When
            List<String> topics = memory.getImportantTopics();

            // Then - should have 10 topics, oldest removed
            assertEquals(10, topics.size());
            assertEquals("topic-3", topics.get(0));
            assertEquals("topic-12", topics.get(9));
        }

        @Test
        @DisplayName("getImportantTopics returns a defensive copy")
        void shouldReturnDefensiveCopy() {
            memory.addImportantTopic("topic1");

            List<String> topics = memory.getImportantTopics();
            topics.clear();

            // Original should not be affected
            assertEquals(1, memory.getImportantTopics().size());
        }
    }

    @Nested
    @DisplayName("Given timestamps")
    class Timestamps {

        @Test
        @DisplayName("firstMeetTime is set on construction")
        void shouldSetFirstMeetTime() {
            assertNotNull(memory.getFirstMeetTime());
        }

        @Test
        @DisplayName("lastInteractionTime is set on construction")
        void shouldSetLastInteractionTime() {
            assertNotNull(memory.getLastInteractionTime());
        }
    }

    @Nested
    @DisplayName("EmotionRecord")
    class EmotionRecordTests {

        @Test
        @DisplayName("toString contains emotion and context")
        void toStringShouldContainEmotionAndContext() {
            memory.addEmotionRecord("anxious", "exam coming");

            UserMemory.EmotionRecord record = memory.getRecentEmotions(1).get(0);
            String str = record.toString();

            assertTrue(str.contains("anxious"), "should contain emotion");
            assertTrue(str.contains("exam coming"), "should contain context");
        }
    }
}
