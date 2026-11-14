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
 */
public final class Passwords {

    public static final int ITERATIONS = 120_000;
    public static final int SALT_BYTES = 16;
    public static final int KEY_BITS = 256;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2$sha256$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    /** Creates a fresh PBKDF2 hash for the given account. */
    public static String hash(String username, String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    /**
     * Checks a password against a stored hash. Supports both the current
     * PBKDF2 format and the legacy SHA-256 format. Never throws on malformed
     * input: it simply returns {@code false}.
     */
    public static boolean verify(String username, String password, String stored) {
        if (stored == null || stored.isBlank()) {
            return false;
        }
        if (stored.startsWith(PREFIX)) {
            return verifyPbkdf2(password, stored);
        }
        return constantTimeEquals(legacyHash(username, password), stored.trim());
    }

    /**
     * True when a stored hash was produced by an older scheme and should be
     * re-hashed after the next successful sign-in.
     */
    public static boolean needsUpgrade(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return true;
        }
        try {
            String[] parts = stored.split("\\$");
            int iterations = Integer.parseInt(parts[2]);
            return iterations < ITERATIONS;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private static boolean verifyPbkdf2(String password, String stored) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 5) {
                return false;
            }
            int iterations = Integer.parseInt(parts[2]);
            if (iterations <= 0) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[3]);
            byte[] expected = Base64.getDecoder().decode(parts[4]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = (password == null ? "" : password).toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException(ALGORITHM + " is not available on this JVM", ex);
        } finally {
            spec.clearPassword();
        }
    }

    /** The original scheme: SHA-256 of {@code lowercase(username) + ":" + password}. */
    private static String legacyHash(String username, String password) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String pass = password == null ? "" : password;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((name + ":" + pass).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
