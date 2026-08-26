package com.uploadpoc.core.cartology.model;

/**
 * Error codes returned by filename validation.
 * <p>
 * Each code distinguishes a specific failure category so that callers receive
 * actionable feedback rather than a generic "invalid filename" message.
 */
public enum ValidationErrorCode {

    /** The filename does not match the expected structural pattern. */
    INVALID_FILENAME_FORMAT,

    /** The channel segment does not match any configured channel. */
    UNKNOWN_CHANNEL,

    /** The campaign type segment does not match any configured campaign type for the channel. */
    UNKNOWN_CAMPAIGN_TYPE,

    /** The media format segment does not match any configured media format. */
    UNKNOWN_MEDIA_FORMAT,

    /** All segments are individually known but the combination is not configured. */
    INVALID_MAPPING,

    /** The configuration cache is not available (e.g. not yet loaded). */
    CONFIGURATION_UNAVAILABLE
}
