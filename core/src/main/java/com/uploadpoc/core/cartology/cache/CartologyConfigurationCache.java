package com.uploadpoc.core.cartology.cache;

import com.uploadpoc.core.cartology.model.NamingRuleMapping;
import com.uploadpoc.core.cartology.normalizer.CartologyNameNormalizer;
import com.uploadpoc.core.cartology.repository.CartologyRepositoryService;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe in-memory cache of the Cartology naming-rule configuration.
 * <p>
 * The cache is loaded from JCR at activation and refreshed after every API
 * mutation. It uses a <em>copy-on-write</em> pattern: a new snapshot is built
 * on each refresh and swapped in atomically via a {@code volatile} reference.
 * <p>
 * <b>Performance:</b> Filename validation hits this cache — it never queries
 * JCR directly.
 */
@Component(service = CartologyConfigurationCache.class, immediate = true)
public class CartologyConfigurationCache {

    private static final Logger LOG = LoggerFactory.getLogger(CartologyConfigurationCache.class);

    @Reference
    private CartologyRepositoryService repositoryService;

    @Reference
    private CartologyNameNormalizer nameNormalizer;

    /** Immutable snapshot replaced on every refresh. */
    private volatile CacheSnapshot snapshot = CacheSnapshot.EMPTY;

    /* ------------------------------------------------------------------
     * Lifecycle
     * ------------------------------------------------------------------ */

    @Activate
    protected void activate() {
        refresh();
    }

    /**
     * Rebuilds the cache from JCR. Called after every create/update/delete
     * and at service activation.
     */
    public void refresh() {
        try {
            List<NamingRuleMapping> mappings = repositoryService.getAllMappings();
            this.snapshot = CacheSnapshot.build(mappings, nameNormalizer);
            LOG.info("Cartology configuration cache refreshed — {} mappings loaded",
                    mappings.size());
        } catch (Exception e) {
            LOG.error("Failed to refresh Cartology configuration cache", e);
        }
    }

    /* ------------------------------------------------------------------
     * Query API (all lookups use normalised keys)
     * ------------------------------------------------------------------ */

    /**
     * @return {@code true} if a mapping for the given combination exists
     */
    public boolean isValidMapping(String channel, String campaignType, String mediaFormat) {
        String normChannel = nameNormalizer.normalize(channel);
        String normCampaign = campaignType != null ? nameNormalizer.normalize(campaignType) : "";
        String normMedia = nameNormalizer.normalize(mediaFormat);
        if (normChannel == null || normMedia == null) {
            return false;
        }
        String key = buildKey(normChannel, normCampaign);
        Set<String> formats = snapshot.mappings.get(key);
        return formats != null && formats.contains(normMedia);
    }

    public boolean isChannelConfigured(String channel) {
        String normChannel = nameNormalizer.normalize(channel);
        return normChannel != null && snapshot.channels.contains(normChannel);
    }

    /**
     * @return {@code true} if the channel is known to use campaign types
     */
    public boolean channelHasCampaignType(String channel) {
        String normChannel = nameNormalizer.normalize(channel);
        if (normChannel == null) return false;
        Boolean has = snapshot.channelHasCampaignType.get(normChannel);
        return has != null && has;
    }

    public boolean isCampaignTypeConfigured(String channel, String campaignType) {
        String normChannel = nameNormalizer.normalize(channel);
        String normCampaign = campaignType != null ? nameNormalizer.normalize(campaignType) : "";
        if (normChannel == null) return false;
        String key = buildKey(normChannel, normCampaign);
        return snapshot.mappings.containsKey(key);
    }

    public Set<String> getMediaFormats(String channel, String campaignType) {
        String normChannel = nameNormalizer.normalize(channel);
        String normCampaign = campaignType != null ? nameNormalizer.normalize(campaignType) : "";
        if (normChannel == null) return Collections.emptySet();
        String key = buildKey(normChannel, normCampaign);
        Set<String> formats = snapshot.mappings.get(key);
        return formats != null ? Collections.unmodifiableSet(formats) : Collections.emptySet();
    }

    /** @return all normalised channel names */
    public Set<String> getAllChannels() {
        return Collections.unmodifiableSet(snapshot.channels);
    }

    public boolean isLoaded() {
        return snapshot != CacheSnapshot.EMPTY;
    }

    /* ------------------------------------------------------------------
     * Internal snapshot
     * ------------------------------------------------------------------ */

    private static String buildKey(String normChannel, String normCampaign) {
        return normChannel + "|" + (normCampaign != null ? normCampaign : "");
    }

    /**
     * Immutable snapshot of the cache state.
     */
    private static final class CacheSnapshot {

        static final CacheSnapshot EMPTY = new CacheSnapshot(
                Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap());

        /** Key: "NORM_CHANNEL|NORM_CAMPAIGN" → Set of normalised media-format names */
        final Map<String, Set<String>> mappings;

        /** All known normalised channel names */
        final Set<String> channels;

        /** Channel → whether it uses campaign types */
        final Map<String, Boolean> channelHasCampaignType;

        CacheSnapshot(Map<String, Set<String>> mappings,
                      Set<String> channels,
                      Map<String, Boolean> channelHasCampaignType) {
            this.mappings = mappings;
            this.channels = channels;
            this.channelHasCampaignType = channelHasCampaignType;
        }

        static CacheSnapshot build(List<NamingRuleMapping> list,
                                   CartologyNameNormalizer normalizer) {
            Map<String, Set<String>> mappings = new HashMap<>();
            Set<String> channels = new HashSet<>();
            Map<String, Boolean> hasCampaign = new HashMap<>();

            for (NamingRuleMapping m : list) {
                String normChannel = normalizer.normalize(m.getChannel());
                String normCampaign = m.getCampaignType() != null
                        ? normalizer.normalize(m.getCampaignType()) : "";
                String normMedia = normalizer.normalize(m.getMediaFormat());

                if (normChannel == null || normMedia == null) {
                    continue;
                }

                channels.add(normChannel);

                boolean hasCampaignType = m.getCampaignType() != null
                        && !m.getCampaignType().trim().isEmpty();

                // A channel uses campaign types if any of its mappings has one
                Boolean existing = hasCampaign.get(normChannel);
                if (existing == null || hasCampaignType) {
                    hasCampaign.put(normChannel, existing != null ? existing || hasCampaignType
                            : hasCampaignType);
                }

                String key = buildKey(normChannel, normCampaign);
                mappings.computeIfAbsent(key, k -> new HashSet<>()).add(normMedia);
            }

            return new CacheSnapshot(mappings, channels, hasCampaign);
        }
    }
}
