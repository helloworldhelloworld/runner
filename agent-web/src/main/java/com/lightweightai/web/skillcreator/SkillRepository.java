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
                // 增量迁移：新增列（SQLite ALTER TABLE 不报错如果列已存在则忽略）
                for (String col : List.of(
                        "ALTER TABLE skills ADD COLUMN version TEXT DEFAULT '1.0.0'",
                        "ALTER TABLE skills ADD COLUMN category TEXT",
                        "ALTER TABLE skills ADD COLUMN scope TEXT DEFAULT '对话内可用'")) {
                    try { stmt.execute(col); } catch (SQLException ignored) { /* 列已存在 */ }
                }
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
        String sql = """
            INSERT INTO skills (id, name, description, version, category, scope, system_prompt, tools_json, triggers_json, priority, metadata_json, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                description = excluded.description,
                version = excluded.version,
                category = excluded.category,
                scope = excluded.scope,
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
            ps.setString(4, draft.getVersion());
            ps.setString(5, draft.getCategory());
            ps.setString(6, draft.getScope());
            ps.setString(7, draft.getSystemPrompt());
            ps.setString(8, MAPPER.writeValueAsString(draft.getToolNames()));
            ps.setString(9, MAPPER.writeValueAsString(draft.getTriggers()));
            ps.setInt(10, draft.getPriority());
            ps.setString(11, MAPPER.writeValueAsString(draft.getMetadata()));
            ps.setString(12, draft.getStatus());
            ps.setString(13, now);
            ps.setString(14, now);
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
        draft.setVersion(rs.getString("version"));
        draft.setCategory(rs.getString("category"));
        draft.setScope(rs.getString("scope"));
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
