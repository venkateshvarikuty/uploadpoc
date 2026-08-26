package com.uploadpoc.core.cartology.normalizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CartologyNameNormalizer}.
 */
class CartologyNameNormalizerTest {

    private CartologyNameNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new CartologyNameNormalizer();
    }

    /* ---------- normalize ---------- */

    @Test
    void normalize_spacesToHyphens() {
        assertEquals("A3-Bin-Card", normalizer.normalize("A3 Bin Card"));
    }

    @Test
    void normalize_shelfWobbler() {
        assertEquals("Shelf-Wobbler", normalizer.normalize("Shelf Wobbler"));
    }

    @Test
    void normalize_offNetwork() {
        assertEquals("Off-Network", normalizer.normalize("Off Network"));
    }

    @Test
    void normalize_digitalScreen() {
        assertEquals("Digital-Screen", normalizer.normalize("Digital Screen"));
    }

    @Test
    void normalize_singleWord() {
        assertEquals("POS", normalizer.normalize("POS"));
    }

    @Test
    void normalize_alreadyNormalized() {
        assertEquals("A3-Bin-Card", normalizer.normalize("A3-Bin-Card"));
    }

    @Test
    void normalize_trimsWhitespace() {
        assertEquals("POS", normalizer.normalize("  POS  "));
    }

    @Test
    void normalize_collapsesRepeatedHyphens() {
        assertEquals("A3-Bin-Card", normalizer.normalize("A3--Bin---Card"));
    }

    @Test
    void normalize_collapsesRepeatedSpaces() {
        assertEquals("A3-Bin-Card", normalizer.normalize("A3  Bin   Card"));
    }

    @Test
    void normalize_removesLeadingTrailingHyphens() {
        assertEquals("POS", normalizer.normalize("-POS-"));
    }

    @Test
    void normalize_stripsJcrInvalidChars() {
        assertEquals("BinCard", normalizer.normalize("Bin:Card"));
        assertEquals("BinCard", normalizer.normalize("[Bin|Card]"));
    }

    @Test
    void normalize_nullReturnsNull() {
        assertNull(normalizer.normalize(null));
    }

    @Test
    void normalize_emptyReturnsNull() {
        assertNull(normalizer.normalize(""));
        assertNull(normalizer.normalize("   "));
    }

    @Test
    void normalize_onlyInvalidCharsReturnsNull() {
        assertNull(normalizer.normalize(":::"));
    }

    @Test
    void normalize_freshMag() {
        assertEquals("Fresh-Mag", normalizer.normalize("Fresh Mag"));
    }

    @Test
    void normalize_endCap() {
        assertEquals("End-Cap", normalizer.normalize("End Cap"));
    }

    /* ---------- toDisplayName ---------- */

    @Test
    void toDisplayName_hyphensToSpaces() {
        assertEquals("A3 Bin Card", normalizer.toDisplayName("A3-Bin-Card"));
    }

    @Test
    void toDisplayName_singleWord() {
        assertEquals("POS", normalizer.toDisplayName("POS"));
    }

    @Test
    void toDisplayName_nullReturnsNull() {
        assertNull(normalizer.toDisplayName(null));
    }

    @Test
    void toDisplayName_emptyReturnsNull() {
        assertNull(normalizer.toDisplayName(""));
    }

    @Test
    void toDisplayName_trims() {
        assertEquals("Off Network", normalizer.toDisplayName("  Off-Network  "));
    }
}
