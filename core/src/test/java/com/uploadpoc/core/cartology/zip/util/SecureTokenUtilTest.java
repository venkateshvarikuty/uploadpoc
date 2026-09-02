package com.uploadpoc.core.cartology.zip.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SecureTokenUtil}.
 */
class SecureTokenUtilTest {

    @Test
    void generateToken_returns64HexChars() {
        String token = SecureTokenUtil.generateToken();

        assertNotNull(token);
        assertEquals(64, token.length());
    }

    @Test
    void generateToken_containsOnlyHexCharacters() {
        String token = SecureTokenUtil.generateToken();

        assertTrue(token.matches("[0-9a-f]+"),
                "Token should contain only lowercase hex characters, got: " + token);
    }

    @Test
    void generateToken_producesUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        int count = 100;

        for (int i = 0; i < count; i++) {
            tokens.add(SecureTokenUtil.generateToken());
        }

        assertEquals(count, tokens.size(),
                "All generated tokens should be unique");
    }

    @Test
    void generateToken_neverReturnsNull() {
        for (int i = 0; i < 50; i++) {
            assertNotNull(SecureTokenUtil.generateToken());
        }
    }
}
