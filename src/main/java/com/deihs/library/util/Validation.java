package com.deihs.library.util;

import java.util.regex.Pattern;

/**
 * Input rules shared by the sign-in screen, the registration dialog and the
 * account manager.
 *
 * <p>Usernames are deliberately strict: letters only. No digits, spaces,
 * underscores or symbols. This is what makes a typo such as {@code 12345}
 * immediately recognisable as "not a username" instead of a failed password.</p>
 *
 * <p>Part of: Shared input rules.<br>
 * Used by: LoginView (sign-in and registration), UsersView (creating and
 * editing accounts) and UserDao (a second check before writing to the
 * database).<br>
 * Key rules: these methods never throw and never touch the database; the
 * {@code *Problem} methods return a sentence ready to show in the UI, or
 * {@code null} when the value is acceptable.</p>
 */
public final class Validation {

    // ============================================================
    // PART: Limits
    // ============================================================

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 20;
    public static final int PASSWORD_MIN = 8;

    /** Unicode letters, so names such as "Jose" and "Peña" both pass. */
    private static final Pattern LETTERS = Pattern.compile("^\\p{L}+$");
    /**
     * Looks for a digit anywhere in the text.
     * Used only to pick a friendlier message ("numbers are not allowed") than
     * the generic symbol message, not to decide validity on its own.
     */
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");

    /** WHAT: Blocks instantiation. WHY: the class holds only static rules. */
    private Validation() {
    }

    // ============================================================
    // PART: Username rules
    // ============================================================

    /**
     * WHAT: True when the text is 3-20 letters and nothing else.
     * WHY: A single predicate lets the DAO re-check a name that came from a
     * caller other than the login screen.
     * CALLED BY: UserDao.createStudent().
     */
    public static boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        // Surrounding spaces are ignored, so " ana " is accepted as "ana".
        String value = username.trim();
        return value.length() >= USERNAME_MIN
                && value.length() <= USERNAME_MAX
                && LETTERS.matcher(value).matches();
    }

    /**
     * WHAT: Explains why a username is rejected, or returns {@code null} when it
     * is acceptable. The messages are user-facing.
     * WHY: The sign-in screen shows this next to the field as the student types,
     * so the wording must say what to change.
     * CALLED BY: LoginView (live hint and submit), UsersView (field validator),
     * UserDao.createStudent().
     */
    public static String usernameProblem(String username) {
        String value = username == null ? "" : username.trim();

        // Step 1: nothing typed at all.
        if (value.isEmpty()) {
            return "Please enter your username.";
        }

        // Step 2: a digit gets its own message. This is checked before the
        // general letter test because "12345" is the most common mistake and
        // deserves a clearer hint than "no symbols".
        if (DIGIT.matcher(value).find()) {
            return "Username is not valid. Use letters only \u2014 numbers are not allowed.";
        }

        // Step 3: spaces, underscores and other symbols.
        if (!LETTERS.matcher(value).matches()) {
            return "Username is not valid. Use letters only \u2014 no spaces or symbols.";
        }

        // Step 4: length, checked last so a short name made of symbols still
        // reports the symbol problem first.
        if (value.length() < USERNAME_MIN) {
            return "Username must be at least " + USERNAME_MIN + " letters.";
        }
        if (value.length() > USERNAME_MAX) {
            return "Username must be at most " + USERNAME_MAX + " letters.";
        }
        return null;
    }

    // ============================================================
    // PART: Password rules
    // ============================================================

    /**
     * WHAT: True when the password has at least 8 characters, one letter and one digit.
     * WHY: Keeps the boolean answer and the wording in one place by delegating.
     */
    public static boolean isValidPassword(String password) {
        return passwordProblem(password) == null;
    }

    /**
     * WHAT: Explains why a password is rejected, or {@code null} when it is acceptable.
     * WHY: Students need to know which rule failed; "invalid password" alone
     * leads to repeated attempts.
     * CALLED BY: LoginView (registration), UsersView (create and reset),
     * UserDao.createStudent() and UserDao.changePassword().
     */
    public static String passwordProblem(String password) {
        // Spaces are allowed inside a password, so the value is not trimmed.
        String value = password == null ? "" : password;

        // Step 1: empty and too-short are reported separately.
        if (value.isEmpty()) {
            return "Please enter a password.";
        }
        if (value.length() < PASSWORD_MIN) {
            return "Password must be at least " + PASSWORD_MIN + " characters.";
        }

        // Step 2: walk the characters once and note which kinds appear.
        boolean letter = false;
        boolean digit = false;
        for (char c : value.toCharArray()) {
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }

        // Step 3: both kinds are required.
        if (!letter || !digit) {
            return "Password must contain at least one letter and one number.";
        }
        return null;
    }

    // ============================================================
    // PART: Display name rules
    // ============================================================

    /**
     * WHAT: A display name may contain spaces, but not be blank.
     * WHY: The display name is what classmates see on a loan slip, so it needs
     * a length cap but not the letters-only rule of a username.
     * CALLED BY: UsersView, when an account is created or edited.
     */
    public static String displayNameProblem(String displayName) {
        String value = displayName == null ? "" : displayName.trim();
        if (value.isEmpty()) {
            return "Please enter a display name.";
        }
        // 100 matches the column width in the schema.
        if (value.length() > 100) {
            return "Display name must be at most 100 characters.";
        }
        return null;
    }
}
