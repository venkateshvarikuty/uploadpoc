package com.uploadpoc.core.cartology.model;

/**
 * Result of parsing an asset filename into its constituent segments.
 * <p>
 * {@code campaignType} is {@code null} when the channel does not use campaign types.
 */
public class ParsedFilename {

    private final String channel;
    private final String campaignType;
    private final String mediaFormat;
    private final String assetName;
    private final String extension;

    public ParsedFilename(String channel, String campaignType,
                          String mediaFormat, String assetName, String extension) {
        this.channel = channel;
        this.campaignType = campaignType;
        this.mediaFormat = mediaFormat;
        this.assetName = assetName;
        this.extension = extension;
    }

    public String getChannel() {
        return channel;
    }

    /** @return the campaign type, or {@code null} if the channel has no campaign types */
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
        return "ParsedFilename{channel='" + channel
                + "', campaignType='" + campaignType
                + "', mediaFormat='" + mediaFormat
                + "', assetName='" + assetName
                + "', extension='" + extension + "'}";
    }
}
