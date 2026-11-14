package com.deihs.library.db;

import com.deihs.library.dao.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Brings an existing database up to the schema this build expects.
 *
 * <p>The first release only knew about {@code is_admin}. This class adds the
 * role column, the lockout counters and the loan due-date columns without
 * dropping any data. Every step checks {@code information_schema} first, so it
 * is safe to run on every launch and on both MySQL and MariaDB.</p>
 */
public final class Schema {

    private Schema() {
    }

    public static void migrate() throws DaoException {
        try (Connection c = Db.getConnection()) {
            requireTable(c, "users");
            requireTable(c, "books");

            migrateUsers(c);
            migrateBooks(c);
            backfill(c);
        } catch (SQLException ex) {
            throw new DaoException("Could not upgrade the database schema.\n\n"
                    + "Run database/schema.sql (or database/migrate_roles.sql) once, then start again.\n\n"
                    + "Driver said: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------
    // users
    // ------------------------------------------------------------------

    private static void migrateUsers(Connection c) throws SQLException {
        addColumn(c, "users", "role", "VARCHAR(20) NOT NULL DEFAULT 'STUDENT'");
        addColumn(c, "users", "failed_attempts", "INT NOT NULL DEFAULT 0");
        addColumn(c, "users", "locked_until", "DATETIME NULL");
        addColumn(c, "users", "active", "TINYINT(1) NOT NULL DEFAULT 1");
        addColumn(c, "users", "last_login", "DATETIME NULL");

        // PBKDF2 hashes are ~100 characters; the old CHAR(64) column truncated them.
        Long hashLength = columnLength(c, "users", "password_hash");
        if (hashLength == null || hashLength < 255) {
            execute(c, "ALTER TABLE users MODIFY password_hash VARCHAR(255) NOT NULL");
        }

        if (!indexExists(c, "users", "idx_users_role")) {
            execute(c, "ALTER TABLE users ADD INDEX idx_users_role (role)");
        }
    }

    // ------------------------------------------------------------------
    // books
    // ------------------------------------------------------------------

    private static void migrateBooks(Connection c) throws SQLException {
        addColumn(c, "books", "borrower_id", "INT NULL");
        addColumn(c, "books", "due_at", "DATETIME NULL");

        if (!indexExists(c, "books", "idx_books_borrower")) {
            execute(c, "ALTER TABLE books ADD INDEX idx_books_borrower (borrower_id)");
        }
        if (!indexExists(c, "books", "idx_books_due")) {
            execute(c, "ALTER TABLE books ADD INDEX idx_books_due (due_at)");
        }
    }

    // ------------------------------------------------------------------
    // Data backfill
    // ------------------------------------------------------------------

    private static void backfill(Connection c) throws SQLException {
        // Old administrator flag becomes the ADMIN role.
        execute(c, "UPDATE users SET role = 'ADMIN' WHERE is_admin = 1 AND role <> 'ADMIN'");

        // Never leave the system without an administrator.
        if (count(c, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'") == 0
                && count(c, "SELECT COUNT(*) FROM users") > 0) {
            execute(c, "UPDATE users SET role = 'ADMIN' WHERE id = "
                    + "(SELECT id FROM (SELECT MIN(id) AS id FROM users) AS first_user)");
        }

        // Keep the legacy column in step with the role column.
        execute(c, "UPDATE users SET is_admin = CASE WHEN role = 'ADMIN' THEN 1 ELSE 0 END");

        // Link existing loans to accounts by display name, then give them a due date.
        execute(c, "UPDATE books b JOIN users u ON u.display_name = b.borrower "
                + "SET b.borrower_id = u.id "
                + "WHERE b.borrowed = 1 AND b.borrower_id IS NULL");
        execute(c, "UPDATE books SET due_at = DATE_ADD(borrowed_at, INTERVAL 7 DAY) "
                + "WHERE borrowed = 1 AND due_at IS NULL AND borrowed_at IS NOT NULL");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void requireTable(Connection c, String table) throws SQLException, DaoException {
        String sql = "SELECT 1 FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DaoException("The database is missing the \"" + table + "\" table.\n\n"
                            + "Import database/schema.sql first.");
                }
            }
        }
    }

    private static void addColumn(Connection c, String table, String column, String definition)
            throws SQLException {
        if (!columnExists(c, table, column)) {
            execute(c, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Long columnLength(Connection c, String table, String column) throws SQLException {
        String sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long value = rs.getLong(1);
                    return rs.wasNull() ? null : value;
                }
            }
        }
        return null;
    }

    private static boolean indexExists(Connection c, String table, String index) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int count(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
