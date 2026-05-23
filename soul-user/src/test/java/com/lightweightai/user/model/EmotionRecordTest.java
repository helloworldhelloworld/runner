package com.lightweightai.user.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmotionRecord - 情绪记录")
class EmotionRecordTest {

    @Test
    @DisplayName("全参构造所有字段正确")
    void fullConstruction() {
        EmotionRecord record = new EmotionRecord("e1", "u1", "开心", "今天天气好", 1700000000L);

        assertEquals("e1", record.getId());
        assertEquals("u1", record.getUserId());
        assertEquals("开心", record.getEmotion());
        assertEquals("今天天气好", record.getNote());
        assertEquals(1700000000L, record.getRecordedAt());
    }

    @Test
    @DisplayName("无参构造 + setter 填充")
    void defaultConstructionWithSetters() {
        EmotionRecord record = new EmotionRecord();
        record.setId("e2");
        record.setUserId("u2");
        record.setEmotion("焦虑");
        record.setNote("工作压力大");
        record.setRecordedAt(1700000001L);

        assertEquals("e2", record.getId());
        assertEquals("u2", record.getUserId());
        assertEquals("焦虑", record.getEmotion());
        assertEquals("工作压力大", record.getNote());
        assertEquals(1700000001L, record.getRecordedAt());
    }

    @Test
    @DisplayName("note 可以为 null")
    void noteNullable() {
        EmotionRecord record = new EmotionRecord("e3", "u3", "平静", null, 0L);

        assertNull(record.getNote());
    }
}
