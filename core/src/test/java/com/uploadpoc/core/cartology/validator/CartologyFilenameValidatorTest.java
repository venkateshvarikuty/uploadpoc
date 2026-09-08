package com.uploadpoc.core.cartology.validator;

import com.uploadpoc.core.cartology.model.ParsedFilename;
import com.uploadpoc.core.cartology.model.ValidationErrorCode;
import com.uploadpoc.core.cartology.model.ValidationResult;
import com.uploadpoc.core.cartology.normalizer.CartologyNameNormalizer;
import com.uploadpoc.core.cartology.parser.CartologyFilenameParser;
import com.uploadpoc.core.cartology.service.CartologyConfigurationService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CartologyFilenameValidator}.
 */
@ExtendWith(MockitoExtension.class)
class CartologyFilenameValidatorTest {

    @Mock
    private CartologyFilenameParser filenameParser;

    @Mock
    private CartologyConfigurationService configurationService;

    @Spy
    private CartologyNameNormalizer nameNormalizer = new CartologyNameNormalizer();

    @InjectMocks
    private CartologyFilenameValidator validator;

    /* ---------- Valid mappings ---------- */

    @Test
    void validate_validMappingWithCampaignType() {
        String filename = "POS_Special_Shelf-Wobbler.pdf";
        ParsedFilename parsed = new ParsedFilename("POS", "Special", "Shelf-Wobbler",
            null, "pdf");

        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(parsed);
        when(configurationService.isChannelConfigured("POS")).thenReturn(true);
        when(configurationService.isCampaignTypeConfigured("POS", "Special")).thenReturn(true);
        when(configurationService.isValidMapping("POS", "Special", "Shelf-Wobbler"))
            .thenReturn(true);

        ValidationResult result = validator.validate(filename);

        assertTrue(result.isValid());
        assertEquals("POS", result.getChannel());
        assertEquals("Special", result.getCampaignType());
        assertEquals("Shelf Wobbler", result.getMediaFormat());
        assertNull(result.getAssetName());
    }

    @Test
    void validate_validMappingWithoutCampaignType() {
        String filename = "Fresh-Mag_Shelf-Wobbler.pdf";
        ParsedFilename parsed = new ParsedFilename("Fresh-Mag", null, "Shelf-Wobbler",
            null, "pdf");

        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(parsed);
        when(configurationService.isChannelConfigured("Fresh-Mag")).thenReturn(true);
        when(configurationService.isValidMapping("Fresh-Mag", null, "Shelf-Wobbler"))
            .thenReturn(true);

        ValidationResult result = validator.validate(filename);

        assertTrue(result.isValid());
        assertNull(result.getCampaignType());
        assertEquals("Shelf Wobbler", result.getMediaFormat());
        assertNull(result.getAssetName());
    }

        @Test
        void validate_freshMagShelfWobblerWithAssetName() {
        String filename = "Fresh-Mag_Shelf-Wobbler_Lighthouse-Report-Viewer.png";
        ParsedFilename parsed = new ParsedFilename("Fresh-Mag", null, "Shelf-Wobbler",
            "Lighthouse-Report-Viewer", "png");

        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(parsed);
        when(configurationService.isChannelConfigured("Fresh-Mag")).thenReturn(true);
        when(configurationService.isValidMapping("Fresh-Mag", null, "Shelf-Wobbler"))
            .thenReturn(true);

        ValidationResult result = validator.validate(filename);

        assertTrue(result.isValid());
        assertEquals("Fresh-Mag", result.getChannel());
        assertNull(result.getCampaignType());
        assertEquals("Shelf Wobbler", result.getMediaFormat());
        assertEquals("png", result.getExtension());
        }

        @Test
        void validate_posShelfWobblerWithoutCampaignType_isInvalid() {
        String filename = "POS_Shelf-Wobbler_Lighthouse-Report-Viewer.pdf";
        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(null);

        ValidationResult result = validator.validate(filename);

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INVALID_FILENAME_FORMAT, result.getErrorCode());
        }

        @Test
        void validate_mediaFormatWithSpaces_isInvalidMapping() {
        String filename = "Fresh-Mag_Shelf Wobbler_Lighthouse-Report-Viewer.pdf";
        ParsedFilename parsed = new ParsedFilename("Fresh-Mag", null, "Shelf Wobbler",
            "Lighthouse-Report-Viewer", "pdf");

        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(parsed);
        when(configurationService.isChannelConfigured("Fresh-Mag")).thenReturn(true);
        when(configurationService.isValidMapping("Fresh-Mag", null, "Shelf Wobbler"))
            .thenReturn(false);
        when(configurationService.getMediaFormats("Fresh-Mag", null))
            .thenReturn(new HashSet<>(Collections.singleton("Shelf-Wobbler")));

        ValidationResult result = validator.validate(filename);

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INVALID_MAPPING, result.getErrorCode());
        }

    /* ---------- Structural failure ---------- */

    @Test
    void validate_invalidFilenameFormat() {
        String filename = "invalid.psd";

        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(null);

        ValidationResult result = validator.validate(filename);

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INVALID_FILENAME_FORMAT, result.getErrorCode());
    }

    /* ---------- Unknown channel ---------- */

    @Test
    void validate_unknownChannel() {
        String filename = "Online_Special_Banner_Template.psd";
        ParsedFilename parsed = new ParsedFilename("Online", "Special", "Banner",
                "Template", "psd");

        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(parsed);
        when(configurationService.isChannelConfigured("Online")).thenReturn(false);

        ValidationResult result = validator.validate(filename);

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.UNKNOWN_CHANNEL, result.getErrorCode());
        assertEquals("Online", result.getChannel());
    }

    /* ---------- Unknown campaign type ---------- */

    @Test
    void validate_unknownCampaignType() {
        String filename = "POS_Seasonal_A3-Bin-Card_Template.psd";
        ParsedFilename parsed = new ParsedFilename("POS", "Seasonal", "A3-Bin-Card",
                "Template", "psd");

        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(parsed);
        when(configurationService.isChannelConfigured("POS")).thenReturn(true);
        when(configurationService.isCampaignTypeConfigured("POS", "Seasonal")).thenReturn(false);

        ValidationResult result = validator.validate(filename);

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.UNKNOWN_CAMPAIGN_TYPE, result.getErrorCode());
        assertEquals("Seasonal", result.getCampaignType());
    }

    /* ---------- Invalid mapping (valid components but invalid combination) ---------- */

    @Test
    void validate_invalidMapping() {
        String filename = "POS_Special_End-Cap_Template.psd";
        ParsedFilename parsed = new ParsedFilename("POS", "Special", "End-Cap",
                "Template", "psd");

        Set<String> existingFormats = new HashSet<>();
        existingFormats.add("A3-Bin-Card");

        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(parsed);
        when(configurationService.isChannelConfigured("POS")).thenReturn(true);
        when(configurationService.isCampaignTypeConfigured("POS", "Special")).thenReturn(true);
        when(configurationService.isValidMapping("POS", "Special", "End-Cap")).thenReturn(false);
        when(configurationService.getMediaFormats("POS", "Special")).thenReturn(existingFormats);

        ValidationResult result = validator.validate(filename);

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INVALID_MAPPING, result.getErrorCode());
        assertTrue(result.getMessage().contains("End-Cap"));
    }

    /* ---------- Unknown media format ---------- */

    @Test
    void validate_unknownMediaFormat() {
        String filename = "POS_Special_Unknown-Format_Template.psd";
        ParsedFilename parsed = new ParsedFilename("POS", "Special", "Unknown-Format",
                "Template", "psd");

        when(configurationService.isCacheLoaded()).thenReturn(true);
        when(filenameParser.parse(filename)).thenReturn(parsed);
        when(configurationService.isChannelConfigured("POS")).thenReturn(true);
        when(configurationService.isCampaignTypeConfigured("POS", "Special")).thenReturn(true);
        when(configurationService.isValidMapping("POS", "Special", "Unknown-Format")).thenReturn(false);
        when(configurationService.getMediaFormats("POS", "Special")).thenReturn(Collections.emptySet());

        ValidationResult result = validator.validate(filename);

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.UNKNOWN_MEDIA_FORMAT, result.getErrorCode());
    }

    /* ---------- Configuration unavailable ---------- */

    @Test
    void validate_configurationUnavailable() {
        when(configurationService.isCacheLoaded()).thenReturn(false);

        ValidationResult result = validator.validate("POS_Special_A3-Bin-Card_Template.psd");

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.CONFIGURATION_UNAVAILABLE, result.getErrorCode());
    }
}
