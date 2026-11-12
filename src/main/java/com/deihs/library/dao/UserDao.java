package com.deihs.library.dao;

import com.deihs.library.db.Db;
import com.deihs.library.model.User;
import com.deihs.library.util.Passwords;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Locale;

/** Authentication and account creation against the {@code users} table. */
public final class UserDao {

    /**
     * Returns the matching user, or {@code null} when the username/password
     * combination is wrong.
     */
    public User authenticate(String username, String password) throws DaoException {
        String name = normalise(username);
        if (name.isEmpty()) {
            return null;
        }
        String sql = "SELECT id, username, display_name, is_admin, password_hash "
                + "FROM users WHERE username = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String stored = rs.getString("password_hash");
                if (!stored.equalsIgnoreCase(Passwords.hash(name, password))) {
                    return null;
                }
                return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getBoolean("is_admin"));
            }
        } catch (SQLException ex) {
            throw new DaoException("Could not check your credentials.\n" + ex.getMessage(), ex);
        }
    }

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

    public User create(String username, String displayName, String password) throws DaoException {
        String name = normalise(username);
        String display = displayName == null || displayName.isBlank() ? username.trim() : displayName.trim();
        String sql = "INSERT INTO users (username, password_hash, display_name, is_admin) VALUES (?, ?, ?, 0)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, Passwords.hash(name, password));
            ps.setString(3, display);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                return new User(id, name, display, false);
            }
        } catch (SQLIntegrityConstraintViolationException ex) {
            throw new DaoException("That username is already taken.", ex);
        } catch (SQLException ex) {
            throw new DaoException("Could not create the account.\n" + ex.getMessage(), ex);
        }
    }

    public void changePassword(int userId, String username, String newPassword) throws DaoException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Passwords.hash(normalise(username), newPassword));
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Could not update the password.\n" + ex.getMessage(), ex);
        }
    }

    private static String normalise(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
