package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * 测试用例 SQLite 持久化
 */
public class SkillTestCaseRepository {

    private static final Logger logger = LoggerFactory.getLogger(SkillTestCaseRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String dbPath;
    private Connection connection;

    public SkillTestCaseRepository(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS skill_test_cases (
                        id TEXT PRIMARY KEY,
                        skill_id TEXT NOT NULL,
                        category TEXT,
                        description TEXT,
                        input TEXT NOT NULL,
                        memory TEXT,
                        user_clarifications_json TEXT,
                        assertions_json TEXT,
                        mock_responses_json TEXT,
                        created_at TEXT
                    )
                    """);
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize test case database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize test case database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    public SkillTestCase save(SkillTestCase tc) {
        if (tc.getId() == null || tc.getId().isBlank()) {
            tc.setId(UUID.randomUUID().toString());
        }
        String sql = """
            INSERT INTO skill_test_cases (id, skill_id, category, description, input, memory, user_clarifications_json, assertions_json, mock_responses_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                skill_id = excluded.skill_id,
                category = excluded.category,
                description = excluded.description,
                input = excluded.input,
                memory = excluded.memory,
                user_clarifications_json = excluded.user_clarifications_json,
                assertions_json = excluded.assertions_json,
                mock_responses_json = excluded.mock_responses_json
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, tc.getId());
            ps.setString(2, tc.getSkillId());
            ps.setString(3, tc.getCategory());
            ps.setString(4, tc.getDescription());
            ps.setString(5, tc.getInput());
            ps.setString(6, tc.getMemory());
            ps.setString(7, MAPPER.writeValueAsString(tc.getUserClarifications()));
            ps.setString(8, MAPPER.writeValueAsString(tc.getAssertions()));
            ps.setString(9, tc.getMockResponses() != null ? MAPPER.writeValueAsString(tc.getMockResponses()) : null);
            ps.setString(10, Instant.now().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to save test case: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save test case", e);
        }
        return tc;
    }

    public void saveAll(List<SkillTestCase> cases) {
        cases.forEach(this::save);
    }

    public List<SkillTestCase> findBySkillId(String skillId) {
        List<SkillTestCase> results = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM skill_test_cases WHERE skill_id = ? ORDER BY category, created_at")) {
            ps.setString(1, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(fromResultSet(rs));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find test cases: {}", e.getMessage(), e);
        }
        return results;
    }

    public boolean delete(String id) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "DELETE FROM skill_test_cases WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("Failed to delete test case: {}", e.getMessage(), e);
            return false;
        }
    }

    public int deleteBySkillId(String skillId) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "DELETE FROM skill_test_cases WHERE skill_id = ?")) {
            ps.setString(1, skillId);
            return ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to delete test cases by skillId: {}", e.getMessage(), e);
            return 0;
        }
    }

    private SkillTestCase fromResultSet(ResultSet rs) throws Exception {
        SkillTestCase tc = new SkillTestCase();
        tc.setId(rs.getString("id"));
        tc.setSkillId(rs.getString("skill_id"));
        tc.setCategory(rs.getString("category"));
        tc.setDescription(rs.getString("description"));
        tc.setInput(rs.getString("input"));
        tc.setMemory(rs.getString("memory"));

        String clarJson = rs.getString("user_clarifications_json");
        if (clarJson != null && !clarJson.isBlank()) {
            tc.setUserClarifications(MAPPER.readValue(clarJson, new TypeReference<>() {}));
        }

        String assertJson = rs.getString("assertions_json");
        if (assertJson != null && !assertJson.isBlank()) {
            tc.setAssertions(MAPPER.readValue(assertJson, new TypeReference<>() {}));
        }

        String mockJson = rs.getString("mock_responses_json");
        if (mockJson != null && !mockJson.isBlank()) {
            tc.setMockResponses(MAPPER.readValue(mockJson, new TypeReference<>() {}));
        }

        return tc;
    }
}
