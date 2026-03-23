package com.lightweightai.user;

import com.lightweightai.user.model.SoulUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite repository for soul_users table.
 */
public class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);

    private final String dbUrl;

    public UserRepository(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS soul_users (
                  id TEXT PRIMARY KEY,
                  username TEXT,
                  password_hash TEXT,
                  openid TEXT,
                  nickname TEXT DEFAULT '匿名用户',
                  member_level TEXT DEFAULT 'FREE',
                  created_at INTEGER,
                  last_active INTEGER
                )
                """);
            ensureColumn(stmt, "soul_users", "username", "TEXT");
            ensureColumn(stmt, "soul_users", "password_hash", "TEXT");
            ensureColumn(stmt, "soul_users", "role", "TEXT DEFAULT 'USER'");
            ensureColumn(stmt, "soul_users", "enabled", "INTEGER DEFAULT 1");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_soul_users_username ON soul_users(username)");
        } catch (SQLException e) {
            logger.error("Failed to init soul_users schema", e);
            throw new RuntimeException(e);
        }
    }

    private void ensureColumn(Statement stmt, String table, String column, String type) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (SQLException ignore) {
            // Already exists
        }
    }

    public void save(SoulUser user) {
        String sql = """
            INSERT INTO soul_users (id, username, password_hash, openid, nickname, member_level, role, enabled, created_at, last_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              username=excluded.username,
              password_hash=excluded.password_hash,
              openid=excluded.openid,
              nickname=excluded.nickname,
              member_level=excluded.member_level,
              role=excluded.role,
              enabled=excluded.enabled,
              last_active=excluded.last_active
            """;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getOpenid());
            ps.setString(5, user.getNickname());
            ps.setString(6, user.getMemberLevel());
            ps.setString(7, user.getRole() != null ? user.getRole() : "USER");
            ps.setInt(8, user.isEnabled() ? 1 : 0);
            ps.setLong(9, user.getCreatedAt());
            ps.setLong(10, user.getLastActive());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save user: {}", user.getId(), e);
            throw new RuntimeException(e);
        }
    }

    public Optional<SoulUser> findById(String id) {
        String sql = "SELECT * FROM soul_users WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find user: {}", id, e);
            throw new RuntimeException(e);
        }
    }

    public Optional<SoulUser> findByUsername(String username) {
        String sql = "SELECT * FROM soul_users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find user by username: {}", username, e);
            throw new RuntimeException(e);
        }
    }

    public void updateLastActive(String userId, long timestamp) {
        String sql = "UPDATE soul_users SET last_active = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, timestamp);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update last_active for user: {}", userId, e);
            throw new RuntimeException(e);
        }
    }

    public List<SoulUser> findAll() {
        String sql = "SELECT * FROM soul_users ORDER BY created_at DESC";
        List<SoulUser> users = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to list users", e);
            throw new RuntimeException(e);
        }
        return users;
    }

    public void updateRole(String userId, String role) {
        String sql = "UPDATE soul_users SET role = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update role for user: {}", userId, e);
            throw new RuntimeException(e);
        }
    }

    public void updateEnabled(String userId, boolean enabled) {
        String sql = "UPDATE soul_users SET enabled = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update enabled for user: {}", userId, e);
            throw new RuntimeException(e);
        }
    }

    private SoulUser mapRow(ResultSet rs) throws SQLException {
        String role;
        boolean enabled;
        try {
            role = rs.getString("role");
            if (role == null) role = "USER";
        } catch (SQLException e) {
            role = "USER";
        }
        try {
            enabled = rs.getInt("enabled") != 0;
        } catch (SQLException e) {
            enabled = true;
        }
        return new SoulUser(
            rs.getString("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("openid"),
            rs.getString("nickname"),
            rs.getString("member_level"),
            role,
            enabled,
            rs.getLong("created_at"),
            rs.getLong("last_active")
        );
    }
}
