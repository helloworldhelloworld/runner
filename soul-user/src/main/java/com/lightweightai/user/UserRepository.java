package com.lightweightai.user;

import com.lightweightai.user.model.SoulUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

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
                  openid TEXT,
                  nickname TEXT DEFAULT '匿名用户',
                  member_level TEXT DEFAULT 'FREE',
                  created_at INTEGER,
                  last_active INTEGER
                )
                """);
        } catch (SQLException e) {
            logger.error("Failed to init soul_users schema", e);
            throw new RuntimeException(e);
        }
    }

    public void save(SoulUser user) {
        String sql = """
            INSERT INTO soul_users (id, openid, nickname, member_level, created_at, last_active)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              openid=excluded.openid,
              nickname=excluded.nickname,
              member_level=excluded.member_level,
              last_active=excluded.last_active
            """;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getOpenid());
            ps.setString(3, user.getNickname());
            ps.setString(4, user.getMemberLevel());
            ps.setLong(5, user.getCreatedAt());
            ps.setLong(6, user.getLastActive());
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

    private SoulUser mapRow(ResultSet rs) throws SQLException {
        return new SoulUser(
            rs.getString("id"),
            rs.getString("openid"),
            rs.getString("nickname"),
            rs.getString("member_level"),
            rs.getLong("created_at"),
            rs.getLong("last_active")
        );
    }
}
