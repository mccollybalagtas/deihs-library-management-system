package com.deihs.library.dao;

import com.deihs.library.db.Db;
import com.deihs.library.model.Role;
import com.deihs.library.model.User;
import com.deihs.library.util.Passwords;
import com.deihs.library.util.Validation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Authentication, lockout and account management.
 *
 * <p>Four wrong passwords lock the account for five minutes. The counter and
 * the lock expiry live in the database, so restarting the application does not
 * reset an attacker's attempts.</p>
 *
 * <p>Part of: Data-access layer. The only class that runs SQL against the
 * {@code users} table.<br>
 * Used by: LoginView (sign in, student self-registration) and UsersView (create,
 * list, change role, disable, unlock, reset password, delete); CirculationView
 * calls {@link #listStudents()} for the desk's student picker.<br>
 * Key rules: usernames are trimmed and lower-cased before every lookup, so
 * "Ana" and "ana" are the same account; the lockout state is persisted in MySQL,
 * not held in memory; a missing or unknown role becomes STUDENT.</p>
 */
public final class UserDao {

    // ============================================================
    // PART: Lockout policy
    // ============================================================

    /** Wrong passwords allowed before the account locks. */
    public static final int MAX_ATTEMPTS = 4;
    /** How long the account stays locked. */
    public static final int LOCK_MINUTES = 5;

    // ============================================================
    // PART: Result types
    // ============================================================

    /** Outcome of a sign-in attempt. */
    public enum LoginStatus {
        SUCCESS,
        /** The text was not a valid username (e.g. it contained digits). */
        INVALID_USERNAME,
        /** The username does not exist, or the password is wrong. */
        INVALID_CREDENTIALS,
        /** Too many wrong passwords; the account is temporarily locked. */
        LOCKED,
        /** The administrator disabled the account. */
        INACTIVE
    }

    /** Everything the login screen needs to explain what happened. */
    public record LoginResult(LoginStatus status, User user, int attemptsLeft, LocalDateTime lockedUntil) {

        /** True only when the credentials were accepted. */
        public boolean isSuccess() {
            return status == LoginStatus.SUCCESS;
        }
    }

    /** One row in the administrator's account list. */
    public record Account(int id, String username, String displayName, Role role, boolean active,
                          int failedAttempts, LocalDateTime lockedUntil, LocalDateTime lastLogin,
                          LocalDateTime createdAt) {

        /** True while the stored lock expiry is still in the future. */
        public boolean isLocked() {
            return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
        }

        /** Disabled beats Locked: a disabled account cannot sign in either way. */
        public String statusText() {
            if (!active) {
                return "Disabled";
            }
            return isLocked() ? "Locked" : "Active";
        }

        /** CSS pill class that matches statusText(). */
        public String statusStyle() {
            if (!active) {
                return "pill-overdue";
            }
            return isLocked() ? "pill-soon" : "pill-available";
        }

        /** JavaFX ComboBox and TableView show this when no cell factory is set. */
        @Override
        public String toString() {
            return displayName + "  (" + username + ")";
        }
    }

    /** Counts for the account page header. */
    public record Summary(int total, int admins, int librarians, int students, int locked, int disabled) {
    }

    // ============================================================
    // PART: Sign-in
    // ============================================================

    /**
     * WHAT: Checks a username and password, enforces the lockout and upgrades old
     * password hashes.
     * WHY: Every sign-in goes through here, so the lockout rule cannot be skipped
     * by another screen.
     * CALLED BY: LoginView.
     */
    public LoginResult login(String username, String password) throws DaoException {
        // Step 1: normalise and reject input that is not a username at all. This
        // is reported separately from a wrong password so a typo like "12345"
        // gets a helpful message instead of "wrong password".
        String name = normalise(username);
        if (!Validation.isValidUsername(name)) {
            return new LoginResult(LoginStatus.INVALID_USERNAME, null, MAX_ATTEMPTS, null);
        }

        String sql = "SELECT id, username, display_name, role, is_admin, password_hash, "
                + "failed_attempts, locked_until, active FROM users WHERE username = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Do not reveal whether the account exists.
                    return new LoginResult(LoginStatus.INVALID_CREDENTIALS, null, MAX_ATTEMPTS, null);
                }

                int id = rs.getInt("id");
                String stored = rs.getString("password_hash");
                LocalDateTime lockedUntil = toDateTime(rs.getTimestamp("locked_until"));
                boolean active = rs.getBoolean("active");
                int failed = rs.getInt("failed_attempts");
                LocalDateTime now = LocalDateTime.now();

                // Step 2: an unexpired lock wins over everything else, even a
                // correct password, so guessing is pointless while locked.
                if (lockedUntil != null && lockedUntil.isAfter(now)) {
                    return new LoginResult(LoginStatus.LOCKED, null, 0, lockedUntil);
                }
                // The lock expired: start a fresh set of attempts.
                if (lockedUntil != null) {
                    failed = 0;
                }

                // Step 3: a disabled account is refused after the lock check so
                // an administrator sees the real reason.
                if (!active) {
                    return new LoginResult(LoginStatus.INACTIVE, null, 0, null);
                }

                // Step 4: verify the password. The work factor (120k PBKDF2
                // rounds) is what makes offline guessing expensive.
                if (!Passwords.verify(name, password, stored)) {
                    int attempts = failed + 1;
                    // Reaching the limit stores the lock expiry in MySQL and
                    // reports LOCKED straight away, so the 4th try already locks.
                    if (attempts >= MAX_ATTEMPTS) {
                        LocalDateTime until = now.plusMinutes(LOCK_MINUTES);
                        updateAttempts(c, id, attempts, until);
                        return new LoginResult(LoginStatus.LOCKED, null, 0, until);
                    }
                    updateAttempts(c, id, attempts, null);
                    return new LoginResult(LoginStatus.INVALID_CREDENTIALS, null, MAX_ATTEMPTS - attempts, null);
                }

                // Step 5: success. Clear the counter and, when the stored hash
                // uses an older scheme, replace it with a fresh PBKDF2 hash.
                // Re-hashing is safe here because the plain password is known now.
                if (Passwords.needsUpgrade(stored)) {
                    String upgraded = Passwords.hash(name, password);
                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE users SET password_hash = ?, failed_attempts = 0, "
                                    + "locked_until = NULL, last_login = NOW() WHERE id = ?")) {
                        up.setString(1, upgraded);
                        up.setInt(2, id);
                        up.executeUpdate();
                    }
                } else {
                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE users SET failed_attempts = 0, locked_until = NULL, "
                                    + "last_login = NOW() WHERE id = ?")) {
                        up.setInt(1, id);
                        up.executeUpdate();
                    }
                }

                User user = new User(
                        id,
                        rs.getString("username"),
                        rs.getString("display_name"),
                        roleOf(rs));
                return new LoginResult(LoginStatus.SUCCESS, user, MAX_ATTEMPTS, null);
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not check your credentials.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * WHAT: Writes the failure counter and, when given, the lock expiry.
     * WHY: Persisting them in the database is what makes the lockout survive an
     * application restart.
     * CALLED BY: {@link #login} only.
     */
    private static void updateAttempts(Connection c, int id, int attempts, LocalDateTime lockedUntil)
            throws SQLException {
        // Reuses the caller's connection so the read and the write happen in the
        // same session; null clears any previous lock.
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?")) {
            ps.setInt(1, attempts);
            ps.setTimestamp(2, lockedUntil == null ? null : Timestamp.valueOf(lockedUntil));
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    // ============================================================
    // PART: Account management
    // ============================================================

    /**
     * WHAT: True when the username already exists.
     * WHY: Lets the registration form warn before the insert fails on the unique
     * index.
     * CALLED BY: UsersView (live check in the create-account dialog).
     */
    public boolean exists(String username) throws DaoException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalise(username));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not look up that username.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * WHAT: Creates an account with a validated name, password and role.
     * WHY: Validation happens here as well as in the dialog, so no caller can
     * store an empty password or an invalid username.
     * CALLED BY: UsersView (admin creates an account) and createStudent().
     */
    public User create(String username, String displayName, String password, Role role) throws DaoException {
        // Step 1: normalise the inputs and pick safe defaults.
        String name = normalise(username);
        String display = displayName == null || displayName.isBlank() ? username.trim() : displayName.trim();
        Role effective = role == null ? Role.STUDENT : role;

        // Step 2: collect the user-facing reason, if any, before touching SQL.
        String problem = Validation.usernameProblem(name);
        if (problem != null) {
            throw new DaoException(problem);
        }
        problem = Validation.passwordProblem(password);
        if (problem != null) {
            throw new DaoException(problem);
        }

        // Step 3: insert. is_admin is kept in step with the role column so
        // older code paths that still read is_admin keep working.
        String sql = "INSERT INTO users (username, password_hash, display_name, role, is_admin, active) "
                + "VALUES (?, ?, ?, ?, ?, 1)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            // Only the hash is stored; the plain password never reaches MySQL.
            ps.setString(2, Passwords.hash(name, password));
            ps.setString(3, display);
            ps.setString(4, effective.name());
            ps.setBoolean(5, effective == Role.ADMIN);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                // id 0 only happens if the driver returned no key; the row still
                // exists, so this is not treated as an error.
                int id = keys.next() ? keys.getInt(1) : 0;
                return new User(id, name, display, effective);
            }
        } catch (SQLIntegrityConstraintViolationException ex) {
            // The unique index on username rejected the insert.
            throw new DaoException("That username is already taken.", ex);
        } catch (SQLException ex) {
            throw new DaoException("Could not create the account.\n" + ex.getMessage(), ex);
        }
    }

    /** Convenience for the public registration form. */
    public User createStudent(String username, String displayName, String password) throws DaoException {
        return create(username, displayName, password, Role.STUDENT);
    }

    /**
     * WHAT: Loads every account as an Account record.
     * WHY: One query feeds the table, the summary counters and the student
     * picker.
     * CALLED BY: UsersView and {@link #listStudents()} / {@link #summary()}.
     */
    public List<Account> listAccounts() throws DaoException {
        // ORDER BY role sorts by the stored string, which happens to group the
        // three roles together; display_name then orders people inside a group.
        String sql = "SELECT id, username, display_name, role, is_admin, active, failed_attempts, "
                + "locked_until, last_login, created_at FROM users ORDER BY role, display_name";
        List<Account> accounts = new ArrayList<>();
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(new Account(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        roleOf(rs),
                        rs.getBoolean("active"),
                        rs.getInt("failed_attempts"),
                        toDateTime(rs.getTimestamp("locked_until")),
                        toDateTime(rs.getTimestamp("last_login")),
                        toDateTime(rs.getTimestamp("created_at"))));
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not load the account list.\n" + ex.getMessage(), ex);
        }
        return accounts;
    }

    /**
     * WHAT: The active students only.
     * WHY: The circulation desk may only lend to enabled student accounts.
     * CALLED BY: CirculationView (student picker).
     */
    public List<Account> listStudents() throws DaoException {
        List<Account> students = new ArrayList<>();
        // Filtering in Java keeps the SQL simple; the account list is small
        // enough that loading it once is cheaper than a second query.
        for (Account account : listAccounts()) {
            if (account.role() == Role.STUDENT && account.active()) {
                students.add(account);
            }
        }
        return students;
    }

    /**
     * WHAT: Counts per role plus locked and disabled totals.
     * WHY: Drives the small numbers above the account table.
     * CALLED BY: UsersView.
     */
    public Summary summary() throws DaoException {
        int admins = 0;
        int librarians = 0;
        int students = 0;
        int locked = 0;
        int disabled = 0;
        List<Account> accounts = listAccounts();
        for (Account account : accounts) {
            switch (account.role()) {
                case ADMIN -> admins++;
                case LIBRARIAN -> librarians++;
                case STUDENT -> students++;
            }
            // Locked and disabled are independent: an account can be both.
            if (account.isLocked()) {
                locked++;
            }
            if (!account.active()) {
                disabled++;
            }
        }
        return new Summary(accounts.size(), admins, librarians, students, locked, disabled);
    }

    /**
     * WHAT: Changes an account's role.
     * WHY: is_admin must be rewritten at the same time, otherwise the legacy
     * column and the role column would disagree.
     * CALLED BY: UsersView (change role dialog).
     */
    public void setRole(int userId, Role role) throws DaoException {
        Role effective = role == null ? Role.STUDENT : role;
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE users SET role = ?, is_admin = ? WHERE id = ?")) {
            ps.setString(1, effective.name());
            ps.setBoolean(2, effective == Role.ADMIN);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not change that account's role.\n" + ex.getMessage(), ex);
        }
    }

    /** WHAT: Enables or disables an account. WHY: a disabled account cannot sign in. CALLED BY: UsersView. */
    public void setActive(int userId, boolean active) throws DaoException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET active = ? WHERE id = ?")) {
            ps.setBoolean(1, active);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not update that account.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * WHAT: Clears the failure counter and the lock expiry.
     * WHY: An administrator must be able to let a locked-out user back in
     * without waiting five minutes.
     * CALLED BY: UsersView (unlock action).
     */
    public void unlock(int userId) throws DaoException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not unlock that account.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * WHAT: Stores a new password hash and clears any lock.
     * WHY: A reset password must also lift the lockout, otherwise the user still
     * cannot sign in.
     * CALLED BY: UsersView (reset action) and {@link #changePassword}.
     */
    public void resetPassword(int userId, String username, String newPassword) throws DaoException {
        String problem = Validation.passwordProblem(newPassword);
        if (problem != null) {
            throw new DaoException(problem);
        }
        String sql = "UPDATE users SET password_hash = ?, failed_attempts = 0, locked_until = NULL "
                + "WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            // The username is part of the hash input only in the legacy scheme;
            // passing it here keeps both schemes consistent.
            ps.setString(1, Passwords.hash(normalise(username), newPassword));
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not reset the password.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * WHAT: Counts the books still out to one account.
     * WHY: An account with loans cannot be deleted, because the loan rows would
     * point at a missing user.
     * CALLED BY: {@link #delete}.
     */
    public int activeLoanCount(int userId) throws DaoException {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM books WHERE borrowed = 1 AND borrower_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not check that account's loans.\n" + ex.getMessage(), ex);
        }
    }

    /**
     * WHAT: Deletes an account, refusing while it still holds books.
     * WHY: The check gives a clear message instead of leaving orphaned loans.
     * CALLED BY: UsersView (delete action).
     */
    public void delete(int userId) throws DaoException {
        // Step 1: refuse early with a readable reason.
        int loans = activeLoanCount(userId);
        if (loans > 0) {
            throw new DaoException("That account still has " + loans
                    + (loans == 1 ? " book" : " books") + " on loan.\n"
                    + "Return them from the circulation desk before deleting the account.");
        }
        // Step 2: delete the row.
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not delete that account.\n" + ex.getMessage(), ex);
        }
    }

    /** Alias kept for callers that talk about "changing" the signed-in user's own password. */
    public void changePassword(int userId, String username, String newPassword) throws DaoException {
        resetPassword(userId, username, newPassword);
    }

    // ============================================================
    // PART: Helpers
    // ============================================================

    /**
     * WHAT: Reads the role column, falling back to the legacy is_admin flag.
     * WHY: Databases upgraded from the first release may still have rows written
     * before the role column existed.
     * CALLED BY: login and listAccounts.
     */
    private static Role roleOf(ResultSet rs) throws SQLException {
        String value = rs.getString("role");
        if (value == null || value.isBlank()) {
            return rs.getBoolean("is_admin") ? Role.ADMIN : Role.STUDENT;
        }
        return Role.from(value);
    }

    /** WHAT: Null-safe Timestamp to LocalDateTime conversion. WHY: avoids repeating the check at each call. */
    private static LocalDateTime toDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * WHAT: Trims and lower-cases a username.
     * WHY: Usernames are case-insensitive and must match the unique index, so
     * every read and write goes through this one rule.
     */
    private static String normalise(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
