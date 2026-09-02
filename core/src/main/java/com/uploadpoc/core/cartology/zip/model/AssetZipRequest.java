package com.uploadpoc.core.cartology.zip.model;

/**
 * DTO representing the incoming JSON request body for the asset ZIP creation API.
 * <p>
 * At least one search filter ({@code channel}, {@code campaignType}, or
 * {@code mediaFormat}) must be provided. All supplied filters are combined
 * with {@code AND} semantics.
 */
public class AssetZipRequest {

    private String channel;
    private String campaignType;
    private String mediaFormat;

    public AssetZipRequest() {
        // default constructor
    }

    public AssetZipRequest(String channel, String campaignType, String mediaFormat) {
        this.channel = channel;
        this.campaignType = campaignType;
        this.mediaFormat = mediaFormat;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getCampaignType() {
        return campaignType;
    }

    public void setCampaignType(String campaignType) {
        this.campaignType = campaignType;
    }

    public String getMediaFormat() {
        return mediaFormat;
    }

    public void setMediaFormat(String mediaFormat) {
        this.mediaFormat = mediaFormat;
    }

    /**
     * Validates that at least one filter is supplied and that supplied values are non-blank.
     *
     * @return {@code null} if valid, or a human-readable error message
     */
    public String validate() {
        boolean hasChannel = !isBlank(channel);
        boolean hasCampaignType = !isBlank(campaignType);
        boolean hasMediaFormat = !isBlank(mediaFormat);

        if (!hasChannel && !hasCampaignType && !hasMediaFormat) {
            return "At least one search filter ('channel', 'campaignType', or 'mediaFormat') must be specified.";
        }

        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
