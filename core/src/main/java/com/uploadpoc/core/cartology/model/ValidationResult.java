package com.uploadpoc.core.cartology.model;

/**
 * Encapsulates the outcome of a filename validation, including the parsed
 * segments and (when invalid) a specific error code and human-readable message.
 */
public class ValidationResult {

    private final boolean valid;
    private final ValidationErrorCode errorCode;
    private final String message;
    private final String channel;
    private final String campaignType;
    private final String mediaFormat;
    private final String assetName;
    private final String extension;

    private ValidationResult(Builder builder) {
        this.valid = builder.valid;
        this.errorCode = builder.errorCode;
        this.message = builder.message;
        this.channel = builder.channel;
        this.campaignType = builder.campaignType;
        this.mediaFormat = builder.mediaFormat;
        this.assetName = builder.assetName;
        this.extension = builder.extension;
    }

    /* ---------- static factories ---------- */

    /**
     * Creates a successful validation result populated from the parsed filename.
     */
    public static ValidationResult success(ParsedFilename parsed) {
        return new Builder()
                .valid(true)
                .channel(parsed.getChannel())
                .campaignType(parsed.getCampaignType())
                .mediaFormat(parsed.getMediaFormat())
                .assetName(parsed.getAssetName())
                .extension(parsed.getExtension())
                .build();
    }

    /**
     * Creates a failure result with the given error code and message.
     */
    public static ValidationResult failure(ValidationErrorCode errorCode, String message) {
        return new Builder()
                .valid(false)
                .errorCode(errorCode)
                .message(message)
                .build();
    }

    /**
     * Creates a failure result with parsed filename context.
     */
    public static ValidationResult failure(ValidationErrorCode errorCode, String message,
                                           ParsedFilename parsed) {
        return new Builder()
                .valid(false)
                .errorCode(errorCode)
                .message(message)
                .channel(parsed.getChannel())
                .campaignType(parsed.getCampaignType())
                .mediaFormat(parsed.getMediaFormat())
                .assetName(parsed.getAssetName())
                .extension(parsed.getExtension())
                .build();
    }

    /* ---------- getters ---------- */

    public boolean isValid() {
        return valid;
    }

    public ValidationErrorCode getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public String getChannel() {
        return channel;
    }

    public String getCampaignType() {
        return campaignType;
    }

    public String getMediaFormat() {
        return mediaFormat;
    }

    public String getAssetName() {
        return assetName;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult{VALID, channel='" + channel
                    + "', campaignType='" + campaignType
                    + "', mediaFormat='" + mediaFormat + "'}";
        }
        return "ValidationResult{INVALID, errorCode=" + errorCode
                + ", message='" + message + "'}";
    }

    /* ---------- builder ---------- */

    public static class Builder {
        private boolean valid;
        private ValidationErrorCode errorCode;
        private String message;
        private String channel;
        private String campaignType;
        private String mediaFormat;
        private String assetName;
        private String extension;

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder errorCode(ValidationErrorCode errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder campaignType(String campaignType) {
            this.campaignType = campaignType;
            return this;
        }

        public Builder mediaFormat(String mediaFormat) {
            this.mediaFormat = mediaFormat;
            return this;
        }

        public Builder assetName(String assetName) {
            this.assetName = assetName;
            return this;
        }

        public Builder extension(String extension) {
            this.extension = extension;
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(this);
        }
    }
}
