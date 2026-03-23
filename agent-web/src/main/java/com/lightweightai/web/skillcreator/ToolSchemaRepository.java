package com.lightweightai.web.skillcreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * SQLite 持久化层 - 管理候选工具 Schema 的 CRUD
 *
 * 工具 Schema 从 Excel 导入，存储在 tool_schemas 表中，
 * 在 Skill Creator 中作为可选的工具列表展示。
 */
public class ToolSchemaRepository {

    private static final Logger logger = LoggerFactory.getLogger(ToolSchemaRepository.class);

    private final String dbPath;
    private Connection connection;

    public ToolSchemaRepository(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS tool_schemas (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        description TEXT,
                        category TEXT,
                        input_schema_json TEXT,
                        output_schema_json TEXT,
                        status TEXT DEFAULT 'enabled',
                        created_at TEXT,
                        updated_at TEXT
                    )
                    """);
            }
            logger.info("ToolSchemaRepository initialized: {}", dbPath);
        } catch (SQLException e) {
            logger.error("Failed to initialize tool_schemas database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize tool_schemas database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    /**
     * 保存或更新工具 Schema（按 name 唯一键 upsert）
     */
    public ToolSchemaEntry save(ToolSchemaEntry entry) {
        if (entry.getId() == null || entry.getId().isBlank()) {
            entry.setId(UUID.randomUUID().toString());
        }

        String now = Instant.now().toString();
        entry.setUpdatedAt(now);
        if (entry.getCreatedAt() == null) entry.setCreatedAt(now);
        if (entry.getStatus() == null) entry.setStatus("enabled");

        String sql = """
            INSERT INTO tool_schemas (id, name, description, category, input_schema_json, output_schema_json, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(name) DO UPDATE SET
                description = excluded.description,
                category = excluded.category,
                input_schema_json = excluded.input_schema_json,
                output_schema_json = excluded.output_schema_json,
                status = excluded.status,
                updated_at = excluded.updated_at
            """;

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, entry.getId());
            ps.setString(2, entry.getName());
            ps.setString(3, entry.getDescription());
            ps.setString(4, entry.getCategory());
            ps.setString(5, entry.getInputSchemaJson());
            ps.setString(6, entry.getOutputSchemaJson());
            ps.setString(7, entry.getStatus());
            ps.setString(8, entry.getCreatedAt());
            ps.setString(9, entry.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to save tool schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save tool schema", e);
        }
        return entry;
    }

    /**
     * 批量保存（upsert）
     */
    public int saveAll(List<ToolSchemaEntry> entries) {
        int count = 0;
        for (ToolSchemaEntry entry : entries) {
            try {
                save(entry);
                count++;
            } catch (Exception e) {
                logger.warn("Failed to save tool schema '{}': {}", entry.getName(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * 获取所有工具 Schema
     */
    public List<ToolSchemaEntry> findAll() {
        List<ToolSchemaEntry> results = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM tool_schemas ORDER BY category, name")) {
            while (rs.next()) {
                results.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to list tool schemas: {}", e.getMessage(), e);
        }
        return results;
    }

    /**
     * 获取启用的工具 Schema
     */
    public List<ToolSchemaEntry> findEnabled() {
        List<ToolSchemaEntry> results = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM tool_schemas WHERE status = 'enabled' ORDER BY category, name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to list enabled tool schemas: {}", e.getMessage(), e);
        }
        return results;
    }

    /**
     * 按分类获取
     */
    public List<ToolSchemaEntry> findByCategory(String category) {
        List<ToolSchemaEntry> results = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM tool_schemas WHERE category = ? AND status = 'enabled' ORDER BY name")) {
            ps.setString(1, category);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find tool schemas by category: {}", e.getMessage(), e);
        }
        return results;
    }

    /**
     * 按 ID 查找
     */
    public Optional<ToolSchemaEntry> findById(String id) {
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM tool_schemas WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find tool schema: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 按名称查找
     */
    public Optional<ToolSchemaEntry> findByName(String name) {
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM tool_schemas WHERE name = ?")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(fromResultSet(rs));
            }
        } catch (Exception e) {
            logger.error("Failed to find tool schema by name: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 删除
     */
    public boolean delete(String id) {
        try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM tool_schemas WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("Failed to delete tool schema: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 清空所有
     */
    public int deleteAll() {
        try (Statement stmt = getConnection().createStatement()) {
            return stmt.executeUpdate("DELETE FROM tool_schemas");
        } catch (Exception e) {
            logger.error("Failed to delete all tool schemas: {}", e.getMessage(), e);
            return 0;
        }
    }

    private ToolSchemaEntry fromResultSet(ResultSet rs) throws SQLException {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setId(rs.getString("id"));
        entry.setName(rs.getString("name"));
        entry.setDescription(rs.getString("description"));
        entry.setCategory(rs.getString("category"));
        entry.setInputSchemaJson(rs.getString("input_schema_json"));
        entry.setOutputSchemaJson(rs.getString("output_schema_json"));
        entry.setStatus(rs.getString("status"));
        entry.setCreatedAt(rs.getString("created_at"));
        entry.setUpdatedAt(rs.getString("updated_at"));
        return entry;
    }
}
