package com.uploadpoc.core.cartology.service;

import com.uploadpoc.core.cartology.cache.CartologyConfigurationCache;
import com.uploadpoc.core.cartology.model.NamingRuleMapping;
import com.uploadpoc.core.cartology.normalizer.CartologyNameNormalizer;
import com.uploadpoc.core.cartology.repository.CartologyRepositoryService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.List;

/**
 * Business-logic service for Cartology naming-rule CRUD operations.
 * <p>
 * Orchestrates input validation, name normalisation, JCR persistence (via
 * {@link CartologyRepositoryService}), and cache refresh.
 */
@Component(service = CartologyNamingRulesService.class, immediate = true)
public class CartologyNamingRulesService {

    private static final Logger LOG = LoggerFactory.getLogger(CartologyNamingRulesService.class);

    @Reference
    private CartologyRepositoryService repositoryService;

    @Reference
    private CartologyConfigurationCache configurationCache;

    @Reference
    private CartologyNameNormalizer nameNormalizer;

    /**
     * Creates or updates a mapping. The operation is idempotent — repeated
     * calls with the same parameters produce the same JCR state.
     *
     * @param channel      business-friendly channel name (required)
     * @param campaignType business-friendly campaign type (nullable)
     * @param mediaFormat  business-friendly media format (required)
     * @throws IllegalArgumentException if required fields are missing
     * @throws RepositoryException      if JCR persistence fails
     */
    public void createOrUpdateMapping(String channel, String campaignType,
                                      String mediaFormat)
            throws RepositoryException {

        // --- Input validation ---
        if (isBlank(channel)) {
            throw new IllegalArgumentException("Field 'channel' is required.");
        }
        if (isBlank(mediaFormat)) {
            throw new IllegalArgumentException("Field 'mediaFormat' is required.");
        }

        // --- Normalise ---
        String normChannel = nameNormalizer.normalize(channel);
        String normCampaign = isBlank(campaignType) ? null
                : nameNormalizer.normalize(campaignType);
        String normMedia = nameNormalizer.normalize(mediaFormat);

        if (normChannel == null || normMedia == null) {
            throw new IllegalArgumentException(
                    "Channel or media format normalised to an empty value.");
        }

        // --- Persist ---
        repositoryService.createMapping(normChannel, normCampaign, normMedia);

        // --- Refresh cache ---
        configurationCache.refresh();

        LOG.info("Cartology mapping created/updated: channel='{}', campaignType='{}', "
                        + "mediaFormat='{}'", channel, campaignType, mediaFormat);
    }

    /**
     * Deletes a mapping.
     *
     * @return {@code true} if the mapping existed and was removed
     * @throws IllegalArgumentException if required fields are missing
     * @throws RepositoryException      if JCR persistence fails
     */
    public boolean deleteMapping(String channel, String campaignType,
                                 String mediaFormat)
            throws RepositoryException {

        if (isBlank(channel)) {
            throw new IllegalArgumentException("Field 'channel' is required.");
        }
        if (isBlank(mediaFormat)) {
            throw new IllegalArgumentException("Field 'mediaFormat' is required.");
        }

        String normChannel = nameNormalizer.normalize(channel);
        String normCampaign = isBlank(campaignType) ? null
                : nameNormalizer.normalize(campaignType);
        String normMedia = nameNormalizer.normalize(mediaFormat);

        if (normChannel == null || normMedia == null) {
            throw new IllegalArgumentException(
                    "Channel or media format normalised to an empty value.");
        }

        boolean deleted = repositoryService.deleteMapping(normChannel, normCampaign, normMedia);

        // Always refresh cache to reflect deletions
        configurationCache.refresh();

        if (deleted) {
            LOG.info("Cartology mapping deleted: channel='{}', campaignType='{}', "
                            + "mediaFormat='{}'", channel, campaignType, mediaFormat);
        } else {
            LOG.debug("Cartology mapping not found for deletion: channel='{}', "
                            + "campaignType='{}', mediaFormat='{}'",
                    channel, campaignType, mediaFormat);
        }

        return deleted;
    }

    /**
     * Returns all configured mappings (display-name form).
     */
    public List<NamingRuleMapping> getAllMappings() {
        return repositoryService.getAllMappings();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
