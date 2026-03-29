package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * 仿真记录 SQLite 持久化
 */
public class SimulationRepository {

    private static final Logger logger = LoggerFactory.getLogger(SimulationRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String dbPath;
    private Connection connection;

    public SimulationRepository(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS skill_simulations (
                        id TEXT PRIMARY KEY,
                        skill_version_id TEXT NOT NULL,
                        scenario_count INTEGER DEFAULT 0,
                        pass_count INTEGER DEFAULT 0,
                        avg_latency_ms REAL DEFAULT 0,
                        status TEXT DEFAULT 'running',
                        report_json TEXT,
                        created_at TEXT NOT NULL,
                        completed_at TEXT
                    )
                    """);
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize simulation database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize simulation database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    public SimulationRecord save(SimulationRecord record) {
        if (record.getId() == null || record.getId().isBlank()) {
            record.setId(UUID.randomUUID().toString());
        }
        String sql = """
            INSERT INTO skill_simulations (id, skill_version_id, scenario_count, pass_count, avg_latency_ms, status, report_json, created_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                scenario_count = excluded.scenario_count,
                pass_count = excluded.pass_count,
                avg_latency_ms = excluded.avg_latency_ms,
                status = excluded.status,
                report_json = excluded.report_json,
                completed_at = excluded.completed_at
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, record.getId());
            ps.setString(2, record.getSkillVersionId());
            ps.setInt(3, record.getScenarioCount());
            ps.setInt(4, record.getPassCount());
            ps.setDouble(5, record.getAvgLatencyMs());
            ps.setString(6, record.getStatus());
            ps.setString(7, record.getReportJson());
            ps.setString(8, record.getCreatedAt() != null ? record.getCreatedAt().toString() : Instant.now().toString());
            ps.setString(9, record.getCompletedAt() != null ? record.getCompletedAt().toString() : null);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to save simulation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save simulation", e);
        }
        return record;
    }

    public Optional<SimulationRecord> findById(String id) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM skill_simulations WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find simulation: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<SimulationRecord> findByVersionId(String versionId) {
        List<SimulationRecord> results = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM skill_simulations WHERE skill_version_id = ? ORDER BY created_at DESC")) {
            ps.setString(1, versionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find simulations: {}", e.getMessage(), e);
        }
        return results;
    }

    private SimulationRecord fromResultSet(ResultSet rs) throws SQLException {
        SimulationRecord r = new SimulationRecord();
        r.setId(rs.getString("id"));
        r.setSkillVersionId(rs.getString("skill_version_id"));
        r.setScenarioCount(rs.getInt("scenario_count"));
        r.setPassCount(rs.getInt("pass_count"));
        r.setAvgLatencyMs(rs.getDouble("avg_latency_ms"));
        r.setStatus(rs.getString("status"));
        r.setReportJson(rs.getString("report_json"));
        String createdAt = rs.getString("created_at");
        if (createdAt != null) r.setCreatedAt(Instant.parse(createdAt));
        String completedAt = rs.getString("completed_at");
        if (completedAt != null) r.setCompletedAt(Instant.parse(completedAt));
        return r;
    }

    /**
     * 仿真记录数据模型
     */
    public static class SimulationRecord {
        private String id;
        private String skillVersionId;
        private int scenarioCount;
        private int passCount;
        private double avgLatencyMs;
        private String status;
        private String reportJson;
        private Instant createdAt;
        private Instant completedAt;

        public SimulationRecord() { this.createdAt = Instant.now(); }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSkillVersionId() { return skillVersionId; }
        public void setSkillVersionId(String v) { this.skillVersionId = v; }
        public int getScenarioCount() { return scenarioCount; }
        public void setScenarioCount(int n) { this.scenarioCount = n; }
        public int getPassCount() { return passCount; }
        public void setPassCount(int n) { this.passCount = n; }
        public double getAvgLatencyMs() { return avgLatencyMs; }
        public void setAvgLatencyMs(double ms) { this.avgLatencyMs = ms; }
        public String getStatus() { return status; }
        public void setStatus(String s) { this.status = s; }
        public String getReportJson() { return reportJson; }
        public void setReportJson(String j) { this.reportJson = j; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant t) { this.createdAt = t; }
        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant t) { this.completedAt = t; }
    }
}
