package com.uploadpoc.core.cartology.zip.util;

import java.security.SecureRandom;

/**
 * Utility for generating cryptographically secure random tokens.
 * <p>
 * Tokens are 64 hexadecimal characters (32 bytes of entropy), generated
 * using {@link SecureRandom}. This provides 256 bits of randomness,
 * making brute-force guessing computationally infeasible.
 */
public final class SecureTokenUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private SecureTokenUtil() {
        // utility class — no instantiation
    }

    /**
     * Generates a cryptographically secure random token.
     *
     * @return a 64-character lowercase hexadecimal string
     */
    public static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }
}
