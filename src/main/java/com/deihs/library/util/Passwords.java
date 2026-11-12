package com.deihs.library.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Password hashing for the users table.
 *
 * <p>The stored value is SHA-256 of {@code lowercase(username) + ":" + password}
 * rendered as 64 lowercase hex characters. The username is part of the digest so
 * two accounts with the same password do not share a hash.</p>
 */
public final class Passwords {

    private Passwords() {
    }

    public static String hash(String username, String password) {
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
}
