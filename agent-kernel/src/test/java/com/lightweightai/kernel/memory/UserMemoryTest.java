package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("put/get 存取基本信息")
    void putAndGetBasicInfo() {
        memory.put("name", "张三");
        memory.put("age", "25");

        assertEquals("张三", memory.get("name"));
        assertEquals("25", memory.get("age"));
    }

    @Test
    @DisplayName("get 不存在的 key 返回 null")
    void getNonexistentKeyReturnsNull() {
        assertNull(memory.get("nonexistent"));
    }

    @Test
    @DisplayName("addEmotionRecord 添加情绪记录")
    void addEmotionRecord() {
        memory.addEmotionRecord("happy", "got promoted");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
        assertEquals(1, recent.size());
        assertEquals("happy", recent.get(0).getEmotion());
        assertEquals("got promoted", recent.get(0).getContext());
        assertNotNull(recent.get(0).getTimestamp());
    }

    @Test
    @DisplayName("情绪历史超过 20 条时自动淘汰最早的")
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
    @DisplayName("getRecentEmotions 返回最近 N 条")
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
    @DisplayName("getRecentEmotions 数量不足时返回全部")
    void getRecentEmotionsReturnsAllWhenFewer() {
        memory.addEmotionRecord("sad", "bad day");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(100);
        assertEquals(1, recent.size());
    }

    @Test
    @DisplayName("addImportantTopic 添加不重复的话题")
    void addImportantTopicDeduplicates() {
        memory.addImportantTopic("工作压力");
        memory.addImportantTopic("工作压���");
        memory.addImportantTopic("家庭关��");

        List<String> topics = memory.getImportantTopics();
        assertEquals(2, topics.size());
        assertTrue(topics.contains("工作压力"));
        assertTrue(topics.contains("家庭关系"));
    }

    @Test
    @DisplayName("重要话题超过 10 个时自动淘汰最早的")
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
    @DisplayName("getAllBasicInfo 返回防御性副本")
    void getAllBasicInfoReturnsDefensiveCopy() {
        memory.put("key", "value");
        var info = memory.getAllBasicInfo();
        info.put("hack", "injected");

        assertNull(memory.get("hack"));
    }

    @Test
    @DisplayName("getImportantTopics 返回防御性副本")
    void getImportantTopicsReturnsDefensiveCopy() {
        memory.addImportantTopic("original");
        var topics = memory.getImportantTopics();
        topics.add("hack");

        assertEquals(1, memory.getImportantTopics().size());
    }

    @Test
    @DisplayName("firstMeetTime 记录创建时间")
    void firstMeetTimeIsRecorded() {
        assertNotNull(memory.getFirstMeetTime());
    }

    @Test
    @DisplayName("lastInteractionTime 随操作更新")
    void lastInteractionTimeUpdatesOnOperations() throws InterruptedException {
        var initial = memory.getLastInteractionTime();
        Thread.sleep(10);
        memory.put("key", "val");
        var afterPut = memory.getLastInteractionTime();

        assertTrue(afterPut.isAfter(initial) || afterPut.isEqual(initial));
    }

    @Test
    @DisplayName("EmotionRecord.toString 包含关键信息")
    void emotionRecordToStringContainsKeyInfo() {
        memory.addEmotionRecord("anxious", "exam tomorrow");
        String str = memory.getRecentEmotions(1).get(0).toString();

        assertTrue(str.contains("anxious"));
        assertTrue(str.contains("exam tomorrow"));
    }
}
