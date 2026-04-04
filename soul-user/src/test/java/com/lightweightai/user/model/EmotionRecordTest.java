package com.lightweightai.user.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmotionRecord")
class EmotionRecordTest {

    @Test
    void shouldCreateWithDefaultConstructor() {
        EmotionRecord record = new EmotionRecord();
        assertNull(record.getId());
        assertNull(record.getUserId());
        assertNull(record.getEmotion());
    }

    @Test
    void shouldCreateWithAllFields() {
        EmotionRecord record = new EmotionRecord("r-1", "u-1", "happy", "Good day!", 1700000000L);

        assertEquals("r-1", record.getId());
        assertEquals("u-1", record.getUserId());
        assertEquals("happy", record.getEmotion());
        assertEquals("Good day!", record.getNote());
        assertEquals(1700000000L, record.getRecordedAt());
    }

    @Test
    void shouldSupportSetters() {
        EmotionRecord record = new EmotionRecord();
        record.setId("r-2");
        record.setUserId("u-2");
        record.setEmotion("sad");
        record.setNote("Tough day");
        record.setRecordedAt(1700000001L);

        assertEquals("r-2", record.getId());
        assertEquals("u-2", record.getUserId());
        assertEquals("sad", record.getEmotion());
        assertEquals("Tough day", record.getNote());
        assertEquals(1700000001L, record.getRecordedAt());
    }
}
