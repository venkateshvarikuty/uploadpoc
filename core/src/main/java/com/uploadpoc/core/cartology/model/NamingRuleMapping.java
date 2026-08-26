package com.uploadpoc.core.cartology.model;

/**
 * Represents a valid Channel / Campaign Type / Media Format combination
 * as maintained by the 3rd-party application.
 * <p>
 * {@code campaignType} is nullable for channels that do not use campaign types
 * (e.g. Fresh Mag).
 */
public class NamingRuleMapping {

    private final String channel;
    private final String campaignType;
    private final String mediaFormat;

    public NamingRuleMapping(String channel, String campaignType, String mediaFormat) {
        this.channel = channel;
        this.campaignType = campaignType;
        this.mediaFormat = mediaFormat;
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

    @Override
    public String toString() {
        return "NamingRuleMapping{channel='" + channel
                + "', campaignType='" + campaignType
                + "', mediaFormat='" + mediaFormat + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NamingRuleMapping that = (NamingRuleMapping) o;
        if (!channel.equals(that.channel)) return false;
        if (campaignType != null ? !campaignType.equals(that.campaignType) : that.campaignType != null) return false;
        return mediaFormat.equals(that.mediaFormat);
    }

    @Override
    public int hashCode() {
        int result = channel.hashCode();
        result = 31 * result + (campaignType != null ? campaignType.hashCode() : 0);
        result = 31 * result + mediaFormat.hashCode();
        return result;
    }
}
