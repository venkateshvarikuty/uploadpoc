package com.uploadpoc.core.cartology.service;

import com.uploadpoc.core.cartology.cache.CartologyConfigurationCache;
import com.uploadpoc.core.cartology.model.NamingRuleMapping;
import com.uploadpoc.core.cartology.repository.CartologyRepositoryService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Read-only configuration query service for Cartology naming rules.
 * <p>
 * All lookups read from the {@link CartologyConfigurationCache}. This service
 * never accesses JCR directly and is safe to call from hot paths such as
 * asset-upload validation.
 */
@Component(service = CartologyConfigurationService.class, immediate = true)
public class CartologyConfigurationService {

    private static final Logger LOG = LoggerFactory.getLogger(CartologyConfigurationService.class);

    @Reference
    private CartologyConfigurationCache cache;

    @Reference
    private CartologyRepositoryService repositoryService;

    /**
     * Checks whether the exact Channel + Campaign Type + Media Format combination
     * exists in the configuration.
     */
    public boolean isValidMapping(String channel, String campaignType, String mediaFormat) {
        return cache.isValidMapping(channel, campaignType, mediaFormat);
    }

    /** @return {@code true} if the channel is known */
    public boolean isChannelConfigured(String channel) {
        return cache.isChannelConfigured(channel);
    }

    /** @return {@code true} if the channel expects a campaign type */
    public boolean channelHasCampaignType(String channel) {
        return cache.channelHasCampaignType(channel);
    }

    /**
     * @return {@code true} if the campaign type is known for the given channel
     */
    public boolean isCampaignTypeConfigured(String channel, String campaignType) {
        return cache.isCampaignTypeConfigured(channel, campaignType);
    }

    /**
     * @return the set of normalised media-format names for the channel
     *         (and optional campaign type), or an empty set
     */
    public Set<String> getMediaFormats(String channel, String campaignType) {
        return cache.getMediaFormats(channel, campaignType);
    }

    /**
     * @return all known normalised channel names
     */
    public Set<String> getAllChannels() {
        return cache.getAllChannels();
    }

    /**
     * @return all configured mappings (display-name form)
     */
    public List<NamingRuleMapping> getAllMappings() {
        return repositoryService.getAllMappings();
    }

    /** @return {@code true} if the cache has been loaded at least once */
    public boolean isCacheLoaded() {
        return cache.isLoaded();
    }
}
