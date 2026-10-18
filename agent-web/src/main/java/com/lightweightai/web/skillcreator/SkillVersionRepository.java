package com.lightweightai.web.skillcreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Skill 版本 SQLite 持久化
 */
public class SkillVersionRepository {

    private static final Logger logger = LoggerFactory.getLogger(SkillVersionRepository.class);

    private final String dbPath;
    private Connection connection;

    public SkillVersionRepository(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS skill_versions (
                        id TEXT PRIMARY KEY,
                        skill_id TEXT NOT NULL,
                        version_tag TEXT NOT NULL,
                        snapshot_json TEXT NOT NULL,
                        test_run_id TEXT,
                        status TEXT DEFAULT 'staged',
                        created_at TEXT NOT NULL,
                        activated_at TEXT
                    )
                    """);
            }
            PublicationSchema.columns(connection, "skill_versions", Map.of("candidate_digest", "TEXT", "evaluation_digest", "TEXT",
                    "report_digest", "TEXT", "required_pass_rate", "REAL DEFAULT 1.0"));
        } catch (SQLException e) {
            logger.error("Failed to initialize skill version database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize skill version database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    public synchronized SkillVersion save(SkillVersion version) {
        if (version.getId() == null || version.getId().isBlank()) {
            version.setId(UUID.randomUUID().toString());
        }
        if (version.getCreatedAt() == null) {
            version.setCreatedAt(Instant.now());
        }
        String sql = """
            INSERT INTO skill_versions (id, skill_id, version_tag, snapshot_json, test_run_id, status, created_at, activated_at, candidate_digest, evaluation_digest, report_digest, required_pass_rate)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                status = excluded.status,
                activated_at = excluded.activated_at
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, version.getId());
            ps.setString(2, version.getSkillId());
            ps.setString(3, version.getVersionTag());
            ps.setString(4, version.getSnapshotJson());
            ps.setString(5, version.getTestRunId());
            ps.setString(6, version.getStatus());
            ps.setString(7, version.getCreatedAt().toString());
            ps.setString(8, version.getActivatedAt() != null ? version.getActivatedAt().toString() : null);
            ps.setString(9, version.getCandidateDigest());
            ps.setString(10, version.getEvaluationDigest());
            ps.setString(11, version.getReportDigest());
            ps.setDouble(12, version.getRequiredPassRate());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to save skill version: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save skill version", e);
        }
        return version;
    }

    public synchronized Optional<SkillVersion> findById(String id) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM skill_versions WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find version: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public synchronized List<SkillVersion> findBySkillId(String skillId) {
        List<SkillVersion> results = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM skill_versions WHERE skill_id = ? ORDER BY created_at DESC")) {
            ps.setString(1, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find versions: {}", e.getMessage(), e);
        }
        return results;
    }

    public synchronized Optional<SkillVersion> findActiveBySkillId(String skillId) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM skill_versions WHERE skill_id = ? AND status = 'active' LIMIT 1")) {
            ps.setString(1, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find active version: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public synchronized void updateStatus(String id, String status) {
        String activatedAt = "active".equals(status) ? Instant.now().toString() : null;
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE skill_versions SET status = ?, activated_at = COALESCE(?, activated_at) WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, activatedAt);
            ps.setString(3, id);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to update version status: {}", e.getMessage(), e);
        }
    }

    /** Durable active content only; legacy unbound versions remain queryable in history. */
    public synchronized List<SkillVersion> findActiveVersions() {
        List<SkillVersion> results = new ArrayList<>();
        try (PreparedStatement query = getConnection().prepareStatement(
                "SELECT * FROM skill_versions WHERE status = 'active' AND candidate_digest IS NOT NULL ORDER BY skill_id, id");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) results.add(fromResultSet(rows));
        } catch (SQLException e) { throw new IllegalStateException("Cannot read active publication", e); }
        return results;
    }

    synchronized SkillVersion activateAtomically(String id, String expectedActive, boolean conditional,
                                                java.util.function.Consumer<SkillVersion> validate) {
        boolean transaction = false;
        try (Statement transactionControl = getConnection().createStatement()) {
            transactionControl.execute("BEGIN IMMEDIATE");
            transaction = true;
            SkillVersion target = findById(id).orElseThrow(() -> new IllegalArgumentException("Version not found: " + id));
            validate.accept(target);
            String current = findActiveBySkillId(target.getSkillId()).map(SkillVersion::getId).orElse(null);
            if (conditional && !Objects.equals(expectedActive, current))
                throw new IllegalStateException("Active version changed; refresh before activating");
            try (PreparedStatement archive = connection.prepareStatement(
                    "UPDATE skill_versions SET status = 'archived' WHERE skill_id = ? AND status = 'active' AND id <> ?")) {
                archive.setString(1, target.getSkillId());
                archive.setString(2, id);
                archive.executeUpdate();
            }
            try (PreparedStatement activate = connection.prepareStatement(
                    "UPDATE skill_versions SET status = 'active', activated_at = ? WHERE id = ?")) {
                activate.setString(1, Instant.now().toString());
                activate.setString(2, id);
                if (activate.executeUpdate() != 1) throw new IllegalStateException("Activation target disappeared");
            }
            SkillVersion result = findById(id).orElseThrow();
            transactionControl.execute("COMMIT");
            transaction = false;
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot activate publication", e);
        } finally {
            if (transaction) {
                try (Statement rollback = connection.createStatement()) { rollback.execute("ROLLBACK"); }
                catch (SQLException e) { logger.error("Publication rollback failed", e); }
            }
        }
    }

    private SkillVersion fromResultSet(ResultSet rs) throws SQLException {
        SkillVersion v = new SkillVersion();
        v.setId(rs.getString("id"));
        v.setSkillId(rs.getString("skill_id"));
        v.setVersionTag(rs.getString("version_tag"));
        v.setSnapshotJson(rs.getString("snapshot_json"));
        v.setTestRunId(rs.getString("test_run_id"));
        v.setStatus(rs.getString("status"));
        v.setCandidateDigest(rs.getString("candidate_digest"));
        v.setEvaluationDigest(rs.getString("evaluation_digest"));
        v.setReportDigest(rs.getString("report_digest"));
        v.setRequiredPassRate(rs.getDouble("required_pass_rate"));
        String createdAt = rs.getString("created_at");
        if (createdAt != null) v.setCreatedAt(Instant.parse(createdAt));
        String activatedAt = rs.getString("activated_at");
        if (activatedAt != null) v.setActivatedAt(Instant.parse(activatedAt));
        return v;
    }
}
