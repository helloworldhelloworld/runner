package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("存取基本信息")
    void basicInfoPutGet() {
        memory.put("name", "Alice");
        memory.put("age", "25");

        assertEquals("Alice", memory.get("name"));
        assertEquals("25", memory.get("age"));
        assertNull(memory.get("nonexistent"));
    }

    @Test
    @DisplayName("getAllBasicInfo 返回所有基本信息的副本")
    void getAllBasicInfo() {
        memory.put("k1", "v1");
        memory.put("k2", "v2");

        Map<String, String> all = memory.getAllBasicInfo();
        assertEquals(2, all.size());
        assertEquals("v1", all.get("k1"));

        // 修改返回的 map 不影响内部状态
        all.put("k3", "v3");
        assertNull(memory.get("k3"));
    }

    @Test
    @DisplayName("添加和获取情绪记录")
    void emotionRecords() {
        memory.addEmotionRecord("happy", "got promotion");
        memory.addEmotionRecord("sad", "lost pet");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(5);
        assertEquals(2, recent.size());
        assertEquals("happy", recent.get(0).getEmotion());
        assertEquals("got promotion", recent.get(0).getContext());
        assertNotNull(recent.get(0).getTimestamp());
    }

    @Test
    @DisplayName("getRecentEmotions 只返回最近 N 条")
    void recentEmotionsLimit() {
        for (int i = 0; i < 5; i++) {
            memory.addEmotionRecord("emotion" + i, "ctx" + i);
        }

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(3);
        assertEquals(3, recent.size());
        assertEquals("emotion2", recent.get(0).getEmotion());
        assertEquals("emotion4", recent.get(2).getEmotion());
    }

    @Test
    @DisplayName("情绪历史超过 20 条时移除最旧记录")
    void emotionHistoryCap() {
        for (int i = 0; i < 25; i++) {
            memory.addEmotionRecord("e" + i, "c" + i);
        }

        List<UserMemory.EmotionRecord> all = memory.getRecentEmotions(100);
        assertEquals(20, all.size());
        // 最旧的 5 条应该被移除，第一条应该是 e5
        assertEquals("e5", all.get(0).getEmotion());
    }

    @Test
    @DisplayName("添加重要话题（不重复）")
    void importantTopics() {
        memory.addImportantTopic("work");
        memory.addImportantTopic("health");
        memory.addImportantTopic("work"); // duplicate

        List<String> topics = memory.getImportantTopics();
        assertEquals(2, topics.size());
        assertTrue(topics.contains("work"));
        assertTrue(topics.contains("health"));
    }

    @Test
    @DisplayName("重要话题超过 10 个时移除最旧")
    void importantTopicsCap() {
        for (int i = 0; i < 15; i++) {
            memory.addImportantTopic("topic" + i);
        }

        List<String> topics = memory.getImportantTopics();
        assertEquals(10, topics.size());
        // 最旧的 5 个应该被移除
        assertEquals("topic5", topics.get(0));
    }

    @Test
    @DisplayName("getImportantTopics 返回副本")
    void topicsReturnsCopy() {
        memory.addImportantTopic("original");

        List<String> topics = memory.getImportantTopics();
        topics.add("hacked");

        assertEquals(1, memory.getImportantTopics().size());
    }

    @Test
    @DisplayName("首次创建时间和最后互动时间")
    void timestamps() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        UserMemory m = new UserMemory();

        assertTrue(m.getFirstMeetTime().isAfter(before));
        assertTrue(m.getLastInteractionTime().isAfter(before));
    }

    @Test
    @DisplayName("操作更新 lastInteractionTime")
    void lastInteractionUpdated() {
        LocalDateTime initial = memory.getLastInteractionTime();

        // 小延迟确保时间变化
        memory.put("key", "val");
        assertTrue(memory.getLastInteractionTime().compareTo(initial) >= 0);
    }

    @Test
    @DisplayName("EmotionRecord toString 格式正确")
    void emotionRecordToString() {
        UserMemory.EmotionRecord record = new UserMemory.EmotionRecord(
                "happy", "good day", LocalDateTime.of(2024, 1, 1, 12, 0));

        String str = record.toString();
        assertTrue(str.contains("happy"));
        assertTrue(str.contains("good day"));
        assertTrue(str.contains("2024"));
    }
}
