package com.lightweightai.kernel.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户长期记忆 - 存储用户的重要信息
 */
public class UserMemory {

    // 基本信息
    private final Map<String, String> basicInfo = new ConcurrentHashMap<>();

    // 情绪历史
    private final List<EmotionRecord> emotionHistory = new ArrayList<>();

    // 重要话题
    private final List<String> importantTopics = new ArrayList<>();

    // 首次见面时间
    private final LocalDateTime firstMeetTime;

    // 最后互动时间
    private LocalDateTime lastInteractionTime;

    public UserMemory() {
        this.firstMeetTime = LocalDateTime.now();
        this.lastInteractionTime = LocalDateTime.now();
    }

    /**
     * 存储基本信息
     */
    public void put(String key, String value) {
        basicInfo.put(key, value);
        updateLastInteraction();
    }

    /**
     * 获取基本信息
     */
    public String get(String key) {
        return basicInfo.get(key);
    }

    /**
     * 添加情绪记录
     */
    public void addEmotionRecord(String emotion, String context) {
        emotionHistory.add(new EmotionRecord(emotion, context, LocalDateTime.now()));
        updateLastInteraction();

        // 限制情绪历史大小
        if (emotionHistory.size() > 20) {
            emotionHistory.remove(0);
        }
    }

    /**
     * 获取最近的情绪记录
     */
    public List<EmotionRecord> getRecentEmotions(int count) {
        int size = emotionHistory.size();
        if (size <= count) {
            return new ArrayList<>(emotionHistory);
        }
        return new ArrayList<>(emotionHistory.subList(size - count, size));
    }

    /**
     * 添加重要话题
     */
    public void addImportantTopic(String topic) {
        if (!importantTopics.contains(topic)) {
            importantTopics.add(topic);
        }
        updateLastInteraction();

        // 限制话题数量
        if (importantTopics.size() > 10) {
            importantTopics.remove(0);
        }
    }

    /**
     * 获取重要话题
     */
    public List<String> getImportantTopics() {
        return new ArrayList<>(importantTopics);
    }

    /**
     * 获取所有基本信息
     */
    public Map<String, String> getAllBasicInfo() {
        return new ConcurrentHashMap<>(basicInfo);
    }

    /**
     * 更新最后互动时间
     */
    private void updateLastInteraction() {
        this.lastInteractionTime = LocalDateTime.now();
    }

    /**
     * 获取首次见面时间
     */
    public LocalDateTime getFirstMeetTime() {
        return firstMeetTime;
    }

    /**
     * 获取最后互动时间
     */
    public LocalDateTime getLastInteractionTime() {
        return lastInteractionTime;
    }

    /**
     * 情绪记录
     */
    public static class EmotionRecord {
        private final String emotion;
        private final String context;
        private final LocalDateTime timestamp;

        public EmotionRecord(String emotion, String context, LocalDateTime timestamp) {
            this.emotion = emotion;
            this.context = context;
            this.timestamp = timestamp;
        }

        public String getEmotion() {
            return emotion;
        }

        public String getContext() {
            return context;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s - %s", timestamp, emotion, context);
        }
    }
}
