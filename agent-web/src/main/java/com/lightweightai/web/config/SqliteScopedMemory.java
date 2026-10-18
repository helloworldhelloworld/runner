package com.lightweightai.web.config;

import com.lightweightai.kernel.memory.*;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Isolated native persistence. Legacy shared files are never imported implicitly. */
final class SqliteScopedMemory implements MemoryProvider {
    private final String url;
    private final String principal;
    private final String agent;
    private final String origin;
    private final long generation;

    SqliteScopedMemory(Path database, String principal, String agent) {
        this("jdbc:sqlite:" + database, principal, agent, null, -1);
        access(connection -> {
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE IF NOT EXISTS native_sessions (principal TEXT, agent TEXT, session TEXT, generation INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(principal,agent,session))");
                sql.execute("CREATE TABLE IF NOT EXISTS native_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, principal TEXT NOT NULL, agent TEXT NOT NULL, session TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL)");
                sql.execute("CREATE INDEX IF NOT EXISTS native_message_scope ON native_messages(principal,agent,session,id)");
                sql.execute("CREATE TABLE IF NOT EXISTS native_memories (id INTEGER PRIMARY KEY AUTOINCREMENT, principal TEXT NOT NULL, agent TEXT NOT NULL, session TEXT, kind TEXT NOT NULL, section TEXT, day TEXT NOT NULL, content TEXT NOT NULL)");
                sql.execute("CREATE INDEX IF NOT EXISTS native_memory_scope ON native_memories(principal,agent,session)");
            }
            return null;
        });
    }
    private SqliteScopedMemory(String url, String principal, String agent, String origin, long generation) {
        this.url = url; this.principal = Objects.requireNonNull(principal); this.agent = Objects.requireNonNull(agent);
        this.origin = origin; this.generation = generation;
    }
    @Override public MemoryProvider forSession(String session) {
        long epoch = access(c -> {
            update(c, "INSERT INTO native_sessions(principal,agent,session) VALUES(?,?,?) ON CONFLICT DO NOTHING", principal, agent, session);
            try (var query = prepare(c, "SELECT generation FROM native_sessions WHERE principal=? AND agent=? AND session=?", principal, agent, session);
                 var rows = query.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Session disappeared");
                return rows.getLong(1);
            }
        });
        return new SqliteScopedMemory(url, principal, agent, session, epoch);
    }
    @Override public void addMessage(String session, Message message) {
        if (origin != null && !origin.equals(session)) throw new IllegalArgumentException("Memory view belongs to another session");
        write(c -> {
            update(c, "INSERT INTO native_messages(principal,agent,session,role,content) VALUES(?,?,?,?,?)",
                    principal, agent, session, message.getRole(), message.getContent());
            return null;
        });
    }
    @Override public List<Message> getHistory(String session, int limit) {
        if (limit <= 0) return List.of();
        return access(c -> {
            var result = new ArrayList<Message>();
            try (var query = prepare(c, "SELECT role,content FROM (SELECT id,role,content FROM native_messages WHERE principal=? AND agent=? AND session=? ORDER BY id DESC LIMIT ?) ORDER BY id", principal, agent, session, limit);
                 var rows = query.executeQuery()) {
                while (rows.next()) result.add(Message.of(rows.getString(1), rows.getString(2)));
            }
            return List.copyOf(result);
        });
    }
    @Override public void clearSession(String session) {
        access(c -> transaction(c, connection -> {
            String prefix = session + ":subagent:";
            // Descendant keys include the original root key, even when the child uses another agent.
            update(connection, "UPDATE native_sessions SET generation=generation+1 WHERE principal=? AND (session=? OR substr(session,1,?)=?)", principal, session, prefix.length(), prefix);
            update(connection, "DELETE FROM native_messages WHERE principal=? AND (session=? OR substr(session,1,?)=?)", principal, session, prefix.length(), prefix);
            update(connection, "DELETE FROM native_memories WHERE principal=? AND kind='ephemeral' AND (session=? OR substr(session,1,?)=?)", principal, session, prefix.length(), prefix);
            return null;
        }));
    }
    @Override public void writeEphemeral(String content) { remember("ephemeral", "", content); }
    @Override public void writeDurable(String section, String content) { remember("durable", section, content); }
    private void remember(String kind, String section, String content) {
        Objects.requireNonNull(content);
        write(c -> {
            update(c, "INSERT INTO native_memories(principal,agent,session,kind,section,day,content) VALUES(?,?,?,?,?,?,?)",
                    principal, agent, origin, kind, section, LocalDate.now().toString(), content);
            return null;
        });
    }
    private record Entry(String kind, String section, String session, String day, String content) {}
    private List<Entry> entries() {
        return access(c -> {
            var result = new ArrayList<Entry>();
            try (var query = prepare(c, "SELECT kind,section,session,day,content FROM native_memories WHERE principal=? AND agent=? ORDER BY id", principal, agent);
                 var rows = query.executeQuery()) {
                while (rows.next()) result.add(new Entry(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4), rows.getString(5)));
            }
            return result;
        });
    }
    @Override public String readDurable() {
        return entries().stream().filter(e -> e.kind.equals("durable"))
                .map(e -> "## " + e.section + "\n" + e.content).collect(java.util.stream.Collectors.joining("\n"));
    }
    @Override public String readTodayEphemeral() {
        return entries().stream().filter(e -> e.kind.equals("ephemeral") && e.day.equals(LocalDate.now().toString()))
                .map(Entry::content).collect(java.util.stream.Collectors.joining("\n"));
    }
    @Override public List<MemorySearchResult> search(String query) { return search(query, SearchOptions.defaults()); }
    @Override public List<MemorySearchResult> search(String query, SearchOptions options) {
        if (query == null || query.isBlank()) return List.of();
        String[] terms = query.toLowerCase(Locale.ROOT).split("\\s+");
        var results = new ArrayList<MemorySearchResult>();
        for (Entry entry : entries()) {
            boolean durable = entry.kind.equals("durable");
            if (durable ? !options.isIncludeDurable() : !options.isIncludeEphemeral()) continue;
            long hits = Arrays.stream(terms).filter(term -> entry.content.toLowerCase(Locale.ROOT).contains(term)).count();
            if (hits == 0) continue;
            double score = (double) hits / terms.length;
            results.add(durable ? MemorySearchResult.durable(entry.content, entry.section, score)
                    : MemorySearchResult.ephemeral(entry.content, entry.session, score));
        }
        return results.stream().sorted(Comparator.comparingDouble(MemorySearchResult::getScore).reversed())
                .limit(Math.max(0, options.getTopK())).toList();
    }
    private <T> T write(Sql<T> operation) {
        return access(c -> transaction(c, connection -> {
            if (origin != null) {
                try (var query = prepare(connection, "SELECT generation FROM native_sessions WHERE principal=? AND agent=? AND session=?", principal, agent, origin);
                     var rows = query.executeQuery()) {
                    if (!rows.next() || rows.getLong(1) != generation) throw new IllegalStateException("Session was cleared; stale writes are rejected");
                }
            }
            return operation.run(connection);
        }));
    }
    private static <T> T transaction(Connection c, Sql<T> operation) throws SQLException {
        try (var statement = c.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            boolean committed = false;
            try {
                T result = operation.run(c);
                statement.execute("COMMIT"); committed = true; return result;
            } finally { if (!committed) statement.execute("ROLLBACK"); }
        }
    }
    @FunctionalInterface private interface Sql<T> { T run(Connection connection) throws SQLException; }
    private <T> T access(Sql<T> operation) {
        try (Connection c = DriverManager.getConnection(url)) {
            try (var options = c.createStatement()) { options.execute("PRAGMA busy_timeout=5000"); }
            return operation.run(c);
        } catch (SQLException error) { throw new IllegalStateException("Native memory persistence failed", error); }
    }
    private static PreparedStatement prepare(Connection c, String query, Object... values) throws SQLException {
        var result = c.prepareStatement(query);
        for (int i = 0; i < values.length; i++) result.setObject(i + 1, values[i]);
        return result;
    }
    private static void update(Connection c, String query, Object... values) throws SQLException {
        try (var statement = prepare(c, query, values)) { statement.executeUpdate(); }
    }
}
