package com.deihs.library.model;

/** A signed-in library account. Never carries the password hash. */
public final class User {

    private final int id;
    private final String username;
    private final String displayName;
    private final Role role;

    public User(int id, String username, String displayName, Role role) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.role = role == null ? Role.STUDENT : role;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    /** Kept for older call sites; the role is the real source of truth. */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** First letter used for the avatar badge. */
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
