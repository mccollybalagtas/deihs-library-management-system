package com.deihs.library.model;

/**
 * What an account is allowed to do.
 *
 * <p>The permission helpers live here rather than in the UI so a view cannot
 * accidentally offer an action the role may not perform.</p>
 */
public enum Role {

    /** Full control, including book editing and account management. */
    ADMIN("Administrator", "Full access to the library system"),

    /** Runs the desk: issues and returns books, monitors every loan. */
    LIBRARIAN("Librarian", "Manages borrowing and returning"),

    /** Self-service: borrows and returns their own books. */
    STUDENT("Student", "Borrows and returns books");

    private final String label;
    private final String description;

    Role(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Only the administrator may add, edit or remove catalog records. */
    public boolean canManageBooks() {
        return this == ADMIN;
    }

    /** Only the administrator may create or change accounts. */
    public boolean canManageUsers() {
        return this == ADMIN;
    }

    /** Staff issue and return books on behalf of any student. */
    public boolean canRunDesk() {
        return this == ADMIN || this == LIBRARIAN;
    }

    /** Students borrow and return their own books. */
    public boolean canBorrowForSelf() {
        return this == STUDENT;
    }

    /** Both staff and students may read the catalog. */
    public boolean canBrowseCatalog() {
        return true;
    }

    /** Only staff see library-wide statistics. */
    public boolean seesLibraryStats() {
        return this == ADMIN || this == LIBRARIAN;
    }

    /** Parses a stored value, defaulting to the least privileged role. */
    public static Role from(String value) {
        if (value == null || value.isBlank()) {
            return STUDENT;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return STUDENT;
        }
    }
}
