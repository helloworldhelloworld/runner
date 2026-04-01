package com.lightweightai.user.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmotionRecord - 情绪记录模型")
class EmotionRecordTest {

    @Test
    @DisplayName("无参构造")
    void defaultConstruction() {
        EmotionRecord record = new EmotionRecord();
        assertNull(record.getId());
        assertNull(record.getUserId());
        assertNull(record.getEmotion());
        assertNull(record.getNote());
        assertEquals(0, record.getRecordedAt());
    }

    @Test
    @DisplayName("全参构造")
    void fullConstruction() {
        long now = System.currentTimeMillis();
        EmotionRecord record = new EmotionRecord("e1", "u1", "happy", "feeling great", now);

        assertEquals("e1", record.getId());
        assertEquals("u1", record.getUserId());
        assertEquals("happy", record.getEmotion());
        assertEquals("feeling great", record.getNote());
        assertEquals(now, record.getRecordedAt());
    }

    @Test
    @DisplayName("setter 正确更新字段")
    void setters() {
        EmotionRecord record = new EmotionRecord();
        record.setId("e2");
        record.setUserId("u2");
        record.setEmotion("sad");
        record.setNote("rough day");
        record.setRecordedAt(12345L);

        assertEquals("e2", record.getId());
        assertEquals("u2", record.getUserId());
        assertEquals("sad", record.getEmotion());
        assertEquals("rough day", record.getNote());
        assertEquals(12345L, record.getRecordedAt());
    }
}
