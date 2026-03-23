package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * SQLite 持久化层 - 管理 Skill 的 CRUD
 */
public class SkillRepository {

    private static final Logger logger = LoggerFactory.getLogger(SkillRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String dbPath;
    private Connection connection;

    public SkillRepository(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS skills (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        description TEXT,
                        system_prompt TEXT,
                        tools_json TEXT,
                        triggers_json TEXT,
                        priority INTEGER DEFAULT 10,
                        metadata_json TEXT,
                        status TEXT DEFAULT 'draft',
                        created_at TEXT,
                        updated_at TEXT
                    )
                    """);
            }
            logger.info("SkillRepository initialized: {}", dbPath);
        } catch (SQLException e) {
            logger.error("Failed to initialize skill database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize skill database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    /**
     * 保存或更新 Skill
     */
    public SkillDraft save(SkillDraft draft) {
        if (draft.getId() == null || draft.getId().isBlank()) {
            draft.setId(UUID.randomUUID().toString());
        }

        String now = Instant.now().toString();

        // Check if a skill with the same name but different ID already exists
        try (PreparedStatement check = getConnection().prepareStatement(
                "SELECT id FROM skills WHERE name = ? AND id != ?")) {
            check.setString(1, draft.getName());
            check.setString(2, draft.getId());
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                // Reuse the existing ID to perform an update instead of failing on UNIQUE(name)
                String existingId = rs.getString("id");
                logger.info("Skill name '{}' already exists with id={}, reusing id for update", draft.getName(), existingId);
                draft.setId(existingId);
            }
        } catch (SQLException e) {
            logger.warn("Failed to check existing skill name: {}", e.getMessage());
        }

        String sql = """
            INSERT INTO skills (id, name, description, system_prompt, tools_json, triggers_json, priority, metadata_json, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                description = excluded.description,
                system_prompt = excluded.system_prompt,
                tools_json = excluded.tools_json,
                triggers_json = excluded.triggers_json,
                priority = excluded.priority,
                metadata_json = excluded.metadata_json,
                status = excluded.status,
                updated_at = excluded.updated_at
            """;

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, draft.getId());
            ps.setString(2, draft.getName());
            ps.setString(3, draft.getDescription());
            ps.setString(4, draft.getSystemPrompt());
            ps.setString(5, MAPPER.writeValueAsString(draft.getToolNames()));
            ps.setString(6, MAPPER.writeValueAsString(draft.getTriggers()));
            ps.setInt(7, draft.getPriority());
            ps.setString(8, MAPPER.writeValueAsString(draft.getMetadata()));
            ps.setString(9, draft.getStatus());
            ps.setString(10, now);
            ps.setString(11, now);
            ps.executeUpdate();
            logger.info("Skill saved: id={}, name={}", draft.getId(), draft.getName());
        } catch (Exception e) {
            logger.error("Failed to save skill: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save skill", e);
        }
        return draft;
    }

    /**
     * 获取所有 Skill
     */
    public List<SkillDraft> findAll() {
        List<SkillDraft> results = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM skills ORDER BY updated_at DESC")) {
            while (rs.next()) {
                results.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to list skills: {}", e.getMessage(), e);
        }
        return results;
    }

    /**
     * 获取所有 active 状态的 Skill
     */
    public List<SkillDraft> findActive() {
        List<SkillDraft> results = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM skills WHERE status = 'active' ORDER BY priority")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to list active skills: {}", e.getMessage(), e);
        }
        return results;
    }

    /**
     * 按 ID 查找
     */
    public Optional<SkillDraft> findById(String id) {
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM skills WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find skill: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 删除 Skill
     */
    public boolean delete(String id) {
        try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM skills WHERE id = ?")) {
            ps.setString(1, id);
            int rows = ps.executeUpdate();
            logger.info("Skill deleted: id={}, rows={}", id, rows);
            return rows > 0;
        } catch (Exception e) {
            logger.error("Failed to delete skill: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 更新状态
     */
    public boolean updateStatus(String id, String status) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE skills SET status = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, Instant.now().toString());
            ps.setString(3, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("Failed to update skill status: {}", e.getMessage(), e);
            return false;
        }
    }

    private SkillDraft fromResultSet(ResultSet rs) throws Exception {
        SkillDraft draft = new SkillDraft();
        draft.setId(rs.getString("id"));
        draft.setName(rs.getString("name"));
        draft.setDescription(rs.getString("description"));
        draft.setSystemPrompt(rs.getString("system_prompt"));
        draft.setStatus(rs.getString("status"));
        draft.setPriority(rs.getInt("priority"));

        String toolsJson = rs.getString("tools_json");
        if (toolsJson != null && !toolsJson.isBlank()) {
            draft.setToolNames(MAPPER.readValue(toolsJson, new TypeReference<List<String>>() {}));
        }

        String triggersJson = rs.getString("triggers_json");
        if (triggersJson != null && !triggersJson.isBlank()) {
            draft.setTriggers(MAPPER.readValue(triggersJson, new TypeReference<List<String>>() {}));
        }

        String metadataJson = rs.getString("metadata_json");
        if (metadataJson != null && !metadataJson.isBlank()) {
            draft.setMetadata(MAPPER.readValue(metadataJson, new TypeReference<Map<String, Object>>() {}));
        }

        return draft;
    }
}
