package com.uploadpoc.core.cartology.validator;

import com.uploadpoc.core.cartology.model.ParsedFilename;
import com.uploadpoc.core.cartology.model.ValidationErrorCode;
import com.uploadpoc.core.cartology.model.ValidationResult;
import com.uploadpoc.core.cartology.parser.CartologyFilenameParser;
import com.uploadpoc.core.cartology.service.CartologyConfigurationService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Two-phase validator for Cartology DAM asset filenames.
 * <p>
 * <b>Phase A — Structural validation:</b> Can the filename be parsed into the
 * expected segments (Channel / CampaignType / MediaFormat / AssetName / Extension)?
 * <p>
 * <b>Phase B — Business mapping validation:</b> Does the parsed combination
 * exist in the configured naming-rule mappings?
 * <p>
 * This service is a reusable OSGi component — it is not coupled to any
 * specific workflow or servlet.
 */
@Component(service = CartologyFilenameValidator.class, immediate = true)
public class CartologyFilenameValidator {

    private static final Logger LOG = LoggerFactory.getLogger(CartologyFilenameValidator.class);

    @Reference
    private CartologyFilenameParser filenameParser;

    @Reference
    private CartologyConfigurationService configurationService;

    /**
     * Validates the given asset filename.
     *
     * @param filename the asset filename including extension,
     *                 e.g. {@code "POS_Special_A3-Bin-Card_Template.psd"}
     * @return a {@link ValidationResult} indicating success or a specific failure
     */
    public ValidationResult validate(String filename) {

        // Pre-check: is the configuration available?
        if (!configurationService.isCacheLoaded()) {
            LOG.error("Configuration cache not loaded — cannot validate filename: {}", filename);
            return ValidationResult.failure(
                    ValidationErrorCode.CONFIGURATION_UNAVAILABLE,
                    "The naming-rule configuration is not available. "
                            + "Please try again later or contact an administrator.");
        }

        // --- Phase A: structural validation ---
        ParsedFilename parsed = filenameParser.parse(filename);
        if (parsed == null) {
            LOG.warn("Cartology filename validation failed: asset={}, reason=INVALID_FILENAME_FORMAT",
                    filename);
            return ValidationResult.failure(
                    ValidationErrorCode.INVALID_FILENAME_FORMAT,
                    "The filename '" + filename + "' does not match the expected naming pattern. "
                            + "Expected: Channel_CampaignType_MediaFormat_AssetName.ext "
                            + "or Channel_MediaFormat_AssetName.ext");
        }

        // --- Phase B: business mapping validation ---

        // B1: Is the channel known?
        if (!configurationService.isChannelConfigured(parsed.getChannel())) {
            LOG.warn("Cartology filename validation failed: asset={}, reason=UNKNOWN_CHANNEL",
                    filename);
            return ValidationResult.failure(
                    ValidationErrorCode.UNKNOWN_CHANNEL,
                    "Channel '" + parsed.getChannel() + "' is not configured.",
                    parsed);
        }

        // B2: If channel has campaign types, is the campaign type known?
        if (parsed.getCampaignType() != null) {
            if (!configurationService.isCampaignTypeConfigured(
                    parsed.getChannel(), parsed.getCampaignType())) {
                LOG.warn("Cartology filename validation failed: asset={}, "
                        + "reason=UNKNOWN_CAMPAIGN_TYPE", filename);
                return ValidationResult.failure(
                        ValidationErrorCode.UNKNOWN_CAMPAIGN_TYPE,
                        "Campaign Type '" + parsed.getCampaignType()
                                + "' is not configured for Channel '" + parsed.getChannel() + "'.",
                        parsed);
            }
        }

        // B3: Is the full combination valid?
        if (!configurationService.isValidMapping(
                parsed.getChannel(), parsed.getCampaignType(), parsed.getMediaFormat())) {

            // Distinguish unknown media format vs. invalid combination
            if (configurationService.getMediaFormats(
                    parsed.getChannel(), parsed.getCampaignType()).isEmpty()) {
                LOG.warn("Cartology filename validation failed: asset={}, "
                        + "reason=UNKNOWN_MEDIA_FORMAT", filename);
                return ValidationResult.failure(
                        ValidationErrorCode.UNKNOWN_MEDIA_FORMAT,
                        "Media Format '" + parsed.getMediaFormat()
                                + "' is not configured for Channel '" + parsed.getChannel()
                                + "'" + (parsed.getCampaignType() != null
                                ? " and Campaign Type '" + parsed.getCampaignType() + "'" : "")
                                + ".",
                        parsed);
            }

            LOG.warn("Cartology filename validation failed: asset={}, reason=INVALID_MAPPING",
                    filename);
            return ValidationResult.failure(
                    ValidationErrorCode.INVALID_MAPPING,
                    "Media Format '" + parsed.getMediaFormat()
                            + "' is not configured for Channel '" + parsed.getChannel()
                            + "'" + (parsed.getCampaignType() != null
                            ? " and Campaign Type '" + parsed.getCampaignType() + "'" : "")
                            + ".",
                    parsed);
        }

        // All checks passed
        LOG.debug("Cartology filename validation passed: asset={}", filename);
        return ValidationResult.success(parsed);
    }
}
