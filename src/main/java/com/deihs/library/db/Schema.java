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
 *
 * <p>Part of: Infrastructure. The database upgrade step that runs at startup.<br>
 * Used by: Db.init(), which calls {@link #migrate()} after a successful
 * connection test.<br>
 * Key rules: every change is guarded by an existence check, so running this
 * twice is harmless; no statement ever drops a column or a row.</p>
 */
public final class Schema {

    /** WHAT: Blocks instantiation. WHY: only the static migrate() step is ever needed. */
    private Schema() {
    }

    /**
     * WHAT: Runs the whole upgrade in order: check the tables exist, add the
     * missing columns and indexes, then repair old rows.
     * WHY: Without it, a database from an older release would fail on the first
     * query that mentions role or due_at.
     * CALLED BY: Db.init().
     */
    public static void migrate() throws DaoException {
        try (Connection c = Db.getConnection()) {
            // Step 1: refuse early with a clear message if the schema was never
            // imported; every later ALTER would fail with a confusing error.
            requireTable(c, "users");
            requireTable(c, "books");

            // Step 2: structure changes, then data changes.
            migrateUsers(c);
            migrateBooks(c);
            backfill(c);
        } catch (SQLException ex) {
            throw new DaoException("Could not upgrade the database schema.\n\n"
                    + "Run database/schema.sql (or database/migrate_roles.sql) once, then start again.\n\n"
                    + "Driver said: " + ex.getMessage(), ex);
        }
    }

    // ============================================================
    // PART: users table
    // ============================================================

    /**
     * WHAT: Adds the columns the role and lockout features need.
     * WHY: A first-release users table has none of them, so login would fail.
     * CALLED BY: {@link #migrate}.
     */
    private static void migrateUsers(Connection c) throws SQLException {
        // addColumn() is a no-op when the column is already there.
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

        // Index on role keeps "list all librarians" queries cheap.
        if (!indexExists(c, "users", "idx_users_role")) {
            execute(c, "ALTER TABLE users ADD INDEX idx_users_role (role)");
        }
    }

    // ============================================================
    // PART: books table
    // ============================================================

    /**
     * WHAT: Adds the loan columns to books.
     * WHY: borrower_id links a loan to an account, and due_at is what the
     * overdue calculations read; without them no due date can be stored.
     * CALLED BY: {@link #migrate}.
     */
    private static void migrateBooks(Connection c) throws SQLException {
        addColumn(c, "books", "borrower_id", "INT NULL");
        addColumn(c, "books", "due_at", "DATETIME NULL");

        // The "my loans" and "overdue" screens filter on these two columns.
        if (!indexExists(c, "books", "idx_books_borrower")) {
            execute(c, "ALTER TABLE books ADD INDEX idx_books_borrower (borrower_id)");
        }
        if (!indexExists(c, "books", "idx_books_due")) {
            execute(c, "ALTER TABLE books ADD INDEX idx_books_due (due_at)");
        }
    }

    // ============================================================
    // PART: Data backfill
    // ============================================================

    /**
     * WHAT: Rewrites old rows so they satisfy the new rules.
     * WHY: Existing accounts and loans were created before role, borrower_id and
     * due_at existed, and the new code would misread them.
     * CALLED BY: {@link #migrate}.
     */
    private static void backfill(Connection c) throws SQLException {
        // Old administrator flag becomes the ADMIN role.
        execute(c, "UPDATE users SET role = 'ADMIN' WHERE is_admin = 1 AND role <> 'ADMIN'");

        // Never leave the system without an administrator.
        // The inner SELECT is wrapped in another SELECT because MySQL refuses to
        // read from the same table it is updating directly.
        if (count(c, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'") == 0
                && count(c, "SELECT COUNT(*) FROM users") > 0) {
            execute(c, "UPDATE users SET role = 'ADMIN' WHERE id = "
                    + "(SELECT id FROM (SELECT MIN(id) AS id FROM users) AS first_user)");
        }

        // Keep the legacy column in step with the role column.
        execute(c, "UPDATE users SET is_admin = CASE WHEN role = 'ADMIN' THEN 1 ELSE 0 END");

        // Link existing loans to accounts by display name, then give them a due date.
        // This is a best-effort match: only rows with no borrower_id yet are
        // touched, so a correct link is never overwritten.
        execute(c, "UPDATE books b JOIN users u ON u.display_name = b.borrower "
                + "SET b.borrower_id = u.id "
                + "WHERE b.borrowed = 1 AND b.borrower_id IS NULL");
        // Old loans had no due date; assume the standard 7-day loan so they can
        // become overdue instead of staying invisible.
        execute(c, "UPDATE books SET due_at = DATE_ADD(borrowed_at, INTERVAL 7 DAY) "
                + "WHERE borrowed = 1 AND due_at IS NULL AND borrowed_at IS NOT NULL");
    }

    // ============================================================
    // PART: information_schema helpers
    // ============================================================

    /**
     * WHAT: Throws when a required table is missing.
     * WHY: A specific message beats the raw SQL error from the first ALTER.
     * CALLED BY: {@link #migrate}.
     */
    private static void requireTable(Connection c, String table) throws SQLException, DaoException {
        // DATABASE() makes the query look only at the connected schema.
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

    /**
     * WHAT: Adds a column only when it does not exist yet.
     * WHY: This single check is what makes the whole migration safe to repeat.
     * CALLED BY: migrateUsers and migrateBooks.
     */
    private static void addColumn(Connection c, String table, String column, String definition)
            throws SQLException {
        if (!columnExists(c, table, column)) {
            // table, column and definition are hard-coded constants from this
            // class, never user input, so string concatenation is safe here.
            execute(c, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    /** WHAT: True when the column is present. WHY: guards every ALTER. CALLED BY: addColumn. */
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

    /**
     * WHAT: Reads the declared maximum length of a text column.
     * WHY: Needed to detect the old CHAR(64) password_hash, which was too short
     * for a PBKDF2 hash.
     * CALLED BY: migrateUsers.
     */
    private static Long columnLength(Connection c, String table, String column) throws SQLException {
        String sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long value = rs.getLong(1);
                    // A NULL length (e.g. a numeric column) must not be read as 0.
                    return rs.wasNull() ? null : value;
                }
            }
        }
        return null;
    }

    /** WHAT: True when the named index exists. WHY: avoids "duplicate key name" errors on re-run. */
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

    /** WHAT: Runs a COUNT query and returns the number. WHY: used by the backfill decisions. */
    private static int count(Connection c, String sql) throws SQLException {
        // No user input here, so a plain Statement is enough.
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** WHAT: Runs one ALTER/UPDATE built inside this class. WHY: removes repeated try-with-resources noise. */
    private static void execute(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
