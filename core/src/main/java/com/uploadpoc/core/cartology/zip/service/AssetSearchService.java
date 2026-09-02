package com.uploadpoc.core.cartology.zip.service;

import com.day.cq.dam.api.Asset;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.uploadpoc.core.cartology.zip.config.CartologyAssetZipConfig;
import com.uploadpoc.core.cartology.zip.model.AssetZipRequest;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries AEM DAM for assets matching metadata filters.
 * <p>
 * Uses {@link QueryBuilder} with configurable metadata property paths and AND semantics.
 * All queries are executed via a read-only service user.
 * <p>
 * Query limit is set to {@code maxAssetsPerRequest + 1} to detect oversized result sets
 * efficiently without loading tens of thousands of matching assets into memory.
 */
@Component(service = AssetSearchService.class, immediate = true)
@Designate(ocd = CartologyAssetZipConfig.class)
public class AssetSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(AssetSearchService.class);

    @Reference
    private QueryBuilder queryBuilder;

    @Reference
    private ResourceResolverFactory resolverFactory;

    private String damRootPath;
    private String channelProperty;
    private String campaignTypeProperty;
    private String mediaFormatProperty;
    private String assetReaderSubService;
    private int maxAssetsPerRequest;

    @Activate
    @Modified
    protected void activate(CartologyAssetZipConfig config) {
        this.damRootPath = config.damRootPath();
        this.channelProperty = config.channelProperty();
        this.campaignTypeProperty = config.campaignTypeProperty();
        this.mediaFormatProperty = config.mediaFormatProperty();
        this.assetReaderSubService = config.assetReaderSubService();
        this.maxAssetsPerRequest = config.maxAssetsPerRequest();
        LOG.info("AssetSearchService configured [damRoot={}, maxAssets={}, readerService={}]",
                damRootPath, maxAssetsPerRequest, assetReaderSubService);
    }

    /**
     * Searches for DAM assets matching the given metadata filters.
     *
     * @param request the filter criteria
     * @return list of valid asset paths (never {@code null})
     * @throws MaxAssetsExceededException if matching assets exceed {@code maxAssetsPerRequest}
     */
    public List<String> findAssets(AssetZipRequest request) throws MaxAssetsExceededException {
        try (ResourceResolver resolver = getServiceResolver()) {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                LOG.error("Unable to obtain JCR Session from service resolver.");
                return Collections.emptyList();
            }

            Map<String, String> predicateMap = buildPredicateMap(request);

            Query query = queryBuilder.createQuery(
                    PredicateGroup.create(predicateMap), session);
            SearchResult result = query.getResult();

            List<Hit> hits = result.getHits();
            if (hits.size() > maxAssetsPerRequest) {
                throw new MaxAssetsExceededException(
                        "Too many assets matched the query (" + hits.size() + "+). Maximum allowed is "
                                + maxAssetsPerRequest + ".");
            }

            List<String> validPaths = new ArrayList<>();
            for (Hit hit : hits) {
                try {
                    String path = hit.getPath();
                    if (isValidAsset(resolver, path)) {
                        validPaths.add(path);
                    } else {
                        LOG.warn("Skipping invalid asset (no original rendition): {}", path);
                    }
                } catch (RepositoryException e) {
                    LOG.error("Error reading search hit", e);
                }
            }

            LOG.info("Asset search complete [channel={}, campaignType={}, mediaFormat={}, totalHits={}, validAssets={}]",
                    request.getChannel(), request.getCampaignType(),
                    request.getMediaFormat(), hits.size(), validPaths.size());

            return validPaths;

        } catch (LoginException e) {
            LOG.error("Failed to obtain service resolver for asset search", e);
            return Collections.emptyList();
        }
    }

    /**
     * Builds the QueryBuilder predicate map using AND semantics.
     * Limits the query results to {@code maxAssetsPerRequest + 1}.
     */
    private Map<String, String> buildPredicateMap(AssetZipRequest request) {
        Map<String, String> predicateMap = new HashMap<>();
        predicateMap.put("path", damRootPath);
        predicateMap.put("type", "dam:Asset");

        int propIndex = 1;

        if (request.getChannel() != null && !request.getChannel().trim().isEmpty()) {
            predicateMap.put(propIndex + "_property", channelProperty);
            predicateMap.put(propIndex + "_property.value", request.getChannel().trim());
            propIndex++;
        }

        if (request.getMediaFormat() != null && !request.getMediaFormat().trim().isEmpty()) {
            predicateMap.put(propIndex + "_property", mediaFormatProperty);
            predicateMap.put(propIndex + "_property.value", request.getMediaFormat().trim());
            propIndex++;
        }

        if (request.getCampaignType() != null && !request.getCampaignType().trim().isEmpty()) {
            predicateMap.put(propIndex + "_property", campaignTypeProperty);
            predicateMap.put(propIndex + "_property.value", request.getCampaignType().trim());
            propIndex++;
        }

        // Limit results to maxAssetsPerRequest + 1 to detect over-limit queries without excessive memory/fetch cost
        predicateMap.put("p.limit", String.valueOf(maxAssetsPerRequest + 1));

        return predicateMap;
    }

    /**
     * Validates that the resource at the given path is a proper DAM asset
     * with an original rendition.
     */
    private boolean isValidAsset(ResourceResolver resolver, String path) {
        Resource resource = resolver.getResource(path);
        if (resource == null) {
            return false;
        }
        Asset asset = resource.adaptTo(Asset.class);
        if (asset == null) {
            return false;
        }
        return asset.getOriginal() != null;
    }

    private ResourceResolver getServiceResolver() throws LoginException {
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, assetReaderSubService);
        return resolverFactory.getServiceResourceResolver(params);
    }

    /**
     * Thrown when the number of matching assets exceeds the configured limit.
     */
    public static class MaxAssetsExceededException extends Exception {
        public MaxAssetsExceededException(String message) {
            super(message);
        }
    }
}
