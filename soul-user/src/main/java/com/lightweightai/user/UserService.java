package com.lightweightai.user;

import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import org.slf4j.Logger;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;

/**
 * Business facade for user and emotion operations.
 */
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final EmotionRepository emotionRepository;

    public UserService(UserRepository userRepository, EmotionRepository emotionRepository) {
        this.userRepository = userRepository;
        this.emotionRepository = emotionRepository;
    }

    /**
     * Create an anonymous user and return it.
     */
    public SoulUser createAnonymousUser() {
        String id = "anon-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();
        SoulUser user = new SoulUser(id, null, "匿名用户", "FREE", now, now);
        userRepository.save(user);
        logger.info("Created anonymous user: {}", id);
        return user;
    }

    /**
     * Get or create user by id.
     */
    public SoulUser getOrCreate(String userId) {
        return userRepository.findById(userId).orElseGet(() -> {
            long now = System.currentTimeMillis();
            SoulUser user = new SoulUser(userId, null, "匿名用户", "FREE", now, now);
            userRepository.save(user);
            return user;
        });
    }

    /**
     * Record a daily emotion check-in.
     *
     * @param userId  user id
     * @param emotion emotion label (e.g. "😊")
     * @param note    optional text note
     * @return the saved EmotionRecord
     */
    public EmotionRecord checkin(String userId, String emotion, String note) {
        getOrCreate(userId);
        long now = System.currentTimeMillis();
        String id = "er-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        EmotionRecord record = new EmotionRecord(id, userId, emotion, note, now);
        emotionRepository.save(record);
        userRepository.updateLastActive(userId, now);
        logger.info("Emotion checkin for user {}: {}", userId, emotion);
        return record;
    }

    /**
     * Get emotion records for the last N days.
     */
    public List<EmotionRecord> getEmotions(String userId, int days) {
        return emotionRepository.findByUserIdWithinDays(userId, days);
    }

    /**
     * Get emotion stats: consecutive streak days and emotion distribution.
     */
    public Map<String, Object> getStats(String userId) {
        List<EmotionRecord> records = emotionRepository.findByUserIdWithinDays(userId, 30);

        // Emotion distribution
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (EmotionRecord r : records) {
            distribution.merge(r.getEmotion(), 1L, Long::sum);
        }

        // Consecutive streak: count unique calendar days (China TZ) from today going backward
        int streak = calculateStreak(records);

        // Today's checkin status
        boolean checkedInToday = emotionRepository.hasCheckinToday(userId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("streak", streak);
        stats.put("distribution", distribution);
        stats.put("checkedInToday", checkedInToday);
        stats.put("totalDays", countUniqueDays(records));
        return stats;
    }

    private int calculateStreak(List<EmotionRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }

        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Asia/Shanghai");
        java.util.Calendar cal = java.util.Calendar.getInstance(tz);

        Set<String> checkinDays = new HashSet<>();
        for (EmotionRecord r : records) {
            cal.setTimeInMillis(r.getRecordedAt());
            String day = cal.get(java.util.Calendar.YEAR) + "-"
                + cal.get(java.util.Calendar.MONTH) + "-"
                + cal.get(java.util.Calendar.DAY_OF_MONTH);
            checkinDays.add(day);
        }

        int streak = 0;
        cal.setTimeInMillis(System.currentTimeMillis());
        while (true) {
            String day = cal.get(java.util.Calendar.YEAR) + "-"
                + cal.get(java.util.Calendar.MONTH) + "-"
                + cal.get(java.util.Calendar.DAY_OF_MONTH);
            if (!checkinDays.contains(day)) {
                break;
            }
            streak++;
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }
        return streak;
    }

    private int countUniqueDays(List<EmotionRecord> records) {
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Asia/Shanghai");
        java.util.Calendar cal = java.util.Calendar.getInstance(tz);
        Set<String> days = new HashSet<>();
        for (EmotionRecord r : records) {
            cal.setTimeInMillis(r.getRecordedAt());
            days.add(cal.get(java.util.Calendar.YEAR) + "-"
                + cal.get(java.util.Calendar.MONTH) + "-"
                + cal.get(java.util.Calendar.DAY_OF_MONTH));
        }
        return days.size();
    }
}
