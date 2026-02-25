package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.ScaleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite repository for assessment_results table.
 */
public class AssessmentRepository {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String dbUrl;

    public AssessmentRepository(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS assessment_results (
                  id TEXT PRIMARY KEY,
                  user_id TEXT NOT NULL,
                  scale_type TEXT NOT NULL,
                  answers TEXT NOT NULL,
                  total_score INTEGER NOT NULL,
                  severity TEXT NOT NULL,
                  ai_report TEXT,
                  created_at INTEGER NOT NULL
                )
                """);
        } catch (SQLException e) {
            logger.error("Failed to init assessment_results schema", e);
            throw new RuntimeException(e);
        }
    }

    public void save(AssessmentResult result, List<Integer> answers) {
        String sql = """
            INSERT INTO assessment_results
              (id, user_id, scale_type, answers, total_score, severity, ai_report, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, result.getId());
            ps.setString(2, result.getUserId());
            ps.setString(3, result.getScaleType().name());
            ps.setString(4, MAPPER.writeValueAsString(answers));
            ps.setInt(5, result.getTotalScore());
            ps.setString(6, result.getSeverity());
            ps.setString(7, result.getAiReport());
            ps.setLong(8, result.getCreatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to save assessment result", e);
            throw new RuntimeException(e);
        }
    }

    public List<AssessmentResult> findByUserId(String userId) {
        String sql = "SELECT * FROM assessment_results WHERE user_id = ? ORDER BY created_at DESC";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            List<AssessmentResult> results = new ArrayList<>();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;
        } catch (SQLException e) {
            logger.error("Failed to query assessment results for user: {}", userId, e);
            throw new RuntimeException(e);
        }
    }

    private AssessmentResult mapRow(ResultSet rs) throws SQLException {
        return new AssessmentResult(
            rs.getString("id"),
            rs.getString("user_id"),
            ScaleType.valueOf(rs.getString("scale_type")),
            rs.getInt("total_score"),
            rs.getString("severity"),
            rs.getString("ai_report"),
            rs.getLong("created_at")
        );
    }
}
