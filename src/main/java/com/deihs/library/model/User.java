package com.deihs.library.model;

/**
 * A signed-in library account. Never carries the password hash.
 *
 * <p>Part of: Model layer. The immutable identity of the person using the
 * application after a successful login.<br>
 * Used by: {@code UserDao.login()} creates it, {@code UserDao.create()} returns
 * it, and the ui classes MainView, LoginView, MyLoansView, DashboardView and
 * CirculationView read it for the current user.<br>
 * Key rules: fields are final, so the signed-in identity cannot be changed
 * halfway through a session; the password hash is deliberately not a field.</p>
 */
public final class User {

    // ============================================================
    // PART: Identity
    // ============================================================

    /** users.id. */
    private final int id;
    /** Login name, already lower-cased by UserDao. */
    private final String username;
    /** Name shown in the top bar and in tables. */
    private final String displayName;
    /** Permission source of truth. */
    private final Role role;

    /** WHAT: Builds the signed-in identity. WHY: one object per successful login. CALLED BY: UserDao. */
    public User(int id, String username, String displayName, Role role) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        // A null role would make every permission check throw, so fall back to
        // the least privileged role instead of trusting the caller.
        this.role = role == null ? Role.STUDENT : role;
    }

    // ============================================================
    // PART: Accessors
    // ============================================================

    /** Account ID used as books.borrower_id. */
    public int getId() {
        return id;
    }

    /** Login name. */
    public String getUsername() {
        return username;
    }

    /** Friendly name for the UI. */
    public String getDisplayName() {
        return displayName;
    }

    /** Role that decides which screens and buttons are available. */
    public Role getRole() {
        return role;
    }

    /** Kept for older call sites; the role is the real source of truth. */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    // ============================================================
    // PART: Display helpers
    // ============================================================

    /**
     * WHAT: First letter used for the avatar badge.
     * WHY: The badge must never be empty, so it falls back through display name,
     * username and finally "?".
     */
    public String initial() {
        String source = displayName == null || displayName.isBlank() ? username : displayName;
        if (source == null || source.isBlank()) {
            return "?";
        }
        return source.substring(0, 1).toUpperCase();
    }

    /** Short label for the top bar, e.g. "Administrator". */
    public String roleLabel() {
        return role.label();
    }
}
