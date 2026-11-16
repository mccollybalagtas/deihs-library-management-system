package com.deihs.library.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Locale;

/**
 * Password hashing and verification.
 *
 * <p>New hashes use PBKDF2-HMAC-SHA256 with a random 16-byte salt and 120,000
 * iterations, stored as:</p>
 *
 * <pre>pbkdf2$sha256$120000$&lt;saltBase64&gt;$&lt;hashBase64&gt;</pre>
 *
 * <p>The first release of the system stored a plain SHA-256 of
 * {@code username:password}. {@link #verify} still accepts that format so
 * existing accounts keep working, and {@link #needsUpgrade} tells the login
 * code when to replace the old hash with a PBKDF2 one.</p>
 *
 * <p>Part of: Security helpers, kept apart from the data layer so the hashing
 * rules live in one place.<br>
 * Used by: UserDao.login() calls {@link #verify} and {@link #needsUpgrade};
 * UserDao.createStudent() and UserDao.changePassword() call {@link #hash}.<br>
 * Key rules: a stored hash is one-way and is never decoded back into a
 * password; every hash gets its own random salt; comparisons use a
 * constant-time check so the time taken does not reveal how much matched.</p>
 */
public final class Passwords {

    // ============================================================
    // PART: Format and tuning constants
    // ============================================================

    public static final int ITERATIONS = 120_000;
    public static final int SALT_BYTES = 16;
    public static final int KEY_BITS = 256;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2$sha256$";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** WHAT: Blocks instantiation. WHY: the class holds only static helpers. */
    private Passwords() {
    }

    // ============================================================
    // PART: Creating a hash
    // ============================================================

    /**
     * WHAT: Builds a new salted PBKDF2 hash string for one account.
     * WHY: A per-account random salt means two users with the same password get
     * different stored values, so a stolen table cannot be attacked in bulk.
     * CALLED BY: UserDao.createStudent() and UserDao.changePassword(); also the
     * upgrade path inside UserDao.login().
     */
    public static String hash(String username, String password) {
        // Step 1: draw a fresh random salt for this hash.
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        // Step 2: run PBKDF2 over the password with that salt.
        byte[] derived = derive(password, salt, ITERATIONS);

        // Step 3: join prefix, iteration count, salt and hash with "$" so the
        // verifying side can split the value again later.
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    /**
     * WHAT: Checks a password against a stored hash. Supports both the current
     * PBKDF2 format and the legacy SHA-256 format. Never throws on malformed
     * input: it simply returns {@code false}.
     *
     * <p>WHY: The two formats must be accepted at the same time so a student
     * whose account predates the upgrade can still sign in.<br>
     * CALLED BY: UserDao.login().</p>
     */
    public static boolean verify(String username, String password, String stored) {
        // An account with no hash at all cannot match anything.
        if (stored == null || stored.isBlank()) {
            return false;
        }

        // Step 1: current format - let the PBKDF2 verifier do the work.
        if (stored.startsWith(PREFIX)) {
            return verifyPbkdf2(password, stored);
        }

        // Step 2: anything else is treated as the legacy SHA-256 value, which
        // mixed the username into the input.
        return constantTimeEquals(legacyHash(username, password), stored.trim());
    }

    /**
     * WHAT: Reports whether a stored hash should be replaced after sign-in.
     * WHY: An old or unreadable hash is the trigger for quietly re-hashing the
     * password the user just typed, without asking them to do anything.
     * CALLED BY: UserDao.login(), right after a successful verify().
     */
    public static boolean needsUpgrade(String stored) {
        // No hash, or a hash from the old scheme, both need replacing.
        if (stored == null || !stored.startsWith(PREFIX)) {
            return true;
        }
        // A PBKDF2 hash with fewer rounds than today's setting is also stale.
        try {
            String[] parts = stored.split("\\$");
            int iterations = Integer.parseInt(parts[2]);
            return iterations < ITERATIONS;
        } catch (RuntimeException ex) {
            // A value that cannot be parsed is treated as unusable.
            return true;
        }
    }

    // ============================================================
    // PART: Verifying a hash
    // ============================================================

    /**
     * WHAT: Rebuilds the PBKDF2 hash from the stored salt and iteration count,
     * then compares it with the stored hash.
     * WHY: The salt and count travel inside the stored string, so an old hash
     * keeps working even after the defaults in this class change.
     * CALLED BY: {@link #verify}.
     */
    private static boolean verifyPbkdf2(String password, String stored) {
        try {
            // Step 1: split prefix, iterations, salt, hash. Five parts means
            // the string is complete; anything else is corrupt.
            String[] parts = stored.split("\\$");
            if (parts.length != 5) {
                return false;
            }

            // Step 2: read the stored cost. A zero or negative count would
            // make derivation meaningless, so reject it.
            int iterations = Integer.parseInt(parts[2]);
            if (iterations <= 0) {
                return false;
            }

            // Step 3: decode salt and expected hash, then derive from the
            // typed password using exactly the same parameters.
            byte[] salt = Base64.getDecoder().decode(parts[3]);
            byte[] expected = Base64.getDecoder().decode(parts[4]);
            byte[] actual = derive(password, salt, iterations);

            // Step 4: compare in constant time to avoid leaking a match prefix.
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            // Bad Base64 or a non-numeric count lands here.
            return false;
        }
    }

    // ============================================================
    // PART: Low-level helpers
    // ============================================================

    /**
     * WHAT: Runs PBKDF2 and returns the raw derived key bytes.
     * WHY: Both hashing and verification must use the same routine, otherwise
     * a hash made here could never be checked.
     * CALLED BY: {@link #hash} and {@link #verifyPbkdf2}.
     */
    private static byte[] derive(String password, byte[] salt, int iterations) {
        // A null password is treated as empty so callers need no null check.
        char[] chars = (password == null ? "" : password).toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            // Missing algorithm means the JVM cannot do security work at all;
            // that is a deployment fault, not a wrong password.
            throw new IllegalStateException(ALGORITHM + " is not available on this JVM", ex);
        } finally {
            // Wipe the password copy held by the spec, even on failure.
            spec.clearPassword();
        }
    }

    /**
     * WHAT: The original scheme: SHA-256 of {@code lowercase(username) + ":" + password}.
     * WHY: Only used to check accounts created before PBKDF2 existed.
     * CALLED BY: {@link #verify}, for stored values with no PBKDF2 prefix.
     */
    private static String legacyHash(String username, String password) {
        // The old code lowercased the username, so the check must do the same.
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String pass = password == null ? "" : password;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((name + ":" + pass).getBytes(StandardCharsets.UTF_8));

            // Turn the raw bytes into the lowercase hex text stored in the
            // original database column.
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", ex);
        }
    }

    /**
     * WHAT: Compares two strings without stopping at the first difference.
     * WHY: An early exit would make a wrong hash return faster than a nearly
     * right one, which is a small clue an attacker can measure.
     * CALLED BY: {@link #verify}, on the legacy path.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
