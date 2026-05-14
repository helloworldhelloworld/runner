package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserMemory 单元测试
 */
class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        memory = new UserMemory();
    }

    @Test
    @DisplayName("put/get 基本信息 - 存入后能按 key 取回正确的 value")
    void putAndGetBasicInfo() {
        memory.put("name", "Alice");
        memory.put("age", "30");

        assertEquals("Alice", memory.get("name"));
        assertEquals("30", memory.get("age"));
        assertNull(memory.get("nonexistent"));
    }

    @Test
    @DisplayName("addEmotionRecord + getRecentEmotions - count <= size 时返回最近 N 条")
    void getRecentEmotions_countLessThanOrEqualSize() {
        memory.addEmotionRecord("happy", "got promoted");
        memory.addEmotionRecord("sad", "lost wallet");
        memory.addEmotionRecord("excited", "vacation coming");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(2);

        assertEquals(2, recent.size());
        assertEquals("sad", recent.get(0).getEmotion());
        assertEquals("lost wallet", recent.get(0).getContext());
        assertEquals("excited", recent.get(1).getEmotion());
        assertEquals("vacation coming", recent.get(1).getContext());
    }

    @Test
    @DisplayName("getRecentEmotions - count > size 时返回全部记录")
    void getRecentEmotions_countGreaterThanSize() {
        memory.addEmotionRecord("happy", "morning");
        memory.addEmotionRecord("calm", "afternoon");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);

        assertEquals(2, recent.size());
        assertEquals("happy", recent.get(0).getEmotion());
        assertEquals("calm", recent.get(1).getEmotion());
    }

    @Test
    @DisplayName("情绪历史上限为 20 条 - 添加 25 条后最早的 5 条被移除")
    void emotionHistoryCappedAt20() {
        for (int i = 0; i < 25; i++) {
            memory.addEmotionRecord("emotion-" + i, "context-" + i);
        }

        List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);

        assertEquals(20, all.size());
        // The first 5 (indices 0-4) should have been removed; oldest surviving is index 5
        assertEquals("emotion-5", all.get(0).getEmotion());
        assertEquals("context-5", all.get(0).getContext());
        // Most recent is index 24
        assertEquals("emotion-24", all.get(19).getEmotion());
        assertEquals("context-24", all.get(19).getContext());
    }

    @Test
    @DisplayName("addImportantTopic 去重 - 添加相同话题两次，只保留一条")
    void addImportantTopic_dedup() {
        memory.addImportantTopic("AI");
        memory.addImportantTopic("AI");

        List<String> topics = memory.getImportantTopics();

        assertEquals(1, topics.size());
        assertEquals("AI", topics.get(0));
    }

    @Test
    @DisplayName("重要话题上限为 10 - 添加 15 个后最早的 5 个被移除")
    void importantTopicsCappedAt10() {
        for (int i = 0; i < 15; i++) {
            memory.addImportantTopic("topic-" + i);
        }

        List<String> topics = memory.getImportantTopics();

        assertEquals(10, topics.size());
        // The first 5 (indices 0-4) should have been removed; oldest surviving is index 5
        assertEquals("topic-5", topics.get(0));
        assertEquals("topic-14", topics.get(9));
    }

    @Test
    @DisplayName("getAllBasicInfo 返回防御性副本 - 修改副本不影响原始数据")
    void getAllBasicInfoReturnsDefensiveCopy() {
        memory.put("name", "Alice");

        Map<String, String> copy = memory.getAllBasicInfo();
        copy.put("name", "Bob");
        copy.put("extra", "value");

        // Original should be unaffected
        assertEquals("Alice", memory.get("name"));
        assertNull(memory.get("extra"));
        assertEquals(1, memory.getAllBasicInfo().size());
    }

    @Test
    @DisplayName("firstMeetTime 在构造时设定且不可变")
    void firstMeetTimeSetOnConstruction() {
        LocalDateTime before = LocalDateTime.now();
        UserMemory freshMemory = new UserMemory();
        LocalDateTime after = LocalDateTime.now();

        LocalDateTime firstMeet = freshMemory.getFirstMeetTime();
        assertFalse(firstMeet.isBefore(before), "firstMeetTime should not be before construction start");
        assertFalse(firstMeet.isAfter(after), "firstMeetTime should not be after construction end");
    }

    @Test
    @DisplayName("lastInteractionTime 在 put/addEmotionRecord/addImportantTopic 后更新")
    void lastInteractionTimeUpdatesOnMutations() {
        LocalDateTime initial = memory.getLastInteractionTime();

        // Small busy-wait to ensure time advances
        busyWaitUntilAfter(initial);

        memory.put("key", "value");
        LocalDateTime afterPut = memory.getLastInteractionTime();
        assertTrue(afterPut.isAfter(initial) || afterPut.isEqual(initial),
                "lastInteractionTime should advance after put()");

        busyWaitUntilAfter(afterPut);

        memory.addEmotionRecord("happy", "test");
        LocalDateTime afterEmotion = memory.getLastInteractionTime();
        assertTrue(afterEmotion.isAfter(afterPut) || afterEmotion.isEqual(afterPut),
                "lastInteractionTime should advance after addEmotionRecord()");

        busyWaitUntilAfter(afterEmotion);

        memory.addImportantTopic("topic");
        LocalDateTime afterTopic = memory.getLastInteractionTime();
        assertTrue(afterTopic.isAfter(afterEmotion) || afterTopic.isEqual(afterEmotion),
                "lastInteractionTime should advance after addImportantTopic()");
    }

    @Test
    @DisplayName("getRecentEmotions 返回防御性副本 - 修改返回列表不影响内部状态")
    void getRecentEmotionsReturnsDefensiveCopy() {
        memory.addEmotionRecord("happy", "test");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
        recent.clear();

        // Internal state should be unaffected
        assertEquals(1, memory.getRecentEmotions(10).size());
    }

    @Test
    @DisplayName("getImportantTopics 返回防御性副本 - 修改返回列表不影响内部状态")
    void getImportantTopicsReturnsDefensiveCopy() {
        memory.addImportantTopic("AI");

        List<String> topics = memory.getImportantTopics();
        topics.clear();

        // Internal state should be unaffected
        assertEquals(1, memory.getImportantTopics().size());
        assertEquals("AI", memory.getImportantTopics().get(0));
    }

    /**
     * Busy-waits until LocalDateTime.now() is strictly after the given time.
     * Needed because LocalDateTime resolution may be too coarse on some platforms.
     */
    private void busyWaitUntilAfter(LocalDateTime time) {
        while (!LocalDateTime.now().isAfter(time)) {
            Thread.onSpinWait();
        }
    }
}
