package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * 测试运行 + 用例结果 SQLite 持久化
 */
public class TestRunRepository {

    private static final Logger logger = LoggerFactory.getLogger(TestRunRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String dbPath;
    private Connection connection;

    public TestRunRepository(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS skill_test_runs (
                        id TEXT PRIMARY KEY,
                        skill_id TEXT NOT NULL,
                        started_at TEXT NOT NULL,
                        completed_at TEXT,
                        total INTEGER DEFAULT 0,
                        passed INTEGER DEFAULT 0,
                        failed INTEGER DEFAULT 0,
                        status TEXT DEFAULT 'running'
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS skill_test_case_results (
                        id TEXT PRIMARY KEY,
                        run_id TEXT NOT NULL,
                        test_case_id TEXT NOT NULL,
                        passed INTEGER DEFAULT 0,
                        llm_output TEXT,
                        tool_calls_json TEXT,
                        assertions_json TEXT,
                        duration_ms INTEGER DEFAULT 0,
                        error_message TEXT
                    )
                    """);
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize test run database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize test run database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    // ==================== TestRun CRUD ====================

    public TestRun saveRun(TestRun run) {
        if (run.getId() == null || run.getId().isBlank()) {
            run.setId(UUID.randomUUID().toString());
        }
        String sql = """
            INSERT INTO skill_test_runs (id, skill_id, started_at, completed_at, total, passed, failed, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                completed_at = excluded.completed_at,
                total = excluded.total,
                passed = excluded.passed,
                failed = excluded.failed,
                status = excluded.status
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, run.getId());
            ps.setString(2, run.getSkillId());
            ps.setString(3, run.getStartedAt() != null ? run.getStartedAt().toString() : Instant.now().toString());
            ps.setString(4, run.getCompletedAt() != null ? run.getCompletedAt().toString() : null);
            ps.setInt(5, run.getTotal());
            ps.setInt(6, run.getPassed());
            ps.setInt(7, run.getFailed());
            ps.setString(8, run.getStatus());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to save test run: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save test run", e);
        }
        return run;
    }

    public Optional<TestRun> findRunById(String id) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM skill_test_runs WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(runFromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find test run: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<TestRun> findRunsBySkillId(String skillId) {
        List<TestRun> results = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM skill_test_runs WHERE skill_id = ? ORDER BY started_at DESC")) {
            ps.setString(1, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(runFromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find test runs: {}", e.getMessage(), e);
        }
        return results;
    }

    public void updateRunStatus(String runId, String status) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE skill_test_runs SET status = ?, completed_at = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, Instant.now().toString());
            ps.setString(3, runId);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to update run status: {}", e.getMessage(), e);
        }
    }

    // ==================== TestCaseResult CRUD ====================

    public TestCaseResult saveCaseResult(TestCaseResult result) {
        if (result.getId() == null || result.getId().isBlank()) {
            result.setId(UUID.randomUUID().toString());
        }
        String sql = """
            INSERT INTO skill_test_case_results (id, run_id, test_case_id, passed, llm_output, tool_calls_json, assertions_json, duration_ms, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                passed = excluded.passed,
                llm_output = excluded.llm_output,
                tool_calls_json = excluded.tool_calls_json,
                assertions_json = excluded.assertions_json,
                duration_ms = excluded.duration_ms,
                error_message = excluded.error_message
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, result.getId());
            ps.setString(2, result.getRunId());
            ps.setString(3, result.getTestCaseId());
            ps.setInt(4, result.isPassed() ? 1 : 0);
            ps.setString(5, result.getLlmOutput());
            ps.setString(6, result.getToolCalls() != null ? MAPPER.writeValueAsString(result.getToolCalls()) : null);
            ps.setString(7, result.getAssertions() != null ? MAPPER.writeValueAsString(result.getAssertions()) : null);
            ps.setLong(8, result.getDurationMs());
            ps.setString(9, result.getErrorMessage());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to save case result: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save case result", e);
        }
        return result;
    }

    public List<TestCaseResult> findCaseResultsByRunId(String runId) {
        List<TestCaseResult> results = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM skill_test_case_results WHERE run_id = ?")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(caseResultFromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find case results: {}", e.getMessage(), e);
        }
        return results;
    }

    // ==================== ResultSet mapping ====================

    private TestRun runFromResultSet(ResultSet rs) throws SQLException {
        TestRun run = new TestRun();
        run.setId(rs.getString("id"));
        run.setSkillId(rs.getString("skill_id"));
        String startedAt = rs.getString("started_at");
        if (startedAt != null) run.setStartedAt(Instant.parse(startedAt));
        String completedAt = rs.getString("completed_at");
        if (completedAt != null) run.setCompletedAt(Instant.parse(completedAt));
        run.setTotal(rs.getInt("total"));
        run.setPassed(rs.getInt("passed"));
        run.setFailed(rs.getInt("failed"));
        run.setStatus(rs.getString("status"));
        return run;
    }

    @SuppressWarnings("unchecked")
    private TestCaseResult caseResultFromResultSet(ResultSet rs) throws Exception {
        TestCaseResult r = new TestCaseResult();
        r.setId(rs.getString("id"));
        r.setRunId(rs.getString("run_id"));
        r.setTestCaseId(rs.getString("test_case_id"));
        r.setPassed(rs.getInt("passed") == 1);
        r.setLlmOutput(rs.getString("llm_output"));
        r.setDurationMs(rs.getLong("duration_ms"));
        r.setErrorMessage(rs.getString("error_message"));

        String toolCallsJson = rs.getString("tool_calls_json");
        if (toolCallsJson != null && !toolCallsJson.isBlank()) {
            r.setToolCalls(MAPPER.readValue(toolCallsJson, new TypeReference<>() {}));
        }

        String assertionsJson = rs.getString("assertions_json");
        if (assertionsJson != null && !assertionsJson.isBlank()) {
            r.setAssertions(MAPPER.readValue(assertionsJson, new TypeReference<>() {}));
        }

        return r;
    }
}
