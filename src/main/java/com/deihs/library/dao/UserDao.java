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
 */
public final class UserDao {

    /** Wrong passwords allowed before the account locks. */
    public static final int MAX_ATTEMPTS = 4;
    /** How long the account stays locked. */
    public static final int LOCK_MINUTES = 5;

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

        public boolean isSuccess() {
            return status == LoginStatus.SUCCESS;
        }
    }

    /** One row in the administrator's account list. */
    public record Account(int id, String username, String displayName, Role role, boolean active,
                          int failedAttempts, LocalDateTime lockedUntil, LocalDateTime lastLogin,
                          LocalDateTime createdAt) {

        public boolean isLocked() {
            return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
        }

        public String statusText() {
            if (!active) {
                return "Disabled";
            }
            return isLocked() ? "Locked" : "Active";
        }

        public String statusStyle() {
            if (!active) {
                return "pill-overdue";
            }
            return isLocked() ? "pill-soon" : "pill-available";
        }

        @Override
        public String toString() {
            return displayName + "  (" + username + ")";
        }
    }

    /** Counts for the account page header. */
    public record Summary(int total, int admins, int librarians, int students, int locked, int disabled) {
    }

    // ------------------------------------------------------------------
    // Sign-in
    // ------------------------------------------------------------------

    public LoginResult login(String username, String password) throws DaoException {
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

                if (lockedUntil != null && lockedUntil.isAfter(now)) {
                    return new LoginResult(LoginStatus.LOCKED, null, 0, lockedUntil);
                }
                // The lock expired: start a fresh set of attempts.
                if (lockedUntil != null) {
                    failed = 0;
                }

                if (!active) {
                    return new LoginResult(LoginStatus.INACTIVE, null, 0, null);
                }

                if (!Passwords.verify(name, password, stored)) {
                    int attempts = failed + 1;
                    if (attempts >= MAX_ATTEMPTS) {
                        LocalDateTime until = now.plusMinutes(LOCK_MINUTES);
                        updateAttempts(c, id, attempts, until);
                        return new LoginResult(LoginStatus.LOCKED, null, 0, until);
                    }
                    updateAttempts(c, id, attempts, null);
                    return new LoginResult(LoginStatus.INVALID_CREDENTIALS, null, MAX_ATTEMPTS - attempts, null);
                }

                // Success: clear the counter and quietly upgrade old hashes.
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

    private static void updateAttempts(Connection c, int id, int attempts, LocalDateTime lockedUntil)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?")) {
            ps.setInt(1, attempts);
            ps.setTimestamp(2, lockedUntil == null ? null : Timestamp.valueOf(lockedUntil));
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Account management
    // ------------------------------------------------------------------

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

    public User create(String username, String displayName, String password, Role role) throws DaoException {
        String name = normalise(username);
        String display = displayName == null || displayName.isBlank() ? username.trim() : displayName.trim();
        Role effective = role == null ? Role.STUDENT : role;

        String problem = Validation.usernameProblem(name);
        if (problem != null) {
            throw new DaoException(problem);
        }
        problem = Validation.passwordProblem(password);
        if (problem != null) {
            throw new DaoException(problem);
        }

        String sql = "INSERT INTO users (username, password_hash, display_name, role, is_admin, active) "
                + "VALUES (?, ?, ?, ?, ?, 1)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, Passwords.hash(name, password));
            ps.setString(3, display);
            ps.setString(4, effective.name());
            ps.setBoolean(5, effective == Role.ADMIN);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                return new User(id, name, display, effective);
            }
        } catch (SQLIntegrityConstraintViolationException ex) {
            throw new DaoException("That username is already taken.", ex);
        } catch (SQLException ex) {
            throw new DaoException("Could not create the account.\n" + ex.getMessage(), ex);
        }
    }

    /** Convenience for the public registration form. */
    public User createStudent(String username, String displayName, String password) throws DaoException {
        return create(username, displayName, password, Role.STUDENT);
    }

    public List<Account> listAccounts() throws DaoException {
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

    public List<Account> listStudents() throws DaoException {
        List<Account> students = new ArrayList<>();
        for (Account account : listAccounts()) {
            if (account.role() == Role.STUDENT && account.active()) {
                students.add(account);
            }
        }
        return students;
    }

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
            if (account.isLocked()) {
                locked++;
            }
            if (!account.active()) {
                disabled++;
            }
        }
        return new Summary(accounts.size(), admins, librarians, students, locked, disabled);
    }

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

    public void resetPassword(int userId, String username, String newPassword) throws DaoException {
        String problem = Validation.passwordProblem(newPassword);
        if (problem != null) {
            throw new DaoException(problem);
        }
        String sql = "UPDATE users SET password_hash = ?, failed_attempts = 0, locked_until = NULL "
                + "WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Passwords.hash(normalise(username), newPassword));
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not reset the password.\n" + ex.getMessage(), ex);
        }
    }

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

    public void delete(int userId) throws DaoException {
        int loans = activeLoanCount(userId);
        if (loans > 0) {
            throw new DaoException("That account still has " + loans
                    + (loans == 1 ? " book" : " books") + " on loan.\n"
                    + "Return them from the circulation desk before deleting the account.");
        }
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not delete that account.\n" + ex.getMessage(), ex);
        }
    }

    public void changePassword(int userId, String username, String newPassword) throws DaoException {
        resetPassword(userId, username, newPassword);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Role roleOf(ResultSet rs) throws SQLException {
        String value = rs.getString("role");
        if (value == null || value.isBlank()) {
            return rs.getBoolean("is_admin") ? Role.ADMIN : Role.STUDENT;
        }
        return Role.from(value);
    }

    private static LocalDateTime toDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String normalise(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
