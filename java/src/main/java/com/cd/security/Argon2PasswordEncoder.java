package com.cd.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

/**
 * Argon2id password encoder with configurable parameters.
 * Uses Argon2id variant (hybrid of Argon2d + Argon2i) as recommended by OWASP.
 *
 * Parameters:
 *   - iterations (cost): 12
 *   - memory: 65536 KiB (64 MiB)
 *   - parallelism: 1 (single-threaded, suitable for web servers)
 */
public final class Argon2PasswordEncoder {

    private static final int ITERATIONS = 12;
    private static final int MEMORY = 65536;
    private static final int PARALLELISM = 1;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    private static final Argon2 ARGON2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id,
            SALT_LENGTH,
            HASH_LENGTH
    );

    private Argon2PasswordEncoder() {
    }

    /**
     * Encode a raw password using Argon2id with salt.
     * @param rawPassword the plaintext password
     * @return Argon2id hash string (includes embedded salt and parameters)
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        try {
            return ARGON2.hash(ITERATIONS, MEMORY, PARALLELISM, rawPassword.toCharArray());
        } finally {
            // Argon2 instance is reused; wipe sensitive data is handled by library
        }
    }

    /**
     * Verify a raw password against an Argon2id hash.
     * @param hash the stored Argon2id hash
     * @param rawPassword the plaintext password to verify
     * @return true if the password matches the hash
     */
    public static boolean matches(String hash, String rawPassword) {
        if (hash == null || rawPassword == null) {
            return false;
        }
        try {
            return ARGON2.verify(hash, rawPassword.toCharArray());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a hash string looks like an Argon2 hash.
     * Argon2 hashes start with "$argon2id$" or "$argon2i$" or "$argon2d$".
     */
    public static boolean isArgon2Hash(String hash) {
        return hash != null && hash.startsWith("$argon2");
    }
}
