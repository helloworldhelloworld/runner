package com.lightweightai.web.skillcreator;

import java.sql.*;
import java.util.*;

/** Additive SQLite migrations for publication evidence; column names are internal constants. */
final class PublicationSchema {
    private PublicationSchema() {}
    static void columns(Connection connection, String table, Map<String, String> definitions) throws SQLException {
        Set<String> existing = new HashSet<>();
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            try (ResultSet rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rows.next()) existing.add(rows.getString("name"));
            }
            for (var entry : definitions.entrySet()) {
                if (!existing.contains(entry.getKey())) statement.execute("ALTER TABLE " + table + " ADD COLUMN "
                        + entry.getKey() + " " + entry.getValue());
            }
        }
    }
}
