package com.lightweightai.user.model;

/**
 * A daily emotion check-in record.
 */
public class EmotionRecord {
    private String id;
    private String userId;
    private String emotion;
    private String note;
    private long recordedAt;

    public EmotionRecord() {}

    public EmotionRecord(String id, String userId, String emotion, String note, long recordedAt) {
        this.id = id;
        this.userId = userId;
        this.emotion = emotion;
        this.note = note;
        this.recordedAt = recordedAt;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getEmotion() {
        return emotion;
    }
    public void setEmotion(String emotion) {
        this.emotion = emotion;
    }
    public String getNote() {
        return note;
    }
    public void setNote(String note) {
        this.note = note;
    }
    public long getRecordedAt() {
        return recordedAt;
    }
    public void setRecordedAt(long recordedAt) {
        this.recordedAt = recordedAt;
    }
}
