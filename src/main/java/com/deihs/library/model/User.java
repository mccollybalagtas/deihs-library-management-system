package com.deihs.library.model;

/** A signed-in library account. Never carries the password hash. */
public final class User {

    private final int id;
    private final String username;
    private final String displayName;
    private final boolean admin;

    public User(int id, String username, String displayName, boolean admin) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.admin = admin;
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

    public boolean isAdmin() {
        return admin;
    }

    /** First letter used for the avatar badge. */
    public String initial() {
        String source = displayName == null || displayName.isBlank() ? username : displayName;
        if (source == null || source.isBlank()) {
            return "?";
        }
        return source.substring(0, 1).toUpperCase();
    }
}
