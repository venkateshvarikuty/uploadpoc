package com.uploadpoc.core.cartology.parser;

import com.uploadpoc.core.cartology.cache.CartologyConfigurationCache;
import com.uploadpoc.core.cartology.model.ParsedFilename;
import com.uploadpoc.core.cartology.normalizer.CartologyNameNormalizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CartologyFilenameParser}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartologyFilenameParserTest {

    @Mock
    private CartologyConfigurationCache configurationCache;

    @Spy
    private CartologyNameNormalizer nameNormalizer = new CartologyNameNormalizer();

    @InjectMocks
    private CartologyFilenameParser parser;

    private Set<String> knownChannels;

    @BeforeEach
    void setUp() {
        knownChannels = new HashSet<>(Arrays.asList("POS", "Off-Network", "Fresh-Mag"));
        when(configurationCache.getAllChannels()).thenReturn(knownChannels);
    }

    /* ---------- Pattern 1: Channel_CampaignType_MediaFormat_AssetName.ext ---------- */

    @Test
    void parse_posSpecialA3BinCard() {
        when(configurationCache.channelHasCampaignType("POS")).thenReturn(true);

        ParsedFilename result = parser.parse("POS_Special_A3-Bin-Card_Template.psd");

        assertNotNull(result);
        assertEquals("POS", result.getChannel());
        assertEquals("Special", result.getCampaignType());
        assertEquals("A3-Bin-Card", result.getMediaFormat());
        assertEquals("Template", result.getAssetName());
        assertEquals("psd", result.getExtension());
    }

    @Test
    void parse_posEverydayShelfWobbler() {
        when(configurationCache.channelHasCampaignType("POS")).thenReturn(true);

        ParsedFilename result = parser.parse("POS_Everyday_Shelf-Wobbler_Specifications.pdf");

        assertNotNull(result);
        assertEquals("POS", result.getChannel());
        assertEquals("Everyday", result.getCampaignType());
        assertEquals("Shelf-Wobbler", result.getMediaFormat());
        assertEquals("Specifications", result.getAssetName());
        assertEquals("pdf", result.getExtension());
    }

    @Test
    void parse_offNetworkSpecialA3BinCard() {
        when(configurationCache.channelHasCampaignType("Off-Network")).thenReturn(true);

        ParsedFilename result = parser.parse(
                "Off-Network_Special_A3-Bin-Card_Template.psd");

        assertNotNull(result);
        assertEquals("Off-Network", result.getChannel());
        assertEquals("Special", result.getCampaignType());
        assertEquals("A3-Bin-Card", result.getMediaFormat());
        assertEquals("Template", result.getAssetName());
        assertEquals("psd", result.getExtension());
    }

    /* ---------- Pattern 2: Channel_MediaFormat_AssetName.ext ---------- */

    @Test
    void parse_freshMagFullPage() {
        when(configurationCache.channelHasCampaignType("Fresh-Mag")).thenReturn(false);

        ParsedFilename result = parser.parse("Fresh-Mag_Full-Page_Specifications.pdf");

        assertNotNull(result);
        assertEquals("Fresh-Mag", result.getChannel());
        assertNull(result.getCampaignType());
        assertEquals("Full-Page", result.getMediaFormat());
        assertEquals("Specifications", result.getAssetName());
        assertEquals("pdf", result.getExtension());
    }

    @Test
    void parse_freshMagFreshMag() {
        when(configurationCache.channelHasCampaignType("Fresh-Mag")).thenReturn(false);

        ParsedFilename result = parser.parse("Fresh-Mag_Fresh-Mag_Template.indd");

        assertNotNull(result);
        assertEquals("Fresh-Mag", result.getChannel());
        assertNull(result.getCampaignType());
        assertEquals("Fresh-Mag", result.getMediaFormat());
        assertEquals("Template", result.getAssetName());
        assertEquals("indd", result.getExtension());
    }

    /* ---------- Asset name with underscores ---------- */

    @Test
    void parse_assetNameWithUnderscores() {
        when(configurationCache.channelHasCampaignType("POS")).thenReturn(true);

        ParsedFilename result = parser.parse(
                "POS_Special_A3-Bin-Card_My_Template_V2.psd");

        assertNotNull(result);
        assertEquals("POS", result.getChannel());
        assertEquals("Special", result.getCampaignType());
        assertEquals("A3-Bin-Card", result.getMediaFormat());
        assertEquals("My_Template_V2", result.getAssetName());
        assertEquals("psd", result.getExtension());
    }

    /* ---------- Error cases ---------- */

    @Test
    void parse_nullFilename() {
        assertNull(parser.parse(null));
    }

    @Test
    void parse_emptyFilename() {
        assertNull(parser.parse(""));
    }

    @Test
    void parse_noExtension() {
        assertNull(parser.parse("POS_Special_A3-Bin-Card_Template"));
    }

    @Test
    void parse_dotOnly() {
        assertNull(parser.parse(".psd"));
    }

    @Test
    void parse_tooFewSegments() {
        assertNull(parser.parse("POS_Template.psd"));
    }

    @Test
    void parse_unknownChannel_fallsBackToFirstSegment() {
        // Unknown channel should still parse (validator catches UNKNOWN_CHANNEL)
        when(configurationCache.channelHasCampaignType("Online")).thenReturn(false);

        ParsedFilename result = parser.parse("Online_Digital-Screen_Template.psd");

        assertNotNull(result);
        assertEquals("Online", result.getChannel());
        assertNull(result.getCampaignType());
        assertEquals("Digital-Screen", result.getMediaFormat());
        assertEquals("Template", result.getAssetName());
    }
}
