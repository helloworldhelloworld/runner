package com.lightweightai.user;

import com.lightweightai.user.model.EmotionRecord;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQLite repository for emotion_records table.
 */
public class EmotionRepository {

    private static final Logger logger = LoggerFactory.getLogger(EmotionRepository.class);

    private final String dbUrl;

    public EmotionRepository(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS emotion_records (
                  id TEXT PRIMARY KEY,
                  user_id TEXT NOT NULL,
                  emotion TEXT NOT NULL,
                  note TEXT,
                  recorded_at INTEGER NOT NULL
                )
                """);
        } catch (SQLException e) {
            logger.error("Failed to init emotion_records schema", e);
            throw new RuntimeException(e);
        }
    }

    public void save(EmotionRecord record) {
        String sql = "INSERT INTO emotion_records (id, user_id, emotion, note, recorded_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getId());
            ps.setString(2, record.getUserId());
            ps.setString(3, record.getEmotion());
            ps.setString(4, record.getNote());
            ps.setLong(5, record.getRecordedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save emotion record", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Find records for a user within the last N days.
     */
    public List<EmotionRecord> findByUserIdWithinDays(String userId, int days) {
        long since = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
        String sql = "SELECT * FROM emotion_records WHERE user_id = ? AND recorded_at >= ? ORDER BY recorded_at DESC";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setLong(2, since);
            ResultSet rs = ps.executeQuery();
            List<EmotionRecord> results = new ArrayList<>();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;
        } catch (SQLException e) {
            logger.error("Failed to query emotion records for user: {}", userId, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Check if user already checked in today (UTC).
     */
    public boolean hasCheckinToday(String userId) {
        long startOfDay = todayStartMillis();
        String sql = "SELECT COUNT(*) FROM emotion_records WHERE user_id = ? AND recorded_at >= ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setLong(2, startOfDay);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            logger.error("Failed to check today's checkin for user: {}", userId, e);
            throw new RuntimeException(e);
        }
    }

    private long todayStartMillis() {
        java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private EmotionRecord mapRow(ResultSet rs) throws SQLException {
        return new EmotionRecord(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("emotion"),
            rs.getString("note"),
            rs.getLong("recorded_at")
        );
    }
}
