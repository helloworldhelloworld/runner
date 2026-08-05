package com.lightweightai.user.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmotionRecord — 情绪记录模型")
class EmotionRecordTest {

    @Test
    @DisplayName("无参构造器创建空记录")
    void shouldCreateEmptyRecord() {
        EmotionRecord record = new EmotionRecord();
        assertNull(record.getId());
        assertNull(record.getUserId());
        assertNull(record.getEmotion());
        assertNull(record.getNote());
        assertEquals(0, record.getRecordedAt());
    }

    @Test
    @DisplayName("5参数构造器设置所有字段")
    void shouldSetAllFields() {
        long ts = System.currentTimeMillis();
        EmotionRecord record = new EmotionRecord("er-1", "user-1", "😊", "开心", ts);

        assertEquals("er-1", record.getId());
        assertEquals("user-1", record.getUserId());
        assertEquals("😊", record.getEmotion());
        assertEquals("开心", record.getNote());
        assertEquals(ts, record.getRecordedAt());
    }

    @Test
    @DisplayName("setter 修改字段")
    void shouldUpdateViaSetters() {
        EmotionRecord record = new EmotionRecord();
        record.setId("er-2");
        record.setUserId("user-2");
        record.setEmotion("😢");
        record.setNote("难过");
        record.setRecordedAt(12345L);

        assertEquals("er-2", record.getId());
        assertEquals("user-2", record.getUserId());
        assertEquals("😢", record.getEmotion());
        assertEquals("难过", record.getNote());
        assertEquals(12345L, record.getRecordedAt());
    }

    @Test
    @DisplayName("note 可以为 null")
    void shouldAcceptNullNote() {
        EmotionRecord record = new EmotionRecord("er-3", "user-1", "😐", null, 100L);
        assertNull(record.getNote());
    }
}
