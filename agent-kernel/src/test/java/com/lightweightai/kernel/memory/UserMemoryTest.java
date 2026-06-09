package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserMemory — user long-term memory storage")
class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        memory = new UserMemory();
    }

    @Nested
    @DisplayName("Basic info — ConcurrentHashMap storage")
    class BasicInfo {

        @Test
        void putAndGetRoundTrips() {
            memory.put("name", "Alice");
            assertEquals("Alice", memory.get("name"));
        }

        @Test
        void getNonexistentReturnsNull() {
            assertNull(memory.get("missing"));
        }

        @Test
        void putOverwritesExisting() {
            memory.put("color", "red");
            memory.put("color", "blue");
            assertEquals("blue", memory.get("color"));
        }

        @Test
        void getAllBasicInfoReturnsCopy() {
            memory.put("a", "1");
            memory.put("b", "2");

            Map<String, String> all = memory.getAllBasicInfo();
            assertEquals(2, all.size());
            assertEquals("1", all.get("a"));

            all.clear();
            assertEquals("1", memory.get("a"));
        }
    }

    @Nested
    @DisplayName("Emotion history — capped list")
    class EmotionHistory {

        @Test
        void addEmotionRecordStoresIt() {
            memory.addEmotionRecord("happy", "got a promotion");
            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
            assertEquals(1, recent.size());
            assertEquals("happy", recent.get(0).getEmotion());
            assertEquals("got a promotion", recent.get(0).getContext());
        }

        @Test
        void getRecentEmotionsReturnsLatestN() {
            for (int i = 0; i < 10; i++) {
                memory.addEmotionRecord("mood-" + i, "ctx-" + i);
            }

            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(3);
            assertEquals(3, recent.size());
            assertEquals("mood-7", recent.get(0).getEmotion());
            assertEquals("mood-9", recent.get(2).getEmotion());
        }

        @Test
        void getRecentEmotionsReturnsAllWhenLessThanCount() {
            memory.addEmotionRecord("happy", "ctx");
            List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
            assertEquals(1, recent.size());
        }

        @Test
        void historyIsCappedAt20() {
            for (int i = 0; i < 25; i++) {
                memory.addEmotionRecord("e-" + i, "c-" + i);
            }
            List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);
            assertEquals(20, all.size());
            assertEquals("e-5", all.get(0).getEmotion());
        }

        @Test
        void returnsDefensiveCopy() {
            memory.addEmotionRecord("happy", "ctx");
            List<UserMemory.EmotionRecord> list = memory.getRecentEmotions(10);
            list.clear();
            assertEquals(1, memory.getRecentEmotions(10).size());
        }
    }

    @Nested
    @DisplayName("Important topics — deduped capped list")
    class ImportantTopics {

        @Test
        void addTopicStoresIt() {
            memory.addImportantTopic("AI");
            List<String> topics = memory.getImportantTopics();
            assertEquals(1, topics.size());
            assertEquals("AI", topics.get(0));
        }

        @Test
        void duplicateTopicIsIgnored() {
            memory.addImportantTopic("AI");
            memory.addImportantTopic("AI");
            assertEquals(1, memory.getImportantTopics().size());
        }

        @Test
        void topicsAreCappedAt10() {
            for (int i = 0; i < 15; i++) {
                memory.addImportantTopic("topic-" + i);
            }
            List<String> topics = memory.getImportantTopics();
            assertEquals(10, topics.size());
            assertEquals("topic-5", topics.get(0));
        }

        @Test
        void returnsDefensiveCopy() {
            memory.addImportantTopic("AI");
            List<String> topics = memory.getImportantTopics();
            topics.clear();
            assertEquals(1, memory.getImportantTopics().size());
        }
    }

    @Nested
    @DisplayName("Timestamps")
    class Timestamps {

        @Test
        void firstMeetTimeIsSetOnConstruction() {
            LocalDateTime now = LocalDateTime.now();
            assertNotNull(memory.getFirstMeetTime());
            assertTrue(memory.getFirstMeetTime().isBefore(now.plusSeconds(1)));
        }

        @Test
        void lastInteractionTimeUpdatesOnPut() {
            LocalDateTime before = memory.getLastInteractionTime();
            memory.put("key", "val");
            assertTrue(memory.getLastInteractionTime().compareTo(before) >= 0);
        }

        @Test
        void lastInteractionTimeUpdatesOnEmotionAdd() {
            LocalDateTime before = memory.getLastInteractionTime();
            memory.addEmotionRecord("happy", "test");
            assertTrue(memory.getLastInteractionTime().compareTo(before) >= 0);
        }

        @Test
        void lastInteractionTimeUpdatesOnTopicAdd() {
            LocalDateTime before = memory.getLastInteractionTime();
            memory.addImportantTopic("new topic");
            assertTrue(memory.getLastInteractionTime().compareTo(before) >= 0);
        }
    }

    @Nested
    @DisplayName("EmotionRecord")
    class EmotionRecordTests {

        @Test
        void fieldsPreserved() {
            LocalDateTime ts = LocalDateTime.of(2024, 1, 1, 12, 0);
            UserMemory.EmotionRecord record = new UserMemory.EmotionRecord("sad", "bad day", ts);
            assertEquals("sad", record.getEmotion());
            assertEquals("bad day", record.getContext());
            assertEquals(ts, record.getTimestamp());
        }

        @Test
        void toStringContainsFields() {
            LocalDateTime ts = LocalDateTime.of(2024, 6, 15, 10, 30);
            UserMemory.EmotionRecord record = new UserMemory.EmotionRecord("anxious", "exam", ts);
            String s = record.toString();
            assertTrue(s.contains("anxious"));
            assertTrue(s.contains("exam"));
        }
    }
}
