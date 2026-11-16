package com.deihs.library.model;

/**
 * What an account is allowed to do.
 *
 * <p>The permission helpers live here rather than in the UI so a view cannot
 * accidentally offer an action the role may not perform.</p>
 *
 * <p>Part of: Model layer. The single source of permission truth for the whole
 * application.<br>
 * Used by: {@code User} (holds one), {@code UserDao} (reads and writes the
 * stored name), and the ui classes MainView, CatalogView, CollectionPanel,
 * DashboardView, CirculationView and UsersView, which call the {@code can...()}
 * methods before building a button.<br>
 * Key rules: three roles only (ADMIN, LIBRARIAN, STUDENT); unknown or blank
 * stored values fall back to STUDENT, the least privileged role, so a corrupt
 * row can never grant extra rights.</p>
 */
public enum Role {

    // ============================================================
    // PART: The three roles
    // ============================================================

    /** Full control, including book editing and account management. */
    ADMIN("Administrator", "Full access to the library system"),

    /** Runs the desk: issues and returns books, monitors every loan. */
    LIBRARIAN("Librarian", "Manages borrowing and returning"),

    /** Self-service: borrows and returns their own books. */
    STUDENT("Student", "Borrows and returns books");

    // ============================================================
    // PART: Display text
    // ============================================================

    /** Text shown in tables and the top bar. */
    private final String label;
    /** One-line explanation shown under the role picker in UsersView. */
    private final String description;

    /** WHAT: Stores the two display strings. WHY: keeps wording next to the role. CALLED BY: enum constants above. */
    Role(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** Friendly name, e.g. "Librarian". */
    public String label() {
        return label;
    }

    /** Short explanation of what the role may do. */
    public String description() {
        return description;
    }

    // ============================================================
    // PART: Permission rules
    // ============================================================

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

    // ============================================================
    // PART: Parsing stored values
    // ============================================================

    /**
     * WHAT: Turns a stored string such as "librarian" back into a Role.
     * WHY: The database column is free text, so a bad value must not crash the
     * account list; anything unrecognised becomes STUDENT.
     * CALLED BY: UserDao.roleOf(), UsersView (role picker), Role.from callers.
     */
    public static Role from(String value) {
        if (value == null || value.isBlank()) {
            return STUDENT;
        }
        try {
            // toUpperCase(Locale.ROOT) avoids the Turkish "i" problem, where a
            // locale-aware upper-case would not produce a valid constant name.
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // Unknown role name in the database: keep the user, drop the rights.
            return STUDENT;
        }
    }
}
