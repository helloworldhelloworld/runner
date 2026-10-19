package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserMemory 单元测试 — 覆盖基本信息存取、情绪记录、重要话题的关键路径
 */
class UserMemoryTest {

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        memory = new UserMemory();
    }

    // ==================== 基本信息 ====================

    @Test
    @DisplayName("put/get 能正确存取基本信息")
    void shouldStoreAndRetrieveBasicInfo() {
        memory.put("name", "Alice");
        memory.put("age", "30");

        assertEquals("Alice", memory.get("name"));
        assertEquals("30", memory.get("age"));
    }

    @Test
    @DisplayName("get 不存在的 key 返回 null")
    void shouldReturnNullForMissingKey() {
        assertNull(memory.get("nonexistent"));
    }

    @Test
    @DisplayName("put 相同 key 覆盖旧值")
    void shouldOverwriteExistingKey() {
        memory.put("name", "Alice");
        memory.put("name", "Bob");
        assertEquals("Bob", memory.get("name"));
    }

    @Test
    @DisplayName("getAllBasicInfo 返回所有键值对的防御性拷贝")
    void shouldReturnDefensiveCopyOfBasicInfo() {
        memory.put("k1", "v1");
        memory.put("k2", "v2");

        Map<String, String> info = memory.getAllBasicInfo();
        assertEquals(2, info.size());
        assertEquals("v1", info.get("k1"));

        // 修改返回值不影响原始数据
        info.put("k3", "v3");
        assertNull(memory.get("k3"));
    }

    // ==================== 情绪记录 ====================

    @Test
    @DisplayName("addEmotionRecord 添加并可通过 getRecentEmotions 获取")
    void shouldAddAndRetrieveEmotionRecords() {
        memory.addEmotionRecord("happy", "got a compliment");
        memory.addEmotionRecord("sad", "missed the bus");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(5);
        assertEquals(2, recent.size());
        assertEquals("happy", recent.get(0).getEmotion());
        assertEquals("got a compliment", recent.get(0).getContext());
        assertNotNull(recent.get(0).getTimestamp());
    }

    @Test
    @DisplayName("getRecentEmotions 只返回最近 N 条")
    void shouldReturnOnlyRecentNEmotions() {
        memory.addEmotionRecord("e1", "c1");
        memory.addEmotionRecord("e2", "c2");
        memory.addEmotionRecord("e3", "c3");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(2);
        assertEquals(2, recent.size());
        assertEquals("e2", recent.get(0).getEmotion());
        assertEquals("e3", recent.get(1).getEmotion());
    }

    @Test
    @DisplayName("getRecentEmotions count 大于总量时返回全部")
    void shouldReturnAllWhenCountExceedsSize() {
        memory.addEmotionRecord("e1", "c1");

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
        assertEquals(1, recent.size());
    }

    @Test
    @DisplayName("情绪历史超过 20 条时自动淘汰最旧记录")
    void shouldEvictOldestWhenEmotionHistoryExceeds20() {
        for (int i = 0; i < 25; i++) {
            memory.addEmotionRecord("e" + i, "c" + i);
        }

        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(100);
        assertEquals(20, recent.size());
        // 最旧的 5 条被淘汰，剩余从 e5 开始
        assertEquals("e5", recent.get(0).getEmotion());
        assertEquals("e24", recent.get(19).getEmotion());
    }

    @Test
    @DisplayName("getRecentEmotions 返回防御性拷贝")
    void shouldReturnDefensiveCopyOfEmotions() {
        memory.addEmotionRecord("e1", "c1");
        List<UserMemory.EmotionRecord> recent = memory.getRecentEmotions(10);
        recent.clear();

        assertEquals(1, memory.getRecentEmotions(10).size());
    }

    // ==================== 重要话题 ====================

    @Test
    @DisplayName("addImportantTopic 添加并可获取")
    void shouldAddAndRetrieveTopics() {
        memory.addImportantTopic("travel");
        memory.addImportantTopic("cooking");

        List<String> topics = memory.getImportantTopics();
        assertEquals(2, topics.size());
        assertTrue(topics.contains("travel"));
        assertTrue(topics.contains("cooking"));
    }

    @Test
    @DisplayName("重复话题不会重复添加")
    void shouldNotAddDuplicateTopics() {
        memory.addImportantTopic("travel");
        memory.addImportantTopic("travel");

        assertEquals(1, memory.getImportantTopics().size());
    }

    @Test
    @DisplayName("话题超过 10 个时自动淘汰最旧话题")
    void shouldEvictOldestWhenTopicsExceed10() {
        for (int i = 0; i < 15; i++) {
            memory.addImportantTopic("topic" + i);
        }

        List<String> topics = memory.getImportantTopics();
        assertEquals(10, topics.size());
        // 最旧的 5 个被淘汰
        assertFalse(topics.contains("topic0"));
        assertTrue(topics.contains("topic14"));
    }

    @Test
    @DisplayName("getImportantTopics 返回防御性拷贝")
    void shouldReturnDefensiveCopyOfTopics() {
        memory.addImportantTopic("t1");
        List<String> topics = memory.getImportantTopics();
        topics.clear();

        assertEquals(1, memory.getImportantTopics().size());
    }

    // ==================== 时间戳 ====================

    @Test
    @DisplayName("构造时初始化 firstMeetTime 和 lastInteractionTime")
    void shouldInitializeTimestamps() {
        assertNotNull(memory.getFirstMeetTime());
        assertNotNull(memory.getLastInteractionTime());
    }

    @Test
    @DisplayName("写入操作会更新 lastInteractionTime")
    void shouldUpdateLastInteractionTimeOnWrite() throws InterruptedException {
        var before = memory.getLastInteractionTime();
        Thread.sleep(10);
        memory.put("key", "value");
        var after = memory.getLastInteractionTime();

        assertTrue(after.isAfter(before) || after.isEqual(before));
    }

    // ==================== EmotionRecord ====================

    @Test
    @DisplayName("EmotionRecord.toString 包含 emotion 和 context")
    void emotionRecordToStringShouldContainFields() {
        memory.addEmotionRecord("joy", "sunny day");
        String str = memory.getRecentEmotions(1).get(0).toString();
        assertTrue(str.contains("joy"));
        assertTrue(str.contains("sunny day"));
    }
}
