package com.deihs.library.util;

import java.util.regex.Pattern;

/**
 * Input rules shared by the sign-in screen, the registration dialog and the
 * account manager.
 *
 * <p>Usernames are deliberately strict: letters only. No digits, spaces,
 * underscores or symbols. This is what makes a typo such as {@code 12345}
 * immediately recognisable as "not a username" instead of a failed password.</p>
 */
public final class Validation {

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 20;
    public static final int PASSWORD_MIN = 8;

    /** Unicode letters, so names such as "Jose" and "Peña" both pass. */
    private static final Pattern LETTERS = Pattern.compile("^\\p{L}+$");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");

    private Validation() {
    }

    /** True when the text is 3-20 letters and nothing else. */
    public static boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        String value = username.trim();
        return value.length() >= USERNAME_MIN
                && value.length() <= USERNAME_MAX
                && LETTERS.matcher(value).matches();
    }

    /**
     * Explains why a username is rejected, or returns {@code null} when it is
     * acceptable. The messages are user-facing.
     */
    public static String usernameProblem(String username) {
        String value = username == null ? "" : username.trim();
        if (value.isEmpty()) {
            return "Please enter your username.";
        }
        if (DIGIT.matcher(value).find()) {
            return "Username is not valid. Use letters only \u2014 numbers are not allowed.";
        }
        if (!LETTERS.matcher(value).matches()) {
            return "Username is not valid. Use letters only \u2014 no spaces or symbols.";
        }
        if (value.length() < USERNAME_MIN) {
            return "Username must be at least " + USERNAME_MIN + " letters.";
        }
        if (value.length() > USERNAME_MAX) {
            return "Username must be at most " + USERNAME_MAX + " letters.";
        }
        return null;
    }

    /** True when the password has at least 8 characters, one letter and one digit. */
    public static boolean isValidPassword(String password) {
        return passwordProblem(password) == null;
    }

    /** Explains why a password is rejected, or {@code null} when it is acceptable. */
    public static String passwordProblem(String password) {
        String value = password == null ? "" : password;
        if (value.isEmpty()) {
            return "Please enter a password.";
        }
        if (value.length() < PASSWORD_MIN) {
            return "Password must be at least " + PASSWORD_MIN + " characters.";
        }
        boolean letter = false;
        boolean digit = false;
        for (char c : value.toCharArray()) {
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        if (!letter || !digit) {
            return "Password must contain at least one letter and one number.";
        }
        return null;
    }

    /** A display name may contain spaces, but not be blank. */
    public static String displayNameProblem(String displayName) {
        String value = displayName == null ? "" : displayName.trim();
        if (value.isEmpty()) {
            return "Please enter a display name.";
        }
        if (value.length() > 100) {
            return "Display name must be at most 100 characters.";
        }
        return null;
    }
}
